package nebula.music

import nebula.music.data.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Header parsing for the browse shapes that carry the title and artwork.
 *
 * The account-playlist case regressed silently once already: the title fell back
 * to the raw browseId and the art box went blank with no error anywhere. These
 * cases pin the wrapper shapes so that failure cannot come back unnoticed.
 */
class DetailHeaderTest {

    private val repo = SearchRepository()

    /** An album page: header renderer at the top level, no wrapper. */
    @Test
    fun albumHeaderHasTitleSubtitleAndArt() = runBlocking {
        val album = repo.home().sections.flatMap { it.cards }
            .firstOrNull { it.browseId.startsWith("MPREb") }
            ?: return@runBlocking // feed shape changed; nothing to pin against
        val page = repo.getDetail(album.browseId)
        println("album '${album.browseId}' -> '${page.title}' / '${page.subtitle}' art=${page.thumbnailUrl.isNotBlank()}")
        check(page.title.isNotBlank()) { "album title blank" }
        check(page.title != album.browseId) { "title fell back to the browseId — header wrapper unparsed" }
        check(page.thumbnailUrl.isNotBlank()) { "album artwork blank" }
        check(page.subtitle.isNotBlank()) { "album subtitle blank" }
    }

    /**
     * A feed playlist page, and — when the test session happens to be signed in —
     * every account playlist too. Account playlists are the shape that was broken:
     * `musicEditablePlaylistDetailHeaderRenderer` wraps a `musicResponsiveHeaderRenderer`
     * under its `header` key, two levels deep.
     */
    @Test
    fun playlistHeadersNeverFallBackToTheBrowseId() = runBlocking {
        val ids = mutableListOf<String>()
        repo.home().sections.flatMap { it.cards }
            .filter { it.browseId.startsWith("VL") }
            .take(2)
            .forEach { ids.add(it.browseId) }
        repo.accountPlaylists().take(2).forEach { ids.add(it.browseId) }

        if (ids.isEmpty()) println("no playlist ids available; skipped")
        ids.forEach { id ->
            val page = repo.getDetail(id)
            println("playlist '$id' -> '${page.title}' / '${page.subtitle}' art=${page.thumbnailUrl.isNotBlank()}")
            check(page.title.isNotBlank()) { "playlist '$id' title blank" }
            check(page.title != id) { "playlist '$id' title fell back to the browseId" }
            check(page.thumbnailUrl.isNotBlank()) { "playlist '$id' artwork blank" }
        }
    }
}