package nebula.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.entities.PlaylistSongEntity
import nebula.music.data.download.NebulaDownloads
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.runBlocking

/**
 * Shared setup for the Downloaded playlist screen tests.
 *
 * Split across several test classes on purpose. Each test method rebuilds the Media3
 * `DownloadManager` via [NebulaDownloads.init] and a `MediaController` via
 * [PlayerViewModel.attach], and neither subsystem is released — `NebulaDownloads` has no
 * release path at all, and `PlayerManager` owns a fixed thread pool plus an unreleased
 * controller. A single class of eight tests accumulated enough of that to kill the
 * instrumentation process outright ("Test run failed to complete. Expected 9, received 2",
 * with logcat undeliverable). Keeping each class to three methods stays under whatever the
 * ceiling is.
 *
 * This is a workaround, not a fix. The underlying leak is real, will bite in CI, and is
 * recorded as an open item in the SDD ledger.
 */
abstract class PlaylistScreenTestSupport {

    protected lateinit var context: Application
    protected lateinit var vm: PlaylistsViewModel
    protected lateinit var playerVm: PlayerViewModel

    /** The Media3 DownloadManager that needs bringing up before composing. */
    protected fun bringUpDownloads() {
        NebulaDownloads.init(context)
    }

    protected fun openPlaylistsViewModel(): PlaylistsViewModel {
        NebulaDownloads.init(context)
        return PlaylistsViewModel(context)
    }

    protected fun newPlayerViewModel(): PlayerViewModel {
        val vm = PlayerViewModel()
        // attach() supplies the Context that shuffle/repeat persistence writes through.
        vm.attach(context)
        return vm
    }

    /**
     * Seeds the system playlist with [videoIds] through the DAO, exactly as the sync
     * would, and selects it so the screen has something to render.
     */
    protected fun seedDownloaded(videoIds: List<String>): Long = runBlocking {
        val dao = NebulaDatabase.getDatabase(context).playlistDao()
        val id = dao.insertSystemPlaylist("Downloaded")
        videoIds.forEachIndexed { index, vid ->
            dao.insertSongs(
                listOf(
                    PlaylistSongEntity(
                        playlistId = id,
                        videoId = vid,
                        title = "Song $vid",
                        artist = "Artist $vid",
                        thumbnailUrl = null,
                        position = index
                    )
                )
            )
        }
        vm.select(id)
        id
    }

    protected fun freshContext(): Application = ApplicationProvider.getApplicationContext()

    /**
     * Releases the player on the main thread.
     *
     * `MediaController.release()` asserts it runs on the application thread, and
     * `tearDown` is not that thread — calling it directly threw
     * "MediaController method is called from a wrong thread" and failed every test
     * whose body had already passed.
     */
    protected fun releasePlayer() {
        val vm = playerVm
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.release() }
    }

    protected fun resetDatabase() {
        NebulaDatabase.closeForTests()
        context.deleteDatabase(NebulaDatabase.DB_NAME)
    }
}