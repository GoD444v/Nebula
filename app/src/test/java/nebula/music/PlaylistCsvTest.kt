package nebula.music

import nebula.music.data.PlaylistCsv
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCsvTest {

    @Test
    fun quotedCommasDoNotSplit() {
        assertEquals(
            listOf("Hey Minnale", "Haricharan, feat. X", "http://u/1"),
            PlaylistCsv.parseLine("\"Hey Minnale\",\"Haricharan, feat. X\",http://u/1")
        )
    }

    @Test
    fun headerMapsByName() {
        val rows = PlaylistCsv.rows("Title,Artist,Album,Duration,URL\n\"Monica\",\"Subla,shini\",\"\",\"\",\"https://music.youtube.com/watch?v=abc123\"")
        assertEquals(1, rows.size)
        assertEquals("Monica", rows[0].title)
        assertEquals("Subla,shini", rows[0].artist)
        assertEquals("abc123", PlaylistCsv.videoIdOf(rows[0].url))
    }

    @Test
    fun positionalFallbackWithoutHeader() {
        val rows = PlaylistCsv.rows("Monica,Subulashini\nPavazha Malli,Sai Abhyankkar")
        assertEquals(2, rows.size)
        assertEquals("Monica", rows[0].title)
        assertEquals("", rows[0].url)
    }

    @Test
    fun blankLinesSkipped() {
        assertEquals(1, PlaylistCsv.rows("\n\nMonica,X\n\n").size)
    }
}
