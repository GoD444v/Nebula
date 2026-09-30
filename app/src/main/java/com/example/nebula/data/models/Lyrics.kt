package com.example.nebula.data.models

/** One synced line: when it starts (ms since song start) + what to show. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** Per-word timings when the provider supplies them (TTML/KRC); empty = line-level only. */
    val words: List<LyricWord> = emptyList()
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
