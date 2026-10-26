package nebula.music.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import nebula.music.data.models.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.blockedSongsDataStore: DataStore<Preferences> by preferencesDataStore(name = "blocked_songs")

/**
 * The blacklist: songs filtered out of Search results and the Home feed.
 * The videoId is the key; title/artist ride along as display-only snapshots so
 * the Settings list can name the song instead of showing a raw id. Same
 * SearchHistory idiom — in-memory state recomposes the UI, DataStore survives
 * a restart.
 */
object BlockedSongs {
    data class Entry(val videoId: String, val title: String, val artist: String)

    /** Newest first. */
    var songs by mutableStateOf<List<Entry>>(emptyList())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        dataStore = context.applicationContext.blockedSongsDataStore
        runBlocking {
            songs = dataStore!!.data.first()[KEY].orEmpty()
                .split(ENTRY_SEP)
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(FIELD_SEP)
                    Entry(
                        videoId = parts.getOrElse(0) { "" },
                        title = parts.getOrElse(1) { "" },
                        artist = parts.getOrElse(2) { "" }
                    )
                }
                .filter { it.videoId.isNotBlank() }
        }
    }

    fun isBlocked(videoId: String): Boolean = songs.any { it.videoId == videoId }

    /** Blank videoIds are never blocked — they have no identity to filter on. */
    fun block(videoId: String, title: String, artist: String) {
        if (videoId.isBlank() || isBlocked(videoId)) return
        songs = listOf(Entry(videoId, title, artist)) + songs
        persist()
    }

    fun unblock(videoId: String) {
        if (songs.none { it.videoId == videoId }) return
        songs = songs.filterNot { it.videoId == videoId }
        persist()
    }

    fun filter(items: List<SearchResult>): List<SearchResult> =
        if (songs.isEmpty()) items else items.filterNot { isBlocked(it.videoId) }

    private fun persist() {
        val snapshot = songs
        scope.launch {
            dataStore?.edit {
                it[KEY] = snapshot.joinToString(ENTRY_SEP) { e ->
                    "${e.videoId}$FIELD_SEP${e.title}$FIELD_SEP${e.artist}"
                }
            }
        }
    }

    private val KEY = stringPreferencesKey("blocked")
    // ASCII record (30) and unit (31) separators: they never occur in titles or
    // artists, so entries and fields split safely without any escaping. Built
    // from Char so no control-character literal sits in this file.
    private val ENTRY_SEP = Char(30).toString()
    private val FIELD_SEP = Char(31).toString()
}
