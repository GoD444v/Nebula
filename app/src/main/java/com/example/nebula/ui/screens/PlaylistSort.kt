package com.example.nebula.ui.screens

import com.example.nebula.data.models.SearchResult
import java.text.Collator
import java.util.Locale

/**
 * How a playlist's songs are ordered on screen.
 *
 * Declaration order is the menu order, and the labels are the ones the reference
 * implementation shows. CUSTOM is first and is the default because it is the only
 * option that can lose a direction — everything else has an ascending and descending
 * side, which manual order has no meaning for.
 */
enum class PlaylistSortType(val label: String) {
    CUSTOM("Custom order"),
    NAME("Name"),
    ARTIST("Artist"),
    DATE_ADDED("Date added"),
    PLAY_TIME("Play time")
}

/**
 * Sorts [songs] for display only.
 *
 * Never mutates anything and never reorders storage: the playlist's `position` values are
 * the user's arrangement, and rewriting them from a sort would destroy it with no way
 * back. This is the property that makes CUSTOM a reliable way back — the stored order is
 * always intact, so returning to it is a no-op rather than a recovery.
 *
 * Custom passes the list through untouched and ignores [descending], because reversing a
 * hand-arranged playlist is never what the user meant by tapping "descending".
 *
 * Name and Artist use a [Collator] at PRIMARY strength, not `String.compareTo`: the
 * latter sorts every capital before every lowercase, so "abbey road" lands after "Zoë",
 * which reads as broken.
 *
 * PLAY_TIME sorts by [SearchResult.playCount] and DATE_ADDED by the order songs were
 * added, both of which are always 0 for now — see the note in the commit. They are wired
 * so enabling them later needs no UI change.
 */
fun sortSongs(
    songs: List<SearchResult>,
    sortType: PlaylistSortType,
    descending: Boolean
): List<SearchResult> {
    if (sortType == PlaylistSortType.CUSTOM || songs.size < 2) return songs

    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }

    val ascending = when (sortType) {
        PlaylistSortType.CUSTOM -> songs
        PlaylistSortType.NAME -> songs.sortedWith(compareBy(collator) { it.title })
        PlaylistSortType.ARTIST -> songs.sortedWith(compareBy(collator) { it.artist })
        // No timestamp on a playlist_songs row today, so "date added" can only mean
        // insertion order, which is the list's own order.
        PlaylistSortType.DATE_ADDED -> songs
        PlaylistSortType.PLAY_TIME -> songs.sortedBy { it.playCount }
    }

    return if (descending) ascending.asReversed() else ascending
}