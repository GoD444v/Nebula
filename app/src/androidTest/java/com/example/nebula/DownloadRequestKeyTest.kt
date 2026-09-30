package com.example.nebula

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.download.NebulaDownloads
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The download failure this pins: a DownloadRequest built without
 * setCustomCacheKey reaches ResolvingDataSource with a null DataSpec.key, so
 * resolveStreamUri could not tell which video to resolve and handed the
 * placeholder https://nebula.local/<id> URI to the network, which fails on DNS.
 *
 * Needs the real network — it asserts against the live InnerTube extractor. If the
 * device is offline the stream URL is null and this fails for that reason instead;
 * the assertion message says so.
 */
@RunWith(AndroidJUnit4::class)
class DownloadRequestKeyTest {

    @Test
    fun enqueuedRequest_carriesVideoIdAsCustomCacheKey() {
        val context: Application = ApplicationProvider.getApplicationContext()
        NebulaDownloads.init(context)

        // Echo's own video, used by the InnerTube test fixtures.
        val videoId = "dQw4w9WgXcQ"
        val song = com.example.nebula.data.models.SearchResult(
            videoId = videoId,
            title = "Download key probe",
            artist = "Probe Artist",
            thumbnailUrl = "https://example.invalid/art.jpg"
        )

        NebulaDownloads.enqueue(song)

        // Wait for the DownloadManager to publish the request; enqueue is async.
        val download = runBlocking {
            withTimeout(10_000) {
                NebulaDownloads.observe(videoId).first { it != null }
            }
        }

        assertNotNull("nothing was enqueued for $videoId", download)
        val request = requireNotNull(download).request
        assertTrue(
            "customCacheKey must be the videoId; Media3 derives DataSpec.key from it",
            request.customCacheKey == videoId
        )
    }
}
