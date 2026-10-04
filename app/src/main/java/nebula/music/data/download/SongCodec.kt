package nebula.music.data.download

import nebula.music.data.models.SearchResult

/**
 * Media3's DownloadRequest carries an opaque `data` byte array, and the only durable
 * record of a download's metadata. Storing just the title meant artist and artwork were
 * gone the moment the app restarted, so the Downloaded playlist could only ever show
 * titles - see d43023e and the `enqueue(SearchResult)` change that followed.
 *
 * A delimiter-joined string rather than JSON: this is written once, read once, and has
 * no dependency to justify. The unit separator cannot appear in a title, artist or URL.
 *
 * [decode] tolerates a bare title with no separators, so a request written before this
 * existed still decodes to a usable title.
 */
internal object SongCodec {

    private const val SEP = "\u001F"

    data class Decoded(val title: String, val artist: String, val thumbnailUrl: String)

    fun encode(song: SearchResult): String =
        listOf(song.title, song.artist, song.thumbnailUrl).joinToString(SEP)

    fun decode(raw: String): Decoded {
        val parts = raw.split(SEP)
        return Decoded(
            title = parts.getOrNull(0).orEmpty(),
            artist = parts.getOrNull(1).orEmpty(),
            thumbnailUrl = parts.getOrNull(2).orEmpty()
        )
    }
}
