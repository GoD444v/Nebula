package nebula.music.data.auth

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "nebula_auth")

/**
 * Persists the YouTube cookie session (Echo architecture, original code).
 *
 * Logged-in = the stored cookie carries SAPISID. Reads come from the
 * in-memory copies so callers never block; writes go to DataStore on a
 * background coroutine.
 */
object YtCookieStore {
    private val COOKIE = stringPreferencesKey("yt_cookie")
    private val VISITOR_DATA = stringPreferencesKey("yt_visitor_data")
    private val DATA_SYNC_ID = stringPreferencesKey("yt_data_sync_id")
    private val ACCOUNT_NAME = stringPreferencesKey("yt_account_name")
    private val ACCOUNT_EMAIL = stringPreferencesKey("yt_account_email")
    private val ACCOUNT_HANDLE = stringPreferencesKey("yt_account_handle")
    private val ACCOUNT_PHOTO = stringPreferencesKey("yt_account_photo")

    private var dataStore: DataStore<Preferences>? = null

    var cookie: String = ""
        private set
    var visitorData: String? = null
        private set
    var dataSyncId: String? = null
        private set
    private var accountName: String? = null
    private var accountEmail: String? = null
    private var accountHandle: String? = null
    private var accountPhoto: String? = null

    fun init(context: Context) {
        dataStore = context.applicationContext.authDataStore
        runBlocking(Dispatchers.IO) {
            val prefs = dataStore!!.data.first()
            cookie = prefs[COOKIE] ?: ""
            visitorData = prefs[VISITOR_DATA]
            dataSyncId = prefs[DATA_SYNC_ID]
            accountName = prefs[ACCOUNT_NAME]
            accountEmail = prefs[ACCOUNT_EMAIL]
            accountHandle = prefs[ACCOUNT_HANDLE]
            accountPhoto = prefs[ACCOUNT_PHOTO]
        }
        // Silent "signed out" on startup is the failure mode that costs the most
        // debugging time, so say plainly what was on disk.
        Log.i(TAG, "session restored: cookieChars=${cookie.length} sapisid=${hasSapisid()} account=${accountName}")
    }

    /** Echo's login gate: SAPISID must be a real cookie key, not a substring. */
    fun hasSapisid(cookie: String = this.cookie): Boolean =
        cookie.split(";").any { it.substringBefore("=").trim() == "SAPISID" }

    fun storedAccount(): YtAccount? =
        accountName?.let { YtAccount(it, accountEmail, accountHandle, accountPhoto) }

    /**
     * Suspends until the cookie is on disk. A fire-and-forget write here is
     * lost: [login] returns, the screen calls restartApp, and Runtime.exit(0)
     * can kill the process before the IO coroutine runs its edit — the cookie
     * would silently vanish and the next launch reads "signed out".
     */
    suspend fun save(cookie: String, visitorData: String?, dataSyncId: String?, account: YtAccount) {
        this.cookie = cookie
        this.visitorData = visitorData
        this.dataSyncId = dataSyncId
        accountName = account.name
        accountEmail = account.email
        accountHandle = account.channelHandle
        accountPhoto = account.photoUrl
        dataStore?.edit { prefs ->
            prefs[COOKIE] = cookie
            setOrRemove(prefs, VISITOR_DATA, visitorData)
            setOrRemove(prefs, DATA_SYNC_ID, dataSyncId)
            prefs[ACCOUNT_NAME] = account.name
            setOrRemove(prefs, ACCOUNT_EMAIL, account.email)
            setOrRemove(prefs, ACCOUNT_HANDLE, account.channelHandle)
            setOrRemove(prefs, ACCOUNT_PHOTO, account.photoUrl)
        }
    }

    /** Suspends until cleared — same race as [save] on sign-out. */
    suspend fun clear() {
        cookie = ""
        visitorData = null
        dataSyncId = null
        accountName = null
        accountEmail = null
        accountHandle = null
        accountPhoto = null
        dataStore?.edit { prefs ->
            prefs.remove(COOKIE)
            prefs.remove(VISITOR_DATA)
            prefs.remove(DATA_SYNC_ID)
            prefs.remove(ACCOUNT_NAME)
            prefs.remove(ACCOUNT_EMAIL)
            prefs.remove(ACCOUNT_HANDLE)
            prefs.remove(ACCOUNT_PHOTO)
        }
    }

    private fun setOrRemove(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: String?
    ) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    private const val TAG = "NebulaAuth"
}
