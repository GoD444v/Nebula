package com.example.nebula.data

import android.util.Log
import com.example.nebula.data.lyrics.KpoeLyrics
import com.example.nebula.data.lyrics.KugouLyrics
import com.example.nebula.data.lyrics.UnisonLyrics
import com.example.nebula.data.models.LyricLine
import com.example.nebula.data.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/** Plain holder — deliberately NOT @Serializable (see search() comment). */
data class LyricsSearchHit(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)

/**
 * Free synced lyrics via LRCLIB (lrclib.net, no key needed).
 *
 * Why search-only (no /api/get): the exact endpoint needs title + artist +
 * album + duration, but at fetch time we know neither album nor duration
 * (the player hasn't reported it yet), so it could only miss. Search with a
 * cleaned title plus fallbacks is what actually finds YouTube-sourced songs.
 */
class LyricsRepository {

    /**
     * Lyrics already fetched this session, keyed on the provider's own lookup input.
     *
     * All four providers are network APIs. Without a cache, a downloaded song is
     * unplayable *and* un-lyricable offline even when its lyrics were fetched a minute
     * ago, which reads as a bug rather than as an absent connection. The reference
     * implementation solves this at download time by writing a lyrics row while the
     * download runs; this is the session-scoped version, which covers the common case
     * without needing a schema change.
     *
     * Case- and whitespace-insensitive, because providers match on title/artist text and
     * the same song can arrive with different capitalisation from different screens.
     */
    private val cache = mutableMapOf<String, LyricsResponse>()

    /** The key every provider lookup agrees on. */
    private fun cacheKey(title: String, artist: String) =
        "${title.trim().lowercase()}|${artist.trim().lowercase()}"

    /** Call after a successful fetch so the next play of this song needs no network. */
    fun remember(title: String, artist: String, lyrics: LyricsResponse) {
        if (title.isBlank()) return
        cache[cacheKey(title, artist)] = lyrics
    }

    /** Stored lyrics for this song, or null if never fetched this session. */
    fun cached(title: String, artist: String): LyricsResponse? {
        if (title.isBlank()) return null
        return cache[cacheKey(title, artist)]
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
        install(ContentNegotiation) { json(json) }
    }

    /**
     * Fan-out: all providers race in parallel (Echo's concept, original code).
     * Selection is by tier, then by provider priority: synced+words, then
     * synced without words, then plain text, then null when nobody has it.
     * Providers never throw except on cancellation, so no per-provider
     * try/catch here.
     */
    suspend fun getSyncedLyrics(title: String, artist: String, durationMs: Long): LyricsResponse? {
        // Cache first, and never for the blank-title case below it — a cached miss must
        // not short-circuit into returning null without trying the network once.
        cached(title, artist)?.let { return it }
        return fetchFromProviders(title, artist, durationMs)
    }

    private suspend fun fetchFromProviders(
        title: String,
        artist: String,
        durationMs: Long
    ): LyricsResponse? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val results = coroutineScope {
                listOf(
                    async { UnisonLyrics.fetch(title, artist, durationMs) },
                    async { KpoeLyrics.fetch(title, artist, durationMs) },
                    async { KugouLyrics.fetch(title, artist, durationMs) },
                    async { fetchLrclib(title, artist, durationMs) }
                ).awaitAll()
            }
            val names = listOf("unison", "kpoe", "kugou", "lrclib")
            // A "synced" result whose lines all sit at 0ms has no timing info —
            // offering it as synced makes every tap seek(0) = restart. Demote.
            val sane = results.map { r ->
                if (r != null && r.isSynced && r.lines.all { it.timeMs <= 0 }) {
                    val text = r.plainText.ifBlank { r.lines.joinToString("\n") { it.text } }.trim()
                    if (text.isNotBlank()) LyricsResponse(emptyList(), text) else null
                } else r
            }
            // Ranked in three tiers, provider priority (names order) kept inside
            // each: a word-timed result beats a wordless synced one, which beats
            // plain text. isSynced alone is not enough — plain LRC satisfies it
            // while carrying no words, and every animated highlight style
            // (Karaoke/Apple/Apple V2/Fade/Glow) degrades to the same whole-line
            // look without them, which is the "all styles look same" bug.
            sane.forEachIndexed { i, r ->
                if (r != null && r.isSynced && r.hasWordTimings) {
                    diag("${names[i]} won synced+words (${r.lines.size} lines)")
                    return@withContext pick(title, artist, r)
                }
            }
            sane.forEachIndexed { i, r ->
                if (r != null && r.isSynced) {
                    diag("${names[i]} won synced (wordless, ${r.lines.size} lines)")
                    return@withContext pick(title, artist, r)
                }
            }
            sane.forEachIndexed { i, r ->
                if (r != null && r.plainText.isNotBlank()) {
                    diag("${names[i]} won plain")
                    return@withContext pick(title, artist, LyricsResponse(emptyList(), r.plainText))
                }
            }
            diag("no lyrics for '$title'")
            null
        }

    /** Stores a winner so the next play of this song needs no network. */
    private fun pick(title: String, artist: String, lyrics: LyricsResponse): LyricsResponse {
        remember(title, artist, lyrics)
        return lyrics
    }

    /**
     * Returns null only when nothing was found. Callers must distinguish
     * loading from not-found with their own flag — null alone can't do both.
     */
    private suspend fun fetchLrclib(title: String, artist: String, durationMs: Long): LyricsResponse? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val ct = cleanTitle(title)
            val ca = cleanArtist(artist)
            val targetSec = durationMs / 1000

            // Strategy chain: precise first, looser on each miss. Each step is
            // one cheap search; the first non-empty hit list wins.
            val hits = search(trackName = ct, artistName = ca)
                .ifEmpty { search(trackName = ct) }
                .ifEmpty { search(query = "$ca $ct".trim()) }
                .ifEmpty { search(query = ct) }
                .ifEmpty {
                    if (ct != title.trim()) search(trackName = title.trim(), artistName = artist.trim())
                    else emptyList()
                }
            diag("title='$title' artist='$artist' → '$ct' / '$ca' hits=${hits.size}")
            if (hits.isEmpty()) return@withContext null

            // Prefer synced lines whose recording length is near what's playing
            // (covers music-video vs album length drift); else closest synced;
            // else closest plain text. Duration 0 = unknown → skip the window.
            val synced = hits.filter { !it.syncedLyrics.isNullOrBlank() }
            val picked = if (targetSec > 0) {
                synced.filter { it.duration?.let { d -> abs(d - targetSec) <= 10 } ?: false }
                    .minByOrNull { abs((it.duration ?: 0.0) - targetSec) }
                    ?: synced.minByOrNull { abs((it.duration ?: 0.0) - targetSec) }
            } else {
                synced.firstOrNull()
            }
            if (picked != null) {
                parseLrc(picked.syncedLyrics)?.let {
                    diag("synced from '${picked.trackName}'")
                    return@withContext LyricsResponse(it)
                }
            }
            hits.minByOrNull { abs((it.duration ?: 0.0) - targetSec) }
                ?.plainLyrics?.takeIf { it.isNotBlank() }
                ?.let { return@withContext LyricsResponse(emptyList(), it.trim()) }

            null
        }

    /**
     * Hand-parsed DOM instead of @Serializable: this module doesn't apply the
     * kotlinx-serialization compiler plugin (only the runtime), so no
     * serializer is generated for app classes — body<List<…>>() always throws.
     * Same hand-rolled style SearchRepository already uses for browse JSON.
     */
    private suspend fun search(
        trackName: String? = null,
        artistName: String? = null,
        query: String? = null
    ): List<LyricsSearchHit> = try {
        val text = http.get("https://lrclib.net/api/search") {
            if (trackName != null) parameter("track_name", trackName)
            if (artistName != null) parameter("artist_name", artistName)
            if (query != null) parameter("q", query)
        }.bodyAsText()
        Json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val synced = o["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plain = o["plainLyrics"]?.jsonPrimitive?.contentOrNull
            if (synced.isNullOrBlank() && plain.isNullOrBlank()) return@mapNotNull null
            LyricsSearchHit(
                trackName = o["trackName"]?.jsonPrimitive?.contentOrNull,
                artistName = o["artistName"]?.jsonPrimitive?.contentOrNull,
                duration = o["duration"]?.jsonPrimitive?.doubleOrNull,
                syncedLyrics = synced,
                plainLyrics = plain
            )
        }
    } catch (e: Exception) {
        diag("search(t=$trackName a=$artistName q=$query) failed: ${e::class.simpleName} ${e.message}")
        emptyList()
    }

    /**
     * JVM-safe diagnostics: println reaches unit-test output; Log reaches
     * logcat on device. android.util.Log throws on plain JVM tests.
     */
    private fun diag(msg: String) {
        println("NebulaLyrics: $msg")
        runCatching { Log.d("NebulaLyrics", msg) }
    }

    /** Strips YouTube junk: "(Official Video)", "[Remastered]", "| Channel", "feat. X". */
    internal fun cleanTitle(title: String): String {
        var out = title.trim()
        out = out.replace(Regex("""\s*[\(\[].*?(official|video|audio|lyric|visualizer|remaster|live|version|edit|radio|clean|explicit|hd|hq|4k).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("""\s*\|.*$"""), "")
        out = out.replace(Regex("""\s*[\(\[].*?(feat|ft)\..*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("""\s+(feat|ft)\..*$""", RegexOption.IGNORE_CASE), "")
        return out.trim().ifBlank { title.trim() }
    }

    /** "A & B", "A, B", "A feat. B" → "A". */
    internal fun cleanArtist(artist: String): String {
        var out = artist.trim()
        for (sep in listOf(" & ", ", ", " feat. ", " feat ", " ft. ", " ft ", " x ", " with ")) {
            val i = out.indexOf(sep, ignoreCase = true)
            if (i > 0) {
                out = out.substring(0, i)
                break
            }
        }
        return out.trim().ifBlank { artist.trim() }
    }

    /** Parses `[mm:ss.xx] words` lines. Null when there is nothing usable. */
    private fun parseLrc(raw: String?): List<LyricLine>? {
        if (raw.isNullOrBlank()) return null
        val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
        val out = ArrayList<LyricLine>(64)
        for (line in raw.lineSequence()) {
            val match = tag.find(line) ?: continue
            val text = line.substring(match.range.last + 1).trim()
            if (text.isBlank()) continue
            val min = match.groupValues[1].toLongOrNull() ?: continue
            val sec = match.groupValues[2].toLongOrNull() ?: continue
            val frac = match.groupValues[3]
            val ms = min * 60_000 + sec * 1_000 + when (frac.length) {
                0 -> 0L
                2 -> frac.toLongOrNull()?.times(10) ?: 0L // centiseconds
                else -> frac.take(3).toLongOrNull() ?: 0L // milliseconds
            }
            out.add(LyricLine(ms, text))
        }
        if (out.isEmpty()) return null
        out.sortBy { it.timeMs }
        return out
    }
}
