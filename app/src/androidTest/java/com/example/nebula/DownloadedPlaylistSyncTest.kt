package com.example.nebula

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.db.dao.PlaylistDao
import com.example.nebula.data.download.DownloadedPlaylistSync
import com.example.nebula.data.download.DownloadedSong
import com.example.nebula.data.models.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Downloaded playlist has to agree with the download index at all times, including
 * after a process kill that swallowed listener events. `reconcile` is what recovers.
 */
@RunWith(AndroidJUnit4::class)
class DownloadedPlaylistSyncTest {

    private lateinit var db: NebulaDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var sync: DownloadedPlaylistSync

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NebulaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.playlistDao()
        sync = DownloadedPlaylistSync(dao)
    }

    @After
    fun tearDown() = db.close()

    private fun song(videoId: String) =
        DownloadedSong(videoId, "Title $videoId", "Artist $videoId", "https://art/$videoId.jpg")

    @Test
    fun ensureExists_createsExactlyOneSystemPlaylist() = runBlocking {
        sync.ensureExists()
        sync.ensureExists()
        sync.ensureExists()

        val systems = dao.observePlaylists().first().filter { it.playlist.isSystem }
        assertEquals(1, systems.size)
        assertEquals(DownloadedPlaylistSync.DEFAULT_NAME, systems.single().playlist.name)
    }

    @Test
    fun onCompleted_appendsTheSongWithItsArtistAndArtwork() = runBlocking {
        val s = song("vid-a")

        sync.onCompleted(SearchResult(s.videoId, s.title, s.artist, s.thumbnailUrl ?: ""))

        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        val rows = dao.observeSongs(id).first()
        assertEquals(1, rows.size)
        assertEquals("Artist vid-a", rows.single().artist)
        assertEquals("https://art/vid-a.jpg", rows.single().thumbnailUrl)
        assertEquals(0, rows.single().position)
    }

    @Test
    fun onCompleted_appendsInOrder() = runBlocking {
        listOf("a", "b", "c").forEach { id ->
            sync.onCompleted(SearchResult(id, "T$id", "A$id", ""))
        }

        val pid = requireNotNull(dao.getSystemPlaylistBlocking()).id
        assertEquals(listOf(0, 1, 2), dao.observeSongs(pid).first().map { it.position })
    }

    @Test
    fun onCompleted_isIdempotentForTheSameVideoId() = runBlocking {
        val s = SearchResult("vid-a", "T", "A", "")

        sync.onCompleted(s)
        sync.onCompleted(s)
        sync.onCompleted(s)

        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        assertEquals(1, dao.observeSongs(id).first().size)
    }

    @Test
    fun onCompleted_afterARenameStillLandsInTheSamePlaylist() = runBlocking {
        sync.ensureExists()
        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        dao.rename(id, "Offline Favourites", System.currentTimeMillis())

        sync.onCompleted(SearchResult("vid-a", "T", "A", ""))

        assertEquals(1, dao.observeSongs(id).first().size)
    }

    @Test
    fun onRemoved_dropsTheRow() = runBlocking {
        sync.onCompleted(SearchResult("vid-a", "T", "A", ""))
        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id

        sync.onRemoved("vid-a")

        assertTrue(dao.observeSongs(id).first().isEmpty())
    }

    @Test
    fun onRemoved_forAnUnknownVideoIsHarmless() = runBlocking {
        sync.ensureExists()

        sync.onRemoved("never-downloaded")

        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        assertTrue(dao.observeSongs(id).first().isEmpty())
    }

    @Test
    fun reconcile_insertsSongsTheEventsMissed() = runBlocking {
        // No event ever fired: simulates a process kill between download and sync.
        sync.reconcile(listOf(song("vid-a"), song("vid-b")))

        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        assertEquals(setOf("vid-a", "vid-b"), dao.observeSongs(id).first().map { it.videoId }.toSet())
    }

    @Test
    fun reconcile_deletesRowsWhoseDownloadIsGone() = runBlocking {
        sync.onCompleted(SearchResult("vid-a", "T", "A", ""))
        sync.onCompleted(SearchResult("vid-b", "T", "B", ""))
        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id

        sync.reconcile(listOf(song("vid-a")))

        assertEquals(listOf("vid-a"), dao.observeSongs(id).first().map { it.videoId })
    }

    @Test
    fun reconcile_survivesAnEmptyIndex() = runBlocking {
        sync.onCompleted(SearchResult("vid-a", "T", "A", ""))
        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id

        sync.reconcile(emptyList())

        assertTrue(dao.observeSongs(id).first().isEmpty())
    }

    @Test
    fun reconcile_isIdempotent() = runBlocking {
        val index = listOf(song("vid-a"), song("vid-b"))

        sync.reconcile(index)
        sync.reconcile(index)
        sync.reconcile(index)

        val id = requireNotNull(dao.getSystemPlaylistBlocking()).id
        assertEquals(2, dao.observeSongs(id).first().size)
    }

    @Test
    fun reconcile_preservesUserPlaylists() = runBlocking {
        val userId = dao.insert(
            com.example.nebula.data.db.entities.PlaylistEntity(name = "Mine")
        )
        dao.insertSongs(
            listOf(
                com.example.nebula.data.db.entities.PlaylistSongEntity(
                    playlistId = userId, videoId = "unrelated",
                    title = "T", artist = "A", thumbnailUrl = null, position = 0
                )
            )
        )

        sync.reconcile(listOf(song("vid-a")))

        assertEquals(1, dao.observeSongs(userId).first().size)
    }
}
