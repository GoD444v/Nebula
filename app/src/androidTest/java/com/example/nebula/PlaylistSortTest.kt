package com.example.nebula

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.screens.PlaylistSortType
import com.example.nebula.ui.screens.sortSongs
import com.example.nebula.viewmodel.PlaylistSortChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sort is view-only, and that is the property the whole menu rests on.
 *
 * The reference implementation never lets a sort rewrite stored positions, so the user's
 * hand-arranged order survives and CUSTOM is a reliable way back. A sort that rewrote
 * storage would make Custom order a lie the first time the user sorted by name.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistSortTest {

    // The id is deliberately not the title: with distinct ids the ARTIST sort cannot pass by
// accident because the input order happens to match.
    private fun song(title: String, artist: String = "Artist") =
        SearchResult(videoId = "id-$title", title = title, artist = artist, thumbnailUrl = "")

    private fun stamped(title: String, addedAt: Long) =
        SearchResult(
            videoId = "id-$title",
            title = title,
            artist = "Artist",
            thumbnailUrl = "",
            addedAt = addedAt
        )

    private val list = listOf(
        song("Wonderwall", "Oasis"),
        song("Abbey Road", "Beatles"),
        song("believer", "Imagine Dragons")
    )

    private fun titles(songs: List<SearchResult>) = songs.map { it.title }

    @Test
    fun customOrderPassesTheListThroughUntouched() {
        assertEquals(listOf("Wonderwall", "Abbey Road", "believer"), titles(sortSongs(list, PlaylistSortType.CUSTOM, false)))
    }

    @Test
    fun customOrderIgnoresDescending() {
        // Reversing a hand-arranged playlist is never what the user meant, and it would
        // silently destroy their arrangement if it did.
        assertEquals(titles(sortSongs(list, PlaylistSortType.CUSTOM, false)), titles(sortSongs(list, PlaylistSortType.CUSTOM, true)))
    }

    @Test
    fun sortByNameIsCaseInsensitive() {
        // A naive compareTo puts every capital before every lowercase, so "believer"
        // would sort after "Wonderwall". That reads as broken.
        assertEquals(listOf("Abbey Road", "believer", "Wonderwall"), titles(sortSongs(list, PlaylistSortType.NAME, false)))
    }

    @Test
    fun sortByNameDescendingReversesIt() {
        assertEquals(listOf("Wonderwall", "believer", "Abbey Road"), titles(sortSongs(list, PlaylistSortType.NAME, true)))
    }

    @Test
    fun sortByArtistGroupsByArtist() {
        // Artists are Beatles, Imagine Dragons, Oasis — so the songs land as Abbey Road,
        // believer, Wonderwall. Asserting on titles, since that is what the helper returns.
        assertEquals(listOf("Abbey Road", "believer", "Wonderwall"), titles(sortSongs(list, PlaylistSortType.ARTIST, false)))
    }

    @Test
    fun sortingNeverMutatesTheInput() {
        val input = list.toMutableList()
        val before = input.map { it.title }

        sortSongs(input, PlaylistSortType.NAME, true)

        assertEquals(before, input.map { it.title })
    }

    @Test
    fun sortDoesNotChangeTheCallersOwnList() {
        // The screen keeps `items` as the manual order and sorts a separate list for
        // display. If sortSongs returned the same instance the two would alias and
        // switching back to Custom would show sorted songs.
        val manual = list.toList()
        val sorted = sortSongs(manual, PlaylistSortType.NAME, false)
        assertEquals(listOf("Wonderwall", "Abbey Road", "believer"), titles(manual))
        assertEquals(listOf("Abbey Road", "believer", "Wonderwall"), titles(sorted))
    }

    @Test
    fun singleSongIsReturnedAsIs() {
        val one = listOf(song("Only"))
        assertEquals(one, sortSongs(one, PlaylistSortType.NAME, true))
    }

    @Test
    fun emptyListIsHandled() {
        assertEquals(emptyList<SearchResult>(), sortSongs(emptyList(), PlaylistSortType.NAME, true))
    }

    /**
     * Date Added used to be a placeholder that returned the list untouched, so it silently
     * meant insertion order. These assert it reads the stored stamp instead.
     */
    @Test
    fun dateAddedSortsByTheStoredTimestamp() {
        // Added newest-first in the list, so insertion order and stamp order disagree.
        val songs = listOf(stamped("C", 300), stamped("A", 100), stamped("B", 200))

        assertEquals(listOf("A", "B", "C"), titles(sortSongs(songs, PlaylistSortType.DATE_ADDED, false)))
    }

    @Test
    fun dateAddedDescendingReversesTheStamps() {
        val songs = listOf(stamped("A", 100), stamped("B", 200), stamped("C", 300))

        assertEquals(listOf("C", "B", "A"), titles(sortSongs(songs, PlaylistSortType.DATE_ADDED, true)))
    }

    @Test
    fun dateAddedIsStableForSongsSharingATimestamp() {
        // Songs added in the same millisecond must not swap places between reads, which
        // would look exactly like the reorder-reset bug this session fixed. Kotlin's
        // sortedWith is stable, so equal keys keep input order.
        val songs = listOf(stamped("X", 500), stamped("Y", 500), stamped("Z", 500))

        assertEquals(listOf("X", "Y", "Z"), titles(sortSongs(songs, PlaylistSortType.DATE_ADDED, false)))
    }

    @Test
    fun dateAddedIsTheDefaultSortChoice() {
        // The human partner asked for this deliberately, having been told a manual reorder
        // stays invisible under it. Pinned here so changing it is a deliberate act.
        assertEquals(
            PlaylistSortType.DATE_ADDED,
            PlaylistSortChoice().type
        )
    }
}