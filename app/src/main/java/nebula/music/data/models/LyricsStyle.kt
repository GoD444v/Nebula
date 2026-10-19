package nebula.music.data.models

import android.content.Context

/** Word-highlight renderer for synced lyrics. */
enum class LyricHighlight {
    LINE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    FADE,
    GLOW
}

/** Lyrics look: persisted in the shared app prefs, no new dependency. */
object LyricsStyleStore {
    private const val FILE = "nebula"
    private const val KEY_MODE = "lyrics_highlight"
    private const val KEY_COLOR = "lyrics_word_color"
    private const val KEY_TRANSLATION = "lyrics_translation"
    private const val KEY_ROMANIZE = "lyrics_romanize"

    fun getMode(context: Context): LyricHighlight = try {
        LyricHighlight.valueOf(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_MODE, null) ?: LyricHighlight.APPLE_V2.name
        )
    } catch (_: Exception) {
        LyricHighlight.APPLE_V2
    }

    fun setMode(context: Context, mode: LyricHighlight) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }

    /** ARGB int, or null = follow the theme primary. 0 is never a real pick. */
    fun getCustomColor(context: Context): Int? {
        val v = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_COLOR, 0)
        return if (v == 0) null else v
    }

    fun setCustomColor(context: Context, argb: Int?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_COLOR, argb ?: 0).apply()
    }

    /**
     * English sub-line above the original. On by default.
     *
     * Read where the translation is PRODUCED rather than where the lyrics are
     * drawn, so turning it off costs nothing per frame. The trade is that a change
     * lands on the next song: the translation runs once per song load, and a
     * running song keeps whatever it already had.
     */
    fun getTranslation(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_TRANSLATION, true)

    fun setTranslation(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRANSLATION, on).apply()
    }

    /**
     * Latin reading of a Korean line, drawn above the English sub-line. On by
     * default. Unlike the translation this can never be unavailable — it is
     * arithmetic, so there is no model, no network and no first-run download.
     *
     * Read where the reading is PRODUCED rather than where the lyrics are drawn, so
     * turning it off costs nothing per frame. Same trade as the switch above: a
     * change lands on the next song.
     */
    fun getRomanize(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ROMANIZE, true)

    fun setRomanize(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ROMANIZE, on).apply()
    }
}
