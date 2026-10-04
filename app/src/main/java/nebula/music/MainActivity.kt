package nebula.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import nebula.music.ui.components.MiniPlayer
import nebula.music.ui.screens.AlbumDetailScreen
import nebula.music.ui.screens.FullSheetPlayer
import nebula.music.ui.screens.download.DownloadQueueScreen
import nebula.music.ui.screens.DownloadedPlaylistScreen
import nebula.music.ui.screens.HomeScreen
import nebula.music.ui.screens.OnboardingScreen
import nebula.music.ui.screens.PlaylistsScreen
import nebula.music.ui.screens.SearchScreen
import nebula.music.ui.screens.SettingsScreen
import nebula.music.ui.screens.TabCustomizerScreen
import nebula.music.ui.screens.allAvailableTabs
import nebula.music.data.HomePrefs
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.NebulaTheme
import nebula.music.ui.theme.OnboardingStore
import nebula.music.ui.theme.TextBlack
import nebula.music.ui.theme.TextWhite
import nebula.music.ui.theme.ThemeStore
import nebula.music.viewmodel.HomeViewModel
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.PlayerViewModel

// DataStore instance for navigation tab order — persists across app restarts
private val android.content.Context.navDataStore: DataStore<Preferences> by preferencesDataStore(name = "nebula_nav")

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val mediaPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(applicationContext)
        OnboardingStore.init(applicationContext)
        HomePrefs.init(applicationContext)
        vm.attach(applicationContext)
        askNotificationPermission()
        askMediaPermission()
        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()
            // Same instance PlaylistsScreen resolves internally: both `viewModel()` calls
            // land in the Activity's ViewModelStore, so the list and the detail screen
            // share one selection instead of each holding its own.
            val playlistsVm: PlaylistsViewModel = viewModel()
            // Same Activity-scoped instance HomeScreen resolves internally — the
            // onboarding gate's loadPersonalized lands on the feed Home shows.
            val homeVm: HomeViewModel = viewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            var screen by remember { mutableStateOf("home") }
            var showSheet by remember { mutableStateOf(false) }
            var showTabCustomizer by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(!OnboardingStore.isDone) }
            // "downloads" is intentionally absent: it is no longer a nav tab —
            // it lives inside Playlists now (PlaylistsScreen's Downloads card).
            val defaultTabs = listOf("home", "search", "playlists", "settings")
            var activeTabs by remember { mutableStateOf(defaultTabs) }
            // Album/playlist detail page — lives inside the Home tab
            var detailId by remember { mutableStateOf<String?>(null) }
            var detailTitle by remember { mutableStateOf("") }
            // Which local playlist the detail screen is showing. Separate from detailId,
            // which is a YouTube browseId for AlbumDetailScreen — the two id spaces are
            // unrelated, so sharing one variable would invite a Long/String mixup.
            var playlistId by remember { mutableStateOf<Long?>(null) }

            // Read saved tab order from DataStore on first composition
            LaunchedEffect(Unit) {
                val prefs = context.navDataStore.data.first()
                val saved = prefs[stringPreferencesKey("tab_order")]
                    ?.split(",")
                    // Old installs persisted "downloads"; drop it so the removed
                    // tab doesn't resurrect from the saved order.
                    ?.filter { it.isNotBlank() && it != "downloads" }
                if (!saved.isNullOrEmpty()) {
                    activeTabs = saved
                }
            }

            // Playlist seeds for personalized home sections. One upgrade load per
            // playlist-set change: songsOf is suspend, so this lives in an effect.
            // Guarded by lastSeeds so adding songs behind the current seeds never
            // refires a feed reload.
            val playlistState by playlistsVm.playlists.collectAsState()
            var lastSeeds by remember {
                mutableStateOf<List<Pair<String, List<String>>>>(emptyList())
            }
            LaunchedEffect(playlistState) {
                if (playlistState.isEmpty()) return@LaunchedEffect
                val seeds = playlistState.take(3).mapNotNull { e ->
                    val vids = playlistsVm.songsOf(e.playlist.id)
                        .take(2).map { it.videoId }.filter { it.isNotBlank() }
                    if (vids.isEmpty()) null else e.playlist.name to vids
                }
                if (seeds.isNotEmpty() && seeds != lastSeeds) {
                    lastSeeds = seeds
                    homeVm.loadPersonalized(seeds)
                }
            }

            // First launch: genre picker gates everything, same early-return
            // pattern as the tab customizer below. Back exits — the user can
            // onboard on next launch; nothing half-persisted.
            if (showOnboarding) {
                BackHandler { (context as? ComponentActivity)?.finish() }
                NebulaTheme {
                    // Pages persist their own data; completion just reveals home.
                    OnboardingScreen(
                        onComplete = {
                            showOnboarding = false
                            homeVm.loadPersonalized()
                        }
                    )
                }
                return@setContent
            }

            if (showTabCustomizer) {
                // This branch returns before the BackHandler below is ever composed,
                // so nothing consumed the system back key and the Activity finished —
                // dumping the user on the launcher. Register one here instead.
                BackHandler { showTabCustomizer = false }
                TabCustomizerScreen(
                    activeTabs = activeTabs,
                    onSave = { tabs ->
                        activeTabs = tabs
                        showTabCustomizer = false
                        // If the current screen was hidden, go to the first visible tab
                        if (screen !in tabs) {
                            screen = tabs.firstOrNull() ?: "home"
                        }
                        // Persist tab order to DataStore
                        scope.launch {
                            context.navDataStore.edit { prefs ->
                                prefs[stringPreferencesKey("tab_order")] = tabs.joinToString(",")
                            }
                        }
                    },
                    onBack = { showTabCustomizer = false }
                )
                return@setContent
            }

            // There was NO BackHandler, so system back finished the activity and dumped
            // the user on the launcher. Back now: close the player sheet first, then walk
            // any tab back to Home, then close an open detail page. Only plain Home exits.
            BackHandler(enabled = showSheet || screen != "home" || detailId != null || playlistId != null) {
                when {
                    showSheet -> showSheet = false
                    // A playlist detail screen is reached from the Playlists tab, so back
                    // returns there rather than to Home.
                    screen == "playlist" -> { playlistId = null; screen = "playlists" }
                    // Downloads is entered from Playlists, so back goes there.
                    screen == "downloads" -> screen = "playlists"
                    screen != "home" -> screen = "home"
                    else -> { detailId = null; detailTitle = "" }
                }
            }
            NebulaTheme {
                Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                // edge-to-edge: without this every banner ("Keep Listening",
                                // "Search", "Playlists", "Settings") sat under the status bar
                                // clock / notification panel. Pushes all screen content clear.
                                .statusBarsPadding()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                // ponytail: VoxMusic/Echo concept (directional slide + fade), original code
                                AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        val order = listOf("home", "search", "playlists", "playlist", "downloads", "settings")
                                        val dir = if (order.indexOf(targetState) >= order.indexOf(initialState)) 1 else -1
                                        (slideInHorizontally(animationSpec = tween(300, easing = EaseOutCubic)) { it * dir / 2 } +
                                            fadeIn(animationSpec = tween(200))) togetherWith
                                            (slideOutHorizontally(animationSpec = tween(300, easing = EaseOutCubic)) { -it * dir / 2 } +
                                                fadeOut(animationSpec = tween(200)))
                                    },
                                    label = "tabswitch"
                                ) { target ->
                                    when (target) {
                                        "home"      -> {
                                            val dId = detailId
                                            if (dId != null) {
                                                AlbumDetailScreen(
                                                    browseId = dId,
                                                    title = detailTitle,
                                                    vm = vm,
                                                    // So the per-track menu's "Add to playlist"
                                                    // has a playlist list to write into.
                                                    playlistsVm = playlistsVm,
                                                    onBack = { detailId = null }
                                                )
                                            } else {
                                                HomeScreen(
                                                    vm,
                                                    playlistsVm = playlistsVm,
                                                    onBrowseClick = { id, pageTitle ->
                                                        detailId = id
                                                        detailTitle = pageTitle
                                                    },
                                                    // Same route the Playlists tab uses, so a
                                                    // playlist opened from Home behaves
                                                    // identically to one opened from the list.
                                                    onOpenPlaylist = { id ->
                                                        playlistId = id
                                                        screen = "playlist"
                                                    }
                                                )
                                            }
                                        }
                                        // Playing from search must not navigate. It used to set
                                         // screen = "home" on play, so tapping a result teleported the
                                         // user out of the results they were reading. The MiniPlayer
                                         // is already visible from every tab, so there is nothing to gain.
                                         // Playing from search opens the full-screen player rather than
                                         // navigating to a tab: the MiniPlayer is the player surface here, and
                                         // switching to Home threw away the results the user was reading.
                                         "search"    -> SearchScreen(
                                            vm = vm,
                                            onPlayDone = { showSheet = true },
                                            playlistsVm = playlistsVm
                                        )
                                        // Downloads is reached from the Playlists
                                        // card, not the nav bar — the route stays.
                                        "playlists" -> PlaylistsScreen(
                                            onOpenDownloads = { screen = "downloads" },
                                            onOpenPlaylist = { id ->
                                                playlistId = id
                                                screen = "playlist"
                                            }
                                        )
                                        "playlist" -> DownloadedPlaylistScreen(
                                            vm = playlistsVm,
                                            playerVm = vm,
                                            onBack = { playlistId = null; screen = "playlists" },
                                            playlistId = playlistId
                                        )
                                        "downloads" -> DownloadQueueScreen(
                                            onBack = { screen = "playlists" },
                                            playlistsVm = playlistsVm,
                                            // So the row overflow can reach queue actions. The
                                            // queue's own DownloadViewModel deliberately knows
                                            // nothing about playback.
                                            playerVm = vm
                                        )
                                        else        -> SettingsScreen(
                                            onCustomizeTabs = { showTabCustomizer = true },
                                            playlistsVm = playlistsVm
                                        )
                                    }
                                }
                            }
                            if (vm.currentVideoId.isNotBlank()) {
                                MiniPlayer(vm = vm, onOpen = { showSheet = true })
                            }
                            AppNavBar(
                                current = screen,
                                activeTabs = activeTabs,
                                onPick = { screen = it }
                            )
                        }
                        if (showSheet) {
                            FullSheetPlayer(
                                vm = vm,
                                onClose = { showSheet = false },
                                playlistsVm = playlistsVm
                            )
                        }
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            mediaPermission.launch(permission)
        }
    }

    override fun onDestroy() {
        vm.release()
        super.onDestroy()
    }
}

@Composable
private fun AppNavBar(current: String, activeTabs: List<String>, onPick: (String) -> Unit) {
    // ponytail: outer padding gives room for the side-extrusion 3D shadow (offset 4,4)
    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
        // 3D shadow box — offset down+right behind the main bar
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(20.dp))
                // The 3D shadow, read from the theme like every other card's.
                // Hardcoding AmoledBlack pinned a black shadow under a cream bar on
                // every palette, which is what made the bar look unthemed.
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            activeTabs.forEach { tabId ->
                val tab = allAvailableTabs.find { it.id == tabId }
                if (tab != null) {
                    NavButton(tab.label, tab.icon, current == tab.id) { onPick(tab.id) }
                }
            }
        }
    }
}

// ponytail: VoxMusic pattern — bare icon unselected, color pill + label selected
@Composable
private fun NavButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Box(contentAlignment = Alignment.Center) {
            // 3D shadow behind pill
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(label.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            // Active pill = the palette's Navigation colour (colorScheme.primaryContainer),
            // so it can differ from the pink accent. Label flips black/white for readability.
            val navColor = MaterialTheme.colorScheme.primaryContainer
            val onNav = if (navColor.luminance() > 0.5f) TextBlack else TextWhite
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(navColor)
                    .border(2.5.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = label, tint = onNav, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(label.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = onNav)
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        }
    }
}
