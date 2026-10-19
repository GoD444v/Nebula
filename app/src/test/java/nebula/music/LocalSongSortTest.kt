package nebula.music

import nebula.music.data.db.entities.LocalSong
import nebula.music.ui.screens.LocalSongSortType
import nebula.music.ui.screens.sortLocalSongs
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSongSortTest {

    private fun song(
        title: String,
        artist: String,
        album: String,
        duration: Long,
        dateModified: Long = 0
    ) = LocalSong(
        title = title, artist = artist, album = album, duration = duration,
        filePath = "content://x/$title", thumbnailUri = "", dateModified = dateModified
    )

    private val songs = listOf(
        song("zeta", "beta", "one", 100, dateModified = 10),
        song("Alpha", "alpha", "one", 300, dateModified = 30),
        song("mid", "gamma", "two", 200, dateModified = 20)
    )

    @Test
    fun single_song_is_returned_untouched() {
        val one = songs.take(1)
        assertEquals(one, sortLocalSongs(one, LocalSongSortType.DATE_MODIFIED, descending = false))
    }

    // compareTo would put every capital before every lowercase, so "Alpha" would sort
    // after "zeta". The Collator at PRIMARY strength is what stops that reading as broken.
    @Test
    fun name_sort_is_case_insensitive() {
        val sorted = sortLocalSongs(songs, LocalSongSortType.NAME, descending = false)
        assertEquals(listOf("Alpha", "mid", "zeta"), sorted.map { it.title })
    }

    @Test
    fun artist_sort_orders_by_artist_then_title() {
        val sorted = sortLocalSongs(songs, LocalSongSortType.ARTIST, descending = false)
        assertEquals(listOf("alpha", "beta", "gamma"), sorted.map { it.artist })
    }

    @Test
    fun album_sort_orders_by_album_then_title() {
        val sorted = sortLocalSongs(songs, LocalSongSortType.ALBUM, descending = false)
        assertEquals(listOf("one", "one", "two"), sorted.map { it.album })
        // Same album, so the tiebreak on title decides: "Alpha" before "zeta".
        assertEquals(listOf("Alpha", "zeta"), sorted.take(2).map { it.title })
    }

    @Test
    fun date_updated_sorts_newest_first() {
        val sorted = sortLocalSongs(songs, LocalSongSortType.DATE_MODIFIED, descending = false)
        assertEquals(listOf("Alpha", "mid", "zeta"), sorted.map { it.title })
    }

    // Two files can share a timestamp to the second, so the sort has to fall back to
    // title or their order swaps between reads.
    @Test
    fun date_updated_breaks_ties_on_title() {
        val tied = listOf(
            song("zeta", "a", "x", 100, dateModified = 50),
            song("Alpha", "b", "x", 100, dateModified = 50)
        )
        val sorted = sortLocalSongs(tied, LocalSongSortType.DATE_MODIFIED, descending = false)
        assertEquals(listOf("Alpha", "zeta"), sorted.map { it.title })
    }

    @Test
    fun descending_reverses_the_ascending_order() {
        val up = sortLocalSongs(songs, LocalSongSortType.NAME, descending = false)
        val down = sortLocalSongs(songs, LocalSongSortType.NAME, descending = true)
        assertEquals(up.reversed(), down)
    }
}