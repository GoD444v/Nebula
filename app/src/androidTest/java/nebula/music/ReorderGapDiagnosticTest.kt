package nebula.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.dao.PlaylistDao
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity
import nebula.music.data.download.DownloadedPlaylistSync
import nebula.music.data.download.DownloadedSong
import nebula.music.viewmodel.PlaylistsViewModel
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
 * Regression cover for "I reorder, go back, come back, and the order has reset".
 *
 * The cause was NOT persistence. `PlaylistsReorderTest` already proved `moveSong` writes
 * correctly, and the first test here pins that down again: nothing rewrites positions
 * when the screen is re-entered.
 *
 * The cause was that `moveSong` shifts on stored `position` values while every caller
 * thinks in screen row indexes, and a prune that skipped renumbering left holes in the
 * positions (0, 2, 3). The two numbering schemes diverged, the `BETWEEN` window covered
 * the wrong rows, and the reorder became a silent no-op.
 *
 * `reorderMovesTheRightRowsWhenPositionsHaveAHole` failed with the order unchanged before
 * the fix. It is kept, holes and all, because that is the exact input that used to break,
 * and installs that already have holes will keep having them for a while.
 */
@RunWith(AndroidJUnit4::class)
class ReorderGapDiagnosticTest {

    private lateinit var context: Application
    private lateinit var dao: PlaylistDao
    private var hotJob: Job? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dao = NebulaDatabase.getDatabase(context).playlistDao()
    }

    @After
    fun tearDown() {
        hotJob?.cancel()
    }

    /** A ViewModel kept subscribed, mirroring how the screen holds `songs` alive. */
    private fun hotViewModel(): PlaylistsViewModel {
        val vm = PlaylistsViewModel(context)
        hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
        return vm
    }

    private fun song(pid: Long, videoId: String, position: Int) = PlaylistSongEntity(
        playlistId = pid,
        videoId = videoId,
        title = "Title $videoId",
        artist = "Artist",
        thumbnailUrl = null,
        position = position
    )

    private fun order(id: Long): List<String> = runBlocking {
        withTimeout(5_000) {
            dao.observeSongs(id).first().map { it.videoId }
        }
    }

    private fun positions(id: Long): List<Int> = runBlocking {
        withTimeout(5_000) {
            dao.observeSongs(id).first().map { it.position }
        }
    }

    private fun awaitOrder(id: Long, expected: List<String>): List<String> = runBlocking {
        withTimeout(5_000) {
            dao.observeSongs(id).first { rows -> rows.map { it.videoId } == expected }
                .map { it.videoId }
        }
    }

    /**
     * Nothing rewrites the stored order on re-entry, so the reset the user saw was never
     * a persistence problem. This is the negative result that ruled that theory out.
     */
    @Test
    fun freshViewModelDoesNotRewriteTheStoredOrder() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "DiagFresh"))
        listOf("a", "b", "c").forEachIndexed { i, v -> dao.insertSongs(listOf(song(id, v, i))) }

        hotViewModel().reorder(id, 0, 2)
        awaitOrder(id, listOf("b", "c", "a"))

        // Leave and come back: a brand new ViewModel, a fresh subscribe on the same rows.
        hotJob?.cancel()
        val reentered = hotViewModel()
        reentered.select(id)
        withTimeout(5_000) { reentered.songs.first { it.size == 3 } }

        assertEquals(listOf("b", "c", "a"), order(id))
    }

    /**
     * The regression itself. Positions with a hole in them: row 0 of the screen is the
     * song at position 0, row 1 is the song at position 2. `reorder(id, 0, 1)` asks to
     * swap the first two ROWS, so the result must be b, a, c.
     */
    @Test
    fun reorderMovesTheRightRowsWhenPositionsHaveAHole() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "DiagGap"))
        // Positions 0, 2, 3. Position 1 was pruned by a reconcile from before the fix.
        dao.insertSongs(
            listOf(song(id, "a", 0), song(id, "b", 2), song(id, "c", 3))
        )

        hotViewModel().reorder(id, 0, 1)

        assertEquals("b, a, c", awaitOrder(id, listOf("b", "a", "c")).joinToString())
    }

    /**
     * A bad row index must still be harmless rather than corrupting the list, now that
     * the indexes travel through a lookup.
     */
    @Test
    fun reorderIgnoresIndexesThatDoNotExist() = runBlocking {
        val id = dao.insert(PlaylistEntity(name = "DiagBounds"))
        listOf("a", "b").forEachIndexed { i, v -> dao.insertSongs(listOf(song(id, v, i))) }

        val vm = hotViewModel()
        vm.reorder(id, 0, 9)
        vm.reorder(id, 7, 0)
        vm.reorder(id, -1, 0)

        // Nothing valid was ever requested, so the order must still be the original.
        // Polled, so a slow no-op cannot be mistaken for a fast one.
        assertEquals("a, b", awaitOrder(id, listOf("a", "b")).joinToString())
    }

    /**
     * The prune path that created the holes in the first place, driven through the sync
     * rather than a hand-built fixture so the shape cannot be wrong.
     *
     * After a download is removed while the process was dead, reconcile must leave the
     * survivors densely numbered. If it did not, the next reorder on that playlist would
     * silently do nothing.
     *
     * Deliberately does NOT go through a ViewModel. `PlaylistsViewModel.init` calls
     * `reconcileDownloadedPlaylist`, which prunes the system playlist against the real
     * download index and races this test's own seed data. The reorder that a hole used to
     * break is covered above, against a playlist the sync does not touch.
     */
    @Test
    fun reconcileRenumbersSurvivorsSoTheNextReorderStillWorks() = runBlocking {
        val sync = DownloadedPlaylistSync(dao)
        val id = sync.ensureExists()
        dao.deleteAllSongs(id)
        sync.reconcile(
            listOf(
                DownloadedSong("a", "A", "Artist", null),
                DownloadedSong("b", "B", "Artist", null),
                DownloadedSong("c", "C", "Artist", null)
            )
        )
        assertEquals(listOf(0, 1, 2), positions(id))

        // "b" is deleted from disk while the process is dead, so reconcile prunes it.
        sync.reconcile(
            listOf(
                DownloadedSong("a", "A", "Artist", null),
                DownloadedSong("c", "C", "Artist", null)
            )
        )
        // The hole IS the bug: without this, the next reorder on this playlist is a no-op.
        assertEquals("prune must renumber", listOf(0, 1), positions(id))
        assertEquals("a, c", order(id).joinToString())
    }
}