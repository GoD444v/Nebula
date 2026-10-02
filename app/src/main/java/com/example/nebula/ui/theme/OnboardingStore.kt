package com.example.nebula.ui.theme

import android.content.Context
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
    var selectedGenres by mutableStateOf<List<String>>(emptyList())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        dataStore = context.applicationContext.onboardingDataStore
        runBlocking {
            val prefs = dataStore!!.data.first()
            isDone = prefs[booleanPreferencesKey("onboarding_done")] ?: false
            val raw = prefs[stringPreferencesKey("selected_genres")] ?: ""
            selectedGenres = if (raw.isEmpty()) emptyList() else raw.split(",")
        }
    }

    fun complete(context: Context, genres: List<String>) {
        isDone = true
        selectedGenres = genres
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = true
                prefs[stringPreferencesKey("selected_genres")] = genres.joinToString(",")
            }
        }
    }

    fun reset(context: Context) {
        isDone = false
        selectedGenres = emptyList()
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = false
                prefs.remove(stringPreferencesKey("selected_genres"))
            }
        }
    }
}
