package nebula.music

import nebula.music.data.BlockedSongs
import nebula.music.data.models.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The blacklist: block filters the song out of a result list, unblock brings it
 * back, duplicates collapse, blank videoIds are never blocked. Uses the object
 * directly without init() — persist() no-ops on a null DataStore, so this runs
 * on the JVM without Android. The singleton keeps state across tests, so every
 * test starts by unblocking the ids this class uses.
 */
class BlockedSongsTest {

    private val ids = listOf("vid_a", "vid_b", "vid_c")

    @Before
    fun reset() = ids.forEach { BlockedSongs.unblock(it) }

    private fun song(id: String, title: String = "Title") =
        SearchResult(videoId = id, title = title, artist = "Artist", thumbnailUrl = "")

    @Test
    fun `blocked song is filtered out, everything else survives`() {
        BlockedSongs.block("vid_a", "Title", "Artist")
        val filtered = BlockedSongs.filter(listOf(song("vid_a"), song("vid_b"), song("vid_c")))
        assertEquals(listOf("vid_b", "vid_c"), filtered.map { it.videoId })
    }

    @Test
    fun `unblock brings the song back`() {
        BlockedSongs.block("vid_a", "Title", "Artist")
        BlockedSongs.unblock("vid_a")
        val items = listOf(song("vid_a"), song("vid_b"))
        assertEquals(2, BlockedSongs.filter(items).size)
    }

    @Test
    fun `blocking the same song twice keeps one entry`() {
        BlockedSongs.block("vid_a", "Title", "Artist")
        BlockedSongs.block("vid_a", "Title", "Artist")
        assertEquals(1, BlockedSongs.songs.count { it.videoId == "vid_a" })
    }

    @Test
    fun `blank videoId is never blocked`() {
        BlockedSongs.block("", "No identity", "Artist")
        assertTrue(BlockedSongs.songs.none { it.videoId.isBlank() })
    }

    @Test
    fun `block keeps title and artist for the settings list`() {
        BlockedSongs.block("vid_a", "Song Name", "The Artist")
        val entry = BlockedSongs.songs.first { it.videoId == "vid_a" }
        assertEquals("Song Name", entry.title)
        assertEquals("The Artist", entry.artist)
    }
}
