package nebula.music

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.ui.screens.DownloadedPlaylistScreen
import nebula.music.ui.theme.NebulaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rendering and the play/shuffle controls. Reorder lives in its own class. */
@RunWith(AndroidJUnit4::class)
class DownloadedPlaylistScreenTest : PlaylistScreenTestSupport() {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        context = freshContext()
        resetDatabase()
        bringUpDownloads()
        vm = openPlaylistsViewModel()
        playerVm = newPlayerViewModel()
    }

    @After
    fun tearDown() {
        releasePlayer()
    }

    private fun show() {
        compose.setContent {
            NebulaTheme(darkTheme = false) {
                DownloadedPlaylistScreen(vm = vm, playerVm = playerVm, onBack = {})
            }
        }
        compose.waitForIdle()
    }

    /** Waits for the seeded song to paint. Polling before the tree attaches throws. */
    private fun awaitSongs() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Song a", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun showsEveryDownloadedSong() {
        seedDownloaded(listOf("a", "b", "c"))

        show()
        awaitSongs()

        listOf("Song a", "Song b", "Song c").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }

    @Test
    fun playButtonQueuesEveryDownloadedSong() {
        seedDownloaded(listOf("a", "b", "c"))

        show()
        awaitSongs()

        // The buttons are icon-only, so they are found by content description, not text.
        compose.onNodeWithContentDescription("Play all").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf("Song a", "Song b", "Song c"),
            playerVm.queueList.map { it.title }
        )
    }

    @Test
    fun shuffleButtonTogglesShuffle() {
        seedDownloaded(listOf("a"))

        show()
        awaitSongs()

        val before = playerVm.shuffleOn
        compose.onNodeWithContentDescription("Shuffle").performClick()
        compose.waitForIdle()

        assertEquals(!before, playerVm.shuffleOn)
    }
}