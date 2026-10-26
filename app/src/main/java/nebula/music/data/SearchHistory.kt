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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * Recent searches shown on the empty Search tab. Same ThemeStore/HomePrefs
 * idiom — in-memory mutableState so the list recomposes on change, DataStore
 * for restart. Plain query strings only, so this is a string list, not Room.
 */
object SearchHistory {
    /** Newest first. */
    var queries by mutableStateOf<List<String>>(emptyList())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        dataStore = context.applicationContext.searchHistoryDataStore
        runBlocking {
            queries = dataStore!!.data.first()[KEY]?.orEmpty()?.split("\n")
                ?.filter { it.isNotBlank() } ?: emptyList()
        }
    }

    /** Re-searching an old term bumps it to the top instead of duplicating it. */
    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        queries = mergeHistory(q, queries)
        persist()
    }

    fun remove(query: String) {
        queries = queries.filterNot { it == query }
        persist()
    }

    fun clear() {
        queries = emptyList()
        persist()
    }

    private fun persist() {
        val snapshot = queries
        scope.launch {
            dataStore?.edit { it[KEY] = snapshot.joinToString("\n") }
        }
    }

    private val KEY = stringPreferencesKey("queries")
}

/** Newest first, case-insensitive dedup, hard cap. Pure — this is what the test covers. */
internal fun mergeHistory(newQuery: String, existing: List<String>, cap: Int = 20): List<String> =
    (listOf(newQuery) + existing.filterNot { it.equals(newQuery, ignoreCase = true) }).take(cap)
