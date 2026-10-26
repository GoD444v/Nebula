package nebula.music.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import nebula.music.data.auth.YtAuth
import nebula.music.data.auth.YtCookieStore
import nebula.music.data.auth.restartApp
import android.widget.Toast
import nebula.music.ui.components.ChunkyAction
import nebula.music.ui.theme.OnboardingStore
import nebula.music.ui.components.ChunkyWindow
import nebula.music.ui.theme.ThemeStore
import nebula.music.ui.theme.paletteById

/**
 * YouTube login, Echo architecture (original code): sign in inside an
 * in-app WebView on music.youtube.com, lift the cookie from the cookie jar,
 * validate it through accountMenu, then restart so every InnerTube client
 * picks up the session. "Paste instead" covers WebViews that misbehave.
 */
@Composable
fun LoginScreen(
    auth: YtAuth,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal = paletteById(ThemeStore.paletteId)
    val signedIn by auth.isLoggedIn.collectAsState(initial = false)
    val account by auth.currentAccount.collectAsState(initial = null)

    var advanced by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    fun finishLogin(cookie: String, visitorData: String?, dataSyncId: String?) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = auth.login(cookie, visitorData, dataSyncId)
            if (ok) {
                // Genres off until the user changes them — tell them where.
                OnboardingStore.clearGenres(context)
                Toast.makeText(
                    context,
                    "Genres are off. Turn them back on any time in Settings \u203a Home & Library \u203a Music genres.",
                    Toast.LENGTH_LONG
                ).show()
                restartApp(context)
            } else {
                busy = false
                error = "YouTube rejected this session — sign in again or paste a fresh cookie."
            }
        }
    }

    if (signedIn) {
        // Signed in: account + sign out. Restart applies the cleared session.
        ChunkyWindow(title = "YouTube account", onDismissRequest = onClose, onBack = onClose) {
            Text(
                account?.name ?: "YouTube account",
                fontSize = 15.sp,
                color = pal.onSurface
            )
            account?.email?.let { Text(it, fontSize = 11.sp, color = pal.onSurface) }
            Spacer(modifier = Modifier.height(12.dp))
            ChunkyAction(
                label = "SIGN OUT",
                color = pal.pink,
                onClick = {
                    scope.launch {
                        // Genres off until the user changes them — tell them where.
                        OnboardingStore.clearGenres(context)
                        Toast.makeText(
                            context,
                            "Genres are off. Turn them back on any time in Settings \u203a Home & Library \u203a Music genres.",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.logout()
                        restartApp(context)
                    }
                }
            )
        }
        return
    }

    if (advanced) {
        // Manual paste fallback: raw cookie string, like Echo's advanced login.
        ChunkyWindow(title = "Paste cookie", onDismissRequest = onClose, onBack = { advanced = false }) {
            Text(
                "Paste your music.youtube.com cookie string.",
                fontSize = 13.sp,
                color = pal.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = paste,
                onValueChange = { paste = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("SAPISID=…; …") }
            )
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = pal.pink)
            }
            Spacer(modifier = Modifier.height(12.dp))
            ChunkyAction(
                label = if (busy) "CHECKING…" else "SAVE",
                color = pal.yellow,
                onClick = {
                    if (paste.isNotBlank()) finishLogin(paste.trim(), null, null)
                }
            )
        }
        return
    }

    // Primary: WebView sign-in. Cookie is lifted when music.youtube.com loads.
    var webView by remember { mutableStateOf<WebView?>(null) }
    var consumed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row {
            ChunkyAction(
                label = "✕ CLOSE",
                color = pal.surface,
                onClick = onClose,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.padding(5.dp))
            ChunkyAction(
                label = "PASTE INSTEAD",
                color = pal.bg,
                onClick = { advanced = true },
                modifier = Modifier.weight(1f)
            )
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = pal.pink)
        }
        Spacer(modifier = Modifier.height(8.dp))
        WebLoginView(
            onWebView = { webView = it },
            onCookie = { cookie, view ->
                if (consumed || !YtCookieStore.hasSapisid(cookie)) return@WebLoginView
                consumed = true
                // VISITOR_DATA / DATASYNC_ID ride on the page's JS config.
                view.evaluateJavascript(
                    "(function(){try{var c=(window.yt&&window.yt.config_)||{};" +
                        "return (c.VISITOR_DATA||'')+'|'+(c.DATASYNC_ID||'')}catch(e){return '|'}})()"
                ) { raw ->
                    val parts = raw.trim().removeSurrounding("\"").replace("\\/", "/").split("|")
                    val visitor = parts.getOrNull(0)?.takeIf { it.startsWith("Cg") }
                    val syncId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    finishLogin(cookie, visitor, syncId)
                }
            }
        )
    }
}

/** WebView that reports the music.youtube.com cookie the moment it lands. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLoginView(
    onWebView: (WebView) -> Unit,
    onCookie: (String, WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    removeAllCookies(null)
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url?.startsWith("https://music.youtube.com") == true) {
                            val cookie = CookieManager.getInstance().getCookie(url) ?: ""
                            if (cookie.isNotBlank()) onCookie(cookie, view)
                        }
                    }
                }
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com")
                onWebView(this)
            }
        }
    )
}
