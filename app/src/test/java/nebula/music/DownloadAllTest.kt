package nebula.music

import nebula.music.data.download.pendingDownloads
import nebula.music.data.models.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the filter a bulk playlist download runs before it starts.
 *
 * `enqueue` hands each song to Media3's DownloadManager and is not idempotent, so the
 * filter is the only thing standing between "Download all" on a 200-song list and a queue
 * full of duplicate work for one file. Android-free by construction so it runs on the JVM.
 */
class DownloadAllTest {

    private fun song(id: String) = SearchResult(id, "Title $id", "Artist", "")

    @Test
    fun a_clean_list_passes_through_in_order() {
        val songs = listOf(song("a"), song("b"), song("c"))
        assertEquals(listOf("a", "b", "c"), pendingDownloads(songs) { false }.map { it.videoId })
    }

    @Test
    fun a_repeated_song_is_queued_once() {
        // Playlists really do repeat a track. Three copies must produce one download, not
        // three competing ones racing to write the same file.
        val songs = listOf(song("a"), song("b"), song("a"), song("c"), song("b"))
        assertEquals(listOf("a", "b", "c"), pendingDownloads(songs) { false }.map { it.videoId })
    }

    @Test
    fun songs_already_on_disk_are_skipped() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val done = setOf("a", "c")
        assertEquals(listOf("b"), pendingDownloads(songs) { it in done }.map { it.videoId })
    }

    @Test
    fun a_fully_downloaded_list_has_nothing_left_to_do() {
        // What the confirm dialog counts. Returning zero here is what makes it say
        // "Download 0 songs?" and the toast say "Already downloaded" rather than
        // promising a download that will never start.
        val songs = listOf(song("a"), song("b"))
        assertEquals(emptyList<String>(), pendingDownloads(songs) { true }.map { it.videoId })
    }

    @Test
    fun an_empty_list_stays_empty() {
        assertEquals(emptyList<String>(), pendingDownloads(emptyList()) { false }.map { it.videoId })
    }

    @Test
    fun the_first_copy_wins_the_place_in_the_queue() {
        // Order is the visible track order, so skipping a later duplicate must not move an
        // earlier song down the queue.
        val songs = listOf(song("a"), song("b"), song("a"))
        val result = pendingDownloads(songs) { false }
        assertEquals(2, result.size)
        assertEquals("a", result[0].videoId)
    }
}