package nebula.music

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.ui.screens.DownloadsIconButton
import nebula.music.ui.theme.NebulaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Playlists header's downloads button. Composable under test takes a plain Int,
 * so no ViewModel and no download subsystem are needed to render it.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsIconButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(activeCount: Int) {
        compose.setContent {
            NebulaTheme(darkTheme = false) {
                DownloadsIconButton(activeCount = activeCount, onClick = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsIconAlways() {
        render(activeCount = 0)

        compose.onNodeWithContentDescription("Downloads").assertIsDisplayed()
    }

    @Test
    fun showsCountBadgeWhenDownloadsAreActive() {
        render(activeCount = 3)

        compose.onNodeWithContentDescription("Downloads").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun hidesBadgeWhenNothingIsActive() {
        render(activeCount = 0)

        // assertCountEquals(0), not onNodeWithText(...).assertDoesNotExist(): asserting
        // absence through a single-node matcher is the path that trips Compose's
        // hierarchy lookup. Counting the matches states the requirement directly.
        compose.onAllNodesWithText("3").assertCountEquals(0)
    }
}
