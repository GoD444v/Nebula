package nebula.music.data.lyrics

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricsResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fills in [LyricLine.translation] with an English rendering, computed on the phone.
 *
 * Why here and not in a provider: the live APIs were checked before this was
 * written, and none of them carries a translation — Unison's TTML has no
 * `<translation>` element and its JSON has no such key, LyricsPlus ignores
 * `language=`, and Kugou/LRCLIB have no field for it. So the English has to be
 * produced locally. ML Kit does that offline once a language pair is downloaded,
 * needs no API key, and never sends a lyric off the device.
 */
object LyricsTranslator {

    private const val TAG = "LyricsTranslator"

    /** Bounded so one long session cannot grow this without limit. */
    private const val CACHE_MAX = 2000

    /**
     * Line text -> English, reused across songs. Choruses repeat within a song and
     * across songs, so a replay re-translates nothing. Only ever touched from the
     * caller's coroutine, which is the lyrics job on the main dispatcher — no lock.
     */
    private val cache = object : LinkedHashMap<String, String>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > CACHE_MAX
    }

    /**
     * One client per source language. Deliberately never closed: this object lives
     * for the life of the process, and closing a client that a skip just cancelled
     * mid-translation would break the next lookup. The script sniff below can only
     * ever name four languages, so four is the ceiling.
     */
    private val clients = mutableMapOf<String, Translator>()

    private val KANA = '\u3040'..'\u30FF'
    private val HANGUL = '\uAC00'..'\uD7AF'
    private val HANGUL_JAMO = '\u1100'..'\u11FF'
    private val HAN = '\u4E00'..'\u9FFF'
    private val CYRILLIC = '\u0400'..'\u04FF'

    /**
     * The same response with [LyricLine.translation] filled in, or [response]
     * itself when there is nothing to do — no lines, already English, or the model
     * could not be fetched. Callers can compare instances to skip a re-emit.
     */
    suspend fun translate(response: LyricsResponse): LyricsResponse {
        val lines = response.lines
        if (lines.isEmpty()) return response

        val source = sourceLanguage(lines) ?: return response
        val translator = client(source)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } catch (e: CancellationException) {
            // A skip cancels the lyrics job. Swallowing this here would keep the
            // loop below alive against a dead coroutine for every remaining line.
            throw e
        } catch (e: Exception) {
            // Offline on first use, or no Play Services. The lyrics still show,
            // just without the extra line.
            Log.w(TAG, "no translation model for $source", e)
            return response
        }

        var changed = false
        val out = lines.map { line ->
            val text = line.text.trim()
            // Blank = instrumental gap. Latin = an English hook inside a Japanese
            // song, which ML Kit would only pass through or mangle.
            if (text.isEmpty() || !hasTranslatableScript(text)) return@map line

            val english = cache[text] ?: try {
                translator.translate(text).await().also { cache[text] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "translate failed: $text", e)
                return@map line
            }
            changed = true
            line.copy(translation = english)
        }
        return if (changed) response.copy(lines = out) else response
    }

    /**
     * The song's language, read off the script its lyrics are written in.
     * `null` means the lyrics are already Latin, so nothing needs translating.
     *
     * A script sniff rather than ML Kit's language-id model: kana and hangul are
     * unambiguous, and this costs no second dependency and no extra model file.
     *
     * ponytail: a song written entirely in kanji with no kana reads as Chinese, and
     * every Cyrillic language reads as Russian. Both are wrong only for songs whose
     * script gives no signal, and ML Kit cannot tell them apart either — add
     * language-id if those cases ever show up.
     *
     * Internal rather than private only so LyricsLanguageTest can reach it: a wrong
     * answer here is silent, and a silent wrong answer means a Japanese song simply
     * never grows an English line.
     */
    internal fun sourceLanguage(lines: List<LyricLine>): String? {
        // A sample, not the whole song: one English hook line among Japanese ones
        // must not outvote the other seventy-five.
        val sample = lines.asSequence()
            .map { it.text }
            .filter { it.isNotBlank() }
            .take(12)
            .joinToString(" ")
        return when {
            sample.any { it in KANA } -> TranslateLanguage.JAPANESE
            sample.any { it in HANGUL || it in HANGUL_JAMO } -> TranslateLanguage.KOREAN
            sample.any { it in HAN } -> TranslateLanguage.CHINESE
            sample.any { it in CYRILLIC } -> TranslateLanguage.RUSSIAN
            else -> null
        }
    }

    internal fun hasTranslatableScript(text: String): Boolean =
        text.any { it in KANA || it in HANGUL || it in HANGUL_JAMO || it in HAN || it in CYRILLIC }

    private fun client(source: String): Translator = clients.getOrPut(source) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }
}

/** Turns an ML Kit task into something a coroutine can wait on and cancel. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
