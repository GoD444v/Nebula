package nebula.music

import nebula.music.data.db.entities.LocalSong
import nebula.music.ui.screens.localAlbumArtists
import nebula.music.ui.screens.localAlbums
import nebula.music.ui.screens.localArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalGroupingTest {

    private fun song(
        title: String, artist: String, album: String, albumArtist: String = ""
    ) = LocalSong(
        title = title, artist = artist, album = album, duration = 0L,
        filePath = "content://x/$title", thumbnailUri = "",
        albumArtist = albumArtist
    )

    private val songs = listOf(
        song("zeta", "beta", "one"),
        song("Alpha", "alpha", "one"),
        song("mid", "gamma", "two")
    )

    @Test
    fun albums_group_by_album() {
        val groups = localAlbums(songs)
        assertEquals(listOf("one", "two"), groups.map { it.name })
        assertEquals(listOf(2, 1), groups.map { it.count })
    }

    @Test
    fun artists_group_by_artist() {
        val groups = localArtists(songs)
        assertEquals(listOf("alpha", "beta", "gamma"), groups.map { it.name })
        assertEquals(listOf(1, 1, 1), groups.map { it.count })
    }

    // Most files carry no ALBUM_ARTIST tag at all — the artist that appears most
    // on the album owns the WHOLE album's shelf, which is what makes this view
    // differ from plain Artists even on a tag-less library.
    @Test
    fun album_without_tag_is_owned_whole_by_its_majority_artist() {
        val album = listOf(
            song("t1", "alpha", "one"),
            song("t2", "alpha", "one"),
            song("t3", "beta", "one")
        )
        val groups = localAlbumArtists(album)
        assertEquals(listOf("alpha"), groups.map { it.name })
        assertEquals(3, groups.first().songs.size)
    }

    // No usable album name → nothing to attribute the song to; it stays on its
    // track artist exactly where the Artists view would put it.
    @Test
    fun album_less_songs_stay_on_their_track_artist() {
        val scattered = listOf(song("t1", "alpha", ""), song("t2", "beta", ""))
        assertEquals(listOf("alpha", "beta"), localAlbumArtists(scattered).map { it.name })
    }

    // Songs with no tag must not each become their own group keyed on an empty string.
    @Test
    fun untagged_files_collapse_into_one_group() {
        val untagged = listOf(
            song("a", "", ""),
            song("b", "", "")
        )
        assertEquals(1, localAlbums(untagged).size)
        assertEquals(1, localArtists(untagged).size)
        assertEquals("Unknown", localAlbums(untagged).first().name)
    }

    @Test
    fun blank_artist_album_still_names_a_subtitle() {
        val mixed = listOf(song("a", "", "album"), song("b", "someone", "album"))
        assertEquals("someone", localAlbums(mixed).first().subtitle)
    }

    @Test
    fun group_sort_is_case_insensitive() {
        val cased = listOf(song("x", "zeta", "Zeta"), song("y", "alpha", "alpha"))
        assertEquals(listOf("alpha", "Zeta"), localAlbums(cased).map { it.name })
    }

    @Test
    fun empty_input_gives_no_groups() {
        assertEquals(emptyList<Any>(), localAlbums(emptyList()))
        assertEquals(emptyList<Any>(), localArtists(emptyList()))
    }

    // The ALBUM_ARTIST tag owns the grouping: a compilation's guest tracks land on
    // the curator's shelf, not scattered across the guests' own shelves.
    @Test
    fun album_artists_group_by_album_artist_tag() {
        val tagged = listOf(
            song("a", "guest", "comp", "curator"),
            song("b", "curator", "comp", "curator")
        )
        val groups = localAlbumArtists(tagged)
        assertEquals(listOf("curator"), groups.map { it.name })
        assertEquals(listOf(2), groups.map { it.count })
    }

    // Most files carry no ALBUM_ARTIST tag at all — the track artist keeps the view
    // populated, exactly the fallback Retro applies before showing "Unknown".
    @Test
    fun blank_album_artist_falls_back_to_track_artist() {
        val groups = localAlbumArtists(listOf(song("a", "alpha", "one")))
        assertEquals(listOf("alpha"), groups.map { it.name })
    }
}