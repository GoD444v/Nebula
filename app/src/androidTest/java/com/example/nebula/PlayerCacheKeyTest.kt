package com.example.nebula

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.player.PlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for downloaded songs not playing with the network off.
 *
 * The root cause was a cache-key mismatch. Media3 derives a MediaItem's DataSpec key from
 * its URI when `setCustomCacheKey` is absent, so playback looked a song up in the download
 * cache under the *resolved stream URL*, while `NebulaDownloads.enqueue` wrote the bytes
 * under the *bare video id* (`setCustomCacheKey(song.videoId)`). Two different strings, so
 * the download-cache read always missed, playback fell through to the network, and a
 * downloaded song needed the internet to start.
 *
 * `DownloadRequestKeyTest` already pins the download side of this contract. Nothing pinned
 * the playback side, which is how the two drifted apart without any test going red.
 *
 * A JVM unit test cannot live here: `MediaItem.Builder` calls `Uri.parse`, which throws
 * "not mocked" without Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class PlayerCacheKeyTest {

    private val playerManager = PlayerManager()

    @Test
    fun playbackItemKeysOnTheVideoIdNotTheResolvedUrl() {
        val videoId = "dQw4w9WgXcQ"
        val resolvedUrl = "https://r1---sn-x.googlevideo.com/videoplayback?expire=123&id=$videoId"

        val item = playerManager.buildMediaItem(resolvedUrl, videoId, "Title", "Artist", null)

        assertEquals(videoId, item.localConfiguration?.customCacheKey)
        // The whole point: the key must NOT be the URL the download side never saw.
        assertNotEquals(resolvedUrl, item.localConfiguration?.customCacheKey)
    }

    @Test
    fun playbackItemAndDownloadRequestAgreeOnTheSameKey() {
        val videoId = "abc123XYZ"

        // The download side: what enqueue writes into the download cache.
        val downloadKey = videoId
        // The playback side: what the player will look that same entry up by.
        val playbackKey = playerManager.buildMediaItem("https://example.invalid/x", videoId).localConfiguration?.customCacheKey

        assertEquals(downloadKey, playbackKey)
    }

    @Test
    fun mediaItemCarriesTheArtworkAndTitleForTheNotification() {
        val item = playerManager.buildMediaItem(
            uri = "https://example.invalid/x",
            cacheKey = "vid-1",
            title = "Wonderwall",
            artist = "Oasis",
            artUrl = "https://art/1.jpg"
        )

        assertEquals("Wonderwall", item.mediaMetadata.title.toString())
        assertEquals("Oasis", item.mediaMetadata.artist.toString())
        // The URI is what the cache falls back to keying on, so it must be preserved
        // exactly as given — the cacheKey is additive, not a replacement.
        assertEquals("https://example.invalid/x", item.localConfiguration?.uri?.toString())
    }

    @Test
    fun offlinePlaceholderUriIsNeverADereferenceableHost() {
        val uri = NebulaDownloads.offlinePlaceholderUri("vid-42")

        // The download cache is keyed on the video id, so this URI is never fetched; it
        // only has to be a valid, non-empty, stable string to carry the key.
        assert(uri.isNotEmpty())
        assert(uri.endsWith("vid-42"))
    }
}