package nebula.music.data

import android.util.Log
import nebula.music.data.models.HomeCard
import nebula.music.data.models.HomeChip
import nebula.music.data.models.HomeFeed
import nebula.music.data.models.HomeSection
import nebula.music.data.models.SearchResult
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.NextResponse
import com.metrolist.innertubex.models.response.SearchResponse
import io.ktor.client.call.body
import nebula.music.data.auth.YtSession
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SearchResponseResult(
    val items: List<SearchResult>,
    val continuationToken: String?
)

/** One album or playlist page: header + tracklist. */
data class DetailPage(
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    val tracks: List<SearchResult> = emptyList()
)

/** A selectable genre from the YouTube Music moods & genres grid. */
data class GenreItem(
    val title: String,
    val browseId: String,
    val params: String
)

/** One playlist row from the signed-in account's library. */
data class YtPlaylistRow(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String
)

class SearchRepository {

    object SearchFilter {
        val ALL: String? = null
        // Songs filter = the one both Metrolist (innertubex's author) and Echo ship.
        // The old string decoded to a different category in the same family -> small page, no ticket.
        const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        // Old value was EgYKABgC = the youtube.com WEB filter (decodes malformed, only 4 of 6 bytes).
        // This is Echo's music-client filter (its YouTube.kt:2075).
        const val VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        const val ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val PLAYLISTS = "EgeKAQwIABABGAEoAEoKEAkQExgJ"
        // The two playlist chips Echo actually shows (verified against its YouTube.kt:2078-2079)
        const val COMMUNITY_PLAYLISTS = "EgeKAQQoAEABagoQAxAEEAoQCRAF"
        const val FEATURED_PLAYLISTS = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"
    }

    // Session tube: cookie/visitorData from the stored login (Echo architecture).
    // innertubex signs SAPISIDHASH itself. The app restarts on login/logout,
    // so this instance always matches the stored session.
    private val httpClient = YtSession.httpClient()

    private val tube = YtSession.tubeOn(httpClient)

    private val extractor: InnerTubeExtractor by lazy {
        val configStore = RemotePlayerConfigStore(httpClient, PlayerConfigRepository.disabled())
        InnerTubeExtractor(
            YtConfigParserImpl(httpClient, tube, configStore),
            YouTubeCipherService(httpClient, configStore),
            tube,
        )
    }

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.arr(key: String) = get(key) as? JsonArray
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

    /** One logcat tag for every repository verdict: `adb logcat -s NebulaRepo`. */
    private fun diag(msg: String) {
        Log.d("NebulaRepo", msg)
    }

    private fun runsText(container: JsonObject?): String {
        val runs = container?.arr("runs") ?: return ""
        return runs.joinToString("") { ((it as? JsonObject)?.str("text")) ?: "" }
    }

    private fun textOf(container: JsonObject?): String {
        if (container == null) return ""
        val runs = runsText(container)
        if (runs.isNotBlank()) return runs
        return container.str("simpleText") ?: ""
    }

    private fun flexRuns(item: JsonObject, col: Int): List<JsonObject> {
        val cols = item.arr("flexColumns") ?: return emptyList()
        val flex = (cols.getOrNull(col) as? JsonObject)?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?: return emptyList()
        return (flex.obj("text")?.arr("runs") ?: emptyList()).mapNotNull { it as? JsonObject }
    }

    private val typeWords = setOf("song", "video", "album", "playlist", "artist", "single", "ep", "explicit")

    private fun cleanArtist(raw: String) = raw.split("•").map { it.trim() }.firstOrNull {
        val lower = it.lowercase()
        it.isNotBlank() && lower !in typeWords && !lower.contains("view") && !lower.contains(":")
    } ?: "Unknown artist"

    private fun artistFromRuns(item: JsonObject): String {
        for (col in 1..2) {
            val runs = flexRuns(item, col)
            runs.firstOrNull { run ->
                val text = (run["text"] as? JsonPrimitive)?.contentOrNull ?: ""
                run.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") != null &&
                    text.lowercase() !in typeWords
            }?.let { return ((it["text"] as? JsonPrimitive)?.contentOrNull) ?: "" }
            val joined = runs.joinToString("") { ((it["text"] as? JsonPrimitive)?.contentOrNull) ?: "" }
            val cleaned = cleanArtist(joined)
            if (cleaned != "Unknown artist") return cleaned
        }
        return "Unknown artist"
    }

    private fun mapCardShelf(card: JsonObject): SearchResult? {
        val header = card.obj("header")?.obj("musicCardShelfHeaderRenderer") ?: return null
        val videoId = header.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: header.obj("navigationEndpoint")?.obj("watchPlaylistEndpoint")?.str("videoId")
            ?: return null
        if (videoId.isBlank()) return null
        val title = textOf(header.obj("title")).ifBlank { return null }
        val artist = cleanArtist(runsText(header.obj("subtitle")))
        val thumb = header.obj("thumbnail")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.arr("thumbnails")
            ?.lastOrNull()
            .let { it as? JsonObject }
            ?.str("url")
            ?.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return SearchResult(videoId, title, artist, thumb)
    }

    private fun mapSearchItem(item: JsonObject): SearchResult? {
        val titleRuns = flexRuns(item, 0)
        val videoId = item.obj("overlay")
            ?.obj("musicItemThumbnailOverlayRenderer")
            ?.obj("content")
            ?.obj("musicPlayButtonRenderer")
            ?.obj("playNavigationEndpoint")
            ?.obj("watchEndpoint")
            ?.str("videoId")
            ?: item.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: titleRuns.firstOrNull()?.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: titleRuns.firstOrNull()?.obj("navigationEndpoint")?.obj("watchPlaylistEndpoint")?.str("videoId")
        val title = runsText(item.obj("title")).ifBlank {
            titleRuns.joinToString("") { ((it["text"] as? JsonPrimitive)?.contentOrNull) ?: "" }
        }
        if (title.isBlank()) return null
        val artist = artistFromRuns(item)
        // Same rule as related-song filtering below: only ATV tracks are songs.
        val musicVideoType = item.str("musicVideoType")
        val isVideoSong = musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV"
        val thumb = item.obj("thumbnail")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.arr("thumbnails")
            ?.lastOrNull()
            .let { it as? JsonObject }
            ?.str("url")
            ?.takeIf { it.isNotBlank() }
        if (!videoId.isNullOrBlank()) {
            // Artist/album browse ids ride along in the flex columns as separate
            // runs with browseEndpoints (Echo's secondaryLine layout). Capture
            // them positionally: first browse-endpoint run = artist, second = album.
            var artistBid = ""
            var albumBid = ""
            val browseRuns = (flexRuns(item, 1) + flexRuns(item, 2))
                .mapNotNull { run ->
                    run.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")?.let { bid -> bid to run }
                }
            // Classify by id shape first (robust to column order), fall back to position.
            artistBid = browseRuns.firstOrNull { it.first.startsWith("UC") }?.first
                ?: browseRuns.getOrNull(0)?.first.orEmpty()
            albumBid = browseRuns.firstOrNull { it.first.startsWith("MPREb_") }?.first
                ?: browseRuns.getOrNull(1)?.first.orEmpty()
            // Avoid assigning the same id to both.
            if (albumBid == artistBid) albumBid = ""
            return SearchResult(
                videoId, title, artist,
                thumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                isVideoSong = isVideoSong,
                artistBrowseId = artistBid, albumBrowseId = albumBid,
                setVideoId = item.obj("playlistItemData")?.str("playlistSetVideoId").orEmpty()
            )
        }
        // Playlist / album / artist card: nothing playable, but a browse target.
        // Previously these fell through and were dropped, so search showed songs only.
        val navEndpoint = titleRuns.firstOrNull()?.obj("navigationEndpoint")
            ?: item.obj("navigationEndpoint")
        val browseEndpoint = navEndpoint?.obj("browseEndpoint") ?: return null
        val browseId = browseEndpoint.str("browseId") ?: return null
        val playlistId = navEndpoint.obj("watchPlaylistEndpoint")?.str("playlistId").orEmpty()
        val pageType = browseEndpoint.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")?.str("pageType").orEmpty()
        val artistBid = if (pageType.contains("ARTIST")) browseId else ""
        val albumBid = if (pageType.contains("ALBUM")) browseId else ""
        return SearchResult(
            "", title, artist, thumb.orEmpty(),
            playlistId = playlistId, browseId = browseId, pageType = pageType,
            artistBrowseId = artistBid, albumBrowseId = albumBid
        )
    }

    private fun continuationToken(host: JsonObject): String? =
        host.arr("continuations")?.firstOrNull().let { it as? JsonObject }
            ?.let {
                it.obj("nextContinuationData")?.str("continuation")
                    ?: it.obj("nextRadioContinuationData")?.str("continuation")
            }

    private fun queuePanel(response: NextResponse): JsonArray? {
        val tabs = response.contents?.singleColumnMusicWatchNextResultsRenderer
            ?.get("tabbedRenderer") as? JsonObject ?: return null
        return tabs.obj("watchNextTabbedResultsRenderer")
            ?.arr("tabs")?.firstOrNull().let { (it as? JsonObject) }
            ?.obj("tabRenderer")?.obj("content")
            ?.obj("musicQueueRenderer")?.obj("content")
            ?.obj("playlistPanelRenderer")?.arr("contents")
    }

    /**
     * STEP 1 & 2: Initial search with optional filter parameter.
     * Parses musicShelfRenderer / sections to extract items AND continuation token.
     */
    suspend fun search(query: String, filter: String? = SearchFilter.SONGS): SearchResponseResult =
        withContext(Dispatchers.IO) {
            try {
                val out = ArrayList<SearchResult>()
                var token: String? = null
                val response = tube.search(YouTubeClient.WEB_REMIX, query = query, params = filter)
                    .body<SearchResponse>()
                val tabs = response.contents?.tabbedSearchResultsRenderer?.get("tabs") as? JsonArray
                    ?: return@withContext SearchResponseResult(emptyList(), null)
                val tab = (tabs.firstOrNull() as? JsonObject)?.obj("tabRenderer")?.obj("content")
                    ?: return@withContext SearchResponseResult(emptyList(), null)
                val sections = tab.obj("sectionListRenderer")?.arr("contents")
                    ?: return@withContext SearchResponseResult(emptyList(), null)

                for (section in sections) {
                    val s = section as? JsonObject ?: continue
                    s.obj("musicCardShelfRenderer")?.let { card ->
                        mapCardShelf(card)?.let { out.add(it) }
                    }
                    // Carousel shelves carry the album/playlist/artist cards in an
                    // unfiltered search (two-row items). Skipping them is why search
                    // showed songs only — the flat sections below hold songs alone.
                    s.obj("musicCarouselShelfRenderer")?.let { carousel ->
                        carousel.arr("contents").orEmpty().forEach { card ->
                            val obj = card as? JsonObject ?: return@forEach
                            obj.obj("musicResponsiveListItemRenderer")?.let {
                                mapSearchItem(it)?.let { hit -> out.add(hit) }
                            } ?: obj.obj("musicTwoRowItemRenderer")?.let {
                                mapTwoRow(it)?.toSearchResult()?.let { hit -> out.add(hit) }
                            }
                        }
                    }
                    val shelfList = ArrayList<JsonObject>(2)
                    s.obj("musicShelfRenderer")?.let { shelfList.add(it) }
                    if (token == null) token = continuationToken(s)

                    val itemSection = s.obj("itemSectionRenderer")
                    if (token == null && itemSection != null) token = continuationToken(itemSection)
                    itemSection?.arr("contents")?.forEach { inner ->
                        val innerObj = inner as? JsonObject ?: return@forEach
                        innerObj.obj("musicResponsiveListItemRenderer")?.let {
                            mapSearchItem(it)?.let { hit -> out.add(hit) }
                        }
                        innerObj.obj("musicShelfRenderer")?.let { shelfList.add(it) }
                    }

                    for (shelf in shelfList) {
                        if (token == null) token = continuationToken(shelf)
                        val entries = shelf.arr("contents") ?: continue
                        for (entry in entries) {
                            val renderer = (entry as? JsonObject)?.obj("musicResponsiveListItemRenderer")
                                ?: continue
                            mapSearchItem(renderer)?.let { out.add(it) }
                        }
                    }
                }
                SearchResponseResult(out.distinctBy(::distinctKey), token)
            } catch (_: Exception) {
                SearchResponseResult(emptyList(), null)
            }
        }

    /**
     * STEP 3: Fetch next page of results using continuation token.
     */
    suspend fun searchContinuation(continuation: String): SearchResponseResult =
        withContext(Dispatchers.IO) {
            try {
                val out = ArrayList<SearchResult>()
                val response = tube.search(YouTubeClient.WEB_REMIX, continuation = continuation)
                    .body<SearchResponse>()
                val shelf = response.continuationContents?.musicShelfContinuation
                val entries = shelf?.get("contents") as? JsonArray ?: emptyList()

                for (entry in entries) {
                    val renderer = (entry as? JsonObject)?.obj("musicResponsiveListItemRenderer")
                        ?: continue
                    mapSearchItem(renderer)?.let { out.add(it) }
                }
                val nextToken = shelf?.let { continuationToken(it) }
                SearchResponseResult(out.distinctBy(::distinctKey), nextToken)
            } catch (_: Exception) {
                SearchResponseResult(emptyList(), null)
            }
        }

    /**
     * Helper to perform initial search and automatically fill 50+ results (up to targetCount / maxPages).
     */
    suspend fun searchInitialAndFill(
        query: String,
        filter: String? = SearchFilter.SONGS,
        targetCount: Int = 50,
        maxPages: Int = 3
    ): SearchResponseResult = withContext(Dispatchers.IO) {
        val initial = search(query, filter)
        val accumulated = ArrayList<SearchResult>(initial.items)
        var currentToken = initial.continuationToken
        var pageCount = 0

        while (accumulated.size < targetCount && currentToken != null && pageCount < maxPages) {
            pageCount++
            val next = searchContinuation(currentToken)
            if (next.items.isEmpty()) {
                currentToken = null
                break
            }
            accumulated.addAll(next.items)
            currentToken = next.continuationToken
        }

        val capped = accumulated.distinctBy(::distinctKey).take(60)
        SearchResponseResult(capped, currentToken)
    }

    suspend fun searchYouTube(query: String, params: String? = SearchFilter.SONGS): List<SearchResult> {
        return searchInitialAndFill(query, params).items
    }

    /**
     * The home feed. Echo asks YouTube Music for its own home + explore pages, which is
     * why it is never empty on a fresh install — we do the same here.
     */
    suspend fun home(params: String? = null): HomeFeed = withContext(Dispatchers.IO) {
        try {
            val home = tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", params = params)
                .body<JsonObject>()
            // Explore only fattens the default feed; a chip filters the home page alone.
            // Own try/catch: a failed explore must not throw away a good home response.
            val exploreSections = if (params == null) {
                try {
                    sectionsOf(
                        tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_explore")
                            .body<JsonObject>()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            HomeFeed(
                chips = chipsOf(home),
                sections = sectionsOf(home) + exploreSections,
                continuation = continuationOf(home)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HomeFeed(emptyList(), emptyList())
        }
    }

    /**
     * Next page of the home feed, fetched via a continuation token YouTube
     * returns on the first browse. Chips are not re-sent on continuation pages,
     * so callers keep the original feed's chips.
     */
    suspend fun homeContinuation(token: String): HomeFeed = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", continuation = token)
                .body<JsonObject>()
            HomeFeed(
                chips = emptyList(),
                sections = sectionsOf(page),
                continuation = continuationOf(page)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HomeFeed(emptyList(), emptyList(), null)
        }
    }

    /**
     * Genres pinned first so the picker's top row matches the promise —
     * everything else keeps YouTube's own grid order.
     */
    private val pinnedGenres = listOf(
        "funk", "english", "tamil", "hindi", "j-pop", "k-pop",
        "pop", "rock", "hip-hop", "jazz", "classical", "country",
        "reggae", "latin", "edm", "disco", "soul", "punk", "metal",
        "r&b", "blues", "folk", "indie", "lo-fi", "house", "techno"
    )

    /**
     * The genre/mood grid from YouTube Music. Browse FEmusic_moods_and_genres,
     * parse musicNavigationButtonRenderer items — each carries its own browseId
     * + params for fetching that genre's track listing.
     */
    suspend fun moodAndGenres(): List<GenreItem> = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_moods_and_genres")
                .body<JsonObject>()
            val contents = page.obj("contents")
                ?.obj("singleColumnBrowseResultsRenderer")
                ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
                ?.obj("tabRenderer")?.obj("content")
                ?.obj("sectionListRenderer")?.arr("contents") ?: return@withContext emptyList()

            val out = ArrayList<GenreItem>()
            for (entry in contents) {
                val grid = (entry as? JsonObject)?.obj("gridRenderer") ?: continue
                // Grid renderers carry items, not contents (cf. Echo's GridRenderer model)
                for (item in grid.arr("items").orEmpty()) {
                    val nav = (item as? JsonObject)?.obj("musicNavigationButtonRenderer") ?: continue
                    // Echo's MoodAndGenres: title lives in buttonText, endpoint in
                    // clickCommand — not text / navigationEndpoint.
                    val title = runsText(nav.obj("buttonText")).ifBlank { continue }
                    val endpoint = nav.obj("clickCommand")?.obj("browseEndpoint") ?: continue
                    val browseId = endpoint.str("browseId") ?: continue
                    val params = endpoint.str("params") ?: continue
                    out.add(GenreItem(title, browseId, params))
                }
            }
            val pinned = pinnedGenres.mapNotNull { want ->
                out.firstOrNull { it.title.equals(want, ignoreCase = true) }
            }
            pinned + out.filter { g -> pinned.none { it.title == g.title } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch a genre's track listing. Browse the genre's browseId + params,
     * parse musicCarouselShelfRenderer sections into a single HomeSection.
     */
    suspend fun genreTracks(browseId: String, params: String): HomeSection = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = browseId, params = params)
                .body<JsonObject>()
            val sections = sectionsOf(page)
            val title = sections.firstOrNull()?.title ?: "Genre"
            val cards = sections.flatMap { it.cards }
            HomeSection(title, cards)
        } catch (_: Exception) {
            HomeSection("Genre", emptyList())
        }
    }

    /**
     * Artist detail page: header + top songs + album/singles carousels.
     */
    data class ArtistDetailPage(
        val title: String,
        val subtitle: String = "",
        val thumbnailUrl: String = "",
        val topSongs: List<SearchResult> = emptyList(),
        val albums: List<SearchResult> = emptyList()
    )

    /**
     * Artist detail page. browseId is an artist browse id ("UC...").
     */
    suspend fun getArtistDetail(browseId: String): ArtistDetailPage = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>()
            val twoCol = page.obj("contents")?.obj("twoColumnBrowseResultsRenderer")
            val singleCol = page.obj("contents")?.obj("singleColumnBrowseResultsRenderer")
            val tab = (twoCol?.arr("tabs") ?: singleCol?.arr("tabs"))?.firstOrNull().let { it as? JsonObject }
            val sections = tab?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")?.arr("contents")
                ?: singleCol?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")?.arr("contents")
                ?: page.obj("sectionListRenderer")?.arr("contents")
                ?: twoCol?.obj("secondaryContents")?.obj("sectionListRenderer")?.arr("contents")
            // Artist pages put the header at the top level (musicImmersiveHeaderRenderer etc.),
            // not in the section list. Try all known header renderers.
            val hdr = page.obj("header")
            val imm = hdr?.obj("musicImmersiveHeaderRenderer")
            val vis = hdr?.obj("musicVisualHeaderRenderer")
            val det = hdr?.obj("musicDetailHeaderRenderer")
            val resp = hdr?.obj("musicResponsiveHeaderRenderer")
            val simple = hdr?.obj("musicHeaderRenderer")
            val header = imm ?: vis ?: det ?: resp ?: simple
            val titleObj = imm?.obj("title") ?: vis?.obj("title") ?: det?.obj("title") ?: resp?.obj("title") ?: simple?.obj("title")
            val thumbObj = imm?.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
                ?: vis?.obj("foregroundThumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
                ?: det?.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
                ?: resp?.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
            val thumbEl = thumbObj?.arr("thumbnails")?.lastOrNull()
            val topSongs = ArrayList<SearchResult>()
            val albums = ArrayList<SearchResult>()
            sections?.forEach { s ->
                val obj = s as? JsonObject ?: return@forEach
                obj.obj("musicShelfRenderer")?.let { shelf ->
                    shelf.arr("contents").orEmpty().forEach { e ->
                        (e as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let { mapSearchItem(it) }?.let { topSongs.add(it) }
                    }
                }
                obj.obj("musicCarouselShelfRenderer")?.let { carousel ->
                    carousel.arr("contents").orEmpty().forEach { c ->
                        val o = c as? JsonObject ?: return@forEach
                        o.obj("musicTwoRowItemRenderer")?.let { mapTwoRow(it)?.toSearchResult() }?.let { albums.add(it) }
                            ?: o.obj("musicResponsiveListItemRenderer")?.let { mapSearchItem(it) }?.let { albums.add(it) }
                    }
                }
            }
            ArtistDetailPage(
                title = runsText(titleObj),
                subtitle = runsText(imm?.obj("subtitle") ?: vis?.obj("subtitle") ?: det?.obj("subtitle") ?: resp?.obj("subtitle")),
                thumbnailUrl = (thumbEl as? JsonObject)?.str("url").orEmpty(),
                topSongs = topSongs.distinctBy { it.videoId.ifBlank { it.browseId } },
                albums = albums.distinctBy { it.browseId.ifBlank { it.title } }
            )
        } catch (_: Exception) {
            ArtistDetailPage(title = "")
        }
    }

    /**
     * Album / playlist / library detail page. browseId comes from a feed card
     * ("MPREb_..." albums, "VL..." playlists) or is a library page
     * ("FEmusic_liked_videos").
     *
     * YouTube Music uses TWO page shapes here:
     *  - two-column (albums, playlists): tracklist in `secondaryContents`.
     *  - single-column (library): tracklist sits directly in the tab content.
     * Reading only the two-column path returned an empty page for library
     * browse ids — shelf never found, so silently no tracks.
     */
    /** Rich media info for a video, via the player endpoint. Null on failure. */
    data class MediaInfo(
        val viewCount: String? = null,
        val itag: Int? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val contentLength: Long? = null,
        val sampleRate: Int? = null,
        val loudnessDb: Double? = null,
        val codecs: String? = null
    )

    suspend fun getMediaInfo(videoId: String): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            val pr = tube.player(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, videoId = videoId)
                .body<com.metrolist.innertubex.models.response.PlayerResponse>()
            val fmt = (pr.streamingData?.adaptiveFormats ?: pr.streamingData?.formats)
                ?.filter { it.mimeType?.startsWith("audio") == true }
                ?.maxByOrNull { it.bitrate }
            MediaInfo(
                viewCount = pr.videoDetails?.viewCount,
                itag = fmt?.itag,
                mimeType = fmt?.mimeType?.substringBefore(";"),
                bitrate = fmt?.bitrate,
                contentLength = fmt?.contentLength,
                sampleRate = fmt?.audioSampleRate,
                loudnessDb = fmt?.loudnessDb,
                codecs = fmt?.mimeType?.substringAfter("codecs=", "")?.trim('"')?.takeIf { it.isNotBlank() && it != fmt.mimeType }
            )
        } catch (_: Exception) {
            null
        }
    }

    /** View count for a video, via the player endpoint. Null on failure. */
    suspend fun getVideoViewCount(videoId: String): String? = getMediaInfo(videoId)?.viewCount

    /**
     * Header renderer names in use across YouTube Music browse pages. Album and
     * playlist pages answer `musicResponsiveHeaderRenderer`; library pages and
     * the older surfaces use the others, and a page with none of them has no
     * header at all (which is why the thumbnail went missing on Liked songs —
     * one hard-coded name returned null and nothing was ever read).
     */
    private val headerRenderers = listOf(
        "musicResponsiveHeaderRenderer",
        "musicHeaderRenderer",
        "musicDetailHeaderRenderer",
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
        "musicEditablePlaylistDetailHeaderRenderer"
    )

    /**
     * Peel wrapper renderers off until we reach the node carrying the title and
     * artwork. A node that has either is already the real header; anything else
     * with an inner `header` (or a single known header renderer) is a wrapper.
     * Bounded so a self-referential payload cannot spin.
     */
    private fun unwrapHeader(node: JsonObject, depth: Int = 0): JsonObject {
        if (depth >= 3) return node
        if (node.obj("title") != null || node.obj("thumbnail") != null) return node
        val inner = node.obj("header")
            ?: headerRenderers.firstNotNullOfOrNull { name -> node.obj(name) }
        return if (inner != null && inner !== node) unwrapHeader(inner, depth + 1) else node
    }

    private fun headerOf(sections: JsonArray?): JsonObject? {
        sections?.forEach { entry ->
            val o = entry as? JsonObject ?: return@forEach
            headerRenderers.forEach { name ->
                val renderer = o.obj(name) ?: return@forEach
                // Account playlists nest the real header TWICE: the section holds
                // musicEditablePlaylistDetailHeaderRenderer, whose `header` key holds
                // a musicResponsiveHeaderRenderer with the title/thumb. Reading at the
                // top level returned blanks, so the title fell back to the raw
                // browseId and the art box stayed blank. Unwrap until we reach the
                // node that actually carries title/thumbnail.
                return unwrapHeader(renderer)
            }
        }
        return null
    }

    /**
     * Thumbnail URL from whichever wrapper this page uses. Reading only
     * `thumbnail.musicThumbnailRenderer.thumbnail.thumbnails` returned null on
     * library headers, which is the other half of the missing artwork.
     */
    private fun thumbUrlOf(container: JsonObject?): String? {
        container ?: return null
        val lists = listOfNotNull(
            container.obj("thumbnail")?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")?.arr("thumbnails"),
            // Two-row grid rows key the wrapper `thumbnailRenderer`, not `thumbnail`.
            container.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")?.arr("thumbnails"),
            container.obj("thumbnail")?.obj("croppedSquareThumbnailRenderer")
                ?.obj("thumbnail")?.arr("thumbnails"),
            container.obj("thumbnail")?.arr("thumbnails"),
            container.obj("musicThumbnailRenderer")?.obj("thumbnail")?.arr("thumbnails"),
            container.obj("thumbnails")?.arr("thumbnails")
        )
        return lists
            .firstNotNullOfOrNull { arr -> arr.mapNotNull { (it as? JsonObject)?.str("url") }.lastOrNull() }
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun getDetail(browseId: String): DetailPage = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>()
            val contents = page.obj("contents")
            val twoCol = contents?.obj("twoColumnBrowseResultsRenderer")
            val singleCol = contents?.obj("singleColumnBrowseResultsRenderer")
            val tab = (twoCol ?: singleCol)?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            val tabSections = tab?.obj("tabRenderer")?.obj("content")
                ?.obj("sectionListRenderer")?.arr("contents")
            val secondarySections = twoCol?.obj("secondaryContents")
                ?.obj("sectionListRenderer")?.arr("contents")
            // Scan every section, not just the first: a library page can lead
            // with a shelf before its header.
            val header = headerOf(tabSections) ?: headerOf(secondarySections)
            
            
            // Both shelves, in document order: album/playlist pages hang theirs
            // off secondaryContents, library pages put it in the tab content.
            val shelfHosts = listOfNotNull(secondarySections, tabSections)
            val tracks = shelfHosts.asSequence()
                .flatMap { it.asSequence() }
                .mapNotNull { it as? JsonObject }
                .mapNotNull { it.obj("musicShelfRenderer") ?: it.obj("musicPlaylistShelfRenderer") }
                .flatMap { it.arr("contents").orEmpty().asSequence() }
                .mapNotNull { (it as? JsonObject)?.obj("musicResponsiveListItemRenderer") }
                .mapNotNull { mapSearchItem(it) }
                .toList()
            DetailPage(
                // Blank, NOT the browseId, when there is no header: library pages
                // (FEmusic_liked_videos) have no header renderer at all, and handing
                // back the raw id made the Liked songs screen render "FEmusic_lik…
                // as its name and a BigLetter "F" as its artwork. Callers all pass
                // their own real name as the fallback, so blank is what they want.
                title = textOf(header?.obj("title")),
                subtitle = textOf(header?.obj("subtitle")),
                thumbnailUrl = thumbUrlOf(header).orEmpty(),
                tracks = tracks
            )
        } catch (_: Exception) {
            DetailPage(title = "")
        }
    }

    /**
     * Renames a playlist inside the signed-in YouTube account.
     *
     * This is a WRITE to the user's real YouTube library, so it is deliberately narrow:
     * playlists only (a "VL…" browse id), and it returns whether YouTube accepted it
     * rather than throwing, so the caller can tell the user the truth.
     */
    suspend fun renameYtPlaylist(browseId: String, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlistId = browseId.removePrefix("VL")
            if (playlistId.isBlank() || newTitle.isBlank()) return@withContext false
            try {
                val response = tube.renamePlaylist(
                    YouTubeClient.WEB_REMIX,
                    playlistId,
                    newTitle
                )
                response.status == io.ktor.http.HttpStatusCode.OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Deletes a playlist from the signed-in YouTube account. Irreversible from the
     * app's side; YouTube keeps the songs in the library, only the playlist goes.
     * Same narrow contract as renameYtPlaylist: VL... browse id only, Boolean result.
     */
    suspend fun deleteYtPlaylist(browseId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlistId = browseId.removePrefix("VL")
            if (playlistId.isBlank()) return@withContext false
            try {
                tube.deletePlaylist(YouTubeClient.WEB_REMIX, playlistId)
                    .status == io.ktor.http.HttpStatusCode.OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Adds one song to a YouTube playlist owned by the signed-in account.
     */
    suspend fun addSongToYtPlaylist(browseId: String, videoId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlistId = browseId.removePrefix("VL")
            if (playlistId.isBlank() || videoId.isBlank()) return@withContext false
            try {
                tube.addToPlaylist(YouTubeClient.WEB_REMIX, playlistId, videoId)
                    .status == io.ktor.http.HttpStatusCode.OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Removes one song from a YouTube playlist. Needs the row's setVideoId, which
     * only the playlist page carries — pass it through from the loaded track.
     */
    suspend fun removeSongFromYtPlaylist(browseId: String, videoId: String, setVideoId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlistId = browseId.removePrefix("VL")
            if (playlistId.isBlank() || videoId.isBlank() || setVideoId.isBlank()) return@withContext false
            try {
                tube.removePlaylistSong(YouTubeClient.WEB_REMIX, playlistId, setVideoId, videoId)
                    .status == io.ktor.http.HttpStatusCode.OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * The signed-in account's own playlists (library page `FEmusic_liked_playlists`).
     *
     * Rows are `musicResponsiveListItemRenderer` items, but playlist rows cannot
     * go through [mapSearchItem]: that deliberately drops anything that is not an
     * ATV track, and a playlist row has no `videoId` — only a playlist browseId,
     * a song count and cover art. Tapping one opens getDetail on its browseId.
     */
    suspend fun accountPlaylists(): List<YtPlaylistRow> = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_liked_playlists")
                .body<JsonObject>()
            val tab = (page.obj("contents")?.obj("singleColumnBrowseResultsRenderer")
                ?: page.obj("contents")?.obj("twoColumnBrowseResultsRenderer"))
                ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            val sections = tab?.obj("tabRenderer")?.obj("content")
                ?.obj("sectionListRenderer")?.arr("contents").orEmpty()

            // Why the list came back empty is otherwise invisible, and this
            // parser has already been wrong twice. Say what the page actually
            // contained instead of returning emptyList() on a shrug.
            val sectionObjs = sections.filterIsInstance<JsonObject>()
            val rows = libraryItems(sectionObjs)

            val out = rows.mapNotNull { item ->
                val id = item.obj("overlay")
                    ?.obj("musicItemThumbnailOverlayRenderer")?.obj("content")
                    ?.obj("musicPlayButtonRenderer")?.obj("playNavigationEndpoint")
                    ?.obj("watchPlaylistEndpoint")?.str("playlistId")
                    ?: item.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                if (id.isNullOrBlank()) {
                    diag("accountPlaylists: dropped row, no browseId, navKeys=${item.obj("navigationEndpoint")?.keys}")
                    return@mapNotNull null
                }
                // flexRuns returns the runs THEMSELVES, so their text is joined
                // directly. Passing them to runsText() — which wants a container
                // holding a "runs" key — always returned "".
                val flexTitle = flexRuns(item, 0).joinToString("") { it.str("text") ?: "" }
                val title = textOf(item.obj("title")).ifBlank {
                    // Two-row grid rows carry a plain string `title`.
                    item.str("title")?.takeIf { it.isNotBlank() }
                        // ...and its text can also sit in title.runs[0].
                        ?: (item.obj("title")?.arr("runs")?.firstOrNull() as? JsonObject)?.str("text").orEmpty()
                }.ifBlank { flexTitle }
                if (title.isBlank()) {
                    diag("accountPlaylists: dropped row, blank title, keys=${item.keys}")
                    return@mapNotNull null
                }
                // Two-row rows put "N songs" in the LAST subtitle run; shelf rows
                // put it in the second flex column.
                val subRuns = item.obj("subtitle")?.arr("runs")
                    ?.mapNotNull { (it as? JsonObject)?.str("text") }.orEmpty()
                YtPlaylistRow(
                    browseId = id,
                    title = title,
                    subtitle = subRuns.lastOrNull()?.takeIf { subRuns.size > 1 }
                        ?: flexRuns(item, 1).joinToString("") { it.str("text") ?: "" },
                    thumbnailUrl = thumbUrlOf(item).orEmpty()
                )
            }
            diag("accountPlaylists: parsed=${out.size}")
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diag("accountPlaylists: failed ${e::class.simpleName} ${e.message}")
            emptyList()
        }
    }

    /**
     * Rows from a library page's section list, whichever renderer holds them.
     *
     * A library page is not always a list: playlists arrive as a two-column
     * `gridRenderer` of `musicTwoRowItemRenderer`, while liked tracks arrive as
     * a `musicShelfRenderer` of `musicResponsiveListItemRenderer`. Reading only
     * `contents` found the shelf and missed the grid outright — which is why the
     * playlists list was always empty. Sections can also sit inside an
     * `itemSectionRenderer`, hence the walk.
     */
    private fun libraryItems(sections: List<JsonObject>): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        fun walk(list: List<JsonObject>) {
            for (s in list) {
                s.obj("musicShelfRenderer")?.arr("contents")?.forEach { c ->
                    (c as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let { out.add(it) }
                }
                s.obj("musicPlaylistShelfRenderer")?.arr("contents")?.forEach { c ->
                    (c as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let { out.add(it) }
                }
                s.obj("gridRenderer")?.arr("items")?.forEach { i ->
                    (i as? JsonObject)?.obj("musicTwoRowItemRenderer")?.let { out.add(it) }
                }
                s.obj("itemSectionRenderer")?.arr("contents")
                    ?.filterIsInstance<JsonObject>()
                    ?.let { walk(it) }
            }
        }
        walk(sections)
        return out
    }

    /** Chips live in the section list header, not in the tab content. */
    private fun chipsOf(page: JsonObject): List<HomeChip> {
        val chips = page.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            ?.obj("tabRenderer")?.obj("content")
            ?.obj("sectionListRenderer")?.obj("header")
            ?.obj("chipCloudRenderer")?.arr("chips") ?: return emptyList()

        return chips.mapNotNull { entry ->
            val chip = (entry as? JsonObject)?.obj("chipCloudChipRenderer") ?: return@mapNotNull null
            val label = runsText(chip.obj("text"))
            val params = chip.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("params")
            // Podcasts and Uploaded are not music — Echo hides them too
            if (label.isBlank() || params.isNullOrBlank() || label in ignoredChips) null
            else HomeChip(label, params)
        }
    }

    /** Section entries for either a home page or a continuation page. */
    private fun sectionEntriesOf(page: JsonObject): JsonArray? {
        page.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            ?.obj("tabRenderer")?.obj("content")
            ?.obj("sectionListRenderer")?.arr("contents")?.let { return it }
        page.obj("continuationContents")
            ?.obj("sectionListContinuation")?.arr("contents")?.let { return it }
        page.obj("sectionListRenderer")?.arr("contents")?.let { return it }
        return null
    }

    private fun continuationOf(page: JsonObject): String? {
        val homeSlr = page.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            ?.obj("tabRenderer")?.obj("content")
            ?.obj("sectionListRenderer")
        homeSlr?.let { continuationToken(it)?.let { t -> return t } }
        page.obj("continuationContents")
            ?.obj("sectionListContinuation")?.let { continuationToken(it)?.let { t -> return t } }
        page.obj("sectionListRenderer")?.let { continuationToken(it)?.let { t -> return t } }
        return null
    }

    private fun sectionsOf(page: JsonObject): List<HomeSection> {
        val contents = sectionEntriesOf(page) ?: return emptyList()

        val out = ArrayList<HomeSection>()
        for (entry in contents) {
            val shelf = entry as? JsonObject ?: continue
            shelf.obj("musicCarouselShelfRenderer")?.let { carousel ->
                val title = runsText(
                    carousel.obj("header")
                        ?.obj("musicCarouselShelfBasicHeaderRenderer")
                        ?.obj("title")
                )
                val cards = carousel.arr("contents").orEmpty().mapNotNull { card ->
                    val obj = card as? JsonObject ?: return@mapNotNull null
                    // Songs reuse the search mapper; albums/playlists/artists are two-row cards
                    obj.obj("musicResponsiveListItemRenderer")?.let { mapSearchItem(it) }
                        ?.let { HomeCard(it.title, it.artist, it.thumbnailUrl, videoId = it.videoId) }
                        ?: obj.obj("musicTwoRowItemRenderer")?.let { mapTwoRow(it) }
                }
                if (title.isNotBlank() && cards.isNotEmpty()) out.add(HomeSection(title, cards))
            }
        }
        return out
    }

    /** Album / playlist / artist card: no videoId, so it opens a page instead of playing. */
    private fun mapTwoRow(item: JsonObject): HomeCard? {
        val title = runsText(item.obj("title")).ifBlank { return null }
        val subtitle = runsText(item.obj("subtitle"))
        val thumb = item.obj("thumbnailRenderer")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.arr("thumbnails")
            ?.lastOrNull() as? JsonObject
        val endpoint = item.obj("navigationEndpoint")
        val browseEndpoint = endpoint?.obj("browseEndpoint")
        return HomeCard(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumb?.str("url").orEmpty(),
            videoId = endpoint?.obj("watchEndpoint")?.str("videoId").orEmpty(),
            browseId = browseEndpoint?.str("browseId").orEmpty(),
            params = browseEndpoint?.str("params").orEmpty(),
            pageType = browseEndpoint?.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")?.str("pageType").orEmpty()
        )
    }

    /**
     * Two-row card as a search result. videoId is deliberately dropped: two-row
     * renderers are album/playlist/artist cards, and a stray videoId would file
     * them under Songs.
     */
    private fun HomeCard.toSearchResult() = SearchResult(
        "", title, subtitle, thumbnailUrl, browseId = browseId, pageType = pageType,
        artistBrowseId = if (pageType.contains("ARTIST")) browseId else "",
        albumBrowseId = if (pageType.contains("ALBUM")) browseId else ""
    )

    /**
     * Dedup key for search results. Songs dedup by videoId; browse rows
     * (playlists/albums/artists) carry no videoId, so keying on it alone
     * collapsed every one of them into a single survivor.
     */
    private fun distinctKey(r: SearchResult): String =
        r.videoId.ifBlank { r.browseId + r.playlistId + r.title }

    private val ignoredChips = setOf("Podcasts", "Uploaded")

    private data class CachedStreamUrl(
        val url: String,
        val headers: Map<String, String>,
        val expiresAtMs: Long
    )

    companion object {
        /** 3 hours. InnerTube audio URLs are typically valid ~6h; refresh early. */
        private const val STREAM_URL_TTL_MS = 3L * 60 * 60 * 1000

        /** Re-resolve this long before the URL's own expire= timestamp. */
        private const val EXPIRY_MARGIN_MS = 60_000L

        /**
         * Shared across every SearchRepository instance, because the player and
         * the downloader each construct their own. Two separate caches would
         * hand out different URLs for the same video and break cache lookup.
         */
        private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()

        /**
         * Headers the winning client minted alongside a stream URL, keyed by the
         * videoId — which is the custom cache key, and therefore Media3's
         * DataSpec.key on every playback request. The playback data source pairs
         * them back together: googlevideo compares the request's headers against
         * the client baked into the URL (its `c=` param) and refuses a mismatch.
         */
        fun headersFor(cacheKey: String): Map<String, String> =
            streamUrlCache[cacheKey]?.headers.orEmpty()

        /**
         * Drops the cached URL + headers for a video so the next resolve mints
         * a fresh pair — Echo's expired-url eviction on playback errors.
         */
        fun invalidateStream(videoId: String) {
            streamUrlCache.remove(videoId)
        }
    }

    /**
     * The playable URL for a videoId, memoised until shortly before it expires.
     *
     * This cache is load-bearing for offline playback, not just a speed-up.
     * A Media3 CacheDataSource keys its cache on the resolved URL, so the
     * player and the downloader must independently arrive at the SAME string or
     * the playback lookup misses the download entirely and silently re-streams.
     * InnerTube returns a stable baseUrl for a given video within that URL's
     * validity window, so a short shared TTL is what keeps the two in step.
     * Re-resolving on every call would also burn a player request per tick.
     */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        streamUrlCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }
            ?.let { return@withContext it.url }
        try {
            val stream = extractor.extract(
                videoId = videoId,
                // InnerTubeX defaults allow Hls and Sabr. Both come back through
                // `audioUrl` as something ExoPlayer cannot open here — an .m3u8
                // manifest (no HLS module on the classpath) or the placeholder
                // "sabr://<videoId>" — and the player went silently to 0:00.
                // Disabling both makes the client fallback return a direct
                // progressive googlevideo URL or fail loudly. Echo ships this
                // exact configuration.
                hints = ContentHints(wantVideo = false)
                    .withStreamCapabilities(allowHls = false, allowSabr = false),
                audioQuality = AudioQuality.HIGH,
            )
            // Belt and braces: anything that is not a direct http(s) URL would
            // die inside the player as another silent 0:00 — fail here instead.
            val url = stream?.audioUrl?.takeIf { it.startsWith("http") }
            if (stream != null && url == null) {
                Log.w(
                    "SearchRepository",
                    "non-progressive stream for $videoId (client=${stream.clientName})"
                )
                return@withContext null
            }
            url?.also {
                // The extractor reports the URL's real lifetime; the old fixed 3h
                // assumption outlived some URLs, which then failed with 403.
                val expiresAtMs =
                    (stream.expiresAt?.toEpochMilliseconds()
                        ?: System.currentTimeMillis() + STREAM_URL_TTL_MS) - EXPIRY_MARGIN_MS
                streamUrlCache[videoId] = CachedStreamUrl(
                    url = it,
                    headers = stream.headers,
                    expiresAtMs = expiresAtMs
                )
            }
        } catch (e: Exception) {
            // Swallowing to null is fine (callers decide), but silently doing it
            // made download failures undiagnosable — log the real reason.
            Log.w("SearchRepository", "getAudioStreamUrl failed for $videoId", e)
            null
        }
    }

    suspend fun getRelatedSongs(videoId: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val plain = tube.next(YouTubeClient.WEB_REMIX, videoId = videoId).body<NextResponse>()
            val response = try {
                val withPlaylist = tube.next(
                    YouTubeClient.WEB_REMIX,
                    videoId = videoId,
                    playlistId = "RDAMVM$videoId",
                ).body<NextResponse>()
                if ((queuePanel(withPlaylist)?.size ?: 0) > 2) withPlaylist else plain
            } catch (_: Exception) {
                plain
            }
            val panel = queuePanel(response) ?: return@withContext emptyList()
            val out = ArrayList<SearchResult>(15)
            for (entry in panel) {
                val r = (entry as? JsonObject)?.obj("playlistPanelVideoRenderer") ?: continue
                val id = r.str("videoId")
                if (id.isNullOrBlank() || id == videoId) continue
                // Echo's filterExplicit: skip songs with an EXPLICIT_BADGE
                val isExplicit = r.arr("badges")?.any { badge ->
                    (badge as? JsonObject)?.obj("musicInlineBadgeRenderer")
                        ?.obj("icon")?.str("iconType") == "EXPLICIT_BADGE"
                } ?: false
                // Echo's filterVideoSongs: skip non-audio tracks (UGC/OMV videos)
                val musicVideoType = r.str("musicVideoType")
                val isVideoSong = musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV"
                if (isExplicit || isVideoSong) continue
                val title = textOf(r.obj("title")).ifBlank { continue }
                val artist = cleanArtist(textOf(r.obj("longBylineText")))
                val thumb = r.obj("thumbnail")
                    ?.arr("thumbnails")
                    ?.lastOrNull()
                    .let { it as? JsonObject }
                    ?.str("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                out.add(SearchResult(id, title, artist, thumb))
                if (out.size >= 15) break
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
