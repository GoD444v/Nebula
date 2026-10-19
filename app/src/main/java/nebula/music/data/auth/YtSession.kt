package nebula.music.data.auth

import android.content.Context
import android.content.Intent
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The one place where Nebula's cookie meets innertubex
 * (Echo architecture, original code).
 *
 * innertubex signs SAPISIDHASH itself once cookie/visitorData/dataSyncId are
 * set, and skips auth when SAPISID is absent — so there is no header code on
 * our side. The app restarts on login/logout, so every tube built here always
 * matches the stored session; nothing refreshes mid-flight.
 */
object YtSession {
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    fun tubeOn(client: HttpClient): InnerTube = InnerTube(client).apply {
        cookie = YtCookieStore.cookie.takeIf { it.isNotEmpty() }
        visitorData = YtCookieStore.visitorData
        dataSyncId = YtCookieStore.dataSyncId
        useLoginForBrowse = YtCookieStore.hasSapisid()
    }

    /** Session tube on its own client. */
    fun tube(): InnerTube = tubeOn(httpClient())

    private fun tubeWith(cookie: String, visitorData: String?, dataSyncId: String?): InnerTube =
        InnerTube(httpClient()).apply {
            this.cookie = cookie
            this.visitorData = visitorData
            this.dataSyncId = dataSyncId
            useLoginForBrowse = true
        }

    /** Account behind the stored cookie, via accountMenu. Null when logged out or rejected. */
    suspend fun accountInfo(): YtAccount? =
        runCatching { parseAccount(tube().accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>()) }
            .getOrNull()

    internal suspend fun accountInfoOn(
        cookie: String,
        visitorData: String?,
        dataSyncId: String?
    ): YtAccount? = runCatching {
        parseAccount(tubeWith(cookie, visitorData, dataSyncId).accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>())
    }.getOrNull()

    /**
     * accountMenu → actions[0].openPopupAction.popup.multiPageMenuRenderer
     * .header.activeAccountHeaderRenderer → name/email/handle/photo.
     * Defensive at every level: anything missing means "not an account page".
     */
    internal fun parseAccount(root: JsonObject): YtAccount? {
        val popup = ((root.arr("actions")?.firstOrNull() as? JsonObject)
            ?.obj("openPopupAction"))?.obj("popup") ?: return null
        val header = popup.obj("multiPageMenuRenderer")?.obj("header") ?: return null
        val acct = header.obj("activeAccountHeaderRenderer") ?: return null
        val name = textOf(acct.obj("accountName"))
        if (name.isBlank()) return null
        val thumbs = acct.obj("accountPhoto")?.arr("thumbnails")?.mapNotNull {
            (it as? JsonObject)?.str("url")
        }
        return YtAccount(
            name = name,
            email = textOf(acct.obj("accountEmail")).takeIf { it.isNotBlank() },
            channelHandle = textOf(acct.obj("accountChannelHandle")).takeIf { it.isNotBlank() },
            photoUrl = thumbs?.lastOrNull()
        )
    }

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.arr(key: String) = get(key) as? JsonArray
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun runsText(container: JsonObject?): String {
        val runs = container?.arr("runs") ?: return ""
        return runs.joinToString("") { ((it as? JsonObject)?.str("text")) ?: "" }
    }

    private fun textOf(container: JsonObject?): String {
        if (container == null) return ""
        val runs = runsText(container)
        if (runs.isNotBlank()) return runs
        return container.str("simpleText") ?: ""
    }
}

/** Cookie-backed [YtAuth]. Validation goes through accountMenu: a cookie that
 * YouTube rejects never gets persisted. */
class YtCookieAuth : YtAuth {
    private val _isLoggedIn = MutableStateFlow(YtCookieStore.hasSapisid())
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentAccount = MutableStateFlow(YtCookieStore.storedAccount())
    override val currentAccount: StateFlow<YtAccount?> = _currentAccount.asStateFlow()

    override suspend fun login(cookie: String, visitorData: String?, dataSyncId: String?): Boolean {
        if (!YtCookieStore.hasSapisid(cookie)) return false
        val account = YtSession.accountInfoOn(cookie, visitorData, dataSyncId) ?: return false
        YtCookieStore.save(cookie, visitorData, dataSyncId, account)
        _isLoggedIn.value = true
        _currentAccount.value = account
        return true
    }

    override suspend fun logout() {
        YtCookieStore.clear()
        _isLoggedIn.value = false
        _currentAccount.value = null
    }
}

/**
 * Full restart so every InnerTube client picks up the new session.
 * Echo does the same after login/logout instead of refreshing mid-flight.
 */
fun restartApp(context: Context) {
    runCatching {
        val component = context.packageManager
            .getLaunchIntentForPackage(context.packageName)?.component
        context.startActivity(Intent.makeRestartActivityTask(component))
    }
    Runtime.getRuntime().exit(0)
}
