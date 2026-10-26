package nebula.music.ui.screens

import nebula.music.data.db.entities.LocalSong
import java.text.Collator
import java.util.Locale

/**
 * How local songs are ordered on screen.
 *
 * Same four options and same order as the reference implementation's local-songs
 * menu, and MODIFIED is first because that is its default. Duration is deliberately
 * absent — it is not offered there, and a sort with no clear use is a label nobody
 * wants to read.
 */
enum class LocalSongSortType(val label: String) {
    DATE_MODIFIED("Date updated"),
    NAME("Name"),
    ARTIST("Artist"),
    ALBUM("Album")
}

/**
 * Sorts [songs] for display only — the stored table is untouched, because it is a cache
 * of the device and a rescan rebuilds it from MediaStore anyway.
 *
 * Name, Artist and Album use a [Collator] at PRIMARY strength rather than
 * `String.compareTo`, for the reason spelled out in [sortSongs]: the latter puts every
 * capital before every lowercase, so "abbey road" lands after "Zoë".
 */
fun sortLocalSongs(
    songs: List<LocalSong>,
    sortType: LocalSongSortType,
    descending: Boolean
): List<LocalSong> {
    if (songs.size < 2) return songs

    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }

    val ascending = when (sortType) {
        // Newest first is what this sort means, so DESCENDING is the sensible default;
        // the caller owns that choice, and NAME is the stable tiebreak when two files
        // share a timestamp to the second.
        LocalSongSortType.DATE_MODIFIED -> songs.sortedWith(
            compareByDescending<LocalSong> { it.dateModified }
                .thenBy(collator) { it.title }
        )
        LocalSongSortType.NAME -> songs.sortedWith(compareBy(collator) { it.title })
        LocalSongSortType.ARTIST -> songs.sortedWith(
            compareBy(collator, LocalSong::artist).thenBy(collator) { it.title }
        )
        LocalSongSortType.ALBUM -> songs.sortedWith(
            compareBy(collator, LocalSong::album).thenBy(collator) { it.title }
        )
    }

    return if (descending) ascending.reversed() else ascending
}