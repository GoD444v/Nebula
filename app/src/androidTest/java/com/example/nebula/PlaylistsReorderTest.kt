package com.example.nebula

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.db.entities.PlaylistEntity
import com.example.nebula.data.db.entities.PlaylistSongEntity
import com.example.nebula.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reorder, tested at the level the behaviour actually lives: `PlaylistsViewModel.reorder`
 * plus the DAO renumbering underneath it. Deliberately not a Compose test — acceptance
 * criterion 7 is "reordering changes playback order", which is a statement about
 * persisted position, not about pixels.
 *
 * That also keeps it runnable on the API 37 emulator, where Espresso cannot run at all
 * (`InputManager.getInstance` was removed). The arrow-button wiring is covered by
 * `DownloadedPlaylistReorderTest`, which needs a real device.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistsReorderTest {

    private lateinit var context: Application
    private lateinit var vm: PlaylistsViewModel
    private lateinit var hotJob: Job
    private var playlistId: Long = -1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NebulaDatabase.closeForTests()
        context.deleteDatabase(NebulaDatabase.DB_NAME)
        vm = PlaylistsViewModel(context)
        hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
    }

    @After
    fun tearDown() {
        hotJob.cancel()
        NebulaDatabase.closeForTests()
    }

    private fun seed(vararg videoIds: String): Long = runBlocking {
        val dao = NebulaDatabase.getDatabase(context).playlistDao()
        val id = dao.insert(PlaylistEntity(name = "Reorder"))
        videoIds.forEachIndexed { index, vid ->
            dao.insertSongs(
                listOf(
                    PlaylistSongEntity(
                        playlistId = id,
                        videoId = vid,
                        title = "Title $vid",
                        artist = "Artist",
                        thumbnailUrl = null,
                        position = index
                    )
                )
            )
        }
        id
    }

    private fun order(): List<String> = runBlocking {
        withTimeout(5_000) {
            NebulaDatabase.getDatabase(context).playlistDao()
                .observeSongs(playlistId).first().map { it.videoId }
        }
    }

    private fun positions(): List<Int> = runBlocking {
        withTimeout(5_000) {
            NebulaDatabase.getDatabase(context).playlistDao()
                .observeSongs(playlistId).first().map { it.position }
        }
    }

    /** Polls until the order matches [expected], so a slow write is not read as a failure. */
    private fun awaitOrder(expected: List<String>): List<String> = runBlocking {
        withTimeout(5_000) {
            NebulaDatabase.getDatabase(context).playlistDao()
                .observeSongs(playlistId).first { rows -> rows.map { it.videoId } == expected }
                .map { it.videoId }
        }
    }

    @Test
    fun movingDownSwapsWithTheNextSong() = runBlocking {
        playlistId = seed("a", "b", "c")

        vm.reorder(playlistId, 0, 1)

        assertEquals(listOf("b", "a", "c"), awaitOrder(listOf("b", "a", "c")))
    }

    @Test
    fun movingUpSwapsWithThePreviousSong() = runBlocking {
        playlistId = seed("a", "b", "c")

        vm.reorder(playlistId, 2, 1)

        assertEquals(listOf("a", "c", "b"), awaitOrder(listOf("a", "c", "b")))
    }

    @Test
    fun reorderRenumbersPositionsWithoutGaps() = runBlocking {
        playlistId = seed("a", "b", "c", "d")

        vm.reorder(playlistId, 0, 2)
        awaitOrder(listOf("b", "c", "a", "d"))

        // Renumbering is what keeps `ORDER BY position` meaningful and nextPosition correct.
        assertEquals(listOf(0, 1, 2, 3), positions())
    }

    @Test
    fun reorderIsANoOpWhenFromEqualsTo() = runBlocking {
        playlistId = seed("a", "b", "c")

        vm.reorder(playlistId, 1, 1)

        assertEquals(listOf("a", "b", "c"), order())
        assertEquals(listOf(0, 1, 2), positions())
    }

    @Test
    fun reorderIgnoresOutOfRangeIndexes() = runBlocking {
        playlistId = seed("a", "b")

        vm.reorder(playlistId, 5, 0)
        vm.reorder(playlistId, 0, 9)

        // A bad index must leave the list alone rather than crash or truncate it.
        assertEquals(listOf("a", "b"), order())
    }

    @Test
    fun reorderOnTheSystemPlaylistStillWorks() = runBlocking {
        // The Downloaded playlist is reorderable even though it is undeletable: only
        // `delete` carries the isSystem guard, `reorder` must not inherit one.
        val dao = NebulaDatabase.getDatabase(context).playlistDao()
        playlistId = dao.insertSystemPlaylist("Downloaded")
        listOf("x", "y", "z").forEachIndexed { index, vid ->
            dao.insertSongs(
                listOf(
                    PlaylistSongEntity(
                        playlistId = playlistId,
                        videoId = vid,
                        title = "T",
                        artist = "A",
                        thumbnailUrl = null,
                        position = index
                    )
                )
            )
        }

        vm.reorder(playlistId, 0, 2)

        assertEquals(listOf("y", "z", "x"), awaitOrder(listOf("y", "z", "x")))
    }
}