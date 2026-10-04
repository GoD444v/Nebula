package nebula.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.models.SearchResult
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
 * These live in androidTest, not test: [PlaylistsViewModel] is an AndroidViewModel
 * because the DAO needs a Context, and there is no Robolectric in this project.
 *
 * Test 2 is Review Focus line 2 (duplicate names must both persist).
 * Test 4 is Review Focus line 5's reporting half (a duplicate add must report 0).
 */
@RunWith(AndroidJUnit4::class)
class PlaylistsViewModelTest {

    private lateinit var context: Application
    private lateinit var vm: PlaylistsViewModel
    private lateinit var hotJob: Job

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NebulaDatabase.closeForTests()
        context.deleteDatabase(NebulaDatabase.DB_NAME)
        vm = PlaylistsViewModel(context)
        // SharingStarted.WhileSubscribed leaves the flow cold until watched, so keep a
        // watcher alive for the whole test and assert on awaited values, never on .value.
        hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
    }

    @After
    fun tearDown() {
        hotJob.cancel()
        NebulaDatabase.closeForTests()
    }

    private fun result(videoId: String) =
        SearchResult(videoId = videoId, title = "Title $videoId", artist = "Artist", thumbnailUrl = "")

    @Test
    fun create_addsPlaylistToTheList() = runBlocking<Unit> {
        vm.create("Road Trip")

        val rows = withTimeout(5_000) { vm.playlists.first { it.isNotEmpty() } }

        assertEquals(1, rows.size)
        assertEquals("Road Trip", rows.single().playlist.name)
        assertEquals(0, rows.single().songCount)
    }

    @Test
    fun create_allowsTwoPlaylistsWithTheSameName() = runBlocking<Unit> {
        vm.create("Road Trip")
        withTimeout(5_000) { vm.playlists.first { it.size == 1 } }
        vm.create("Road Trip")

        val rows = withTimeout(5_000) { vm.playlists.first { it.size == 2 } }

        // Name is deliberately not a unique key — keeping "Road Trip" twice is legitimate.
        assertEquals(listOf("Road Trip", "Road Trip"), rows.map { it.playlist.name })
    }

    @Test
    fun delete_removesPlaylistFromTheList() = runBlocking<Unit> {
        vm.create("Doomed")
        val created = withTimeout(5_000) { vm.playlists.first { it.isNotEmpty() } }

        vm.delete(created.single().playlist.id)

        withTimeout(5_000) { vm.playlists.first { it.isEmpty() } }
    }

    @Test
    fun addSongs_returnsCountOfNewlyAddedSongsOnly() = runBlocking<Unit> {
        vm.create("Mix")
        val created = withTimeout(5_000) { vm.playlists.first { it.isNotEmpty() } }
        val id = created.single().playlist.id

        val firstAdd = vm.addSongs(id, listOf(result("vid-a"), result("vid-b")))
        val secondAdd = vm.addSongs(id, listOf(result("vid-a"), result("vid-b")))

        assertEquals(2, firstAdd)
        assertEquals(0, secondAdd)
        // Must await the new count, not merely a non-empty list: the playlist already
        // existed with songCount 0, so `first { it.isNotEmpty() }` would return the
        // stale value and pass or fail by timing.
        val rows = withTimeout(5_000) { vm.playlists.first { it.single().songCount == 2 } }
        assertEquals(2, rows.single().songCount)
    }

    @Test
    fun create_rejectsBlankNames() = runBlocking<Unit> {
        // Queued before the real one, so once "Real" is visible the blank launch has
        // already been processed. If blanks were accepted, two rows would exist.
        vm.create("   ")
        vm.create("Real")

        val rows = withTimeout(5_000) { vm.playlists.first { it.isNotEmpty() } }

        assertEquals(1, rows.size)
        assertEquals("Real", rows.single().playlist.name)
    }
}
