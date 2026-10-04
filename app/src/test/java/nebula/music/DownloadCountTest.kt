package nebula.music

import nebula.music.viewmodel.DownloadItem
import nebula.music.viewmodel.DownloadStatus
import nebula.music.viewmodel.activeDownloadCount
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The badge on the Playlists header button counts downloads that are still in
 * flight. Plain JUnit: DownloadItem and DownloadStatus are ordinary data classes,
 * so nothing here needs an Android device.
 */
class DownloadCountTest {

    private fun item(status: DownloadStatus) = DownloadItem(
        videoId = "vid-${status.name}",
        title = "Title",
        status = status,
        percent = 0,
        downloadedBytes = 0L,
        contentLength = 0L,
        removable = true
    )

    @Test
    fun emptyQueue_isZero() {
        assertEquals(0, activeDownloadCount(emptyList()))
    }

    @Test
    fun finishedDownloads_areNotCounted() {
        val items = listOf(
            item(DownloadStatus.COMPLETED),
            item(DownloadStatus.COMPLETED),
            item(DownloadStatus.REMOVING)
        )

        assertEquals(0, activeDownloadCount(items))
    }

    @Test
    fun inFlightAndFailed_areCounted() {
        val items = listOf(
            item(DownloadStatus.QUEUED),
            item(DownloadStatus.DOWNLOADING),
            item(DownloadStatus.PAUSED),
            item(DownloadStatus.RESTARTING),
            item(DownloadStatus.FAILED),
            item(DownloadStatus.COMPLETED)
        )

        assertEquals(5, activeDownloadCount(items))
    }
}
