package com.example.nebula.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class PlayerManager {

    private var controller: MediaController? = null
    private var pendingItem: MediaItem? = null

    // ponytail: one callback, no Flow/StateFlow until the UI needs more than playing/not
    var onIsPlayingChanged: ((Boolean) -> Unit)? = null
    // Fired when the current song plays to the end, so the ViewModel can
    // advance per the shuffle / loop / sequential mode.
    var onTrackEnded: (() -> Unit)? = null

    // Repeat/shuffle set before the controller connects are remembered here
    // and applied on connect — toggles must survive a cold start.
    private var pendingRepeat: Int? = null
    private var pendingShuffle: Boolean? = null
    // Seek position for a pending item — survives the async controller connect.
    private var pendingPosition: Long = 0L

    fun initializePlayer(context: Context) {
        if (controller != null) return
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val ctrl = future.get()
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onIsPlayingChanged?.invoke(isPlaying)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d("NebulaPlayer", "state=$state playing=${ctrl.isPlaying}")
                        if (state == Player.STATE_ENDED) onTrackEnded?.invoke()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("NebulaPlayer", "error: ${error.errorCodeName} ${error.message}")
                    }
                })
                controller = ctrl
                pendingRepeat?.let { ctrl.repeatMode = it }
                pendingShuffle?.let { ctrl.shuffleModeEnabled = it }
                pendingItem?.let { item ->
                    pendingItem = null
                    ctrl.setMediaItem(item)
                    if (pendingPosition > 0) ctrl.seekTo(pendingPosition)
                    ctrl.prepare()
                    ctrl.play()
                }
            } catch (_: Exception) {
                Log.e("NebulaPlayer", "controller connect failed")
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    // ponytail: metadata rides on the item so the session notification shows the song, not the app name
    fun playFromUrl(url: String, title: String? = null, artist: String? = null, artUrl: String? = null, positionMs: Long = 0L) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "Unknown title")
            .setArtist(artist)
            .setArtworkUri(artUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build()
        val ctrl = controller
        if (ctrl == null) {
            pendingItem = item
            pendingPosition = positionMs
            return
        }
        ctrl.setMediaItem(item)
        if (positionMs > 0) ctrl.seekTo(positionMs)
        ctrl.prepare()
        ctrl.play()
    }

    fun resumeAudio() {
        controller?.play()
    }

    fun pauseAudio() {
        controller?.pause()
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    // The song the service is holding, or null when the session has nothing loaded.
    // Used to tell "still playing" apart from "the service was killed since".
    fun currentMediaItem(): MediaItem? = controller?.currentMediaItem

    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun setRepeatMode(@Player.RepeatMode mode: Int) {
        pendingRepeat = mode
        controller?.repeatMode = mode
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        pendingShuffle = enabled
        controller?.shuffleModeEnabled = enabled
    }

    fun releasePlayer() {
        controller?.release()
        controller = null
        pendingItem = null
    }
}
