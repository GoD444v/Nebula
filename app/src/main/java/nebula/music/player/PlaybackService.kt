package nebula.music.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import nebula.music.NebulaApplication
import nebula.music.R
import nebula.music.data.SearchRepository
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
        // Nebula's EQ is an in-pipeline AudioProcessor, not an OS audio effect, so
        // it must be installed in the player's audio sink - the only place PCM can
        // be filtered. This is what the OS Equalizer could not do on devices whose
        // effect engine refuses it.
        val eqProcessor = EqDspProcessor(NebulaEqualizer.bandFrequencies)
        // The processor is created AFTER NebulaEqualizer.init() ran (that is at
        // app start, before any player exists), so the saved on/off + levels must
        // be handed over now - otherwise the new processor starts with all-zero
        // gains and the EQ is silent until the user first touches a fader.
        NebulaEqualizer.attachProcessor(eqProcessor)
        // Media3 1.6 has no setAudioSink on the factory - the seam is the
        // protected buildAudioSink, so the override is the supported route.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(eqProcessor))
                .build()
        }

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory()))
            // Media3's default `bufferForPlaybackMs` is 2500 — the player refuses to
            // start audio until 2.5s is buffered, which on a cold cache is seconds of
            // silence behind a `0:00 / 0:00` slider. 150ms starts near-instantly.
            // The 6-minute ceiling is what lets it stay ahead of a dropout; without
            // it the default 50s cap re-buffers on every slow network.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        360_000,  // minBufferMs
                        360_000,  // maxBufferMs
                        150,      // bufferForPlaybackMs
                        500       // bufferForPlaybackAfterRebufferMs
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            // Without this the CPU may sleep mid-download and the song stalls at 0:00
            // with nothing on screen to say why.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                setAudioAttributes(audio, true)
                setHandleAudioBecomingNoisy(true)
                volume = 1.0f
            }
        player = exo
        // The audio session the system Equalizer hooks. It may already be set (try
        // now) or land later when the sink comes up (listen); 0/unset is ignored
        // inside attach(). The UI side only has a MediaController, which does not
        // expose the session id — this is the one place that owns the ExoPlayer.
        NebulaEqualizer.attach(exo.audioSessionId)
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                NebulaEqualizer.attach(audioSessionId)
            }

            // The session id can already be set before this listener is attached
            // (attach at line above saw 0), and onAudioSessionIdChanged only fires on
            // CHANGE. Re-check whenever playback actually starts or becomes ready, so
            // the studio is never stuck on "Start a song first" while music is playing.
            override fun onIsPlayingChanged(
                eventTime: AnalyticsListener.EventTime,
                isPlaying: Boolean
            ) {
                if (isPlaying) NebulaEqualizer.attach(exo.audioSessionId)
            }

            override fun onPlaybackStateChanged(
                eventTime: AnalyticsListener.EventTime,
                state: Int
            ) {
                if (state == Player.STATE_READY) NebulaEqualizer.attach(exo.audioSessionId)
            }
        })
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

        /**
         * 1MB per range request. googlevideo holds a connection open far longer than
         * a phone on mobile data can keep one, then drops it — a song that plays a
         * while and stops, or never starts. Short requests finish instead.
         */
        private const val CHUNK_SIZE_BYTES = 1024L * 1024L

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
        // DefaultHttpDataSource opens http/https ONLY — it holds no ContentResolver — so a
        // local song's content:// URI failed to load and the player sat at 0:00 with no
        // visible error. DefaultDataSource picks the right source per scheme (content, file,
        // asset) and still hands http/https to the same factory as before, so streaming and
        // downloads behave exactly as they did.
        val network: DataSource.Factory = DefaultDataSource.Factory(this, headerInjecting(
            // Chunking sits INSIDE DefaultDataSource, so it only ever sees what that
            // routes to http/https; a local content:// song bypasses it untouched.
            DefaultHttpDataSource.Factory().chunked(CHUNK_SIZE_BYTES)
        ))

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

    /**
     * Attaches the headers the stream resolver minted for the winning client to
     * each outgoing googlevideo request. googlevideo compares them against the
     * client baked into the URL (its `c=` param) and 403s a mismatch, which the
     * player then swallowed as a silent 0:00. MediaItem carries no per-item
     * headers in Media3, so the pair is rejoined here, where the request is
     * visible: DataSpec.key is the custom cache key, i.e. the videoId the
     * resolver cached the headers under. Same mechanism as Echo's DataSpec
     * rewrite. Unknown key (local file, already-cached bytes) = no headers,
     * exactly the old behaviour.
     */
    private fun headerInjecting(upstream: DataSource.Factory): DataSource.Factory =
        object : DataSource.Factory {
            override fun createDataSource(): DataSource =
                HeaderInjectingDataSource(upstream.createDataSource())
        }

    private class HeaderInjectingDataSource(private val delegate: DataSource) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) =
            delegate.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            val headers = dataSpec.key?.let { SearchRepository.headersFor(it) }.orEmpty()
            return delegate.open(
                if (headers.isEmpty()) dataSpec else dataSpec.withRequestHeaders(headers)
            )
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
        override fun close() = delegate.close()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // ponytail: empty on purpose — music keeps playing when the app is swiped away
    }

    override fun onDestroy() {
        // The effect lives on the player's audio session; releasing the player
        // without releasing it first would leak the Equalizer.
        NebulaEqualizer.release()
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
