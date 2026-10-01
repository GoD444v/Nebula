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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Outcome of adding songs to one playlist.
 *
 * Split counts rather than a boolean because "added 2, 1 was already there" and "nothing
 * was added" are different messages and the user acted differently in each case.
 */
data class AddToPlaylistResult(val added: Int, val duplicates: Int)

/**
 * Playlists the user has created in Nebula. Local-first: nothing here talks to
 * YouTube Music, so the tab works with no account and no network.
 */
class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = NebulaDatabase.getDatabase(app)
    private val dao = db.playlistDao()

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

    /** Which playlist the detail screen is showing; null = showing none. */
    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    /**
     * The songs of the selected playlist, or an empty list while none is selected.
     *
     * `flatMapLatest` so switching playlists drops the previous subscription instead of
     * leaving two collectors writing the same slot. Null maps to emptyList because
     * "no playlist selected" is a legitimate state that must not throw.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<PlaylistSongEntity>> = selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeSongs(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens [id] for [songs]. Called when a card is tapped. */
    fun select(id: Long) {
        _selectedPlaylistId.value = id
    }

    /**
     * The one built-in playlist's id, or null if it has not been created yet.
     *
     * Matches on `isSystem` rather than on the name, which is the whole reason the flag
     * exists: a user who renames the Downloaded playlist must still find it.
     */
    suspend fun systemPlaylistId(): Long? = dao.getSystemPlaylistBlocking()?.id

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
     * Moves the song at index [from] to index [to] in [playlistId].
     *
     * The bounds check reads the current order first. It is not redundant: `moveSong`'s
     * `BETWEEN` window is computed from the raw indexes, so an out-of-range `from` would
     * otherwise shift every row instead of doing nothing. One extra read to keep a bad
     * index harmless.
     */
    fun reorder(playlistId: Long, from: Int, to: Int) = viewModelScope.launch {
        if (from == to) return@launch
        val size = dao.observeSongs(playlistId).first().size
        if (from !in 0 until size || to !in 0 until size) return@launch
        dao.moveSong(playlistId, from, to)
    }

    /**
     * Creates a playlist named [name] and immediately puts [songs] in it, returning the
     * new playlist's id.
     *
     * One call rather than `create` then `addSongs`, because the two-step version leaves a
     * real window in which the songs are lost: `create` launches into `viewModelScope` and
     * returns immediately, so a caller cannot await the id it needs for `addSongs`. The
     * insert happens inline here so the songs and the playlist are written together.
     *
     * The id is also published through [selectedPlaylistId], so the picker can create-then-
     * populate and land the user in the playlist they just made.
     *
     * Returns null for a blank name, which [create] also rejects.
     */
    suspend fun createWithSongs(name: String, songs: List<SearchResult>): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val id = dao.insert(PlaylistEntity(name = trimmed))
        addSongs(id, songs)
        select(id)
        return id
    }

    /**
     * Adds [songs] to an existing playlist and reports what happened, so the picker can
     * tell the difference between "added" and "already there" instead of appearing to do
     * nothing.
     */
    suspend fun addToPlaylist(id: Long, songs: List<SearchResult>): AddToPlaylistResult {
        val added = addSongs(id, songs)
        return AddToPlaylistResult(
            added = added,
            duplicates = songs.size - added
        )
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
