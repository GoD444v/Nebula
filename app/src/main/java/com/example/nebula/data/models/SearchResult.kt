package com.example.nebula.data.models

data class SearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val explicit: Boolean = false,
    val isVideoSong: Boolean = false
)
