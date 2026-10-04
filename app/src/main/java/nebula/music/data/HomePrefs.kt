package nebula.music.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(name = "home_prefs")

/**
 * Home tab visibility: the "Your playlists" section master switch plus
 * per-playlist hides. Same ThemeStore/OnboardingStore idiom — in-memory
 * mutableState so every reader recomposes on change, DataStore for restart.
 */
object HomePrefs {
    var showYourPlaylists by mutableStateOf(true)
        private set
    /**
     * Explicit per-playlist overrides. Absent = default, and the default is
     * visibility EXCEPT the built-in Downloaded mirror — system rows stay off
     * Home until the user opts in, so a fresh install shows only real playlists.
     */
    var visibilityOverrides by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun isVisible(id: Long, isSystem: Boolean): Boolean =
        visibilityOverrides[id] ?: !isSystem

    fun init(context: Context) {
        dataStore = context.applicationContext.homeDataStore
        runBlocking {
            val prefs = dataStore!!.data.first()
            showYourPlaylists = prefs[booleanPreferencesKey("show_your_playlists")] ?: true
            val raw = prefs[stringPreferencesKey("playlist_visibility")] ?: ""
            visibilityOverrides = if (raw.isEmpty()) emptyMap()
            else raw.split(",").mapNotNull { entry ->
                val (id, v) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                id.toLongOrNull()?.let { it to (v == "1") }
            }.toMap()
        }
    }

    fun setShowYourPlaylists(context: Context, show: Boolean) {
        showYourPlaylists = show
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("show_your_playlists")] = show
            }
        }
    }

    /** An override matching the default is dropped, so deleted playlists leave no residue. */
    fun setVisible(context: Context, id: Long, isSystem: Boolean, visible: Boolean) {
        visibilityOverrides =
            if (visible == !isSystem) visibilityOverrides - id
            else visibilityOverrides + (id to visible)
        persist()
    }

    /** Bulk restore from a backup file. */
    fun restore(context: Context, show: Boolean, overrides: Map<Long, Boolean>) {
        showYourPlaylists = show
        visibilityOverrides = overrides
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("show_your_playlists")] = show
                prefs[stringPreferencesKey("playlist_visibility")] =
                    overrides.entries.joinToString(",") { (k, v) -> "$k=${if (v) 1 else 0}" }
            }
        }
    }

    private fun persist() {
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("playlist_visibility")] =
                    visibilityOverrides.entries.joinToString(",") { (k, v) -> "$k=${if (v) 1 else 0}" }
            }
        }
    }
}
