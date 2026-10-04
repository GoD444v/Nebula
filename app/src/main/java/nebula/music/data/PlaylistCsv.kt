package nebula.music.data

/**
 * CSV rows for playlist import (Spotify/Chosic/TuneMyMusic/Nebula exports).
 * Pure Kotlin, no Android APIs — unit-tested.
 */
object PlaylistCsv {

    /** One parsed data row: title, artist, optional watch URL. */
    data class CsvRow(
        val title: String,
        val artist: String,
        val url: String
    )

    /** Quote-aware line split: commas inside quotes don't split, "" unescapes. */
    fun parseLine(line: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c == ',' && !inQuotes -> {
                    out.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        out.add(current.toString())
        return out.map { it.trim().trim('"') }
    }

    private fun colIndex(header: List<String>, vararg keys: String): Int =
        header.indexOfFirst { cell -> keys.any { cell.equals(it, ignoreCase = true) } }

    /**
     * Header-aware row extraction. Recognized headers (title/name/song,
     * artist/artists, url/link) map by name; otherwise col0 = title,
     * col1 = artist. The header row itself is skipped only when recognized.
     */
    fun rows(text: String): List<CsvRow> {
        val lines = text.lines().map { parseLine(it) }
            .filter { row -> row.any { it.isNotBlank() } }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first()
        val ti = colIndex(header, "title", "name", "song", "track")
        val ai = colIndex(header, "artist", "artists", "channel", "author")
        val ui = colIndex(header, "url", "link")
        val hasHeader = ti >= 0 || ai >= 0 || ui >= 0
        val data = if (hasHeader) lines.drop(1) else lines
        return data.mapNotNull { row ->
            val title = if (ti >= 0) row.getOrNull(ti).orEmpty() else row.getOrNull(0).orEmpty()
            val artist = if (ai >= 0) row.getOrNull(ai).orEmpty() else row.getOrNull(1).orEmpty()
            val url = if (ui >= 0) row.getOrNull(ui).orEmpty() else ""
            if (title.isBlank() && artist.isBlank() && url.isBlank()) null
            else CsvRow(title, artist, url)
        }
    }

    /** videoId from a watch URL, "" when the row carries no link. */
    fun videoIdOf(url: String): String =
        Regex("[?&]v=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1).orEmpty()
}
