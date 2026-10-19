package nebula.music

import nebula.music.ui.screens.artUrls
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Album-art URL order for the full player.
 *
 * Worth a test because the failure is silent: the wrong order does not crash, it just
 * shows a soft picture. The soft art was the queue's ~544px InnerTube thumbnail winning
 * over `maxresdefault.jpg`, so the high-res URL has to lead — and the fallback has to
 * survive the tracks that have no maxres thumbnail.
 */
class ArtUrlsTest {

    @Test
    fun `a youtube song leads with the hi-res thumbnail`() {
        val urls = artUrls("abc123", "https://lh3.googleusercontent.com/w544")
        assertEquals("https://i.ytimg.com/vi/abc123/maxresdefault.jpg", urls.first())
    }

    @Test
    fun `a track with no maxres still gets the api thumbnail instead of a letter tile`() {
        assertEquals(
            listOf("https://i.ytimg.com/vi/abc123/maxresdefault.jpg", "https://x/w544"),
            artUrls("abc123", "https://x/w544")
        )
    }

    @Test
    fun `a local song never asks ytimg, its media store uri is all the art there is`() {
        assertEquals(
            listOf("content://media/external/audio/albumart/9"),
            artUrls("local:content://media/audio/media/9", "content://media/external/audio/albumart/9")
        )
    }

    @Test
    fun `a local song with no stored art has nothing to try`() {
        assertEquals(emptyList<String>(), artUrls("local:content://media/audio/media/9", null))
        assertEquals(emptyList<String>(), artUrls("local:content://media/audio/media/9", ""))
    }

    @Test
    fun `nothing playing asks for no image at all`() {
        assertEquals(emptyList<String>(), artUrls("", "https://x/w544"))
    }
}