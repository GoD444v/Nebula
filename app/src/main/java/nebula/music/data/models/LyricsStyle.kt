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
}
