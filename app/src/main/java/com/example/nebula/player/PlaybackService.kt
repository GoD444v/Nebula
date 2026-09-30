package com.example.nebula.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.nebula.NebulaApplication

// CacheDataSource / DefaultHttpDataSource are @UnstableApi. The requirement is
// declared in Java, so kotlinc only warns — but the IDE inspection reports it as
// an error, so opt in explicitly rather than leaving 17 red marks.
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
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
        session = MediaSession.Builder(this, exo).build()
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
