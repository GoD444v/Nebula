package com.example.nebula.data.lyrics

import com.example.nebula.data.models.LyricLine
import com.example.nebula.data.models.LyricWord
import com.example.nebula.data.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Unison lyrics (Better Lyrics crowd-sourced API, no key needed).
 *  GET /lyrics?song=&artist=&duration=(sec) → { success, data: { lyrics, format }}.
 *  Hand-parsed JSON DOM (no serialization plugin here). Null on any miss. */
object UnisonLyrics {

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
    }

    suspend fun fetch(title: String, artist: String, durationMs: Long): LyricsResponse? =
        withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) return@withContext null
                val text = http.get("https://unison.boidu.dev/lyrics") {
                    parameter("song", title.trim())
                    if (artist.isNotBlank()) parameter("artist", artist.trim())
                    if (durationMs > 0) parameter("duration", durationMs / 1000)
                }.bodyAsText()
                parseEnvelope(Json.parseToJsonElement(text) as? JsonObject)
                    ?: fetchFuzzy(title.trim(), artist.trim())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    /** Exact envelope shared by /lyrics and /lyrics/{id}. */
    private fun parseEnvelope(root: JsonObject?): LyricsResponse? {
        if (root == null) return null
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) return null
        val data = root["data"] as? JsonObject ?: return null
        val raw = data["lyrics"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return null
        return when (data["format"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "ttml" -> parseTtml(raw)?.let { LyricsResponse(it) }
            "lrc" -> parseLrc(raw)?.let { LyricsResponse(it) }
            else -> LyricsResponse(emptyList(), raw.trim())
        }
    }

    /** Fuzzy fallback: /lyrics/search?q= → closest title+artist id → /lyrics/{id}. */
    private suspend fun fetchFuzzy(title: String, artist: String): LyricsResponse? {
        val text = http.get("https://unison.boidu.dev/lyrics/search") {
            parameter("q", "$title $artist".trim())
        }.bodyAsText()
        val items = (Json.parseToJsonElement(text) as? JsonObject)
            ?.get("data") as? JsonArray ?: return null
        val ct = title.lowercase()
        val ca = artist.lowercase()
        val hit = items.mapNotNull { it as? JsonObject }.firstOrNull { o ->
            val s = o["song"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val a = o["artist"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val f = o["format"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            (s == ct || (ct.isNotEmpty() && s.contains(ct)) || (s.isNotEmpty() && ct.contains(s))) &&
                (ca.isEmpty() || a.contains(ca) || ca.contains(a)) &&
                (f == "ttml" || f == "lrc")
        } ?: return null
        val id = hit["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val detail = http.get("https://unison.boidu.dev/lyrics/$id").bodyAsText()
        return parseEnvelope(Json.parseToJsonElement(detail) as? JsonObject)
    }

    /** TTML <p begin> lines + <span begin end> words (Apple-style richsync). */
    private fun parseTtml(raw: String): List<LyricLine>? = try {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(raw))
        val out = ArrayList<LyricLine>(64)
        val lineText = StringBuilder()
        val spanText = StringBuilder()
        var words = ArrayList<LyricWord>(8)
        var inP = false
        var inSpan = false
        var lineBegin = -1L
        var spanStart = -1L
        var spanEnd = -1L
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "p" -> {
                        inP = true
                        lineBegin = ttmlTime(parser.getAttributeValue(null, "begin")) ?: -1L
                        lineText.clear()
                        words = ArrayList(8)
                    }
                    "span" -> if (inP) {
                        inSpan = true
                        spanText.clear()
                        spanStart = ttmlTime(parser.getAttributeValue(null, "begin")) ?: -1L
                        spanEnd = ttmlTime(parser.getAttributeValue(null, "end")) ?: -1L
                    }
                }
                XmlPullParser.TEXT -> if (inSpan) spanText.append(parser.text) else if (inP) lineText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "span" -> {
                        inSpan = false
                        val w = spanText.toString().trim()
                        if (inP && w.isNotEmpty() && spanStart >= 0) {
                            words.add(LyricWord(w, spanStart, if (spanEnd >= 0) spanEnd else spanStart))
                        }
                        if (inP) lineText.append(spanText)
                    }
                    "p" -> {
                        inP = false
                        val text = lineText.toString().trim().replace(Regex("\\s+"), " ")
                        if (lineBegin >= 0 && text.isNotEmpty()) out.add(LyricLine(lineBegin, text, words.toList()))
                    }
                }
            }
            ev = parser.next()
        }
        out.sortBy { it.timeMs }
        out.takeIf { it.isNotEmpty() }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        null
    }

    /** Parses `[mm:ss.xx] words` lines. Null when there is nothing usable. */
    private fun parseLrc(raw: String): List<LyricLine>? {
        val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
        val out = ArrayList<LyricLine>(64)
        for (line in raw.lineSequence()) {
            val m = tag.find(line) ?: continue
            val text = line.substring(m.range.last + 1).trim()
            if (text.isBlank()) continue
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val frac = m.groupValues[3]
            val ms = min * 60_000 + sec * 1_000 + when (frac.length) {
                0 -> 0L
                2 -> (frac.toLongOrNull() ?: 0L) * 10 // centiseconds
                else -> frac.take(3).toLongOrNull() ?: 0L // milliseconds
            }
            out.add(LyricLine(ms, text))
        }
        if (out.isEmpty()) return null
        out.sortBy { it.timeMs }; return out
    }

    /** TTML clock: "12.000" or "m:ss.mmm" (also h:mm:ss.mmm) → ms. */
    private fun ttmlTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val parts = s.trim().split(':')
            val last = parts.last().split('.', ',')
            var ms = last[0].toLong() * 1_000 + (last.getOrNull(1)?.take(3)?.padEnd(3, '0')?.toLong() ?: 0L)
            var unit = 60_000L
            for (i in parts.size - 2 downTo 0) { ms += parts[i].toLong() * unit; unit *= 60 }
            ms
        } catch (_: Exception) { null }
    }
}
