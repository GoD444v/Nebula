package com.example.nebula.ui.theme

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
        }
    }

    fun complete(context: Context, genres: List<Triple<String, String, String>>) {
        isDone = true
        selectedGenreEndpoints = genres
        selectedGenres = genres.map { it.first }
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = true
                prefs[stringPreferencesKey("selected_genres")] =
                    genres.joinToString(",") { encode(it.first, it.second, it.third) }
            }
        }
    }

    fun reset(context: Context) {
        isDone = false
        selectedGenres = emptyList()
        selectedGenreEndpoints = emptyList()
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = false
                prefs.remove(stringPreferencesKey("selected_genres"))
            }
        }
    }
}
