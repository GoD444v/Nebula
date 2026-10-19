package nebula.music

import nebula.music.data.lyrics.WordTiming
import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricWord
import nebula.music.data.models.LyricsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Word timings invented for line-level (plain LRC) lyrics — the case where every
 * animated style used to render the identical whole-line look. What the styles
 * depend on is what is under test: units that are real words, spans that stay
 * inside their line, and real timings that are never touched.
 */
class WordTimingTest {

    private fun fill(vararg lines: LyricLine) = WordTiming.fill(lines.toList())

    @Test
    fun `wordless line gets its words back in order`() {
        val out = fill(
            LyricLine(0, "Hello there world"),
            LyricLine(3_000, "next")
        )
        assertEquals(listOf("Hello", "there", "world"), out[0].words.map { it.text })
    }

    @Test
    fun `words cover the line from its start to the next line`() {
        val out = fill(
            LyricLine(1_000, "a bb ccc"),
            LyricLine(4_000, "next")
        )
        val words = out[0].words
        assertEquals(1_000L, words.first().startMs)
        assertEquals(4_000L, words.last().endMs)
        words.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    }

    @Test
    fun `longer words get longer spans`() {
        val out = fill(
            LyricLine(0, "a bbbb"),
            LyricLine(5_000, "next")
        )
        val (short, long) = out[0].words
        assertTrue(long.endMs - long.startMs > short.endMs - short.startMs)
    }

    @Test
    fun `line without spaces is split per character`() {
        val out = fill(
            LyricLine(0, "君の名前"),
            LyricLine(4_000, "next")
        )
        assertEquals(listOf("君", "の", "名", "前"), out[0].words.map { it.text })
    }

    @Test
    fun `last line borrows the span of the one before it`() {
        val out = fill(
            LyricLine(0, "a b"),
            LyricLine(1_000, "c d"),
            LyricLine(4_000, "e")
        )
        assertEquals(4_000L, out[2].words.single().startMs)
        assertEquals(7_000L, out[2].words.single().endMs)
    }

    @Test
    fun `a long gap is clamped`() {
        val out = fill(
            LyricLine(0, "a"),
            LyricLine(30_000, "b")
        )
        assertEquals(8_000L, out[0].words.single().endMs)
    }

    @Test
    fun `blank lines stay wordless`() {
        val out = fill(LyricLine(0, ""), LyricLine(1_000, "  "), LyricLine(2_000, "ok"))
        assertTrue(out[0].words.isEmpty())
        assertTrue(out[1].words.isEmpty())
        assertEquals(1, out[2].words.size)
    }

    @Test
    fun `a line that already has word timings is passed through untouched`() {
        val real = LyricLine(0, "a b", listOf(LyricWord("a", 0, 500), LyricWord("b", 500, 900)))
        val out = WordTiming.fill(listOf(real, LyricLine(2_000, "next")))
        assertSame(real, out[0])
    }

    @Test
    fun `a response that needs nothing is returned as is`() {
        val response = LyricsResponse(listOf(LyricLine(0, "a b", listOf(LyricWord("a", 0, 500)))))
        assertSame(response, WordTiming.fill(response))
    }

    @Test
    fun `every unit lands inside its own line`() {
        val out = fill(
            LyricLine(0, "first line here"),
            LyricLine(2_000, "second one"),
            LyricLine(4_500, "third")
        )
        out.forEach { line ->
            assertTrue(line.words.isNotEmpty())
            assertEquals(line.timeMs, line.words.first().startMs)
            assertTrue(line.words.last().endMs > line.words.first().startMs)
        }
    }
}
