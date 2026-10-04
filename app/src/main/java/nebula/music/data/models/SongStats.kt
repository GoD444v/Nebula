package nebula.music.data.models

/**
 * How often a song has been played, and when it was last heard.
 *
 * Null [lastPlayedAtMs] means never. Every field is a zero/null default rather than being
 * required, so a caller showing song info can pass what it knows and omit the rest rather
 * than inventing placeholders.
 */
data class SongStats(
    val playCount: Int = 0,
    val totalPlayMs: Long = 0L,
    val lastPlayedAtMs: Long? = null
)