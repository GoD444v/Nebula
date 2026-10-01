package com.example.nebula

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.models.SearchResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "create a playlist and put these songs in it" path, which the picker needs.
 *
 * The two-step version (`create` then `addSongs`) cannot work: `create` launches into
 * `viewModelScope` and returns before the row exists, so the id `addSongs` needs is not
 * available yet and the songs are silently dropped.
 *
 * No database reset between tests — see the note on `PlaylistsReorderTest`. Isolation is
 * by playlist id, and the ViewModel is built lazily because its `init` runs a reconcile
 * that prunes the system playlist.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistsCreateWithSongsTest {

    private lateinit var context: Application
    private lateinit var vm: PlaylistsViewModel
    private var hotJob: Job? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        hotJob?.cancel()
    }

    private fun ensureViewModel(): PlaylistsViewModel {
        if (!::vm.isInitialized) {
            vm = PlaylistsViewModel(context)
            hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
        }
        return vm
    }

    private fun song(videoId: String) =
        SearchResult(videoId = videoId, title = "Title $videoId", artist = "Artist $videoId", thumbnailUrl = "")

    private fun songIdsOf(playlistId: Long): List<String> = runBlocking {
        withTimeout(5_000) {
            NebulaDatabase.getDatabase(context).playlistDao()
                .observeSongs(playlistId).first().map { it.videoId }
        }
    }

    @Test
    fun createWithSongs_putsTheSongsInTheNewPlaylist() = runBlocking {
        val id = ensureViewModel().createWithSongs("Road Trip", listOf(song("a"), song("b"), song("c")))

        assertNotNull(id)
        assertEquals(listOf("a", "b", "c"), songIdsOf(id!!))
    }

    @Test
    fun createWithSongs_keepsTheArtistAndArtwork() = runBlocking {
        val s = song("a").copy(thumbnailUrl = "https://art/a.jpg")
        val id = ensureViewModel().createWithSongs("Mix", listOf(s))!!

        val row = withTimeout(5_000) {
            NebulaDatabase.getDatabase(context).playlistDao().observeSongs(id).first().single()
        }
        assertEquals("Artist a", row.artist)
        assertEquals("https://art/a.jpg", row.thumbnailUrl)
    }

    @Test
    fun createWithSongs_rejectsABlankName() = runBlocking {
        assertNull(ensureViewModel().createWithSongs("   ", listOf(song("a"))))
    }

    @Test
    fun createWithSongs_createsAnEmptyPlaylistWhenGivenNoSongs() = runBlocking {
        val id = ensureViewModel().createWithSongs("Later", emptyList())

        assertNotNull(id)
        assertEquals(emptyList<String>(), songIdsOf(id!!))
    }

    @Test
    fun createWithSongs_selectsTheNewPlaylistSoThePickerCanNavigate() = runBlocking {
        val id = ensureViewModel().createWithSongs("Chosen", listOf(song("a")))

        assertEquals(id, ensureViewModel().selectedPlaylistId.value)
    }

    @Test
    fun addingTheSameSongTwiceReportsZeroNewRatherThanDuplicating() = runBlocking {
        val vmm = ensureViewModel()
        val id = vmm.createWithSongs("Twice", listOf(song("a")))!!

        val addedAgain = vmm.addSongs(id, listOf(song("a")))

        assertEquals(0, addedAgain)
        assertEquals(listOf("a"), songIdsOf(id))
    }

    @Test
    fun addingASongAlreadyPresentStillAddsTheNewOnes() = runBlocking {
        val vmm = ensureViewModel()
        val id = vmm.createWithSongs("Partial", listOf(song("a")))!!

        val added = vmm.addSongs(id, listOf(song("a"), song("b")))

        assertEquals(1, added)
        assertEquals(listOf("a", "b"), songIdsOf(id))
    }
}