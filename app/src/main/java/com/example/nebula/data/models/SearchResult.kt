package com.example.nebula.data.models

data class SearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val explicit: Boolean = false,
    val isVideoSong: Boolean = false,
    /**
     * Times played. Always 0 today — nothing increments it yet — but the Play time sort
     * and the song info dialog both read it, so adding tracking later needs no change to
     * either. Not persisted: it is derived state, not something a caller sets.
     */
    val playCount: Int = 0
)
