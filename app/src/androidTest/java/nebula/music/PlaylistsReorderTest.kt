package nebula.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity
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
    private var hotJob: Job? = null
    private var playlistId: Long = -1

    /**
     * The shared Room singleton is deliberately NOT closed between tests.
     *
     * `closeForTests()` races with coroutines still in flight: `reorder` reads
     * `observeSongs` from `viewModelScope`, which is not cancelled by closing the
     * database, so the next test's close tore the connection pool out from under the
     * previous test's read ("Cannot perform this operation because the connection pool
     * has been closed"). Isolation comes from each test using its own playlist id
     * instead, which is sufficient and deterministic.
     */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        hotJob?.cancel()
    }

    /**
     * Built lazily and only by the tests that need it.
     *
     * Constructing a [PlaylistsViewModel] runs `reconcileDownloadedPlaylist` from its
     * `init`, which prunes the system playlist against the real download index. That is
     * correct production behaviour, but it races any test seeding that playlist — so the
     * one test that targets the system playlist must not bring a ViewModel into scope.
     */
    private fun ensureViewModel(): PlaylistsViewModel {
        if (!::vm.isInitialized) {
            vm = PlaylistsViewModel(context)
            hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
        }
        return vm
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

        ensureViewModel().reorder(playlistId, 0, 1)

        assertEquals(listOf("b", "a", "c"), awaitOrder(listOf("b", "a", "c")))
    }

    @Test
    fun movingUpSwapsWithThePreviousSong() = runBlocking {
        playlistId = seed("a", "b", "c")

        ensureViewModel().reorder(playlistId, 2, 1)

        assertEquals(listOf("a", "c", "b"), awaitOrder(listOf("a", "c", "b")))
    }

    @Test
    fun reorderRenumbersPositionsWithoutGaps() = runBlocking {
        playlistId = seed("a", "b", "c", "d")

        ensureViewModel().reorder(playlistId, 0, 2)
        awaitOrder(listOf("b", "c", "a", "d"))

        // Renumbering is what keeps `ORDER BY position` meaningful and nextPosition correct.
        assertEquals(listOf(0, 1, 2, 3), positions())
    }

    @Test
    fun reorderIsANoOpWhenFromEqualsTo() = runBlocking {
        playlistId = seed("a", "b", "c")

        ensureViewModel().reorder(playlistId, 1, 1)

        assertEquals(listOf("a", "b", "c"), order())
        assertEquals(listOf(0, 1, 2), positions())
    }

    @Test
    fun reorderIgnoresOutOfRangeIndexes() = runBlocking {
        playlistId = seed("a", "b")

        ensureViewModel().reorder(playlistId, 5, 0)
        ensureViewModel().reorder(playlistId, 0, 9)

        // A bad index must leave the list alone rather than crash or truncate it.
        assertEquals(listOf("a", "b"), order())
    }

    /**
 * Reordering the system playlist must work even though deleting it must not.
 *
 * Asserted against the DAO rather than through the ViewModel, and that is the point:
 * `PlaylistsViewModel.init` calls `reconcileDownloadedPlaylist`, which correctly prunes
 * any row not present in the real download index. Driving this through the ViewModel
 * therefore races the test's own seed data against a prune — which is right behaviour,
 * not a bug, and makes the test untestable at that level. The property under test is
 * that `moveSong` carries no `isSystem` guard while `delete` does.
 */
@Test
fun moveSongWorksOnTheSystemPlaylistWhileDeleteStillRefusesIt() = runBlocking {
    val dao = NebulaDatabase.getDatabase(context).playlistDao()
    val id = dao.insertSystemPlaylist("Downloaded")
    // Not reset between tests (see setUp) and insertSystemPlaylist is idempotent, so this
    // playlist may already hold rows. Clear it so the assertion is about known data.
    dao.deleteAllSongs(id)
    listOf("x", "y", "z").forEachIndexed { index, vid ->
        dao.insertSongs(
            listOf(
                PlaylistSongEntity(
                    playlistId = id,
                    videoId = vid,
                    title = "T",
                    artist = "A",
                    thumbnailUrl = null,
                    position = index
                )
            )
        )
    }

    dao.moveSong(id, 0, 2)

    val order = withTimeout(5_000) {
        dao.observeSongs(id).first { rows -> rows.map { it.videoId } == listOf("y", "z", "x") }
            .map { it.videoId }
    }
    assertEquals(listOf("y", "z", "x"), order)

    // And the delete guard is untouched: the row must still be there.
    assertEquals(0, dao.delete(id))
    assertEquals(id, dao.getSystemPlaylistBlocking()?.id)
}
}