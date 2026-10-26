package nebula.music.data.models

/** One synced line: when it starts (ms since song start) + what to show. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** Per-word timings when the provider supplies them (TTML/KRC); empty = line-level only. */
    val words: List<LyricWord> = emptyList(),
    /**
     * English rendering of [text], drawn as a smaller line above it.
     *
     * Lives on the line itself rather than in a parallel list, so it can never
     * drift out of step with the line it belongs to. Empty = nothing to show,
     * which is also the case for lyrics that are already English.
     */
    val translation: String = "",

    /**
     * Latin reading of [text], drawn above the translation.
     *
     * Only Korean songs get one — see Romanizer in data/lyrics for why Japanese
     * cannot be done without a kanji dictionary. Empty = nothing to show.
     */
    val romanized: String = ""
)

/** One word with its own start/end — powers karaoke highlighting. */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

/** Synced lines plus a plain-text fallback when no timestamps exist. */
data class LyricsResponse(
    val lines: List<LyricLine>,
    val plainText: String = ""
) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    /**
     * True when at least one line carries per-word timings (TTML/KRC/syllabus).
     *
     * Distinct from [isSynced], and the distinction is load-bearing: `isSynced`
     * only means the lines have timestamps, so plain-LRC results are synced but
     * have no words. Every animated highlight style needs words — without them
     * Apple, Apple V2, Karaoke, Fade and Glow all render the identical
     * whole-line look. Providers must be ranked on THIS, not on [isSynced].
     */
    val hasWordTimings: Boolean get() = lines.any { it.words.isNotEmpty() }
}
