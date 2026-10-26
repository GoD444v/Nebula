package nebula.music.ui.screens

import nebula.music.data.db.entities.LocalSong
import java.text.Collator
import java.util.Locale

/** The five ways into the local library. */
enum class LocalBrowseMode(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    ALBUM_ARTISTS("Album artists")
}

/**
 * One album or artist row: the name, a subtitle, and the songs behind it.
 *
 * [songs] is the whole point — tapping the row plays exactly these, in one queue, with
 * no second lookup at play time.
 */
data class LocalGroup(
    val name: String,
    val subtitle: String,
    val songs: List<LocalSong>
) {
    val count: Int get() = songs.size
}

/** Name MediaStore uses when a file carries no tag at all. */
private const val UNTAGGED = "Unknown"

/**
 * Groups [songs] by album, sorted by album name.
 *
 * Untagged files collapse into a single "Unknown" album rather than one group per empty
 * string, and songs with no album are still reachable that way.
 */
fun localAlbums(songs: List<LocalSong>): List<LocalGroup> =
    songs.groupBy { it.album.ifBlank { UNTAGGED } }
        .map { (album, inAlbum) ->
            LocalGroup(
                name = album,
                // Every song on one album should name the same artist; take the first
                // non-blank so an untagged track does not blank out the subtitle.
                subtitle = inAlbum.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty(),
                songs = inAlbum
            )
        }
        .sortedWith(compareBy(collator(), LocalGroup::name))

/** Groups [songs] by artist, sorted by artist name. Same shape as [localAlbums]. */
fun localArtists(songs: List<LocalSong>): List<LocalGroup> =
    songs.groupBy { it.artist.ifBlank { UNTAGGED } }
        .map { (artist, byArtist) ->
            LocalGroup(
                name = artist,
                subtitle = "${byArtist.size} song${if (byArtist.size == 1) "" else "s"}",
                songs = byArtist
            )
        }
        .sortedWith(compareBy(collator(), LocalGroup::name))

/**
 * Groups [songs] by the folder the file lives in, sorted by folder name.
 *
 * The scan stores RELATIVE_PATH ("Music/Albums") or DATA's parent; the name shown
 * is the last segment, so deep and top-level folders compare side by side. Files
 * scanned before the column existed — or whose device answered no path — collapse
 * into one "Unknown" bucket rather than vanishing, same policy as untagged albums.
 */
fun localFolders(songs: List<LocalSong>): List<LocalGroup> =
    songs.groupBy { it.folderPath.substringAfterLast('/').ifBlank { UNTAGGED } }
        .map { (folder, inFolder) ->
            LocalGroup(
                name = folder,
                subtitle = "${inFolder.size} song${if (inFolder.size == 1) "" else "s"}",
                songs = inFolder
            )
        }
        .sortedWith(compareBy(collator(), LocalGroup::name))

/**
 * Groups [songs] by the ALBUM artist — the one shelf an album belongs to, which is
 * what keeps a guest track from flooding the guest's own shelf.
 *
 * The owner is decided per ALBUM, not per track: the file's ALBUM_ARTIST tag when
 * any of the album's tracks carries one, else the artist that appears most on that
 * album (ties: collator-first name). A tag-less library therefore still shows a
 * different, tidier view than plain Artists — the whole point of having both.
 * Songs without a usable album name ("Unknown" from MediaStore, or blank) have
 * nothing to attribute, so they stay on their track artist, like Artists does.
 */
fun localAlbumArtists(songs: List<LocalSong>): List<LocalGroup> {
    val ownerOfAlbum = songs.asSequence()
        .filter { it.album.isNotBlank() && it.album != UNTAGGED }
        .groupBy { it.album }
        .mapValues { (_, tracks) ->
            tracks.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist
                ?: tracks.groupingBy { it.artist }.eachCount().entries
                    // most-shelved artist first, then the collator-first name on ties
                    .minWithOrNull(
                        compareByDescending<Map.Entry<String, Int>> { it.value }
                            .thenBy(collator()) { it.key }
                    )?.key.orEmpty()
        }
    return songs.groupBy { song ->
        when {
            song.albumArtist.isNotBlank() -> song.albumArtist
            song.album.isNotBlank() && song.album != UNTAGGED ->
                ownerOfAlbum[song.album].orEmpty()
            else -> song.artist
        }.ifBlank { UNTAGGED }
    }
        .map { (artist, byArtist) ->
            LocalGroup(
                name = artist,
                subtitle = "${byArtist.size} song${if (byArtist.size == 1) "" else "s"}",
                songs = byArtist
            )
        }
        .sortedWith(compareBy(collator(), LocalGroup::name))
}

private fun collator() =
    Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }