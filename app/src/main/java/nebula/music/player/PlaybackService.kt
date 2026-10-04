package nebula.music.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import nebula.music.NebulaApplication
import nebula.music.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// CacheDataSource / DefaultHttpDataSource are @UnstableApi. The requirement is
// declared in Java, so kotlinc only warns — but the IDE inspection reports it as
// an error, so opt in explicitly rather than leaving 17 red marks.
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // White note silhouette: without a small icon the Active-apps panel
        // shows the Android fallback robot instead of anything Nebula.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
        // ponytail: player lives here from the next step; PlayerManager keeps its copy until then
        val audio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory()))
            .build().apply {
                setAudioAttributes(audio, true)
                setHandleAudioBecomingNoisy(true)
                volume = 1.0f
            }
        player = exo
        // The timeline holds one item, so ExoPlayer reports no next/previous and the
        // OS disables those buttons on the system tile. The app owns the queue, so a
        // ForwardingPlayer advertises the four seek commands and routes them to the
        // same handlers as the custom notification buttons.
        // ponytail: wrapper only, no queue copy here; the day the tile needs
        // hasNext/hasPrevious truth, publish queue ends from the ViewModel.
        val queuePlayer = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                getAvailableCommands().contains(command)

            override fun seekToNextMediaItem() {
                nextHandler?.invoke()
            }

            override fun seekToPreviousMediaItem() {
                prevHandler?.invoke()
            }

            override fun seekToNext() {
                nextHandler?.invoke()
            }

            override fun seekToPrevious() {
                prevHandler?.invoke()
            }
        }
        session = MediaSession.Builder(this, queuePlayer)
            .setCallback(SessionCallback())
            .build()
            .also {
                // The timeline holds one item, so ExoPlayer reports no next/previous
                // and Media3 hides those buttons. Custom layout buttons bypass the
                // timeline check and route straight to the app queue instead.
                // ponytail: static handlers die with the UI process; an
                // application-scoped playback holder when that matters.
                it.setCustomLayout(listOf(prevButton(), nextButton()))
            }
    }

    private fun prevButton() = CommandButton.Builder()
        .setDisplayName("Previous")
        .setIconResId(android.R.drawable.ic_media_previous)
        .setSessionCommand(SessionCommand(ACTION_PREV, Bundle.EMPTY))
        .build()

    private fun nextButton() = CommandButton.Builder()
        .setDisplayName("Next")
        .setIconResId(android.R.drawable.ic_media_next)
        .setSessionCommand(SessionCommand(ACTION_NEXT, Bundle.EMPTY))
        .build()

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_PREV, Bundle.EMPTY))
                .add(SessionCommand(ACTION_NEXT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_PREV -> prevHandler?.invoke()
                ACTION_NEXT -> nextHandler?.invoke()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        private const val ACTION_PREV = "nebula_prev"
        private const val ACTION_NEXT = "nebula_next"

        /** Set by PlayerViewModel.attach; cleared never (see ceiling note above). */
        var prevHandler: (() -> Unit)? = null
        var nextHandler: (() -> Unit)? = null
    }

    /**
     * Read path for playback, three layers deep:
     *
     *   1. downloadCache — songs the user saved offline, read-only
     *   2. playerCache    — the streaming cache, read/write (FLAG_BLOCK_ON_CACHE)
     *   3. network
     *
     * This is what makes a downloaded song play from disk with no localUri check
     * and no "is it downloaded?" branch: Media3 keys the cache on the resolved
     * URL, and the download side resolves the same URL, so layer 1 simply hits.
     *
     * The download layer is read-only (setCacheWriteDataSinkFactory(null)) so
     * playback can never write into the offline store or trip its evictor.
     */
    private fun playbackDataSourceFactory(): DataSource.Factory {
        val network = DefaultHttpDataSource.Factory()

        val streaming = CacheDataSource.Factory()
            .setCache(NebulaApplication.getCache())
            .setUpstreamDataSourceFactory(network)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)

        return CacheDataSource.Factory()
            .setCache(NebulaApplication.getDownloadCache())
            .setUpstreamDataSourceFactory(streaming)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // ponytail: empty on purpose — music keeps playing when the app is swiped away
    }

    override fun onDestroy() {
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
