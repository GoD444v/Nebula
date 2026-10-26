package nebula.music.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.models.SearchResult
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.SunnyYellow
import kotlinx.coroutines.launch
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.PlayerViewModel

/**
 * The playback queue, as a sheet over whatever is showing.
 *
 * Shows `queueList` only — the real, ordered playback queue. `upNext` is deliberately NOT
 * here: it is a radio-suggestion list that gets consumed and appended into the queue as
 * playback advances, so listing it beside the queue would make the header count a lie. It
 * keeps its existing home in NowPlayingScreen's "Up Next" row.
 *
 * Rows key by INDEX, not videoId. A queue can legitimately hold the same song twice (radio
 * appends, repeated playlists, Play next then Add to queue on one song), and a videoId key
 * makes Compose throw on duplicate keys.
 */
// ModalBottomSheet is still @ExperimentalMaterial3Api in Material3 1.3.1 (Compose BOM
// 2024.12.01), contrary to the plan's expectation that it had graduated. The opt-in is
// scoped to this one composable rather than the file so a future Material3 bump that
// stabilises it turns into a removable annotation, not a file-wide one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit, onBrowse: (String, String) -> Unit = { _, _ -> }, onListenTogether: () -> Unit = {}, playlistsVm: PlaylistsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Material's default surfaceContainer is not defined by the Vox palettes — the
        // exact unthemed-background problem that made SongMenuSheet abandon its
        // ModalBottomSheet. Naming the colour explicitly is what makes this sheet usable
        // with the Vox design system.
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { ChunkyDragHandle() },
        content = { QueueSheetContent(vm = vm, onDismiss = onDismiss, onBrowse = onBrowse, onListenTogether = onListenTogether, playlistsVm = playlistsVm) }
    )
}

@Composable
private fun ChunkyDragHandle() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun QueueSheetContent(vm: PlayerViewModel, onDismiss: () -> Unit, onBrowse: (String, String) -> Unit = { _, _ -> }, onListenTogether: () -> Unit = {}, playlistsVm: PlaylistsViewModel) {
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Scrolls to the playing row on open and on every advance, so the sheet is never
    // showing a stale position after the queue moves under it.
    LaunchedEffect(vm.currentQueueIndex) {
        if (vm.currentQueueIndex >= 0) listState.animateScrollToItem(vm.currentQueueIndex)
    }

    // Save-queue flow: the dialog is windowed, so a plain boolean gate is enough — no
    // sheet navigation, same AddToPlaylistDialog the song menus already use.
    var saving by remember { mutableStateOf(false) }
    val playlistList by playlistsVm.playlists.collectAsState()

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Up Next",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${vm.queueList.size} song${if (vm.queueList.size == 1) "" else "s"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
        if (vm.queueList.isNotEmpty()) {
            SaveQueueButton(onClick = { saving = true })
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    if (vm.queueList.isEmpty()) {
        Text(
            "Nothing queued",
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }

    // Which row's overflow is open, by index. Read into a local BEFORE clearing it: the
    // remove callback nulls this out first, so reading it afterwards passes null and the
    // removal silently no-ops.
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val menuSong = menuFor?.let { vm.queueList.getOrNull(it) }
    var showingInfoFor by remember { mutableStateOf<SearchResult?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(vm.queueList, key = { index, _ -> index }) { index, song ->
            QueueRow(
                song = song,
                isCurrent = index == vm.currentQueueIndex,
                isPlaying = vm.isPlaying,
                onClick = { vm.playAtQueueIndex(index) },
                onMenu = { menuFor = index }
            )
        }
    }

    if (saving) {
        AddToPlaylistDialog(
            songs = vm.queueList,
            playlists = playlistList,
            onAddToExisting = { id ->
                scope.launch {
                    val result = playlistsVm.addToPlaylist(id, vm.queueList)
                    nebula.music.ui.screens.toastResult(context, result)
                    saving = false
                }
            },
            onCreateWith = { name ->
                scope.launch {
                    playlistsVm.createWithSongs(name, vm.queueList)
                    android.widget.Toast.makeText(context, "Playlist saved", android.widget.Toast.LENGTH_SHORT).show()
                    saving = false
                }
            },
            onDismiss = { saving = false }
        )
    }

    if (menuSong != null) {
        SongMenuSheet(
            song = menuSong,
            isDownloaded = NebulaDownloads.isDownloaded(menuSong.videoId),
            onDismiss = { menuFor = null },
            onPlayNext = { vm.playNext(menuSong); menuFor = null },
            onAddToQueue = { vm.addToQueue(menuSong); menuFor = null },
            onDownload = { NebulaDownloads.enqueue(menuSong); menuFor = null },
            onRemoveDownload = {
                NebulaDownloads.remove(menuSong.videoId)
                menuFor = null
            },
            onRemoveFromQueue = {
                val target = menuFor
                menuFor = null
                if (target != null) vm.removeFromQueue(target)
            },
            onViewArtist = {
                menuFor = null
                val bid = menuSong.artistBrowseId
                if (bid.isNotBlank()) {
                    onBrowse(bid, menuSong.artist)
                } else {
                    scope.launch {
                        val found = nebula.music.data.SearchRepository().search(menuSong.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                            .items.firstOrNull { it.browseId.isNotBlank() && it.title.equals(menuSong.artist, ignoreCase = true) }
                            ?: nebula.music.data.SearchRepository().search(menuSong.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                                .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { menuSong.artist })
                    }
                }
            },
            onViewAlbum = {
                menuFor = null
                val bid = menuSong.albumBrowseId
                if (bid.isNotBlank()) {
                    onBrowse(bid, menuSong.title)
                } else {
                    scope.launch {
                        val found = nebula.music.data.SearchRepository().search(menuSong.title, nebula.music.data.SearchRepository.SearchFilter.ALBUMS)
                            .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { menuSong.title })
                    }
                }
            },
            onRefetch = {
                menuFor = null
                nebula.music.data.SearchRepository.invalidateStream(menuSong.videoId)
                if (menuSong.videoId == vm.currentVideoId) vm.retryPlayback()
                android.widget.Toast.makeText(context, "Refetched", android.widget.Toast.LENGTH_SHORT).show()
            },
            onShowInfo = {
                menuFor = null
                showingInfoFor = menuSong
            }
        )

        showingInfoFor?.let { song ->
            SongInfoDialog(
                title = song.title,
                artist = song.artist,
                artworkUrl = song.thumbnailUrl,
                videoId = song.videoId,
                onDismiss = { showingInfoFor = null }
            )
        }
    }
}

@Composable
private fun QueueRow(
    song: SearchResult,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MintTeal)
                .border(3.dp, BorderBlack, RoundedCornerShape(10.dp))
        ) {
            SubcomposeAsyncImage(
                model = song.thumbnailUrl.takeIf { it.isNotBlank() },
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { QueueLetter(song.title) },
                error = { QueueLetter(song.title) }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                song.artist.ifBlank { "Unknown artist" },
                fontSize = 12.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Queue options",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QueueLetter(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            title.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Compact save affordance in the "Up Next" header, same extruded idiom as BackRow's buttons. */
@Composable
private fun SaveQueueButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box {
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .size(36.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(SunnyYellow)
                .border(3.dp, BorderBlack, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = "Save queue to playlist",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}