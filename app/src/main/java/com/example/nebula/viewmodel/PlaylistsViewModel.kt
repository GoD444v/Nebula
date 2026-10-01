package com.example.nebula.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.data.db.dao.PlaylistWithCount
import com.example.nebula.data.db.entities.PlaylistEntity
import com.example.nebula.data.db.entities.PlaylistSongEntity
import com.example.nebula.data.models.SearchResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Playlists the user has created in Nebula. Local-first: nothing here talks to
 * YouTube Music, so the tab works with no account and no network.
 */
class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = NebulaDatabase.getDatabase(app).playlistDao()

    init {
        // Repairs the Downloaded playlist against anything the listener missed while
        // the app was dead. Done here, not in NebulaDownloads.init, so opening the app
        // never writes to the database behind the user's back.
        viewModelScope.launch {
            NebulaDownloads.reconcileDownloadedPlaylist()
        }
    }

    val playlists: StateFlow<List<PlaylistWithCount>> =
        dao.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.insert(PlaylistEntity(name = trimmed))
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.rename(id, trimmed, System.currentTimeMillis())
    }

    fun delete(id: Long) = viewModelScope.launch {
        dao.delete(id)
    }

    /**
     * Appends [results] to the playlist and returns how many were genuinely new.
     * A song already present is skipped by the composite primary key, so the count
     * lets the caller say "already in playlist" instead of appearing to do nothing.
     */
    suspend fun addSongs(playlistId: Long, results: List<SearchResult>): Int {
        if (results.isEmpty()) return 0
        var position = dao.nextPosition(playlistId)
        val rows = results.map { result ->
            PlaylistSongEntity(
                playlistId = playlistId,
                videoId = result.videoId,
                title = result.title,
                artist = result.artist,
                thumbnailUrl = result.thumbnailUrl,
                position = position++
            )
        }
        return dao.insertSongs(rows).count { it != -1L }
    }
}
