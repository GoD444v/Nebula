package nebula.music.data.lyrics

import com.metrolist.innertubex.models.YouTubeClient
import android.util.Log
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import nebula.music.data.auth.YtSession

/**
 * YouTube Music's own lyrics — the source Echo-Music and Metrolist both use.
 *
 * innertubex 0.7.1 has no lyrics helper (its older sibling innertube did),
 * so this makes the two calls the YouTube Music web app itself makes:
 *   1. next(videoId) — the watch page. Its tab list holds a lyrics tab;
 *      that tab's browseEndpoint (browseId + params) is the address of the
 *      lyrics page. The tab is identified by its pageType
 *      (MUSIC_PAGE_TYPE_TRACK_LYRICS) — its slot in the list is not part
 *      of the contract, so the typed lookup wins and the fixed
 *      queue/lyrics/related slot order is only a fallback.
 *   2. browse(endpoint) — the lyrics page. The text lives in the first
 *      musicDescriptionShelfRenderer's description runs, joined with "".
 *
 * Returns the RAW LRC text ("[mm:ss.xx] line") — parsing stays in
 * LyricsRepository, Echo's provider contract. Null = no lyrics for this
 * video (many uploads have none; the other providers still race on).
 */
object YouTubeMusicLyrics {

    private const val MUSIC_PAGE_TYPE_TRACK_LYRICS = "MUSIC_PAGE_TYPE_TRACK_LYRICS"

    /** Local songs queue under "local:<content-uri>"; they have no watch page. */
    private const val LOCAL_PREFIX = "local:"

    // Session tube (Echo architecture): the stored cookie rides on requests,
    // innertubex signs SAPISIDHASH itself. Same session as SearchRepository.
    private val tube = YtSession.tube()

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.arr(key: String) = get(key) as? JsonArray
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

    /** Same JVM-safe idiom as LyricsRepository.diag — same logcat tag, so
     *  `adb logcat -s NebulaLyrics` covers both. */
    private fun diag(msg: String) {
        println("NebulaLyricsYT: $msg")
        runCatching { Log.d("NebulaLyrics", msg) }
    }

    suspend fun fetch(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (videoId.isBlank() || videoId.startsWith(LOCAL_PREFIX)) return@withContext null
            val watchText = tube.next(YouTubeClient.WEB_REMIX, videoId = videoId).bodyAsText()
            val endpoint = lyricsEndpoint(json.parseToJsonElement(watchText).jsonObject)
                ?: run {
                    diag("no lyrics tab on the watch page for $videoId")
                    return@withContext null
                }
            val browseId = endpoint.str("browseId") ?: run {
                diag("lyrics tab had no browseId for $videoId")
                return@withContext null
            }
            val pageText = tube.browse(
                YouTubeClient.WEB_REMIX,
                browseId = browseId,
                params = endpoint.str("params"),
            ).bodyAsText()
            lyricsText(json.parseToJsonElement(pageText).jsonObject) ?: run {
                diag("lyrics page had no text for $videoId")
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            diag("fetch failed for $videoId: ${e::class.simpleName} ${e.message}")
            null
        }
    }

    /** The lyrics tab's browseEndpoint from the watch page, or null. */
    private fun lyricsEndpoint(watchPage: JsonObject): JsonObject? {
        val tabs = watchPage
            .obj("contents")?.obj("singleColumnMusicWatchNextResultsRenderer")
            ?.obj("tabbedRenderer")?.obj("watchNextTabbedResultsRenderer")
            ?.arr("tabs") ?: return null
        tabs.forEach { entry ->
            val endpoint = (entry as? JsonObject)
                ?.obj("tabRenderer")?.obj("endpoint")?.obj("browseEndpoint")
                ?: return@forEach
            val pageType = endpoint
                .obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")?.str("pageType")
            if (pageType == MUSIC_PAGE_TYPE_TRACK_LYRICS) return endpoint
        }
        // Fallback: the library author's tab order is queue, lyrics, related,
        // so the second tab is the lyrics one when no typed tab was found.
        return (tabs.getOrNull(1) as? JsonObject)
            ?.obj("tabRenderer")?.obj("endpoint")?.obj("browseEndpoint")
    }

    /** The lyrics text from a lyrics browse page, or null. */
    private fun lyricsText(browsePage: JsonObject): String? {
        val sections = browsePage.obj("contents")?.obj("sectionListRenderer")?.arr("contents")
            ?: return null
        return sections.mapNotNull { it as? JsonObject }
            .mapNotNull { it.obj("musicDescriptionShelfRenderer") }
            .mapNotNull { shelf ->
                shelf.obj("description")?.arr("runs")?.mapNotNull { run ->
                    (run as? JsonObject)?.str("text")
                }?.joinToString("")?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }
}
