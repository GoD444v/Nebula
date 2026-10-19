package nebula.music.data.lyrics

import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricWord
import nebula.music.data.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** KPoE/LyricsPlus community mirrors: first mirror with usable lyrics wins. */
object KpoeLyrics {
    // Ordered by MEASURED hit rate, and both are needed. On a 10-song sample
    // (J-pop, K-pop, Western) prjktla answered for 6 and binimum for 4, but the
    // coverage does not overlap: アイドル exists only on binimum, 紅蓮華 only on
    // prjktla. Neither may replace the other.
    //
    // First-hit-wins is deliberate, not laziness. "Best" hit would mean counting
    // lines, and binimum's longer answer for Lemon was a DIFFERENT TRACK — a
    // Chinese cover ("Lemon·柠檬气息 (合唱版)"), 98 lines of credits and all,
    // against the real one's 55 word-timed lines. Ranking mirrors by size would
    // hand back the wrong song.
    //
    // lyricsplus.prjktla.workers.dev was removed: it answered a Cloudflare HTML
    // 404 on every path including "/", so it was a deployment that no longer
    // exists rather than a mirror having a slow day. Re-adding it is one line if
    // it ever comes back.
    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.binimum.org"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetch(title: String, artist: String, durationMs: Long): LyricsResponse? {
        if (title.isBlank() && artist.isBlank()) return null
        for (mirror in MIRRORS) {
            parse(fetchFrom(mirror, title, artist, durationMs))?.let { return it }
        }
        return null
    }

    private suspend fun fetchFrom(mirror: String, title: String, artist: String, durationMs: Long): String? {
        return try {
            http.get("$mirror/v2/lyrics/get") {
                parameter("title", title)
                parameter("artist", artist)
                if (durationMs > 0) parameter("duration", durationMs / 1000)
            }.bodyAsText().takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun parse(body: String?): LyricsResponse? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val items = root["lyrics"]?.jsonArray ?: return null
            if (items.isEmpty()) return null
            val type = root["type"]?.jsonPrimitive?.contentOrNull ?: ""
            val lines = mutableListOf<LyricLine>()
            val plain = mutableListOf<String>()
            for (item in items) {
                val o = item.jsonObject
                val text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isNotBlank()) plain.add(text.trim())
                // Times are usually integer ms, but some responses use doubles —
                // missing/unparseable must not silently become 0 (seek-to-0 trap).
                var timeMs = o["time"]?.jsonPrimitive?.longOrNull
                    ?: o["time"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: o["time"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
                val words = o["syllabus"]?.jsonArray?.mapNotNull { s ->
                    val w = s.jsonObject
                    val wt = w["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (wt.isBlank()) return@mapNotNull null
                    val start = w["time"]?.jsonPrimitive?.longOrNull
                        ?: w["time"]?.jsonPrimitive?.intOrNull?.toLong()
                        ?: w["time"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
                        ?: return@mapNotNull null
                    val dur = w["duration"]?.jsonPrimitive?.longOrNull
                        ?: w["duration"]?.jsonPrimitive?.intOrNull?.toLong()
                        ?: w["duration"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
                        ?: 0L
                    LyricWord(wt, start, (start + dur).coerceAtLeast(start))
                }.orEmpty()
                // Line time missing but words timed (absolute ms): inherit first word.
                if ((timeMs == null || timeMs <= 0) && words.isNotEmpty()) timeMs = words.first().startMs
                if (text.isNotBlank() && (type != "None" || (timeMs ?: 0L) > 0 || words.isNotEmpty())) {
                    lines.add(LyricLine((timeMs ?: 0L).coerceAtLeast(0), text.trim(), words))
                }
            }
            when {
                lines.isNotEmpty() -> LyricsResponse(lines.sortedBy { it.timeMs })
                plain.isNotEmpty() -> LyricsResponse(emptyList(), plain.joinToString("\n"))
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
