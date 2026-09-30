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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Storage-level guarantees for [PlaylistDao].
 *
 * Two of these are Review Focus items: the duplicate-add no-op (which must also report
 * that nothing was added) and the cascading delete.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

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
    fun tearDown() {
        db.close()
    }

    private fun song(playlistId: Long, videoId: String, position: Int) = PlaylistSongEntity(
        playlistId = playlistId,
        videoId = videoId,
        title = "Title $videoId",
        artist = "Artist $videoId",
        thumbnailUrl = null,
        position = position
    )

    @Test
    fun observePlaylists_returnsZeroCountForEmptyPlaylist() = runBlocking {
        dao.insert(PlaylistEntity(name = "Empty"))

        val rows = dao.observePlaylists().first()

        assertEquals(1, rows.size)
        assertEquals(0, rows.first().songCount)
    }

    @Test
    fun observePlaylists_countsSongsViaSubquery() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "Road Trip"))
        dao.insertSongs(
            listOf(
                song(id, "vid-a", 0),
                song(id, "vid-b", 1),
                song(id, "vid-c", 2)
            )
        )

        val rows = dao.observePlaylists().first()

        // Counted in SQL, not by looping songs in Kotlin — this is the N+1 guard.
        assertEquals(3, rows.single().songCount)
    }

    @Test
    fun insertSongs_ignoresDuplicateVideoIdAndKeepsOriginalPosition() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "Dupes"))
        dao.insertSongs(listOf(song(id, "vid-a", 0)))

        val secondAttempt = dao.insertSongs(listOf(song(id, "vid-a", 5)))
        val stored = dao.observeSongs(id).first()

        assertEquals(1, stored.size)
        assertEquals(0, stored.single().position)
        // Room reports -1 for a row the IGNORE strategy skipped. This is how the
        // ViewModel tells the user "already in playlist" instead of silently doing nothing.
        assertTrue("expected a -1 for the skipped insert", secondAttempt.contains(-1L))
    }

    @Test
    fun delete_playlist_cascadesAndRemovesItsSongs() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "Doomed"))
        dao.insertSongs(listOf(song(id, "vid-a", 0), song(id, "vid-b", 1)))
        assertEquals(2, dao.observeSongs(id).first().size)

        dao.delete(id)

        assertTrue("songs outlived their playlist", dao.observeSongs(id).first().isEmpty())
        assertTrue(dao.observePlaylists().first().isEmpty())
    }
}
