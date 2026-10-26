package nebula.music.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * YouTube account seam, cookie-shaped (Echo architecture, original code).
 *
 * Login = a YouTube cookie with SAPISID, exactly how InnerTube clients
 * authenticate: the cookie rides on requests, SAPISID signs the
 * SAPISIDHASH Authorization header, and the account name comes from the
 * accountMenu endpoint. No Google SDK, no OAuth client IDs.
 *
 * Everything auth-shaped lives behind this interface. SearchRepository feeds
 * the cookie into innertubex (which supports it natively); screens only
 * render this state.
 */
interface YtAuth {
    /** True when the stored cookie contains SAPISID. */
    val isLoggedIn: StateFlow<Boolean>

    /** Account from accountMenu after login; null when logged out. */
    val currentAccount: StateFlow<YtAccount?>

    /**
     * Log in with a raw YouTube cookie string. Validates (SAPISID present +
     * accountMenu answers), persists cookie/visitorData/dataSyncId, and
     * returns true on success. Caller restarts the app so every InnerTube
     * client picks up the new session.
     */
    suspend fun login(cookie: String, visitorData: String?, dataSyncId: String?): Boolean

    /** Clears the cookie + account. Caller restarts the app. */
    suspend fun logout()
}

data class YtAccount(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val photoUrl: String?
)
