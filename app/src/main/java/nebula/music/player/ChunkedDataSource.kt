package nebula.music.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Reads an http stream as a sequence of bounded range requests instead of one
 * open-ended GET.
 *
 * googlevideo drops long-lived connections, which produced two of Nebula's
 * symptoms: a song that played a while and then died, and one that sat at 0:00
 * with no error. Re-opening every [chunkSize] bytes makes each request short
 * enough to finish, and a dropped chunk is re-requested rather than ending the
 * track.
 *
 * Deliberately a DataSource and not a MediaSource: it sits UNDER DefaultDataSource,
 * so it only ever sees what that routes to http/https. A local `content://` song
 * passes straight through untouched.
 */
internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long
) : DataSource {

    private var spec: DataSpec? = null
    private var bytesReadTotal = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var open = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        bytesReadTotal = 0
        bytesRemaining = dataSpec.length
        open = true
        openChunk()
        return bytesRemaining
    }

    private fun openChunk() {
        val current = spec ?: throw IOException("open() was never called")
        upstream.open(
            current.buildUpon()
                .setPosition(ChunkWindow.position(current.position, bytesReadTotal))
                .setLength(ChunkWindow.length(chunkSize, bytesRemaining))
                .build()
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!open) throw IllegalStateException("read() before open()")
        if (ChunkWindow.length(chunkSize, bytesRemaining) <= 0L) return C.RESULT_END_OF_INPUT

        val read = try {
            upstream.read(buffer, offset, length)
        } catch (_: IOException) {
            // A connection reset mid-chunk is transient; losing the rest of the song
            // over it is exactly what this class exists to prevent. Re-open the same
            // window once and let a second failure surface to the player as an error.
            upstream.close()
            openChunk()
            upstream.read(buffer, offset, length)
        }

        if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
        bytesReadTotal += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        open = false
        upstream.close()
    }
}

/**
 * Where a chunk starts and how much it may cover.
 *
 * Split out of [ChunkedDataSource] because the arithmetic is the part that is
 * wrong in ways nothing else catches: an offset that is one byte short skips a
 * byte (an audible click mid-song) and one that is too large truncates the track.
 * It is a pure object so a plain JVM unit test can pin it — building a real
 * `DataSpec` needs `android.net.Uri`, which no unit test can construct.
 */
internal object ChunkWindow {

    /** Media3's own "no length known" marker, restated so this file has no Media3 import. */
    const val LENGTH_UNSET = -1L

    /**
     * Bytes the next chunk may cover. Never more than [chunkSize], and never more
     * than the caller still wants — a 0 means the requested range is satisfied and
     * reading must stop rather than open one more request.
     */
    fun length(chunkSize: Long, bytesRemaining: Long): Long =
        if (bytesRemaining == LENGTH_UNSET) chunkSize else minOf(chunkSize, bytesRemaining)

    /** Absolute offset of the next chunk: the caller's start plus everything served. */
    fun position(specPosition: Long, bytesReadTotal: Long): Long = specPosition + bytesReadTotal
}

/** Wraps [this] so every request it serves is a bounded range read. */
internal fun DataSource.Factory.chunked(chunkSize: Long): DataSource.Factory {
    // Captured in a local FIRST, and that is the entire point of the line. Writing
    // `ChunkedDataSource(createDataSource(), chunkSize)` inside the object looks
    // right and is not: an unqualified call there resolves to the object's OWN
    // createDataSource, not to this extension receiver, so it recursed forever and
    // every song play died with a StackOverflowError.
    val upstream = this
    return object : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }
}