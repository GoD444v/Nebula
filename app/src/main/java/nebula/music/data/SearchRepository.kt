package nebula.music.data

import android.util.Log
import nebula.music.data.models.HomeCard
import nebula.music.data.models.HomeChip
import nebula.music.data.models.HomeFeed
import nebula.music.data.models.HomeSection
import nebula.music.data.models.SearchResult
import com.metrolist.innertubex.InnerTube
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

class SearchRepository {

    object SearchFilter {
        val ALL: String? = null
        // Songs filter = the one both Metrolist (innertubex's author) and Echo ship.
        // The old string decoded to a different category in the same family -> small page, no ticket.
        const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        // Old value was EgYKABgC = the youtube.com WEB filter (decodes malformed, only 4 of 6 bytes).
        // This is Echo's music-client filter (its YouTube.kt:2075).
        const val VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        const val ALBUMS = "EgeKAQwIABABGAEoAEoLEAkQBhgHKAA%3D"
        const val ARTISTS = "EgeKAQwIABABGAEoAEoKEAoQAxgE"
        const val PLAYLISTS = "EgeKAQwIABABGAEoAEoKEAkQExgJ"
        // The two playlist chips Echo actually shows (verified against its YouTube.kt:2078-2079)
        const val COMMUNITY_PLAYLISTS = "EgeKAQQoAEABagoQAxAEEAoQCRAF"
        const val FEATURED_PLAYLISTS = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"
    }

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val tube = InnerTube(httpClient)

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
            return SearchResult(
                videoId, title, artist,
                thumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                isVideoSong = isVideoSong
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
        return SearchResult(
            "", title, artist, thumb.orEmpty(),
            playlistId = playlistId, browseId = browseId, pageType = pageType
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
                sections = sectionsOf(home) + exploreSections
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HomeFeed(emptyList(), emptyList())
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
     * Album / playlist detail page. browseId comes straight from a feed card
     * ("MPREb_..." albums, "VL..." playlists). Header path and the two shelf
     * variants (musicShelfRenderer for albums, musicPlaylistShelfRenderer for
     * playlists) were probed against the live payload before writing this.
     */
    suspend fun getDetail(browseId: String): DetailPage = withContext(Dispatchers.IO) {
        try {
            val page = tube.browse(YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>()
            val twoCol = page.obj("contents")?.obj("twoColumnBrowseResultsRenderer")
            val tab = twoCol?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            val header = tab?.obj("tabRenderer")?.obj("content")
                ?.obj("sectionListRenderer")?.arr("contents")
                ?.firstOrNull().let { it as? JsonObject }
                ?.obj("musicResponsiveHeaderRenderer")
            val shelfHost = twoCol?.obj("secondaryContents")
                ?.obj("sectionListRenderer")?.arr("contents")
                ?.firstOrNull().let { it as? JsonObject }
            val shelf = shelfHost?.obj("musicShelfRenderer")
                ?: shelfHost?.obj("musicPlaylistShelfRenderer")
            val tracks = shelf?.arr("contents").orEmpty().mapNotNull { entry ->
                (entry as? JsonObject)?.obj("musicResponsiveListItemRenderer")
                    ?.let { mapSearchItem(it) }
            }
            val thumbEl = header?.obj("thumbnail")
                ?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")
                ?.arr("thumbnails")
                ?.lastOrNull()
            DetailPage(
                title = runsText(header?.obj("title")),
                subtitle = runsText(header?.obj("subtitle")),
                thumbnailUrl = (thumbEl as? JsonObject)?.str("url").orEmpty(),
                tracks = tracks
            )
        } catch (_: Exception) {
            DetailPage(title = "")
        }
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

    private fun sectionsOf(page: JsonObject): List<HomeSection> {
        val contents = page.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")?.firstOrNull().let { it as? JsonObject }
            ?.obj("tabRenderer")?.obj("content")
            ?.obj("sectionListRenderer")?.arr("contents") ?: return emptyList()

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
        "", title, subtitle, thumbnailUrl, browseId = browseId, pageType = pageType
    )

    /**
     * Dedup key for search results. Songs dedup by videoId; browse rows
     * (playlists/albums/artists) carry no videoId, so keying on it alone
     * collapsed every one of them into a single survivor.
     */
    private fun distinctKey(r: SearchResult): String =
        r.videoId.ifBlank { r.browseId + r.playlistId + r.title }

    private val ignoredChips = setOf("Podcasts", "Uploaded")

    private data class CachedStreamUrl(val url: String, val expiresAtMs: Long)

    companion object {
        /** 3 hours. InnerTube audio URLs are typically valid ~6h; refresh early. */
        private const val STREAM_URL_TTL_MS = 3L * 60 * 60 * 1000

        /**
         * Shared across every SearchRepository instance, because the player and
         * the downloader each construct their own. Two separate caches would
         * hand out different URLs for the same video and break cache lookup.
         */
        private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
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
            extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false),
                audioQuality = AudioQuality.HIGH,
            )?.audioUrl?.takeIf { it.isNotBlank() }?.also { url ->
                streamUrlCache[videoId] = CachedStreamUrl(
                    url = url,
                    // No expiry is reported by the extractor, so assume the
                    // usual InnerTube window and refresh well inside it.
                    expiresAtMs = System.currentTimeMillis() + STREAM_URL_TTL_MS
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
