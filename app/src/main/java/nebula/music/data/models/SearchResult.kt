package nebula.music.data.models

data class SearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val explicit: Boolean = false,
    val isVideoSong: Boolean = false,
    /**
     * Set when the result is a playlist (watchPlaylistEndpoint) rather than a
     * playable song. Tap opens the detail page instead of playing.
     */
    val playlistId: String = "",
    /**
     * Set for album/artist/playlist cards (browseEndpoint). Tap opens the
     * detail page. pageType carries MUSIC_PAGE_TYPE_* for grouping.
     */
    val browseId: String = "",
    val pageType: String = "",
    /**
     * Times played. Always 0 today — nothing increments it yet — but the Play time sort
     * and the song info dialog both read it, so adding tracking later needs no change to
     * either. Not persisted: it is derived state, not something a caller sets.
     */
    val playCount: Int = 0,
    /**
     * When this song was added to its playlist, in epoch millis.
     *
     * Defaults to 0 so search results and playback queue entries -- neither of which
     * belongs to a playlist -- need no value. Only rows read back out of
     * `playlist_songs` carry a real stamp, which is what the Date Added sort reads.
     *
     * Previously Date Added had nothing to sort on and fell through to insertion order,
     * which made it indistinguishable from Custom order and quietly untrue.
     */
    val addedAt: Long = 0L
) {
    val isPlaylist: Boolean get() = playlistId.isNotBlank()
    val isBrowsable: Boolean get() = browseId.isNotBlank()
}
