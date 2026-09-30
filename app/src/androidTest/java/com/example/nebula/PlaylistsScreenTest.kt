package com.example.nebula

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.ui.screens.PlaylistsScreen
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 1 is Review Focus line 1: a first-run user with no playlists must see a
 * create prompt, not a blank list.
 * Test 2 is Review Focus line 3: an over-long name must not break the card layout.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Application
    private lateinit var vm: PlaylistsViewModel
    private lateinit var hotJob: Job

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NebulaDatabase.closeForTests()
        context.deleteDatabase(NebulaDatabase.DB_NAME)
        // PlaylistsScreen composes a DownloadViewModel for its Downloads card, and that
        // ViewModel's init builds a Media3 DownloadManager. Bring the subsystem up here
        // so the screen can compose under test. Idempotent, and a no-op once the real
        // NebulaDownloadService has initialised it in the app.
        NebulaDownloads.init(context)
        vm = PlaylistsViewModel(context)
        hotJob = CoroutineScope(Dispatchers.Default).launch { vm.playlists.collect {} }
    }

    @After
    fun tearDown() {
        hotJob.cancel()
        NebulaDatabase.closeForTests()
    }

    @Test
    fun emptyDatabase_showsCreatePromptNotBlankList() {
        compose.setContent {
            NebulaTheme(darkTheme = false) { PlaylistsScreen(vm = vm) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Create your first playlist").assertIsDisplayed()
    }

    @Test
    fun longPlaylistName_truncatesWithoutBreakingTheCard() {
        val longName = "Extremely Long Playlist Name That Should Not Wrap Or Push Anything Off Screen " +
            "But Keeps Going Well Past Any Reasonable Title Length For A List Row"
        vm.create(longName)

        compose.setContent {
            NebulaTheme(darkTheme = false) { PlaylistsScreen(vm = vm) }
        }
        // Settle the composition before polling. waitUntil evaluates its condition
        // immediately, and fetching semantics nodes before the tree is attached throws
        // IllegalStateException rather than returning false, so the poll never gets to retry.
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(longName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(longName, substring = true).assertExists()
        // The play button sharing the row must survive the long title.
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }
}
