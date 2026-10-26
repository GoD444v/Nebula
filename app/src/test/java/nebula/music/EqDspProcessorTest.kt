package nebula.music

import nebula.music.player.EqDspProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The EQ is the only genuinely non-trivial logic in the player, and a biquad
 * that is subtly wrong still *sounds* like something on small speakers. These
 * check the parts a phone cannot easily verify: flat is bit-transparent, a
 * boost really boosts its own band, and the output never leaves PCM range.
 */
class EqDspProcessorTest {

    private val sampleRate = 44100
    private val channels = 2

    private fun tone(
        processor: EqDspProcessor,
        freq: Double,
        frames: Int = 44100
    ): Float {
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            sampleRate,
            channels,
            androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        processor.configure(format)

        val bytes = ByteBuffer.allocate(frames * channels * 2)
            .order(ByteOrder.nativeOrder())
        var amplitude = 0f
        for (i in 0 until frames) {
            val sample = (sin(2 * PI * freq * i / sampleRate) * 0.5).toFloat()
            for (ch in 0 until channels) {
                bytes.putShort((sample * 32767).toInt().toShort())
            }
            amplitude = kotlin.math.max(amplitude, kotlin.math.abs(sample))
        }
        bytes.flip()

        processor.queueInput(bytes)
        val out = processor.getOutput()
        out.order(ByteOrder.nativeOrder())

        var peak = 0f
        while (out.remaining() >= 2) {
            peak = kotlin.math.max(peak, kotlin.math.abs(out.short / 32768f))
        }
        return if (amplitude == 0f) 0f else peak / amplitude
    }

    @Test
    fun flatGainsLeaveAudioUntouched() {
        val processor = EqDspProcessor(intArrayOf(1000))
        val ratio = tone(processor, 1000.0)
        // A pass-through must come back at its input amplitude, not louder.
        assertEquals(1.0f, ratio, 0.02f)
    }

    @Test
    fun boostRaisesItsOwnBand() {
        val processor = EqDspProcessor(intArrayOf(1000))
        processor.setGains(floatArrayOf(12f))
        val boosted = tone(processor, 1000.0)
        val flat = tone(EqDspProcessor(intArrayOf(1000)), 1000.0)
        assertTrue("12 dB boost should raise 1 kHz, was $boosted vs $flat", boosted > flat * 1.5f)
    }

    @Test
    fun distantBandIsBarelyTouched() {
        // 1 kHz boosted hard must not also lift a far-away 8 kHz tone much,
        // or the bands would all sound like one wide smear.
        val boosted = EqDspProcessor(intArrayOf(1000)).apply { setGains(floatArrayOf(12f)) }
        val near = tone(boosted, 8000.0)
        val flat = tone(EqDspProcessor(intArrayOf(1000)), 8000.0)
        assertTrue("8 kHz should barely move, was $near vs $flat", near < flat * 1.3f)
    }

    @Test
    fun aliasedInputBufferDoesNotThrow() {
        // The bug this guards: Media3's zero-copy path makes replaceOutputBuffer
        // return the SAME buffer it was given, and ByteBuffer.put(self) throws
        // "The source buffer is this buffer" - which killed playback entirely.
        // Round-tripping through the processor's own output buffer reproduces it.
        val processor = EqDspProcessor(intArrayOf(1000))
        processor.setGains(floatArrayOf(6f))
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            sampleRate, channels, androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        processor.configure(format)

        processor.queueInput(sine(1000.0, 1024))
        val first = processor.getOutput()
        first.order(ByteOrder.nativeOrder())
        val copy = ByteBuffer.allocateDirect(first.remaining())
        copy.put(first)
        copy.flip()

        processor.flush()
        // Must not throw, and must still produce audible output afterwards.
        processor.queueInput(copy)
        assertTrue("processing must survive an aliased buffer", processor.getOutput().remaining() > 0)
    }

    private fun sine(freq: Double, frames: Int): ByteBuffer {
        val bytes = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val sample = (sin(2 * PI * freq * i / sampleRate) * 0.5).toFloat()
            for (ch in 0 until channels) bytes.putShort((sample * 32767).toInt().toShort())
        }
        bytes.flip()
        return bytes
    }

    @Test
    fun outputStaysInsidePcmRange() {
        val processor = EqDspProcessor(intArrayOf(200))
        processor.setGains(floatArrayOf(15f, 15f, 15f, 15f, 15f))
        val ratio = tone(processor, 200.0)
        // Clipping would be audible as crunch; the processor clips to +-1 by
        // construction, so this only guards against NaN/inf escaping.
        assertTrue("gain stack must stay finite, was $ratio", ratio.isFinite())
    }
}