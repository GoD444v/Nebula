package nebula.music.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import nebula.music.data.LocalMusicRepository
import nebula.music.data.LocalSongScanSummary
import nebula.music.data.db.entities.LocalSong
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.SunnyYellow
import nebula.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.Player

private const val SORT_KEY = "local_sort_type"
private const val SORT_DESC_KEY = "local_sort_desc"

// The queue-scope key for this list. Local songs get their own, so a shuffle switched on
// here never follows you into a streaming playlist (or back).
private const val LocalScope = "local"

/**
 * Songs stored on this device, reachable from Playlists → Local.
 *
 * Deliberately screen-local state over a ViewModel: this is a list plus a search box, and
 * OnboardingScreen already reaches for a `remember { SearchRepository() }` the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSongsScreen(
    playerVm: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val repo = remember { LocalMusicRepository() }

    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<LocalSongScanSummary?>(null) }

    // Sort + scan filters, persisted so the choices survive a restart — same prefs file
    // the rest of the player uses.
    val prefs = remember { context.getSharedPreferences("nebula", Context.MODE_PRIVATE) }
    var sortType by remember {
        mutableStateOf(
            LocalSongSortType.valueOf(
                prefs.getString(SORT_KEY, LocalSongSortType.NAME.name)
                    ?: LocalSongSortType.NAME.name
            )
        )
    }
    var sortDescending by remember { mutableStateOf(prefs.getBoolean(SORT_DESC_KEY, false)) }
    // Sort + direction live in the header's 3-dot menu now, so the search bar has the whole
    // row to itself. Hoisted out of the row it used to live in, which is being deleted.
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Songs / Albums / Artists / Folders. Null means the tile landing is showing;
    // every open starts there. Albums and artists are derived from the columns
    // local_songs already carries, so this costs no new table.
    var browseMode by remember { mutableStateOf<LocalBrowseMode?>(null) }
    // Which album or artist is open, or null when showing the group list itself.
    var openGroup by remember { mutableStateOf<String?>(null) }
    // One snapshot of the mode for this pass of the composition — the delegated
    // var cannot smart-cast, and every layer below branches on it.
    val mode = browseMode

    fun persistFilters() {
        prefs.edit()
            .putString(SORT_KEY, sortType.name)
            .putBoolean(SORT_DESC_KEY, sortDescending)
            .apply()
    }

    // The permission decides whether MediaStore returns anything at all, and a denied
    // permission looks exactly like "no music on the phone". Track it so the empty state
    // can say which one it is instead of guessing.
    fun neededPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, neededPermission()) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Nothing ever called scanLocalMusic(), so the table was permanently empty and this
    // screen would have opened blank forever. Errors are surfaced instead of swallowed —
    // a SecurityException here used to render as "no songs on this device".
    suspend fun scan() {
        scanning = true
        error = null
        runCatching {
            repo.scanLocalMusic(context)
        }
            .onSuccess { summary = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        scanning = false
        songs = runCatching {
            if (query.isBlank()) repo.all(context) else repo.search(context, query)
        }.getOrDefault(emptyList())
    }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) scope.launch { scan() }
    }

    LaunchedEffect(Unit) {
        if (granted && repo.count(context) == 0) scan()
    }

    // Debounced so typing does not fire a query per keystroke.
    var searchJob: Job? = null
    LaunchedEffect(query) {
        searchJob?.cancel()
        searchJob = launch {
            delay(250)
            songs = if (query.isBlank()) repo.all(context) else repo.search(context, query)
        }
    }

    // Back peels one layer at a time: the open group first, then the mode back to
    // the tiles (and the search with it — the landing is always the full library),
    // then leaves the screen. The header arrow and every in-screen arrow share
    // this one path, so the button and the gesture never behave differently.
    fun peelBack() {
        when {
            openGroup != null -> openGroup = null
            browseMode != null -> {
                browseMode = null
                openGroup = null
                query = ""
            }
            else -> onBack()
        }
    }

    BackHandler { peelBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { peelBack() }) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Local",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${songs.size} SONGS",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Deliberately the same overflow as a playlist's header: the same chunky 3-dot
            // and the same menu, so Sort lives in the same place on every list. The search
            // field had been sharing its row with the sort pill and the direction toggle,
            // neither of which it has anything to do with. Hidden on the tile landing:
            // there is no list there for it to order.
            if (mode != null) Box {
                ChunkyIconButton(
                    icon = Icons.Filled.MoreVert,
                    description = "More options",
                    tint = MaterialTheme.colorScheme.surface,
                    size = 48.dp,
                    onClick = { sortMenuOpen = true }
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    // Pinned to `surface`, as on the playlist screen. A bare DropdownMenu
                    // takes Material's `surfaceContainer`, which no Vox palette defines,
                    // so the panel resolved to a colour nobody chose while the item text used
                    // the palette's `onSurface` — near-black on a dark panel, unreadable.
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "Sort by",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LocalSongSortType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(type.label, color = MaterialTheme.colorScheme.onSurface)
                            },
                            leadingIcon = {
                                Icon(
                                    if (sortType == type) Icons.Filled.RadioButtonChecked
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                sortType = type
                                sortMenuOpen = false
                                persistFilters()
                            }
                        )
                    }
                    // Direction stays open when flipped, as on the playlist screen: it is a
                    // mode you can keep adjusting, not a choice you are done with. Leaving it
                    // open is also the only way to see the result against the list behind it.
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (sortDescending) "Descending" else "Ascending",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (sortDescending) Icons.Filled.ArrowDownward
                                else Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            sortDescending = !sortDescending
                            persistFilters()
                        }
                    )
                }
            }
        }

        // Inside a mode: just the search bar. No extra arrow crowds the list —
        // the gesture and the header arrow are the way back to the tiles, and
        // they both peel one layer at a time. On the landing the search hides;
        // the tiles ARE the chooser.
        if (mode != null) {
            // A touch of air between the header and the input — at the user's ask
            // they sat flush, which read as one cluttered block.
            Spacer(modifier = Modifier.height(12.dp))

            LocalSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search ${mode.label.lowercase()}",
                keyboard = keyboard
            )

            // Halved at the user's ask (was 20 + 12): 10 + 6 still clears the
            // 4dp extrusion shadow under the search bar.
            Spacer(modifier = Modifier.height(10.dp))
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Play all / Shuffle / Repeat — the same three controls the playlist, liked and
        // browse screens carry, so local is not the one list you cannot shuffle. `visible`
        // (the searched + sorted list) is what plays, not the whole library, matching what
        // tapping a row does.
        //
        // Only in SONGS view: Albums and Artists are grouped headers, not a track list, so
        // "play all" there has no queue to build from.
        if (songs.isNotEmpty() && mode == LocalBrowseMode.SONGS) {
            // Exactly the list a row tap queues: the searched set in the shown order.
            val visible = sortLocalSongs(songs, sortType, sortDescending)
            val repeatMode = playerVm.repeatFor(LocalScope)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoxPlayAllButton(onClick = { playerVm.playLocalSongs(visible, 0, LocalScope) })
                ChunkyIconButton(
                    icon = Icons.Filled.Shuffle,
                    description = "Shuffle local songs",
                    tint = if (playerVm.shuffleFor(LocalScope)) SunnyYellow else MaterialTheme.colorScheme.surface,
                    size = 44.dp,
                    onClick = { playerVm.shufflePlayLocalSongs(visible, LocalScope) }
                )
                ChunkyIconButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    description = if (repeatMode != Player.REPEAT_MODE_OFF) "Repeat on" else "Repeat off",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) SunnyYellow else MaterialTheme.colorScheme.surface,
                    size = 44.dp,
                    onClick = { playerVm.toggleRepeat(LocalScope) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Scan summary, EchoMusic's "Last scan: N songs found, M removed". Shown only after a
        // scan has actually run, so it never claims a scan that did not happen.
        summary?.let {
            Text(
                "Last scan: ${it.scannedSongs} songs found, ${it.removedSongs} removed",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Pull down to rescan — same PullToRefreshBox HomeScreen uses, so no
        // Rescan button crowding the control row. Wraps everything so even the
        // empty state can refresh.
        PullToRefreshBox(
            isRefreshing = scanning,
            onRefresh = { scope.launch { scan() } },
            modifier = Modifier.fillMaxSize()
        ) {
        when {
            scanning -> LocalMessage("Scanning your device...")
            !granted -> LocalMessage(
                "Storage permission needed",
                detail = "Nebula cannot see your music until you allow it.",
                action = "Allow" to { askPermission.launch(neededPermission()) }
            )
            error != null -> LocalMessage(
                "Scan failed: $error",
                action = "Try again" to { scope.launch { scan() } }
            )
            songs.isEmpty() && query.isNotBlank() ->
                LocalMessage("No matching local songs")
            songs.isEmpty() -> LocalMessage(
                "No local songs found on this device",
                detail = "Pull down to rescan"
            )
            else -> {
                when {
                    // The tile landing: the ways in, each with its live count so a card
                    // answers "is anything in here" before you enter it.
                    mode == null -> LocalLanding(songs) { chosen ->
                        browseMode = chosen
                        openGroup = null
                    }
                    mode == LocalBrowseMode.SONGS -> {
                        // Sorted ONCE and used for both the display order and the queue.
                        // Sorting inline in itemsIndexed while handing the RAW list to
                        // playLocalSongs made `index` an offset into a different list, so tapping
                        // a row played whatever sat at that position in the unsorted one.
                        val visible = sortLocalSongs(songs, sortType, sortDescending)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            // The MiniPlayer floats over the bottom of the screen, so without
                            // this the last row was cut through the middle and could not be
                            // tapped. Matches the player's own height plus the nav bar.
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(visible) { index, song ->
                                LocalSongRow(
                                    song = song,
                                    isPlaying = playerVm.currentSongTitle == song.title &&
                                        playerVm.isPlayingLocal(),
                                    onClick = { playerVm.playLocalSongs(visible, index, LocalScope) }
                                )
                            }
                        }
                    }
                    else -> {
                        // Group modes. The groups come from the SAME list the Songs view
                        // shows (on the landing the search is always blank), and each
                        // group carries its own songs — opening one needs no query.
                        val groups = when (mode) {
                            LocalBrowseMode.ALBUMS -> localAlbums(songs)
                            LocalBrowseMode.ARTISTS -> localArtists(songs)
                            LocalBrowseMode.ALBUM_ARTISTS -> localAlbumArtists(songs)
                            else -> localFolders(songs)
                        }
                        val open = openGroup
                        if (open != null) {
                            val group = groups.firstOrNull { it.name == open }
                            if (group != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ChunkyIconButton(
                                        icon = Icons.Filled.ArrowBack,
                                        description = "Back to the ${mode.label} list",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        size = 44.dp,
                                        onClick = { peelBack() }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            group.name,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            group.subtitle,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding =
                                        androidx.compose.foundation.layout.PaddingValues(
                                            bottom = 96.dp
                                        ),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(group.songs) { index, song ->
                                        LocalSongRow(
                                            song = song,
                                            isPlaying = playerVm.currentSongTitle == song.title &&
                                                playerVm.isPlayingLocal(),
                                            onClick = {
                                                playerVm.playLocalSongs(group.songs, index, LocalScope)
                                            }
                                        )
                                    }
                                }
                            } else {
                                // The drill's name fell off the list (the search box outran it);
                                // render the list the way a closed drill would.
                                LocalGroupList(groups) { openGroup = it }
                            }
                        } else {
                            LocalGroupList(groups) { openGroup = it }
                        }
                    }
                }
            }
        }
        }
    }
}


/**
 * One album / artist / folder row. The group carries its own songs, so tapping
 * it just drills in — no screen change, no second query.
 */
@Composable
private fun LocalGroupRow(group: LocalGroup, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The same letter square the song rows use as their art fallback — the
            // one identity a group without artwork still has.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial(group.name),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    group.subtitle,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open ${group.name}",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LocalGroupList(groups: List<LocalGroup>, onOpenGroup: (String) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // The MiniPlayer floats over the bottom, same as the Songs list.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(groups) { _, group ->
            LocalGroupRow(group = group, onClick = { onOpenGroup(group.name) })
        }
    }
}

/**
 * The landing the Local screen opens on. Tiles pair up per row; one row more
 * than four entries stacks the odd card full-width. Each carries the live count
 * of the thing it leads to, so a tile answers "is anything in here" before you
 * enter it.
 */
@Composable
private fun LocalLanding(songs: List<LocalSong>, onOpenMode: (LocalBrowseMode) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            // The MiniPlayer floats over the bottom — the same clearance every
            // list on this screen keeps, so the lower shadows stay visible.
            .padding(bottom = 96.dp)
    ) {
        LocalBrowseMode.entries.chunked(2).forEach { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowModes.forEach { tileMode ->
                    val count = when (tileMode) {
                        LocalBrowseMode.SONGS -> songs.size
                        LocalBrowseMode.ALBUMS -> localAlbums(songs).size
                        LocalBrowseMode.ARTISTS -> localArtists(songs).size
                        LocalBrowseMode.FOLDERS -> localFolders(songs).size
                        LocalBrowseMode.ALBUM_ARTISTS -> localAlbumArtists(songs).size
                    }
                    LocalTile(
                        mode = tileMode,
                        count = count,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenMode(tileMode) }
                    )
                }
            }
        }
    }
}

/**
 * One landing card. Same extrusion, border and letter-square family as the rows
 * below; the icon square is SunnyYellow so the ways in read as buttons,
 * not data rows.
 */
@Composable
private fun LocalTile(
    mode: LocalBrowseMode,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SunnyYellow)
                    .border(3.dp, BorderBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tileIcon(mode),
                    contentDescription = mode.label,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                mode.label,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                tileCountLabel(mode, count),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun tileIcon(mode: LocalBrowseMode) = when (mode) {
    LocalBrowseMode.SONGS -> Icons.Filled.QueueMusic
    LocalBrowseMode.ALBUMS -> Icons.Filled.Album
    LocalBrowseMode.ARTISTS -> Icons.Filled.Person
    LocalBrowseMode.FOLDERS -> Icons.Filled.Folder
    LocalBrowseMode.ALBUM_ARTISTS -> Icons.Filled.Groups
}

private fun tileCountLabel(mode: LocalBrowseMode, count: Int): String = when (mode) {
    LocalBrowseMode.SONGS -> if (count == 1) "1 song" else "$count songs"
    LocalBrowseMode.ALBUMS -> if (count == 1) "1 album" else "$count albums"
    LocalBrowseMode.ARTISTS -> if (count == 1) "1 artist" else "$count artists"
    LocalBrowseMode.FOLDERS -> if (count == 1) "1 folder" else "$count folders"
    LocalBrowseMode.ALBUM_ARTISTS -> if (count == 1) "1 album artist" else "$count album artists"
}

/**
 * The chunky search field, extracted whole so the mode layer can show and hide
 * it. Same idiom as SearchScreen's input: transparent indicators, and the 3dp
 * border does the work — an OutlinedTextField would draw its own 1dp outline on
 * top, a double edge with mismatched corners.
 */
@Composable
private fun LocalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    keyboard: SoftwareKeyboardController?
) {
    Box {
        // 3D extrusion behind the input, the same idiom every card on this screen uses.
        // Without it the search bar sat flat while everything around it was chunky.
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(placeholder) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                // TextField defaults to a 4dp corner, so its container was drawn with
                // squarer corners than the 16dp border wrapped around it, which read as a
                // mismatched double edge. The shape has to match the border exactly.
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(3.dp, BorderBlack, RoundedCornerShape(16.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun LocalSongRow(
    song: LocalSong,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surface
                )
                .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art is a MediaStore content URI; albumId is often 0 for files with no
            // album tag, which resolves to nothing, hence the letter fallback.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUri.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = song.thumbnailUri,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { Text(initial(song.title)) },
                        error = { Text(initial(song.title)) }
                    )
                } else {
                    Text(initial(song.title))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    song.artist,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                formatDuration(song.duration),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LocalMessage(
    text: String,
    detail: String? = null,
    action: Pair<String, () -> Unit>? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (action != null) {
                Spacer(modifier = Modifier.height(14.dp))
                // PinkButton lives in OnboardingScreen.kt and is private there; this is
                // the same chunky idiom rather than pulling that file into the dependency.
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .offset(x = (-3).dp, y = (-3).dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                        .clickable(onClick = action.second)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        action.first,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun initial(title: String): String =
    title.firstOrNull()?.uppercase() ?: "?"
