package nebula.music

import androidx.compose.ui.test.assertCountEquals
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `overflowMenuHasNoDeleteOption` is Review Focus: the Downloaded playlist is undeletable,
 * and a Delete button that silently does nothing is worse than no button.
 *
 * Absence is asserted with `onAllNodesWithText(...).assertCountEquals(0)`. A single-node
 * matcher throws when the node is absent, which is what broke an earlier test here.
 */
@RunWith(AndroidJUnit4::class)
class DownloadedPlaylistScreenAffordancesTest : PlaylistScreenTestSupport() {

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

    private fun openOverflow() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Song a", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
    }

    @Test
    fun overflowMenuHasNoDeleteOption() {
        seedDownloaded(listOf("a"))

        show()
        openOverflow()

        compose.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun overflowMenuHasNoRemoveSongOption() {
        seedDownloaded(listOf("a", "b"))

        show()
        openOverflow()

        // Removal goes through the download queue screen, where the consequence is explicit.
        compose.onAllNodesWithText("Remove download").assertCountEquals(0)
    }

    @Test
    fun renameDialogIsOffered() {
        seedDownloaded(listOf("a"))

        show()
        openOverflow()

        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Rename playlist").assertIsDisplayed()
    }
}