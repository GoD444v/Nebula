package nebula.music.ui.screens

import nebula.music.data.models.SearchResult
import java.text.Collator
import java.util.Locale

/**
 * Ordering options for a remote playlist / album track list.
 *
 * Name and Artist mirror `LocalSongSortType` exactly, so the two sort menus use the
 * same words in the same order. ALBUM is deliberately absent: the local songs carry one,
 * but a YouTube search result has no album name to sort on, and offering a sort that
 * cannot sort anything is worse than not offering it.
 */
enum class PlaylistSortType(val label: String) {
    DEFAULT("Default order"),
    NAME("Name"),
    ARTIST("Artist"),
    CUSTOM("Custom order"),
    // The partner asked for this one by name: a playlist that mixes old and new additions
    // reads best newest-first. It sorts the STORED ROWS, not the fetched list, so it
    // needs the addedAt the entity carries — see sortRemoteTracks for why it cannot sort
    // it here.
    DATE_ADDED("Date added")
}

/**
 * Sorts [tracks] for DISPLAY ONLY — the fetched list is untouched.
 *
 * The page is re-fetched on every open, so a remote sort cannot outlive the screen it
 * was set on; persisting it would imply an ordering the next open immediately discards.
 * Name/Artist/Album use a [Collator] at PRIMARY strength for the reason spelled out in
 * `sortSongs`: `String.compareTo` sorts every capital before every lowercase, which put
 * "abbey road" after "Zoë".
 */
fun sortRemoteTracks(
    tracks: List<SearchResult>,
    sortType: PlaylistSortType,
    descending: Boolean
): List<SearchResult> {
    if (tracks.size < 2 || sortType == PlaylistSortType.DEFAULT) return tracks

    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        PlaylistSortType.DEFAULT -> tracks
        PlaylistSortType.NAME -> tracks.sortedWith { a, b -> collator.compare(a.title, b.title) }
        PlaylistSortType.ARTIST -> tracks.sortedWith { a, b -> collator.compare(a.artist, b.artist) }
        PlaylistSortType.CUSTOM -> tracks
        // Date added sorts the STORED ROWS by their addedAt, which this function cannot
        // see — it only ever gets fetched tracks. The playlist screen pre-sorts its rows
        // by addedAt and asks for DEFAULT here, so this branch is the safety net for a
        // caller that passes DATE_ADDED directly: the rows keep the order they arrived in.
        PlaylistSortType.DATE_ADDED -> tracks
    }
    return if (descending) sorted.reversed() else sorted
}