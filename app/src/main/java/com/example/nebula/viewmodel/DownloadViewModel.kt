package com.example.nebula.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.example.nebula.data.download.NebulaDownloads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Media3's download states, renamed for the queue screen.
 *
 * An enum rather than a label string: the screen branches on it and picks its own accent
 * per state, so no "Failed".equals(label) string-matching to get wrong later.
 */
enum class DownloadStatus { QUEUED, PAUSED, DOWNLOADING, COMPLETED, FAILED, REMOVING, RESTARTING }

/** One row of the download queue, flattened for display. */
data class DownloadItem(
    val videoId: String,
    val title: String,
    val status: DownloadStatus,
    /** 0..100, or [UNKNOWN_PERCENT] when Media3 has no estimate yet. */
    val percent: Int,
    val downloadedBytes: Long,
    /** -1 (Media3's C.LENGTH_UNSET) when the stream had no Content-Length. */
    val contentLength: Long,
    val removable: Boolean
) {
    val failed: Boolean get() = status == DownloadStatus.FAILED

    companion object {
        const val UNKNOWN_PERCENT = -1
    }
}

/**
 * Read-only view of [NebulaDownloads] for the download queue screen.
 *
 * The object IS the repository — Media3's own DownloadIndex already persists the queue,
 * so there is no Room table here. NebulaDownloads rehydrates the map from that index on
 * init and pushes a new map on every listener callback, which is all the state we need.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    init {
        // Required, and idempotent: only NebulaDownloadService inits the downloads object
        // today, and that service only runs while a download is in flight. Pausing an
        // already-paused queue from a cold start would otherwise hit
        // "NebulaDownloads.init(context) was never called". Re-entry after the service
        // got there first returns early, so the shared DownloadManager is not rebuilt.
        NebulaDownloads.init(application)
    }

    // NebulaDownloads.allPaused is a plain getter over DownloadManager.downloadsPaused,
    // not a Flow, so nothing observes it for us. Re-read it on every queue emission
    // (pauseDownloads() flips each in-flight download back to STATE_QUEUED, which fires
    // onDownloadChanged) and also set it eagerly in pauseAll()/resumeAll() below —
    // pausing an EMPTY queue changes nothing inside Media3, so no callback fires and the
    // re-read alone would leave the button stuck on the wrong label.
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    val downloads: StateFlow<List<DownloadItem>> = NebulaDownloads.downloads
        .map { raw -> raw.values.map(::toItem) }
        .onEach { _isPaused.value = NebulaDownloads.allPaused }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Queue-wide by necessity, not by choice: Media3 1.6.1's DownloadManager exposes only
     * pauseDownloads()/resumeDownloads() for the whole queue — there is no per-download
     * pause. It preserves every partial file, so it is what a user means by "pause".
     */
    fun pauseAll() {
        NebulaDownloads.pauseAll()
        _isPaused.value = true
    }

    fun resumeAll() {
        NebulaDownloads.resumeAll()
        _isPaused.value = false
    }

    /** Destructive: deletes the song file and its cache data. */
    fun remove(videoId: String) = NebulaDownloads.remove(videoId)

    fun removeAll() {
        downloads.value.forEach { NebulaDownloads.remove(it.videoId) }
    }

    private fun toItem(d: Download): DownloadItem {
        // Media3 reports C.PERCENTAGE_UNSET (-1) until it knows the total, so a raw
        // toInt() would render "-1%" on the row. Negative is also the only value that can
        // appear here, so one check covers both the sentinel and a not-yet-known total.
        val rawPercent = d.percentDownloaded
        val percent = when {
            d.state == Download.STATE_COMPLETED -> 100
            rawPercent < 0f -> DownloadItem.UNKNOWN_PERCENT
            // Content-Length is an estimate while streaming, so the ratio can overshoot
            // 100 by a fraction before the download flips to COMPLETED.
            else -> rawPercent.toInt().coerceIn(0, 100)
        }
        return DownloadItem(
            videoId = d.request.id,
            title = NebulaDownloads.titleOf(d),
            status = when (d.state) {
                // Media3 1.6.1 keeps only STOP_REASON_NONE, and NebulaDownloads never
                // calls setStopReason, so STATE_STOPPED should not occur — if it ever
                // does, "held" is the closest honest thing to show the user.
                Download.STATE_QUEUED -> DownloadStatus.QUEUED
                Download.STATE_STOPPED -> DownloadStatus.PAUSED
                Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
                Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
                Download.STATE_FAILED -> DownloadStatus.FAILED
                Download.STATE_REMOVING -> DownloadStatus.REMOVING
                else -> DownloadStatus.RESTARTING
            },
            percent = percent,
            downloadedBytes = d.bytesDownloaded,
            contentLength = d.contentLength,
            // Already on its way out: another tap would just enqueue a second removal.
            removable = d.state != Download.STATE_REMOVING
        )
    }
}
