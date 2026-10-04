package nebula.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nebula.music.data.PlaylistCsv
import nebula.music.data.SearchRepository
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.db.dao.PlaylistWithCount
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity
import nebula.music.data.models.SearchResult
import nebula.music.ui.screens.PlaylistSortType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Outcome of adding songs to one playlist.
 *
 * Split counts rather than a boolean because "added 2, 1 was already there" and "nothing
 * was added" are different messages and the user acted differently in each case.
 */
data class AddToPlaylistResult(val added: Int, val duplicates: Int)

/**
 * One playlist's sort choice: which order, and which direction.
 *
 * A data class rather than two loose fields so the pair is replaced together and cannot
 * be left half-updated.
 *
 * Defaults to DATE_ADDED because the human partner asked for it directly, having been
 * told that a manual reorder stays invisible while it is selected. The stored positions
 * are never rewritten by a sort, so choosing CUSTOM order still restores the arrangement
 * exactly — it is one tap away, not a recovery. Changing this default back to CUSTOM is
 * the whole fix if that trade stops suiting.
 */
data class PlaylistSortChoice(
    val type: PlaylistSortType = PlaylistSortType.DATE_ADDED,
    val descending: Boolean = false
)

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
     * The sort choice for each playlist, so it survives leaving and re-entering.
     *
     * Held here rather than in the screen with `remember` because the screen composable
     * is rebuilt from scratch on every visit: `remember` starts at CUSTOM again, so a
     * list sorted by Name silently reverted to manual order the moment the user went
     * back and returned. Keyed by playlist id so two playlists keep separate choices.
     *
     * Only in memory, so it resets when the app process dies — the same lifetime as the
     * tab order in DataStore would give, minus the file. Deliberately not persisted:
     * sort is view-only and a forgotten choice is harmless, so there is nothing here
     * worth a schema.
     */
    private val _sorts = MutableStateFlow<Map<Long, PlaylistSortChoice>>(emptyMap())
    val sorts: StateFlow<Map<Long, PlaylistSortChoice>> = _sorts.asStateFlow()

    fun sortFor(playlistId: Long): PlaylistSortChoice =
        _sorts.value[playlistId] ?: PlaylistSortChoice()

    fun setSort(playlistId: Long, choice: PlaylistSortChoice) {
        _sorts.value = _sorts.value + (playlistId to choice)
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
     * Moves the song shown at row [from] to row [to] in [playlistId].
     *
     * The row indexes are translated to stored `position` values before touching the DAO,
     * and that translation is the whole point of this function. `PlaylistDao.moveSong`
     * shifts on `position`, but every caller — the playlist screen, the drag handle, a
     * future gesture — thinks in row indexes. Those are the same number only while the
     * positions are exactly 0..n-1.
     *
     * They are not guaranteed to be. Anything that removes a song without renumbering
     * leaves a permanent hole (`DownloadedPlaylistSync.reconcile` used to do exactly
     * that), and installs that already did have holes before this translation existed
     * still have them. With a hole the two numbering schemes diverge, `moveSong`'s
     * `BETWEEN MIN(..) AND MAX(..)` window covers the wrong rows, and the reorder
     * silently does nothing — which reads to the user as "the order reset itself".
     * Demonstrated by `ReorderGapDiagnosticTest`, which failed with the order unchanged.
     *
     * Reading the rows is therefore load-bearing, not a defensive extra: it is what makes
     * the two schemes line up. It also bounds the indexes, since a row that does not
     * exist can have no position.
     */
    fun reorder(playlistId: Long, from: Int, to: Int) = viewModelScope.launch {
        if (from == to) return@launch
        val rows = dao.observeSongs(playlistId).first()
        val fromPosition = rows.getOrNull(from)?.position ?: return@launch
        val toPosition = rows.getOrNull(to)?.position ?: return@launch
        if (fromPosition == toPosition) return@launch
        dao.moveSong(playlistId, fromPosition, toPosition)
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
     * Tracklist import: one "Artist - Title" per line, first song result wins.
     * Sequential, not parallel — search bursts get throttled, and 50 lines
     * finish in under a minute. Returns songs matched (0 = nothing found).
     */
    suspend fun importTracklist(name: String, lines: List<String>): Int = withContext(Dispatchers.IO) {
        val matched = matchLines(
            SearchRepository(),
            lines.map { it.trim() }.filter { it.isNotBlank() }
        )
        if (matched.isEmpty()) return@withContext 0
        createWithSongs(name.ifBlank { "Imported songs" }, matched)
        matched.size
    }

    /**
     * CSV import (Nebula/Spotify-Chosic/Others-TuneMyMusic exports). Rows with
     * a watch URL restore exact videoIds with no search; the rest fall through
     * to the same first-hit matching as pasted lists.
     */
    suspend fun importCsv(name: String, text: String): Int = withContext(Dispatchers.IO) {
        val rows = PlaylistCsv.rows(text)
        if (rows.isEmpty()) return@withContext 0
        val repository = SearchRepository()
        val exact = ArrayList<SearchResult>()
        val searchLines = ArrayList<String>()
        for (row in rows.take(100)) {
            val vid = PlaylistCsv.videoIdOf(row.url)
            if (vid.isNotBlank()) {
                exact.add(SearchResult(vid, row.title, row.artist, ""))
            } else if (row.title.isNotBlank()) {
                searchLines.add(
                    listOf(row.artist, row.title).filter { it.isNotBlank() }.joinToString(" - ")
                )
            }
        }
        val all = exact + matchLines(repository, searchLines)
        if (all.isEmpty()) return@withContext 0
        createWithSongs(name.ifBlank { "Imported songs" }, all)
        all.size
    }

    private suspend fun matchLines(
        repository: SearchRepository,
        lines: List<String>
    ): List<SearchResult> {
        val matched = ArrayList<SearchResult>()
        for (line in lines.take(50)) {
            try {
                repository.search(line, SearchRepository.SearchFilter.SONGS)
                    .items.firstOrNull()?.let { matched.add(it) }
            } catch (_: Exception) {
            }
        }
        return matched
    }

    /**
     * The songs of [playlistId] as queue items, in stored order.
     *
     * Exists because nothing could previously read a playlist's songs without observing
     * it: "Play all" from a Home card needs the list up front, and subscribing to the
     * detail screen's flow just to take a snapshot of it would keep a collector alive for
     * a one-shot read.
     *
     * The SDD ledger lists a `queueItems` here as already shipped. It was not, which is
     * why this is being added rather than called.
     */
    suspend fun songsOf(playlistId: Long): List<SearchResult> =
        dao.observeSongs(playlistId).first().map { row ->
            SearchResult(
                videoId = row.videoId,
                title = row.title,
                artist = row.artist,
                thumbnailUrl = row.thumbnailUrl.orEmpty(),
                addedAt = row.addedAt
            )
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
