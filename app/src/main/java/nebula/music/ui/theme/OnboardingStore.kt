package nebula.music.ui.theme

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

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "nebula_onboarding")

object OnboardingStore {
    var isDone by mutableStateOf(false)
        private set
    /** Genre titles, for display. */
    var selectedGenres by mutableStateOf<List<String>>(emptyList())
        private set
    /** Full endpoints (title, browseId, params) — what HomeViewModel actually loads. */
    var selectedGenreEndpoints by mutableStateOf<List<Triple<String, String, String>>>(emptyList())
        private set
    /**
     * Artist seeds: (artist name, top song videoIds). Resolved once at pick
     * time so cold starts never re-search — HomeViewModel goes straight to
     * related(). Same encoding idiom as genres: entries ";"-joined.
     */
    var artistSeeds by mutableStateOf<List<Pair<String, List<String>>>>(emptyList())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // One entry per genre: title|browseId|params, entries comma-joined.
    // YT genre titles carry no commas or pipes, so no escaping layer.
    private fun encode(title: String, browseId: String, params: String) =
        "$title|$browseId|$params"

    private fun decode(raw: String): Triple<String, String, String>? {
        val parts = raw.split("|")
        return if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
    }

    fun init(context: Context) {
        dataStore = context.applicationContext.onboardingDataStore
        runBlocking {
            val prefs = dataStore!!.data.first()
            isDone = prefs[booleanPreferencesKey("onboarding_done")] ?: false
            val raw = prefs[stringPreferencesKey("selected_genres")] ?: ""
            selectedGenreEndpoints = if (raw.isEmpty()) emptyList()
            else raw.split(",").mapNotNull { decode(it) }
            selectedGenres = selectedGenreEndpoints.map { it.first }
            val artistsRaw = prefs[stringPreferencesKey("artist_seeds")] ?: ""
            artistSeeds = if (artistsRaw.isEmpty()) emptyList()
            else artistsRaw.split(";").mapNotNull { entry ->
                val name = entry.substringBefore("|", "")
                val vids = entry.substringAfter("|", "").split(",").filter { it.isNotBlank() }
                if (name.isBlank() || vids.isEmpty()) null else name to vids
            }
        }
    }

    fun complete(context: Context, genres: List<Triple<String, String, String>>) {
        saveGenres(context, genres)
        markDone(context)
    }

    /** Persist genres without finishing — the flow continues to import/artists. */
    fun saveGenres(context: Context, genres: List<Triple<String, String, String>>) {
        selectedGenreEndpoints = genres
        selectedGenres = genres.map { it.first }
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("selected_genres")] =
                    genres.joinToString(",") { encode(it.first, it.second, it.third) }
            }
        }
    }

    fun markDone(context: Context) {
        isDone = true
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = true
            }
        }
    }

    fun saveArtists(context: Context, seeds: List<Pair<String, List<String>>>) {
        artistSeeds = seeds
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("artist_seeds")] =
                    seeds.joinToString(";") { (name, vids) -> "$name|${vids.joinToString(",")}" }
            }
        }
    }

    /** Raw store strings for backup: the same encoding init decodes. */
    fun rawGenres(): String =
        selectedGenreEndpoints.joinToString(",") { encode(it.first, it.second, it.third) }

    fun rawArtists(): String =
        artistSeeds.joinToString(";") { (name, vids) -> "$name|${vids.joinToString(",")}" }

    /** Bulk restore from a backup file: raw store strings, decoded like init. */
    fun restoreRaw(context: Context, genresRaw: String, artistsRaw: String) {
        selectedGenreEndpoints = if (genresRaw.isEmpty()) emptyList()
        else genresRaw.split(",").mapNotNull { decode(it) }
        selectedGenres = selectedGenreEndpoints.map { it.first }
        artistSeeds = if (artistsRaw.isEmpty()) emptyList()
        else artistsRaw.split(";").mapNotNull { entry ->
            val name = entry.substringBefore("|", "")
            val vids = entry.substringAfter("|", "").split(",").filter { it.isNotBlank() }
            if (name.isBlank() || vids.isEmpty()) null else name to vids
        }
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("selected_genres")] = genresRaw
                prefs[stringPreferencesKey("artist_seeds")] = artistsRaw
            }
        }
    }

    fun reset(context: Context) {
        isDone = false
        selectedGenres = emptyList()
        selectedGenreEndpoints = emptyList()
        artistSeeds = emptyList()
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = false
                prefs.remove(stringPreferencesKey("selected_genres"))
                prefs.remove(stringPreferencesKey("artist_seeds"))
            }
        }
    }
}
