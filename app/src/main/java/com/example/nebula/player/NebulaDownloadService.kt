package com.example.nebula.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.nebula.R
import com.example.nebula.data.download.NebulaDownloads

/**
 * Foreground service that keeps downloads alive and shows their progress.
 *
 * Media3 builds the progress notification via [DownloadNotificationHelper]; this
 * subclass adds only what the base class lacks: a "remove all" action, and a
 * terminal notification when a download fails so a silent background stall is
 * visible to the user.
 *
 * The scheduler is [PlatformScheduler] (JobScheduler), not WorkManager — the
 * reference implementation makes the same choice, and Media3 already persists its
 * own index and requirements, so a second scheduler would be a parallel source
 * of truth.
 */
// DownloadService / DownloadManager / DownloadNotificationHelper are all
// @UnstableApi; declared in Java so kotlinc only warns, but the IDE flags it.
@UnstableApi
class NebulaDownloadService : DownloadService(
    NOTIFICATION_ID,
    FOREGROUND_UPDATE_INTERVAL_MS,
    CHANNEL_ID,
    /* channelNameResourceId= */ 0,
    /* channelDescriptionResourceId= */ 0
) {

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager {
        NebulaDownloads.init(applicationContext)
        return NebulaDownloads.manager
    }

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val label = if (downloads.size == 1) {
            Util.fromUtf8Bytes(downloads[0].request.data)
                .ifBlank { downloads[0].request.id }
        } else {
            resources.getQuantityString(R.plurals.nebula_download_count, downloads.size, downloads.size)
        }
        return Notification.Builder.recoverBuilder(
            this,
            notificationHelper.buildProgressNotification(
                this,
                NOTIFICATION_ICON,
                /* contentIntent= */ null,
                label,
                downloads,
                notMetRequirements
            )
        ).addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_delete),
                getString(R.string.nebula_remove_all_downloads),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, NebulaDownloadService::class.java).setAction(ACTION_REMOVE_ALL),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        ).build()
    }

    /** One-off failure notification, so a download that dies off-screen is visible. */
    private val terminalListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.state != Download.STATE_FAILED) return
            val label = Util.fromUtf8Bytes(download.request.data)
                .ifBlank { download.request.id }
            val n = notificationHelper.buildDownloadFailedNotification(
                this@NebulaDownloadService,
                NOTIFICATION_ICON,
                /* contentIntent= */ null,
                label
            )
            NotificationUtil.setNotification(
                this@NebulaDownloadService,
                FAILURE_ID_BASE + (download.request.id.hashCode() and 0xFF),
                n
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        NebulaDownloads.init(applicationContext)
        NebulaDownloads.manager.addListener(terminalListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REMOVE_ALL) {
            NebulaDownloads.manager.currentDownloads.forEach {
                NebulaDownloads.manager.removeDownload(it.request.id)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        NebulaDownloads.manager.removeListener(terminalListener)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "nebula_downloads"
        const val NOTIFICATION_ID = 1001
        const val JOB_ID = 1001
        const val ACTION_REMOVE_ALL = "com.example.nebula.action.REMOVE_ALL_DOWNLOADS"
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1000L
        private const val FAILURE_ID_BASE = 2000
        private const val NOTIFICATION_ICON = android.R.drawable.stat_sys_download
    }
}
