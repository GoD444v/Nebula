package nebula.music.data.lyrics

import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricWord
import nebula.music.data.models.LyricsResponse

/**
 * Approximate word timings for lyrics that only carry line timings — plain LRC,
 * which is what LRCLIB and YouTube Music hand back.
 *
 * Why it exists: every animated style (Karaoke/Apple/Apple V2/Fade/Glow) draws
 * from [LyricLine.words], and `LyricsScreen.ActiveLine` falls back to the plain
 * whole-line look for a line that has none. So on a song whose best provider was
 * line-level, choosing Apple or Glow changed nothing at all. Lines that already
 * have real timings (TTML/KRC/syllabus) are passed through untouched.
 *
 * The guess: a line's words split its own span — from its start to the next
 * line's start — in proportion to how long they are. Crude, but plain LRC
 * carries nothing better, and moving roughly beats not moving.
 */
object WordTiming {
    // A short span (fast delivery, or two lines sharing a timestamp) must still
    // read as a sweep, and a long instrumental gap must not stretch one line's
    // fill across twenty seconds. Measured on real LRCLIB lyrics (200 lines,
    // 4 songs): spans run 0.8s min, 3.5–6.4s median, 6.5–17.6s p90, 34s max —
    // so 8s covers p90 and only trims the sparse lines.
    private const val MIN_SPAN_MS = 900L
    private const val MAX_SPAN_MS = 8_000L

    private val WHITESPACE = Regex("\\s+")

    /** The same response when no line was missing words; a copy carrying them otherwise. */
    fun fill(response: LyricsResponse): LyricsResponse {
        val lines = fill(response.lines)
        return if (lines === response.lines) response else response.copy(lines = lines)
    }

    /** The same list when nothing needed words; a rebuilt one otherwise. */
    internal fun fill(lines: List<LyricLine>): List<LyricLine> {
        if (lines.none { it.words.isEmpty() && it.text.isNotBlank() }) return lines
        val out = ArrayList<LyricLine>(lines.size)
        var lastSpan = 0L
        lines.forEachIndexed { i, line ->
            // The line's own span; the last line borrows the previous one, having
            // nothing after it to bound it.
            val span = (lines.getOrNull(i + 1)?.timeMs?.minus(line.timeMs) ?: lastSpan)
                .coerceIn(MIN_SPAN_MS, MAX_SPAN_MS)
            lastSpan = span
            val units = if (line.words.isEmpty()) unitsOf(line.text) else emptyList()
            if (units.isEmpty()) out.add(line)
            else out.add(line.copy(words = spread(units, line.timeMs, span)))
        }
        return out
    }

    /**
     * Words as the renderers want them: split on whitespace, except in scripts
     * written without it (kana, Han), where one character is one unit. Otherwise a
     * Japanese line arrives as a single span and every style lights it whole.
     */
    private fun unitsOf(text: String): List<String> =
        text.trim().split(WHITESPACE).filter { it.isNotEmpty() }.flatMap { token ->
            if (token.any(::isUnspaced)) token.map(Char::toString) else listOf(token)
        }

    private fun isUnspaced(c: Char): Boolean {
        val code = c.code
        return code in 0x3040..0x30FF || // hiragana + katakana
            code in 0x3400..0x9FFF || // Han
            code in 0xFF66..0xFF9F // halfwidth katakana
    }

    /** Units laid end to end across the span, each as long as it is wide. */
    private fun spread(units: List<String>, startMs: Long, spanMs: Long): List<LyricWord> {
        val total = units.sumOf { it.length }
        val words = ArrayList<LyricWord>(units.size)
        var cursor = startMs
        units.forEachIndexed { i, unit ->
            // The last unit takes the remainder, so rounding cannot drift the end.
            val end = if (i == units.lastIndex) startMs + spanMs
            else cursor + spanMs * unit.length / total
            words.add(LyricWord(unit, cursor, end))
            cursor = end
        }
        return words
    }
}
