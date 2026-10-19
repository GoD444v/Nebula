package nebula.music

import android.net.Uri
import androidx.media3.datasource.DataSource
import nebula.music.player.ChunkWindow
import nebula.music.player.ChunkedDataSource
import nebula.music.player.chunked
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ChunkWindow decides where every 1MB chunk of a googlevideo stream starts and how
 * long it is. Get it wrong and nothing throws — an offset one byte short skips a
 * byte (a click mid-song) and one too large truncates the track, both of which
 * look like a flaky network rather than a bug. So the arithmetic is pinned here.
 *
 * These are pure functions precisely so this test runs: ChunkedDataSource itself
 * needs a real DataSpec, and DataSpec needs android.net.Uri, which no JVM unit
 * test can construct.
 */
class ChunkWindowTest {

    private val chunkSize = 1024L

    @Test
    fun `a chunk never asks for more than the chunk size`() {
        assertEquals(chunkSize, ChunkWindow.length(chunkSize, bytesRemaining = 5000))
    }

    @Test
    fun `the last chunk is trimmed to what is left`() {
        assertEquals(400L, ChunkWindow.length(chunkSize, bytesRemaining = 400))
    }

    @Test
    fun `an unknown length still asks for a full chunk`() {
        // Media3 asks for LENGTH_UNSET when it does not know the content length.
        // Treating that as 0 would end every unknown-length stream immediately.
        assertEquals(chunkSize, ChunkWindow.length(chunkSize, bytesRemaining = ChunkWindow.LENGTH_UNSET))
    }

    @Test
    fun `an exhausted range reports zero so no further request is opened`() {
        // 0 is the signal to stop reading. Without it the caller would open one
        // more request past the end of the song on every read.
        assertEquals(0L, ChunkWindow.length(chunkSize, bytesRemaining = 0))
    }

    @Test
    fun `each chunk starts where the previous one ended`() {
        assertEquals(0L, ChunkWindow.position(specPosition = 0, bytesReadTotal = 0))
        assertEquals(1024L, ChunkWindow.position(specPosition = 0, bytesReadTotal = 1024))
        assertEquals(2048L, ChunkWindow.position(specPosition = 0, bytesReadTotal = 2048))
    }

    @Test
    fun `seeking into a track offsets every chunk from the seek point`() {
        // A seek is just a non-zero start. Measuring from 0 here would replay the
        // wrong audio after a seek, which is why this is not specPosition + chunk.
        assertEquals(1500L, ChunkWindow.position(specPosition = 1500, bytesReadTotal = 0))
        assertEquals(2524L, ChunkWindow.position(specPosition = 1500, bytesReadTotal = 1024))
    }

    /**
     * Regression: the factory helper once wrote
     *
     *     override fun createDataSource() = ChunkedDataSource(createDataSource(), chunkSize)
     *
     * and the unqualified call resolved to the anonymous object's OWN method, not to
     * the extension receiver — so creating a data source recursed forever and the app
     * died with a StackOverflowError the first time any song played. Nothing covered
     * the helper, only the arithmetic above, so it passed CI-shaped review and crashed
     * on device.
     *
     * Building the chunked source needs no DataSpec and no android.net.Uri, so this
     * IS runnable as a unit test — which is exactly why the gap was not forced shut.
     */
    @Test
    fun `the chunked factory wraps the upstream instead of calling itself`() {
        var upstreamCalls = 0
        val upstream = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                upstreamCalls++
                return UnusedDataSource
            }
        }
        val created = upstream.chunked(chunkSize).createDataSource()
        assertEquals(ChunkedDataSource::class.java, created::class.java)
        // Exactly one: the wrap. The regression recursed here forever, so a count of
        // anything but 1 is the failure — the stack overflow happens before this
        // assertion is ever reached, which is why the test fails as an Error.
        assertEquals(1, upstreamCalls)
    }

    /** Never opened; the factory test only cares how many times it is handed out. */
    private object UnusedDataSource : DataSource {
        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) = Unit
        override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
}