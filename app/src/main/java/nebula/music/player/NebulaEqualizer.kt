package nebula.music.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Nebula's own five-band EQ plus spatial FX.
 *
 * The EQ is DSP, not android.media.audiofx.Equalizer: the test device's OS
 * effect engine refused the system effect outright ("Cannot initialize effect
 * engine ... Error: -3"), which left the studio with no bands at all while music
 * played. [EqDspProcessor] runs inside the player's own audio pipeline instead,
 * so it works on every phone.
 *
 * Spatial FX (surround / reverb) are still OS effects, because the platform has
 * no in-pipeline equivalent for them. They are created independently so a device
 * that refuses one cannot take the EQ down with it.
 *
 * Same idiom as BlockedSongs: Compose state so the screen reads it directly with
 * no ViewModel in between, SharedPreferences for restart. Band centres and the
 * level range are Nebula's own fixed curve - with a fixed DSP the "whatever the
 * device happens to expose" freedom of a system EQ no longer exists.
 */
object NebulaEqualizer {
    data class Band(val freqHz: Int, val levelMb: Int)

    /** Octave-ish spacing across the audible range, 60 Hz to 14 kHz. */
    val bandFrequencies = intArrayOf(60, 230, 910, 3600, 14000)

    /** ±15 dB, matching the range the old system effect reported. */
    const val LEVEL_MIN = -1500
    const val LEVEL_MAX = 1500

    private const val PREFS = "nebula_eq"
    private const val KEY_ON = "eq_on"
    private const val KEY_PRESET_NAME = "eq_preset_name"
    private const val KEY_LEVELS = "eq_levels"
    private const val KEY_SURROUND = "eq_surround"
    private const val KEY_REVERB = "eq_reverb"

    /** Off until the player hands us its audio session (spatial FX need one). */
    var attached by mutableStateOf(false)
        private set

    var enabled by mutableStateOf(false)
        private set

    var bands by mutableStateOf(bandFrequencies.map { Band(it, 0) })
        private set

    var levelMin by mutableStateOf(LEVEL_MIN)
        private set

    var levelMax by mutableStateOf(LEVEL_MAX)
        private set

    private val REF_HZ = intArrayOf(60, 150, 400, 1000, 2500, 6000, 16000)

    private val STUDIO_CURVES: List<Pair<String, List<Float>>> = listOf(
        "Bass Boost" to listOf(4.5f, 3.0f, 0.5f, 0.0f, 1.0f, 2.5f, 3.5f),
        "Retro Wave" to listOf(3.0f, 1.5f, -1.0f, 2.0f, 3.5f, 4.0f, 4.5f),
        "Vocal Boost" to listOf(-1.5f, 0.0f, 2.5f, 4.0f, 3.5f, 2.0f, 0.5f),
        "Party Mode" to listOf(5.0f, 3.5f, 1.0f, -1.0f, 2.0f, 4.0f, 5.0f),
        "Acoustic" to listOf(2.0f, 2.5f, 1.5f, 0.5f, 1.0f, 2.0f, 1.5f),
        "Flat" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    )

    /** The studio preset shelf the chips show - a fixed order, no HAL roulette. */
    val presets = STUDIO_CURVES.map { it.first }
    var activePreset by mutableStateOf("Flat")
        private set

    // Spatial card: 0..1 strength for the virtualizer, 0..1 room dial for the
    // reverb (0 = off, then small room up to large hall).
    var surroundStrength by mutableStateOf(0f)
        private set
    var reverbLevel by mutableStateOf(0f)
        private set

    private var virtualizer: android.media.audiofx.Virtualizer? = null
    private var reverb: android.media.audiofx.PresetReverb? = null
    private var sessionId = 0
    private var savedLevels: List<Int>? = null
    private var prefs: SharedPreferences? = null

    /** Set by PlaybackService when it builds the player's audio pipeline. */
    private var processor: EqDspProcessor? = null

    /**
     * Install the DSP and immediately hand it the current settings. Called from
     * PlaybackService, which runs after init() - without the immediate push the
     * fresh processor would start flat and stay silent.
     */
    fun attachProcessor(p: EqDspProcessor) {
        processor = p
        pushGains()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs!!.getBoolean(KEY_ON, false)
        activePreset = prefs!!.getString(KEY_PRESET_NAME, null) ?: "Flat"
        val stored = prefs!!.getString(KEY_LEVELS, null)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }
        if (stored != null && stored.size == bandFrequencies.size) {
            savedLevels = stored
            bands = bandFrequencies.mapIndexed { i, f -> Band(f, stored[i]) }
        }
        surroundStrength = prefs!!.getInt(KEY_SURROUND, 0) / 100f
        reverbLevel = prefs!!.getInt(KEY_REVERB, 0) / 100f
        pushGains()
    }

    /**
     * Hook the spatial FX to a playback session. 0 is
     * C.AUDIO_SESSION_ID_UNSET - the audio sink is not up yet, nothing to attach
     * to. A different id means the session moved (output routing change).
     */
    fun attach(id: Int) {
        if (id == 0) return
        if (id == sessionId) return
        releaseEffects()
        sessionId = id
        attached = true
        runCatching {
            val v = android.media.audiofx.Virtualizer(0, id)
            if (surroundStrength > 0f) v.setStrength((surroundStrength * 1000).toInt().toShort())
            v.enabled = surroundStrength > 0f
            virtualizer = v
        }
        runCatching {
            val r = android.media.audiofx.PresetReverb(0, id)
            r.preset = REVERB_ORDER[reverbIndexFor(reverbLevel)]
            r.enabled = reverbLevel > 0f
            reverb = r
        }
    }

    /** Service is dying: the session (and its effects) are going away. */
    fun release() {
        releaseEffects()
        sessionId = 0
        attached = false
    }

    // Named toggle, not setEnabled: the `enabled` property's delegate already
    // generates a JVM setEnabled(Boolean), and a same-signature function is a
    // platform clash. The Switch reads the property, so blind toggling is exact.
    fun toggleEnabled() {
        enabled = !enabled
        pushGains()
        prefs?.edit()?.putBoolean(KEY_ON, enabled)?.apply()
    }

    fun selectPreset(name: String) {
        activePreset = name
        val gains = STUDIO_CURVES.firstOrNull { it.first == name }?.second
        if (gains != null) {
            val levels = gainsAtBands(gains)
            bands = bandFrequencies.mapIndexed { i, f -> Band(f, levels[i]) }
        }
        savedLevels = bands.map { it.levelMb }
        pushGains()
        prefs?.edit()
            ?.putString(KEY_PRESET_NAME, name)
            ?.putString(KEY_LEVELS, savedLevels?.joinToString(","))
            ?.apply()
    }

    fun setBand(index: Int, levelMb: Int) {
        val level = levelMb.coerceIn(levelMin, levelMax)
        activePreset = "Custom"
        bands = bands.mapIndexed { i, b -> if (i == index) b.copy(levelMb = level) else b }
        savedLevels = bands.map { it.levelMb }
        pushGains()
        prefs?.edit()
            ?.putString(KEY_PRESET_NAME, "Custom")
            ?.putString(KEY_LEVELS, savedLevels!!.joinToString(","))
            ?.apply()
    }

    /** Vox refresh button: zero every fader, no preset juggling. */
    fun resetEq() {
        bands = bands.map { it.copy(levelMb = 0) }
        savedLevels = bands.map { it.levelMb }
        pushGains()
        prefs?.edit()?.putString(KEY_LEVELS, savedLevels!!.joinToString(","))?.apply()
    }

    /** Hand the current band levels to the DSP, honouring the on/off switch. */
    private fun pushGains() {
        val db = FloatArray(bands.size) { i ->
            if (enabled) bands[i].levelMb / 100f else 0f
        }
        processor?.setGains(db)
    }

    /**
     * Snap the seven reference gains onto our five band centres by linear
     * interpolation, holding the end values outside the covered range.
     */
    private fun gainsAtBands(reference: List<Float>): IntArray {
        val out = IntArray(bandFrequencies.size)
        for (i in bandFrequencies.indices) {
            val hz = bandFrequencies[i].toFloat()
            var g = reference[0]
            for (k in 0 until REF_HZ.size - 1) {
                val lo = REF_HZ[k].toFloat()
                val hi = REF_HZ[k + 1].toFloat()
                if (hz in lo..hi) {
                    val t = if (hi == lo) 0f else (hz - lo) / (hi - lo)
                    g = reference[k] + (reference[k + 1] - reference[k]) * t
                    break
                }
                if (hz > hi) g = reference.last()
            }
            out[i] = (g * 100f).roundToInt().coerceIn(LEVEL_MIN, LEVEL_MAX)
        }
        return out
    }

    fun applySurroundStrength(v: Float) {
        surroundStrength = v.coerceIn(0f, 1f)
        virtualizer?.let { vp ->
            runCatching {
                if (surroundStrength > 0f) {
                    vp.setStrength((surroundStrength * 1000).toInt().toShort())
                    vp.enabled = true
                } else {
                    vp.enabled = false
                }
            }
        }
        prefs?.edit()?.putInt(KEY_SURROUND, (surroundStrength * 100).toInt())?.apply()
    }

    fun setReverb(v: Float) {
        reverbLevel = v.coerceIn(0f, 1f)
        reverb?.let { r -> runCatching { r.preset = REVERB_ORDER[reverbIndexFor(reverbLevel)] } }
        prefs?.edit()?.putInt(KEY_REVERB, (reverbLevel * 100).toInt())?.apply()
    }

    private fun reverbIndexFor(level: Float): Int =
        if (level <= 0f) 0 else ((level * 5f).roundToInt().coerceIn(1, 5))

    private val REVERB_ORDER = shortArrayOf(
        android.media.audiofx.PresetReverb.PRESET_NONE,
        android.media.audiofx.PresetReverb.PRESET_SMALLROOM,
        android.media.audiofx.PresetReverb.PRESET_MEDIUMROOM,
        android.media.audiofx.PresetReverb.PRESET_LARGEROOM,
        android.media.audiofx.PresetReverb.PRESET_MEDIUMHALL,
        android.media.audiofx.PresetReverb.PRESET_LARGEHALL
    )

    private fun releaseEffects() {
        runCatching { virtualizer?.release() }
        runCatching { reverb?.release() }
        virtualizer = null
        reverb = null
    }
}