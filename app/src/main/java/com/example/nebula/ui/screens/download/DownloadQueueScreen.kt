package com.example.nebula.ui.screens.download

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nebula.ui.components.ChunkyAction
import com.example.nebula.ui.components.ChunkyWindow
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.ui.components.AddToPlaylistDialog
import com.example.nebula.ui.components.SongMenuSheet
import com.example.nebula.ui.screens.shareSong
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.AddToPlaylistResult
import com.example.nebula.viewmodel.DownloadItem
import com.example.nebula.viewmodel.DownloadStatus
import com.example.nebula.viewmodel.DownloadViewModel
import com.example.nebula.viewmodel.PlaylistsViewModel
import com.example.nebula.viewmodel.PlayerViewModel
import com.example.nebula.viewmodel.previewSong
import kotlinx.coroutines.launch

/** Sentinel for the bulk button; a YouTube videoId is never "*". */
private const val CLEAR_ALL = "*"

/**
 * The offline download queue.
 *
 * Kept as a thin wrapper that owns the ViewModel plus a [DownloadQueueContent] that takes
 * plain values, so the layout previews without an Application behind it.
 */
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    playlistsVm: PlaylistsViewModel? = null,
    playerVm: PlayerViewModel? = null
) {
    val vm: DownloadViewModel = viewModel()
    val downloads by vm.downloads.collectAsState()
    val isPaused by vm.isPaused.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The picker needs the playlist list, which lives in PlaylistsViewModel. Resolved
    // optionally so @Preview and the queue screen can still compose without one — the
    // add button simply does nothing rather than crashing the screen.
    val playlists = playlistsVm?.playlists?.collectAsState()?.value.orEmpty()

    var addingVideoId by remember { mutableStateOf<String?>(null) }
    val addingSong = remember(addingVideoId, downloads) {
        downloads.firstOrNull { it.videoId == addingVideoId }?.song
    }

    // Which row's overflow is open. One at a time: the sheet closes before the playlist
    // picker opens, so nothing stacks.
    var menuVideoId by remember { mutableStateOf<String?>(null) }
    val menuSong = remember(menuVideoId, downloads) {
        downloads.firstOrNull { it.videoId == menuVideoId }?.song
    }

    DownloadQueueContent(
        downloads = downloads,
        isPaused = isPaused,
        onBack = onBack,
        onTogglePause = { if (isPaused) vm.resumeAll() else vm.pauseAll() },
        onRemove = vm::remove,
        onRemoveAll = vm::removeAll,
        onRequestAddToPlaylist = { menuVideoId = it }
    )

    if (menuSong != null) {
        // The same sheet search results and playlist rows use. Download is omitted: a row
        // here is already a download, and the sheet's Download/Remove label is decided by
        // completion state, which would read wrong for a queued or failed one. Remove
        // stays the visible button below, where the confirmation window is.
        SongMenuSheet(
            song = menuSong,
            isDownloaded = NebulaDownloads.isDownloaded(menuSong.videoId),
            onDismiss = { menuVideoId = null },
            onPlayNext = { playerVm?.playNext(menuSong); menuVideoId = null },
            onAddToQueue = { playerVm?.addToQueue(menuSong); menuVideoId = null },
            onAddToPlaylist = {
                menuVideoId = null
                addingVideoId = menuSong.videoId
            },
            onShare = {
                menuVideoId = null
                shareSong(context, menuSong)
            }
        )
    }

    if (addingSong != null && playlistsVm != null) {
        AddToPlaylistDialog(
            songs = listOf(addingSong),
            playlists = playlists,
            onAddToExisting = { id ->
                scope.launch {
                    val result = playlistsVm.addToPlaylist(id, listOf(addingSong))
                    addingVideoId = null
                    toastFor(context, result)
                }
            },
            onCreateWith = { name ->
                scope.launch {
                    playlistsVm.createWithSongs(name, listOf(addingSong))
                    addingVideoId = null
                    Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { addingVideoId = null }
        )
    }
}

/** Says what actually happened, including the "already there" case. */
private fun toastFor(context: android.content.Context, result: AddToPlaylistResult) {
    val message = when {
        result.added == 0 -> "Already in that playlist"
        result.duplicates == 0 -> "Added ${result.added}"
        else -> "Added ${result.added}, ${result.duplicates} already there"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun DownloadQueueContent(
    downloads: List<DownloadItem>,
    isPaused: Boolean,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
    onRequestAddToPlaylist: (String) -> Unit = {}
) {
    // Removing is destructive and Media3 gives no undo — the file and its cached data
    // are gone. One window for both buttons: null = closed, a videoId = that song,
    // CLEAR_ALL = the bulk button.
    var removing by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            ChunkyIconBtn(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                MaterialTheme.colorScheme.surface, 48.dp, 16.dp, onClick = onBack
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ChunkyBanner("Downloads${if (downloads.isEmpty()) "" else " (${downloads.size})"}")

        Spacer(modifier = Modifier.height(16.dp))

        // Controls live OUTSIDE the list and the list takes the leftover height. A
        // scrolling column would have let a long queue push these buttons off the bottom
        // of the screen, which is exactly where they are needed.
        if (downloads.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChunkyAction(
                    label = if (isPaused) "RESUME QUEUE" else "PAUSE QUEUE",
                    color = if (isPaused) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.tertiary,
                    icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    height = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePause
                )
                ChunkyAction(
                    label = "CLEAR ALL",
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.Delete,
                    height = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { removing = CLEAR_ALL }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Pausing stops every download at once — Media3 1.6.1 has no per-song pause. " +
                        "Partial downloads are kept and resume where they stopped.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (downloads.isEmpty()) {
            QueueEmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(downloads, key = { it.videoId }) { item ->
                    DownloadRow(
                        item = item,
                        onRequestRemove = { removing = it },
                        onRequestAddToPlaylist = onRequestAddToPlaylist
                    )
                }
            }
        }
    }

    if (removing != null) {
        val bulk = removing == CLEAR_ALL
        ChunkyWindow(
            title = if (bulk) "Clear all downloads?" else "Remove download?",
            onDismissRequest = { removing = null }
        ) {
            Text(
                if (bulk) "All saved songs and their cached data will be deleted."
                else "This song and its cached data will be deleted. You can download it again later.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChunkyAction(
                label = if (bulk) "Clear all" else "Remove",
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    if (bulk) onRemoveAll() else onRemove(removing!!)
                    removing = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChunkyAction(
                label = "Cancel",
                // colorScheme.background, not surface: the window itself is surface-coloured,
                // so a surface Cancel would disappear into it.
                color = MaterialTheme.colorScheme.background,
                onClick = { removing = null }
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onRequestRemove: (String) -> Unit,
    onRequestAddToPlaylist: (String) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    // Three accents, one job each: pink = in flight, teal = done, yellow = needs attention.
    // Idle states stay in plain ink so a still queue does not shout.
    val accent = when (item.status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, shape)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Artist only when it is actually known. A blank row reads as a bug;
                    // omitting the line reads as a song with no credited artist.
                    val artist = item.song?.artist.orEmpty()
                    if (artist.isNotBlank()) {
                        Text(
                            artist,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextGrey()
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .border(2.5.dp, BorderBlack, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            item.status.label(),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = accent
                        )
                        val detail = item.detail()
                        if (detail.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                // Only offered once the song's metadata is recoverable. Adding needs a real
                // SearchResult, and a request from before SongCodec carries a bare title
                // with no artist or artwork — a row built from that would insert a playlist
                // entry that can never be completed later.
                //
                // This replaced a dedicated "Add to playlist" button rather than sitting
                // beside it: the sheet already offers that action, so keeping both meant
                // two controls for one job. Remove stays a visible button on purpose —
                // it is destructive, and the confirmation window lives on this screen,
                // not in the sheet.
                if (item.song != null) {
                    ChunkyIconBtn(
                        Icons.Filled.MoreVert, "More options",
                        MaterialTheme.colorScheme.secondary, 38.dp, 12.dp
                    ) { onRequestAddToPlaylist(item.videoId) }
                }
                if (item.removable) {
                    ChunkyIconBtn(
                        Icons.Filled.Delete, "Remove download",
                        MaterialTheme.colorScheme.primary, 38.dp, 12.dp
                    ) { onRequestRemove(item.videoId) }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ChunkyProgressBar(item, accent)
        }
    }
}

/**
 * The queue bar. A [LinearProgressIndicator] would be the Material default and reads wrong
 * next to 3dp black borders, so this is a bordered track with a flat fill inside it.
 */
@Composable
private fun ChunkyProgressBar(item: DownloadItem, accent: Color) {
    val shape = RoundedCornerShape(10.dp)
    val known = item.percent >= 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(3.dp, BorderBlack, shape)
    ) {
        if (known) {
            // Skip the fill entirely at 0% rather than passing 0f to fillMaxWidth.
            if (item.percent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.percent / 100f)
                        .fillMaxHeight()
                        .background(accent)
                )
            }
        } else {
            // No total known yet (the stream sent no Content-Length), so a filled fraction
            // would be a guess. Hazard stripes across the whole track read as
            // "working, size unknown" instead of inventing a percentage.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gap = size.width / 22f
                var x = -gap
                while (x < size.width + gap) {
                    drawRect(accent, Offset(x, 0f), Size(gap, size.height))
                    x += gap * 2f
                }
            }
        }
    }
}

@Composable
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, shape)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .border(3.dp, BorderBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No downloads yet",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Save a song for offline and it will show up here.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Screen-title banner: offset shadow box behind a bordered, filled one. */
@Composable
private fun ChunkyBanner(text: String) {
    val shape = RoundedCornerShape(12.dp)
    Box {
        Box(
            modifier = Modifier
                .offset(x = 5.dp, y = 5.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.tertiary)
                .border(3.dp, BorderBlack, shape)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Square chunky button — same extrusion idiom as the player's controls. */
@Composable
private fun ChunkyIconBtn(
    icon: ImageVector,
    description: String,
    bg: Color,
    size: Dp,
    radius: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box {
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(bg)
                .border(3.dp, BorderBlack, shape)
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.REMOVING -> "Removing"
    DownloadStatus.RESTARTING -> "Restarting"
}

private fun DownloadItem.detail(): String = when {
    // A finished download's useful number is its size, not "100%".
    status == DownloadStatus.COMPLETED -> formatBytes(downloadedBytes)
    percent >= 0 -> "$percent%"
    downloadedBytes > 0 -> formatBytes(downloadedBytes)
    else -> ""
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

@Preview(showBackground = true, name = "Downloads Empty")
@Composable
private fun DownloadsEmptyPreview() {
    NebulaTheme(darkTheme = false, paletteId = "classic") {
        DownloadQueueContent(
            downloads = emptyList(),
            isPaused = false,
            onBack = {}, onTogglePause = {}, onRemove = {}, onRemoveAll = {}
        )
    }
}

@Preview(showBackground = true, name = "Downloads Busy")
@Composable
private fun DownloadsBusyPreview() {
    NebulaTheme(darkTheme = false, paletteId = "classic") {
        DownloadQueueContent(
            downloads = listOf(
                DownloadItem("a1", "Midnight City — M83", previewSong("a1"), DownloadStatus.DOWNLOADING, 62, 3_211_264, 5_242_880, true),
                DownloadItem("b2", "Somebody That I Used to Know", previewSong("b2"), DownloadStatus.COMPLETED, 100, 7_340_032, 7_340_032, true),
                // contentLength -1 = the stream sent no Content-Length
                DownloadItem("c3", "A Song With No Content-Length", previewSong("c3"), DownloadStatus.DOWNLOADING, DownloadItem.UNKNOWN_PERCENT, 913_408, -1L, true),
                DownloadItem("d4", "Broken Link", previewSong("d4"), DownloadStatus.FAILED, 18, 442_368, 2_400_000, true),
                DownloadItem("e5", "Waiting For Wi-Fi", previewSong("e5"), DownloadStatus.QUEUED, 0, 0, 4_000_000, true),
                DownloadItem("f6", "Going Away", previewSong("f6"), DownloadStatus.REMOVING, 40, 1_200_000, 3_000_000, false)
            ),
            isPaused = false,
            onBack = {}, onTogglePause = {}, onRemove = {}, onRemoveAll = {}
        )
    }
}

@Preview(showBackground = true, name = "Downloads AMOLED Paused")
@Composable
private fun DownloadsAmoledPreview() {
    NebulaTheme(darkTheme = true, paletteId = "amoled") {
        DownloadQueueContent(
            downloads = listOf(
                DownloadItem("a1", "Midnight City — M83", previewSong("a1"), DownloadStatus.PAUSED, 62, 3_211_264, 5_242_880, true),
                DownloadItem("b2", "Somebody That I Used to Know", previewSong("b2"), DownloadStatus.COMPLETED, 100, 7_340_032, 7_340_032, true)
            ),
            isPaused = true,
            onBack = {}, onTogglePause = {}, onRemove = {}, onRemoveAll = {}
        )
    }
}
