package nebula.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import androidx.media3.common.Player
import nebula.music.data.DetailPage
import nebula.music.data.SearchRepository
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.auth.AccountLibrary
import nebula.music.data.models.SearchResult
import nebula.music.ui.components.AddToPlaylistDialog
import nebula.music.ui.components.DownloadAllDialog
import nebula.music.ui.components.SongMenuSheet
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.SunnyYellow
import nebula.music.ui.theme.TextGrey
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Album / playlist detail page: header art + Play All + tracklist.
 * browseId comes straight from the Home feed cards ("MPREb_..." albums,
 * "VL..." playlists — the exact id YouTube's browse endpoint expects).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    browseId: String,
    title: String,
    vm: PlayerViewModel,
    playlistsVm: PlaylistsViewModel? = null,
    onBack: () -> Unit,
    onBrowse: (String, String) -> Unit = { _, _ -> },
    onListenTogether: () -> Unit = {}
) {
    val repo = remember { SearchRepository() }
    var page by remember(browseId) { mutableStateOf<DetailPage?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One song's overflow menu at a time. null = closed.
    var menuFor by remember { mutableStateOf<SearchResult?>(null) }
    var showingInfoFor by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<SearchResult?>(null) }

    val playlists by (playlistsVm?.playlists?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    // Shuffle and repeat belong to THIS browse page. `browseScope` because `scope` above
    // is the coroutine scope the sheets launch in. Liked songs arrives here as its own
    // browse id, so it gets its own mode without a separate screen.
    val browseScope = "browse:$browseId"

    LaunchedEffect(browseId) {
        page = repo.getDetail(browseId)
    }

    // Sort lives here, not in the header: it reorders the LIST below, and the header only
    // owns the menu that changes it. Display-only — the page is re-fetched on every open,
    // exactly like sortLocalSongs on the local screen.
    var sortType by remember { mutableStateOf(PlaylistSortType.DEFAULT) }
    var descending by remember { mutableStateOf(false) }
    // The overflow lives up here with the Back button, in the top-right corner — the same
    // place the local playlist screen puts it. It used to sit beside the title inside the
    // header, which read as a different screen's button.
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmingDownload by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val pageContext = LocalContext.current
    val pageScope = rememberCoroutineScope()
    // The sorted list is computed up here, not inside the Column: the overflow's dialogs
    // sit outside the Column and need the same list the page is showing.
    val current = page
    val sortedTracks = remember(current?.tracks, sortType, descending) {
        sortRemoteTracks(current?.tracks.orEmpty(), sortType, descending)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // Back button — chunky Vox square with 3D shadow — and the overflow in the
        // opposite corner, so both live on one strip of the screen.
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                ChunkyIconButton(
                    icon = Icons.Filled.MoreVert,
                    description = "Options for ${title.ifBlank { browseId }}",
                    tint = MaterialTheme.colorScheme.surface,
                    size = 48.dp,
                    onClick = { overflowOpen = true }
                )
                HeaderMenu(
                    expanded = overflowOpen,
                    onDismiss = { overflowOpen = false },
                    onDownloadAll = { overflowOpen = false; confirmingDownload = true },
                    onRename = { overflowOpen = false; renaming = true },
                    sortType = sortType,
                    onSortType = { sortType = it },
                    descending = descending,
                    onToggleDescending = { descending = !descending },
                    canRename = browseId.startsWith("VL"),
                    onSaveLocalCopy = if (browseId.startsWith("VL") && playlistsVm != null) {
                        {
                            overflowOpen = false
                            val listId = browseId.removePrefix("VL")
                            pageScope.launch {
                                val msg = importPlaylistUrl(
                                    repo,
                                    playlistsVm,
                                    "https://music.youtube.com/playlist?list=$listId",
                                    title.ifBlank { "Imported playlist" }
                                )
                                Toast.makeText(pageContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        null
                    },
                    onDelete = if (browseId.startsWith("VL")) {
                        {
                            overflowOpen = false
                            confirmingDelete = true
                        }
                    } else {
                        null
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            current == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Loading...", color = BorderBlack)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Loading...",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header") {
                    DetailHeader(
                        page = current,
                        fallbackTitle = title,
                        vm = vm,
                        browseScope = browseScope,
                        tracks = sortedTracks,
                        browseId = browseId
                    )
                }
                if (sortedTracks.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nothing to show here yet",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = TextGrey()
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        sortedTracks,
                        key = { index, track -> "$index-${track.videoId}" }
                    ) { _, track ->
                        TrackRow(
                            track = track,
                            isPlaying = track.videoId == vm.currentVideoId,
                            onPlay = {
                                // Queue the rest of the album from this song. playFromHere
                                // puts the tapped track at the head and honours the
                                // hand-built lockout; playYouTubeSong did neither — it
                                // dropped the queue to this one song. Same browseScope as
                                // the header, so a shuffle set on this album survives a
                                // tap inside it.
                                vm.playFromHere(track, sortedTracks, browseScope)
                            },
                            onShowMenu = { menuFor = track }
                        )
                    }
                }
            }
        }

        // One sheet at a time: the menu closes before the playlist picker opens.
        menuFor?.let { song ->
            SongMenuSheet(
                song = song,
                isDownloaded = NebulaDownloads.isDownloaded(song.videoId),
                onDismiss = { menuFor = null },
                onPlayNext = { vm.playNext(song); menuFor = null },
                onAddToQueue = { vm.addToQueue(song); menuFor = null },
                onAddToPlaylist = { menuFor = null; addToPlaylistFor = song },
                onShare = {
                    menuFor = null
                    shareSong(context, song)
                },
                onDownload = { NebulaDownloads.enqueue(song); menuFor = null },
                onRemoveDownload = { NebulaDownloads.remove(song.videoId); menuFor = null },
                onViewArtist = {
                    menuFor = null
                    val bid = song.artistBrowseId
                    if (bid.isNotBlank()) onBrowse(bid, song.artist)
                    else scope.launch {
                        val found = nebula.music.data.SearchRepository().search(song.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                            .items.firstOrNull { it.browseId.isNotBlank() && it.title.equals(song.artist, ignoreCase = true) }
                            ?: nebula.music.data.SearchRepository().search(song.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                                .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { song.artist })
                    }
                },
                onViewAlbum = {
                    menuFor = null
                    val bid = song.albumBrowseId
                    if (bid.isNotBlank()) onBrowse(bid, song.title)
                    else scope.launch {
                        val found = nebula.music.data.SearchRepository().search(song.title, nebula.music.data.SearchRepository.SearchFilter.ALBUMS)
                            .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { song.title })
                    }
                },
                onRefetch = {
                    menuFor = null
                    nebula.music.data.SearchRepository.invalidateStream(song.videoId)
                    if (song.videoId == vm.currentVideoId) vm.retryPlayback()
                    android.widget.Toast.makeText(context, "Refetched", android.widget.Toast.LENGTH_SHORT).show()
                },
                onShowInfo = {
                    menuFor = null
                    showingInfoFor = song
                },
                onListenTogether = {
                    menuFor = null
                    onListenTogether()
                },
                onRemoveFromPlaylist = if (browseId.startsWith("VL") && song.setVideoId.isNotBlank()) {
                    {
                        menuFor = null
                        scope.launch {
                            val ok = repo.removeSongFromYtPlaylist(browseId, song.videoId, song.setVideoId)
                            if (ok) {
                                // Re-fetch: every remaining row's setVideoId shifts after a removal.
                                page = repo.getDetail(browseId)
                                Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "YouTube refused to remove it", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    null
                }
            )

        showingInfoFor?.let { s ->
            nebula.music.ui.components.SongInfoDialog(
                title = s.title,
                artist = s.artist,
                artworkUrl = s.thumbnailUrl,
                videoId = s.videoId,
                onDismiss = { showingInfoFor = null }
            )
        }

        // Confirmation before deleting the playlist from the account: this is a
        // real, irreversible write to the user's YouTube library.
        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("Delete playlist?") },
                text = { Text("\"${title.ifBlank { browseId }}\" will be removed from your YouTube account.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingDelete = false
                        scope.launch {
                            val ok = repo.deleteYtPlaylist(browseId)
                            if (ok) {
                                AccountLibrary.load()
                                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "YouTube refused to delete this playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
                }
            )
        }
        }

        addToPlaylistFor?.let { song ->
            if (playlistsVm != null) {
                AddToPlaylistDialog(
                    songs = listOf(song),
                    playlists = playlists,
                    onAddToExisting = { id ->
                        scope.launch {
                            val result = playlistsVm.addToPlaylist(id, listOf(song))
                            addToPlaylistFor = null
                            toastResult(context, result)
                        }
                    },
                    onCreateWith = { n ->
                        scope.launch {
                            playlistsVm.createWithSongs(n, listOf(song))
                            addToPlaylistFor = null
                        }
                    },
                    onDismiss = { addToPlaylistFor = null }
                )
            } else {
                addToPlaylistFor = null
            }
        }
    }

    // The overflow's two dialogs, here with the overflow itself. They used to live in
    // DetailHeader, which meant the button had to live there too — and the button is
    // supposed to be in the top-right corner, next to Back.
    if (confirmingDownload) {
        DownloadAllDialog(
            count = sortedTracks.count { !NebulaDownloads.isDownloaded(it.videoId) },
            onConfirm = {
                confirmingDownload = false
                val queued = NebulaDownloads.enqueueAll(sortedTracks)
                Toast.makeText(
                    pageContext,
                    if (queued == 0) "Already downloaded" else "Downloading $queued songs",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { confirmingDownload = false }
        )
    }

    if (renaming) {
        RenameDialog(
            current = page?.title?.ifBlank { title }.orEmpty(),
            // VL… is a playlist id, and only playlists can be renamed. Albums and the
            // liked-songs library page are not editable, so the item is hidden rather
            // than offered and silently failing.
            onConfirm = { newName ->
                renaming = false
                pageScope.launch {
                    val ok = runCatching {
                        repo.renameYtPlaylist(browseId, newName)
                    }.getOrDefault(false)
                    Toast.makeText(
                        pageContext,
                        if (ok) "Renamed" else "Couldn't rename that",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) page = page?.copy(title = newName)
                }
            },
            onDismiss = { renaming = false }
        )
    }
}

/** Big square art + title + subtitle + chunky yellow Play All / Shuffle / Repeat row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailHeader(
    page: DetailPage,
    fallbackTitle: String,
    vm: PlayerViewModel,
    browseScope: String,
    tracks: List<nebula.music.data.models.SearchResult>,
    browseId: String
) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth()) {
        // Artwork: side-extrusion 3D shadow + 3dp border
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .size(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (page.thumbnailUrl.isBlank()) {
                    // Liked songs has no artwork anywhere on YouTube — the page has no
                    // header renderer at all — so a letter off the title ("L", or worse
                    // "F" off the browse id) is the only thing that ever showed. This
                    // library page gets a Nebula identity tile instead.
                    if (fallbackTitle.equals("Liked songs", ignoreCase = true)) {
                        LikedSongsTile()
                    } else {
                        BigLetter(page.title.ifBlank { fallbackTitle })
                    }
                } else {
                    SubcomposeAsyncImage(
                        model = page.thumbnailUrl,
                        contentDescription = page.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { BigLetter(page.title.ifBlank { fallbackTitle }) },
                        error = { BigLetter(page.title.ifBlank { fallbackTitle }) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title and the header overflow share a row so the 3-dot sits top-right of the
            // name, the same place the local-songs screen puts it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.title.ifBlank { fallbackTitle },
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = page.subtitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = TextGrey(),
                        modifier = Modifier.basicMarquee()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (tracks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoxPlayAllButton(
                            onClick = { vm.playAll(tracks, browseScope) },
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoxPillButton(
                            icon = Icons.Filled.Shuffle,
                            accent = if (vm.shuffleFor(browseScope)) SunnyYellow else MaterialTheme.colorScheme.surface,
                            onClick = { vm.shufflePlayAll(tracks, browseScope) },
                            modifier = Modifier.weight(1f)
                        )
                        VoxPillButton(
                            icon = Icons.Filled.Share,
                            accent = MaterialTheme.colorScheme.surface,
                            onClick = { sharePlaylist(context, page.title.ifBlank { fallbackTitle }, browseId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Play all / Shuffle / Repeat stay visible above — repeating them in a menu too
        // would be two ways to do one thing on one screen. Everything that cannot be an
        // inline button lives in the overflow in the top-right corner, with Back.
    }
}

/**
 * The header overflow: Sort (identical shape to the local-songs menu), Rename,
 * Download all and Share. Play all / Shuffle / Repeat stay inline and visible.
 */
@Composable
private fun HeaderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownloadAll: () -> Unit,
    onRename: () -> Unit,
    sortType: PlaylistSortType,
    onSortType: (PlaylistSortType) -> Unit,
    descending: Boolean,
    onToggleDescending: () -> Unit,
    canRename: Boolean,
    // Null = this page is not a YouTube playlist, or the app has no way to save a copy
    // (no signed-in account to write into). Null means the item is absent, not inert.
    onSaveLocalCopy: (() -> Unit)? = null,
    // Null = not a playlist this account can delete (not VL..., or not owned). Null means
    // the item is absent, not inert.
    onDelete: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            "Sort by",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        PlaylistSortType.entries.forEach { type ->
            DropdownMenuItem(
                text = { Text(type.label, color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = {
                    Icon(
                        if (sortType == type) Icons.Filled.RadioButtonChecked
                        else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { onSortType(type) }
            )
        }
        // Direction stays open when flipped, as on the local screen: it is a mode you keep
        // adjusting, not a choice you are done with.
        DropdownMenuItem(
            text = {
                Text(
                    if (descending) "Descending" else "Ascending",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Icon(
                    if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            onClick = onToggleDescending
        )
        DropdownMenuItem(
            text = { Text("Download all", color = MaterialTheme.colorScheme.onSurface) },
            onClick = onDownloadAll
        )
        if (canRename) {
            DropdownMenuItem(
                text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                onClick = onRename
            )
        }
        // A playlist in the user's YouTube library is READ ONLY here: the app can read
        // YouTube Music's playlists but it has no write access to them, so songs cannot be
        // added to or removed from this one. This is the way out — copy it into Nebula,
        // where it becomes a normal local playlist you can edit however you like.
        if (onSaveLocalCopy != null) {
            DropdownMenuItem(
                text = { Text("Save a local copy", color = MaterialTheme.colorScheme.onSurface) },
                onClick = onSaveLocalCopy
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        }
    }
}

/** Opens the system share sheet with the playlist's public YouTube Music URL. */
private fun sharePlaylist(context: android.content.Context, title: String, browseId: String) {
    val listId = browseId.removePrefix("VL")
    val url = "https://music.youtube.com/playlist?list=$listId"
    context.startActivity(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$url")
        }
    )
}

/**
 * One track: small square thumb, marquee title/artist, overflow menu right.
 *
 * The pink play button that used to sit here was a second control for an action the row
 * itself already performs on tap, so it was removed at the human partner's request. Its
 * own tap target also meant a mis-tap started the wrong song.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: SearchResult,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShowMenu: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surface
                )
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onPlay)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl.isBlank()) {
                    TrackLetter(track.title)
                } else {
                    SubcomposeAsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { TrackLetter(track.title) },
                        error = { TrackLetter(track.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey(),
                    modifier = Modifier.basicMarquee()
                )
            }

            // Overflow menu. Same sheet the search and playlist rows use, so a song's menu
            // behaves identically wherever it appears. Its own IconButton means opening it
            // does not also trigger the row's play.
            IconButton(onClick = onShowMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options for ${track.title}",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Type a new name for the playlist. Same dialog shape as the local-playlist rename. */
@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Artwork stand-in for the Liked songs page, which carries none on YouTube.
 * A pink heart on the surface colour, in the same 3D card as everything else.
 */
@Composable
private fun LikedSongsTile() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
    }
}

/** Letter placeholder while artwork loads. */
@Composable
private fun BigLetter(title: String) {
    Text(
        title.firstOrNull()?.uppercase() ?: "?",
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun TrackLetter(title: String) {
    Text(
        title.firstOrNull()?.uppercase() ?: "?",
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}
