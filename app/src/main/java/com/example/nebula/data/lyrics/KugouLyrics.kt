package com.example.nebula.data.lyrics

import android.util.Base64
import com.example.nebula.data.models.LyricLine
import com.example.nebula.data.models.LyricWord
import com.example.nebula.data.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

object KugouLyrics {
    private const val SEARCH_URL = "https://lyrics.kugou.com/search"
    private const val DOWNLOAD_URL = "https://lyrics.kugou.com/download"
    private val KEY = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105)
    private val json = Json { ignoreUnknownKeys = true }
    private val lineHead = Regex("""^((?:\[\d+,\d+\])+)(.*)$""")
    private val headTag = Regex("""\[(\d+),(\d+)\]""")
    private val wordTag = Regex("""<(\d+),(\d+)(?:,\d+)?>([^<]*)""")
    private val lrcLine = Regex("""^((?:\[\d+:\d+(?:[.:]\d+)?\])+)(.*)$""")
    private val lrcTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetch(title: String, artist: String, durationMs: Long): LyricsResponse? {
        return try {
            val query = "$title $artist".trim()
            val searchText = http.get(SEARCH_URL) {
                parameter("ver", "1"); parameter("man", "yes"); parameter("client", "pc")
                url.parameters.append("keyword", query)
                parameter("duration", durationMs.coerceAtLeast(0))
            }.bodyAsText()
            val cands = json.parseToJsonElement(searchText).jsonObject["candidates"]?.jsonArray ?: return null
            var bestId = ""; var bestKey = ""; var bestDelta = Double.MAX_VALUE
            var firstId = ""; var firstKey = ""
            for (c in cands) {
                val o = c.jsonObject
                val id = o["id"]?.jsonPrimitive?.content ?: continue
                val key = o["accesskey"]?.jsonPrimitive?.content ?: continue
                if (firstId.isEmpty()) { firstId = id; firstKey = key }
                val durRaw = o["duration"]?.jsonPrimitive?.longOrNull
                    ?: o["duration"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: o["duration"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: continue
                // Response unit unverified from here (endpoint is region-fenced):
                // compare in seconds, treating >100000 as ms first.
                val durSec = if (durRaw > 100000) durRaw / 1000.0 else durRaw.toDouble()
                val d = kotlin.math.abs(durSec - durationMs / 1000.0)
                if (d < bestDelta) { bestDelta = d; bestId = id; bestKey = key }
            }
            // Duration unknown at fetch time (player hasn't reported it yet):
            // relevance order beats a bogus closest-to-zero pick.
            if (durationMs <= 0) { bestId = firstId; bestKey = firstKey }
            if (bestId.isEmpty()) return null
            download(bestId, bestKey, "krc")?.let { parseKrc(it) }?.takeIf { it.lines.isNotEmpty() }
                ?: download(bestId, bestKey, "lrc")?.let { parseLrc(it) }?.takeIf { it.lines.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun download(id: String, key: String, fmt: String): String? {
        return try {
            val url = "$DOWNLOAD_URL?ver=1&client=pc&id=${id.encodeURLQueryComponent()}" +
                "&accesskey=${key.encodeURLQueryComponent()}&fmt=$fmt&charset=utf8"
            val el = json.parseToJsonElement(http.get(url).bodyAsText()).jsonObject
            val content = el["content"]?.jsonPrimitive?.content ?: return null
            if (content.isBlank()) return null
            val raw = Base64.decode(content, Base64.DEFAULT)
            if (fmt == "krc") decodeKrc(raw) else String(raw, Charsets.UTF_8)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun decodeKrc(raw: ByteArray): String? {
        return try {
            if (raw.size <= 4) return null
            val xored = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor KEY[i % KEY.size].toInt()).toByte() }
            String(inflate(xored), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        return try {
            inf.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) { if (inf.needsInput()) break else continue }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally { inf.end() }
    }

    private fun parseKrc(text: String): LyricsResponse {
        val lines = mutableListOf<LyricLine>()
        for (raw in text.split("\n")) {
            val line = raw.trim().trim('\uFEFF').ifEmpty { continue }
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
                line.startsWith("[by:") || line.startsWith("[offset:")) continue
            val m = lineHead.find(line) ?: continue
            val tags = headTag.findAll(m.groupValues[1]).toList().ifEmpty { continue }
            val body = m.groupValues[2]
            val words = wordTag.findAll(body).map {
                Triple(it.groupValues[1].toLongOrNull() ?: 0L, it.groupValues[2].toLongOrNull() ?: 0L, it.groupValues[3])
            }.filter { it.third.isNotEmpty() }.toList().ifEmpty { continue }
            for (t in tags) {
                val start = t.groupValues[1].toLongOrNull() ?: continue
                val ws = words.map { (off, dur, txt) -> LyricWord(txt, start + off, start + off + dur) }
                val txt = StringBuilder().apply { words.forEach { append(it.third) } }.toString().trim()
                if (txt.isNotEmpty()) lines.add(LyricLine(start, txt, ws))
            }
        }
        lines.sortBy { it.timeMs }
        return LyricsResponse(lines, lines.joinToString("\n") { it.text })
    }

    private fun parseLrc(text: String): LyricsResponse {
        val lines = mutableListOf<LyricLine>()
        for (raw in text.split("\n")) {
            val line = raw.trim().trim('\uFEFF').ifEmpty { continue }
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
                line.startsWith("[by:") || line.startsWith("[offset:")) continue
            val m = lrcLine.find(line) ?: continue
            val txt = m.groupValues[2].trim().ifEmpty { continue }
            for (t in lrcTag.findAll(m.groupValues[1])) {
                val min = t.groupValues[1].toLongOrNull() ?: continue
                val sec = t.groupValues[2].toLongOrNull() ?: continue
                val frac = t.groupValues[3]
                val ms = when {
                    frac.isEmpty() -> 0L
                    frac.length == 2 -> frac.toLongOrNull()?.times(10) ?: 0L
                    frac.length == 3 -> frac.toLongOrNull() ?: 0L
                    else -> frac.take(3).toLongOrNull() ?: 0L
                }
                lines.add(LyricLine((min * 60 + sec) * 1000 + ms, txt))
            }
        }
        lines.sortBy { it.timeMs }
        return LyricsResponse(lines, lines.joinToString("\n") { it.text })
    }
}
