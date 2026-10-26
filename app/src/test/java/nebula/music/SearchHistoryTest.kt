package nebula.music

import nebula.music.data.mergeHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recent-searches list logic: newest first, dedup, hard cap. */
class SearchHistoryTest {

    @Test
    fun `new query lands at the front`() {
        assertEquals(listOf("queen", "abba"), mergeHistory("queen", listOf("abba")))
    }

    @Test
    fun `re-searching an old query moves it to the front instead of duplicating it`() {
        assertEquals(
            listOf("abba", "queen", "madonna"),
            mergeHistory("abba", listOf("queen", "abba", "madonna"))
        )
    }

    @Test
    fun `dedup ignores case so Abba and abba are one entry`() {
        assertEquals(
            listOf("Abba", "queen"),
            mergeHistory("Abba", listOf("queen", "abba"))
        )
    }

    @Test
    fun `list never grows past the cap of 20`() {
        // Existing list is newest-first, so the last entry is the oldest one.
        val existing = (1..20).map { "query $it" }
        val merged = mergeHistory("fresh", existing)
        assertEquals(20, merged.size)
        assertEquals("fresh", merged.first())
        assertEquals("query 19", merged.last())
        assertTrue("query 20" !in merged)
    }

    @Test
    fun `first-ever query starts the list`() {
        assertEquals(listOf("hello"), mergeHistory("hello", emptyList()))
    }
}
