package nebula.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Five-band peaking EQ as a Media3 AudioProcessor, applied to the decoded PCM
 * stream before it reaches the speaker.
 *
 * Why not android.media.audiofx.Equalizer: on the test device the OS effect
 * engine refused it outright ("Cannot initialize effect engine ... Error: -3"),
 * so the system EQ is simply not available on every phone. Filtering the samples
 * ourselves needs no effect engine - the DSP runs inside the player's own audio
 * pipeline and behaves identically everywhere.
 *
 * The math is the standard RBJ peaking-EQ biquad: per band, a second-order
 * section with the requested gain at the band centre, roughly an octave wide.
 * Cascading five of those is a graphic EQ.
 *
 * Coefficients depend on the sample rate (known only in [configure]) and on the
 * gains (changeable from the UI at any time). Both are handled without locking:
 * the sample rate comes from configure(), and gains arrive as an immutable
 * snapshot ([setGains]) that the audio thread picks up through a version counter.
 */
class EqDspProcessor(private val bandFrequencies: IntArray) : BaseAudioProcessor() {

    /** Q for octave-spaced peaking bands, so neighbours barely overlap. */
    private val q = 1.41f

    /** Second-order section state, one per channel per band. */
    private class Biquad {
        var b0 = 1f
        var b1 = 0f
        var b2 = 0f
        var a1 = 0f
        var a2 = 0f
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        fun clearState() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    private var sampleRate = 0
    private var channelCount = 0
    private var pcmEncoding = C.ENCODING_PCM_16BIT

    private var filters: Array<Array<Biquad>> = emptyArray()

    @Volatile
    private var gainsDb: FloatArray = FloatArray(bandFrequencies.size)

    @Volatile
    private var gainsVersion = 0

    private var appliedVersion = -1
    private var bypass = true
    /** Format we cannot filter - copy through rather than break playback. */
    private var passthrough = false

    /** New per-band gains in dB. Safe to call from any thread (the UI does). */
    fun setGains(db: FloatArray) {
        if (db.size != bandFrequencies.size) return
        gainsDb = db.copyOf()
        gainsVersion++
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        pcmEncoding = inputAudioFormat.encoding
        // NEVER throw here. An UnhandledAudioFormatException kills the whole audio
        // renderer, which surfaces as "couldn't load audio" and kills playback
        // outright - the EQ losing its effect is a far better failure than the
        // song refusing to play. Anything we cannot filter passes straight through.
        passthrough = sampleRate <= 0 ||
                (pcmEncoding != C.ENCODING_PCM_16BIT && pcmEncoding != C.ENCODING_PCM_FLOAT)
        
        filters = Array(channelCount) { Array(bandFrequencies.size) { Biquad() } }
        appliedVersion = -1
        return inputAudioFormat
    }

    private fun applyGains() {
        val db = gainsDb
        bypass = db.all { it == 0f }
        for (ch in filters.indices) {
            for (b in bandFrequencies.indices) {
                val section = filters[ch][b]
                setPeaking(section, db[b], bandFrequencies[b], sampleRate)
                section.clearState()
            }
        }
        appliedVersion = gainsVersion
    }

    /** RBJ peaking EQ normalized by a0; 0 dB collapses to an exact pass-through. */
    private fun setPeaking(section: Biquad, gainDb: Float, freqHz: Int, fs: Int) {
        // Keep the centre below Nyquist even when a high band outruns a low
        // sample rate, otherwise w0 approaches PI and the section blows up.
        val f = minOf(freqHz.toFloat(), fs / 2f * 0.98f)
        val amp = 10.0.pow(gainDb / 40.0).toFloat()
        val w0 = (2.0 * PI * f / fs).toFloat()
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2f * q)
        val a0 = 1f + alpha / amp
        section.b0 = (1f + alpha * amp) / a0
        section.b1 = (-2f * cosW0) / a0
        section.b2 = (1f - alpha * amp) / a0
        section.a1 = (-2f * cosW0) / a0
        section.a2 = (1f - alpha / amp) / a0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (appliedVersion != gainsVersion) applyGains()

        // AudioProcessor contract: consume the input and publish the result
        // through getOutput(). Filtering the input buffer in place would look
        // right but hand the sink nothing to read.
        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        out.order(ByteOrder.nativeOrder())
        // replaceOutputBuffer may hand back the SAME buffer as the input
        // (Media3's zero-copy path), and ByteBuffer.put(self) throws. When they
        // alias there is nothing to copy - the samples are already in place.
        if (out !== inputBuffer) out.put(inputBuffer)
        out.flip()

        val bytesPerSample = if (pcmEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frames = size / (bytesPerSample * channelCount)

        if (!bypass && !passthrough) {
            for (i in 0 until frames) {
                for (ch in 0 until channelCount) {
                    val offset = i * channelCount * bytesPerSample + ch * bytesPerSample
                    var sample = if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
                        out.getFloat(offset)
                    } else {
                        out.getShort(offset) / 32768f
                    }
                    for (b in bandFrequencies.indices) sample = step(filters[ch][b], sample)
                    if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
                        out.putFloat(offset, sample)
                    } else {
                        val scaled = (sample * 32767f).toInt()
                        out.putShort(offset, scaled.coerceIn(-32768, 32767).toShort())
                    }
                }
            }
        }
    }

    /** Direct form I: two samples of history per section, easiest to read. */
    private fun step(section: Biquad, x: Float): Float {
        val y = section.b0 * x + section.b1 * section.x1 + section.b2 * section.x2 -
                section.a1 * section.y1 - section.a2 * section.y2
        section.x2 = section.x1
        section.x1 = x
        section.y2 = section.y1
        section.y1 = y
        return y
    }

    /**
     * Always true once configured. Media3 evaluates this at configure time and
     * DROPS inactive processors from the chain entirely - reporting "flat right
     * now, so I'm inactive" would mean never being handed a single buffer, and
     * the first preset tap could not wake it back up. Flatness is therefore
     * handled inside [queueInput] instead.
     */
    override fun isActive(): Boolean = sampleRate > 0 && !passthrough

    override fun onFlush() {
        for (ch in filters.indices) for (b in bandFrequencies.indices) filters[ch][b].clearState()
    }
}