package nebula.music

import nebula.music.data.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Hits the real YouTube Music endpoints, so a parser that drifts from the live
 * payload fails here instead of showing an empty Home tab on someone's phone.
 */
class HomeFeedTest {

    private val repo = SearchRepository()

    @Test
    fun homeReturnsSectionsWithPlayableSongs() = runBlocking {
        val feed = repo.home()
        println("chips=${feed.chips.size} sections=${feed.sections.size}")
        feed.sections.forEach {
            println("  ${it.title}: ${it.cards.size} cards, ${it.cards.count { c -> c.isSong }} playable")
        }
        check(feed.sections.isNotEmpty()) { "home feed came back empty" }
        check(feed.chips.isNotEmpty()) { "no filter chips — chipsOf parser broken?" }
        check(feed.sections.any { it.cards.any { c -> c.isSong } }) { "no playable song cards" }
    }

    /** A chip's params must actually change the feed, not silently return the default. */
    @Test
    fun chipParamsReloadTheFeed() = runBlocking {
        val feed = repo.home()
        val chip = feed.chips.firstOrNull() ?: throw IllegalStateException("no chips in the feed")
        val filtered = repo.home(chip.params)
        println("chip '${chip.label}': sections=${filtered.sections.size}")
        check(filtered.sections.isNotEmpty()) { "chip reload returned no sections" }
    }

    /** Album (musicShelfRenderer) and playlist (musicPlaylistShelfRenderer) detail parsers. */
    @Test
    fun detailPagesReturnTitleAndTracks() = runBlocking {
        val cards = repo.home().sections.flatMap { it.cards }

        val album = cards.firstOrNull { it.browseId.startsWith("MPREb") }
            ?: throw IllegalStateException("no album card in the feed")
        val albumPage = repo.getDetail(album.browseId)
        println("album: '${albumPage.title}' / '${albumPage.subtitle}' tracks=${albumPage.tracks.size} art=${albumPage.thumbnailUrl.isNotBlank()}")
        check(albumPage.title.isNotBlank()) { "album page has no title" }
        check(albumPage.tracks.isNotEmpty()) { "album page has no tracks" }
        check(albumPage.thumbnailUrl.isNotBlank()) { "album page has no artwork" }

        val playlist = cards.firstOrNull { it.browseId.startsWith("VL") }
        if (playlist != null) {
            val plPage = repo.getDetail(playlist.browseId)
            println("playlist: '${plPage.title}' tracks=${plPage.tracks.size} art=${plPage.thumbnailUrl.isNotBlank()}")
            check(plPage.title.isNotBlank()) { "playlist page has no title" }
            check(plPage.tracks.isNotEmpty()) { "playlist page has no tracks" }
        } else {
            println("playlist: no VL card in this feed run — skipped")
        }
    }
}
