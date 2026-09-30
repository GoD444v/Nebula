package com.example.nebula

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.db.dao.PlaylistDao
import com.example.nebula.data.db.entities.PlaylistEntity
import com.example.nebula.data.db.entities.PlaylistSongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards around the one built-in playlist. The system playlist mirrors the download
 * index, so it must be findable by flag (not by name, so a rename cannot break sync),
 * must refuse deletion, and must still be renameable.
 */
@RunWith(AndroidJUnit4::class)
class DownloadedPlaylistDaoTest {

    private lateinit var db: NebulaDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NebulaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    private fun song(playlistId: Long, videoId: String, position: Int = 0) =
        PlaylistSongEntity(
            playlistId = playlistId,
            videoId = videoId,
            title = "Title $videoId",
            artist = "Artist",
            thumbnailUrl = null,
            position = position
        )

    @Test
    fun insertSystemPlaylist_isFoundByBothSystemLookups() = runBlocking {
        val id = dao.insertSystemPlaylist("Downloaded")

        assertNotNull(dao.getSystemPlaylistBlocking())
        assertEquals(id, dao.getSystemPlaylistBlocking()!!.id)
        assertEquals(id, dao.observeSystemPlaylist().first()!!.id)
    }

    @Test
    fun insertSystemPlaylist_doesNotDuplicateOnSecondCall() = runBlocking {
        dao.insertSystemPlaylist("Downloaded")
        dao.insertSystemPlaylist("Downloaded")

        val all = dao.observePlaylists().first().filter { it.playlist.isSystem }
        assertEquals(1, all.size)
    }

    @Test
    fun observeSystemPlaylist_isNullBeforeAnythingIsCreated() = runBlocking {
        assertNull(dao.observeSystemPlaylist().first())
    }

    @Test
    fun delete_refusesASystemPlaylist() = runBlocking {
        val id = dao.insertSystemPlaylist("Downloaded")
        dao.insertSongs(listOf(song(id, "vid-a")))

        val rowsDeleted = dao.delete(id)

        assertEquals("delete must affect zero rows for a system playlist", 0, rowsDeleted)
        assertNotNull("the system playlist must survive", dao.getSystemPlaylistBlocking())
        assertEquals(1, dao.observeSongs(id).first().size)
    }

    @Test
    fun delete_removesUserPlaylistAndItsSongs() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "Mine"))
        dao.insertSongs(listOf(song(id, "vid-a"), song(id, "vid-b")))

        val rowsDeleted = dao.delete(id)

        assertEquals(1, rowsDeleted)
        assertTrue(dao.observeSongs(id).first().isEmpty())
        assertTrue(dao.observePlaylists().first().none { it.playlist.id == id })
    }

    @Test
    fun deleteSongsNotIn_prunesOnlyRowsMissingFromTheIndex() = runBlocking {
        val id = dao.insertSystemPlaylist("Downloaded")
        dao.insertSongs(listOf(song(id, "keep-1"), song(id, "keep-2"), song(id, "gone")))

        dao.deleteSongsNotIn(listOf("keep-1", "keep-2"), id)

        val remaining = dao.observeSongs(id).first().map { it.videoId }.toSet()
        assertEquals(setOf("keep-1", "keep-2"), remaining)
    }

    @Test
    fun deleteSongsNotIn_withEmptyIndexClearsThePlaylist() = runBlocking {
        // "NOT IN ()" is invalid SQL, so the empty case needs its own path.
        val id = dao.insertSystemPlaylist("Downloaded")
        dao.insertSongs(listOf(song(id, "a"), song(id, "b")))

        dao.deleteSongsNotIn(emptyList(), id)

        assertTrue(dao.observeSongs(id).first().isEmpty())
    }

    @Test
    fun rename_worksOnASystemPlaylist() = runBlocking {
        val id = dao.insertSystemPlaylist("Downloaded")

        dao.rename(id, "Offline Favourites", System.currentTimeMillis())

        // Still findable by flag after a rename: that is the whole point.
        assertEquals("Offline Favourites", dao.getSystemPlaylistBlocking()!!.name)
    }
}
