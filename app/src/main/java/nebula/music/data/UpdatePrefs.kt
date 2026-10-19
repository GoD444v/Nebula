package nebula.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * DataStore-backed preferences for app updates — the settings half of the
 * updater. Same idiom as [DownloadPrefs]: in-memory StateFlows fed by a
 * DataStore collect started in [init].
 *
 * Split of duties: this file owns the switches and the status display state.
 * The check itself, the download and the install belong to the updater
 * engine, which writes its result here via [setAvailable]/[setLastChecked].
 * [init] is idempotent so both MainActivity and the settings section may
 * call it without stacking collectors.
 */
object UpdatePrefs {
    private var appContext: Context? = null
    private var inited = false

    private val AUTO_CHECK = booleanPreferencesKey("auto_check")
    private val NOTIFICATIONS = booleanPreferencesKey("update_notifications")
    private val AVAILABLE = booleanPreferencesKey("update_available")
    private val AVAILABLE_VERSION = stringPreferencesKey("available_version")
    private val LAST_CHECKED = stringPreferencesKey("last_checked")

    private val _autoCheck = MutableStateFlow(true)
    val autoCheck: StateFlow<Boolean> = _autoCheck

    private val _notifications = MutableStateFlow(true)
    val notifications: StateFlow<Boolean> = _notifications

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private val _availableVersion = MutableStateFlow("")
    val availableVersion: StateFlow<String> = _availableVersion

    private val _lastChecked = MutableStateFlow("")
    val lastChecked: StateFlow<String> = _lastChecked

    /** The persisted auto-check switch, read straight from DataStore. */
    suspend fun shouldAutoCheck(context: Context): Boolean =
        context.updateDataStore.data.first()[AUTO_CHECK] ?: true

    fun init(context: Context) {
        if (inited) return
        inited = true
        appContext = context.applicationContext
        // Load the persisted values into the StateFlows
        CoroutineScope(Dispatchers.IO).launch {
            context.applicationContext.updateDataStore.data.collect { prefs ->
                _autoCheck.value = prefs[AUTO_CHECK] ?: true
                _notifications.value = prefs[NOTIFICATIONS] ?: true
                _updateAvailable.value = prefs[AVAILABLE] ?: false
                _availableVersion.value = prefs[AVAILABLE_VERSION].orEmpty()
                _lastChecked.value = prefs[LAST_CHECKED].orEmpty()
            }
        }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        checkNotNull(appContext).updateDataStore.edit { it[AUTO_CHECK] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        _notifications.value = enabled
        checkNotNull(appContext).updateDataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    /** Written by the updater engine after a check finishes. */
    suspend fun setAvailable(available: Boolean, version: String = "") {
        _updateAvailable.value = available
        _availableVersion.value = version
        checkNotNull(appContext).updateDataStore.edit {
            it[AVAILABLE] = available
            it[AVAILABLE_VERSION] = version
        }
    }

    /** Written by the updater engine after a check finishes. */
    suspend fun setLastChecked(text: String) {
        _lastChecked.value = text
        checkNotNull(appContext).updateDataStore.edit { it[LAST_CHECKED] = text }
    }
}

private val Context.updateDataStore by preferencesDataStore(name = "update_prefs")

/**
 * True when [latest] is a newer release than [current]. Segments compare
 * numerically ("1.10" beats "1.9"); a missing segment counts as 0; leading
 * "v"/"b" tags are ignored. Pure function, unit-tested.
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val cleanLatest = latest.removePrefix("b").removePrefix("v").trim()
    val cleanCurrent = current.removePrefix("b").removePrefix("v").trim()
    if (cleanLatest == cleanCurrent) return false
    val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    return false
}
