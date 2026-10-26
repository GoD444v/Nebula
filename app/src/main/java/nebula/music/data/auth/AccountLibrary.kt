package nebula.music.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nebula.music.data.SearchRepository
import nebula.music.data.YtPlaylistRow

/**
 * The account's YouTube playlists, loaded once and held in memory.
 *
 * The browse costs 1-2s, and doing it when the Playlists tab composes put that
 * wait in front of the user every time the tab opened. Kicked off at app start
 * instead, so the tab reads a value that is usually already there. null = not
 * loaded yet, which is distinct from an empty account.
 */
object AccountLibrary {
    private val _playlists = MutableStateFlow<List<YtPlaylistRow>?>(null)
    val playlists: StateFlow<List<YtPlaylistRow>?> = _playlists.asStateFlow()

    private var loading = false

    /** No-op while a load is already in flight or a result is already held. */
    fun load(force: Boolean = false) {
        if (loading && !force) return
        if (_playlists.value != null && !force) return
        loading = true
        CoroutineScope(Dispatchers.IO).launch {
            _playlists.value = runCatching { SearchRepository().accountPlaylists() }
                .getOrDefault(emptyList())
            loading = false
        }
    }
}