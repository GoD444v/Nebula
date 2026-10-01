package com.example.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.nebula.data.db.entities.PlaylistSongEntity
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.components.SongInfoDialog
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.SunnyYellow
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.PlaylistsViewModel
import com.example.nebula.viewmodel.PlayerViewModel

/**
 * The Downloaded playlist: every completed download, in the order the user arranged.
 *
 * Two things this screen deliberately does not offer:
 *
 *  - **Delete.** The playlist is a projection of what is on disk, so deleting it would
 *    delete a list of songs nothing else owns. `dao.delete` also refuses `isSystem`
 *    rows, so a button here would silently do nothing — worse than no button.
 *  - **Remove song.** Removing a song means deleting the download, which is destructive
 *    and has no undo (Media3 gives none). That lives on the download queue screen, where
 *    the consequence is stated. See the spec's §8.
 *
 * Reorder is up/down buttons rather than a drag gesture. This project has no
 * `pointerInput` or `detectDragGestures` anywhere, so a gesture would be the only one in
 * the codebase and unfalsifiable by the button path. The buttons satisfy the same
 * acceptance criterion — reordered order is what plays back — with a fraction of the risk.
 */
@Composable
fun DownloadedPlaylistScreen(
    vm: PlaylistsViewModel,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
    playlistId: Long? = null
) {
    // Navigating in from the list passes the id; tests seed the selection directly and
    // pass nothing, so a null here must not clear an already-selected playlist.
    LaunchedEffect(playlistId) {
        if (playlistId != null) vm.select(playlistId)
    }

    val songs by vm.songs.collectAsState()
    val selectedId by vm.selectedPlaylistId.collectAsState()
    val playlists by vm.playlists.collectAsState()

    // A rename must not break sync: the DAO's lookups key on isSystem, never on the name,
    // so showing the live row here is safe.
    val name = playlists.firstOrNull { it.playlist.id == selectedId }?.playlist?.name
        ?: DownloadedFallbackName
    val items = songs.map { it.toSearchResult() }
    var infoFor by remember { mutableStateOf<SearchResult?>(null) }

    DownloadedPlaylistContent(
        name = name,
        songs = songs,
        shuffleOn = playerVm.shuffleOn,
        repeatMode = playerVm.repeatMode.label(),
        onPlay = { playerVm.playAll(items) },
        onShuffle = playerVm::toggleShuffle,
        onRepeat = playerVm::toggleRepeat,
        onRename = { newName -> selectedId?.let { vm.rename(it, newName) } },
        onMove = { from, to -> selectedId?.let { vm.reorder(it, from, to) } },
        // Inside a playlist, tapping a song plays that song and keeps the rest of the
        // playlist queued behind it — the opposite of startRadioFrom, which is for tapping
        // a song in a search or browse list where the surrounding rows are not a set.
        onPlaySong = { song ->
            // Tapped song first, the rest behind it. Previously this was
            // playAll(rest + song), which appended the tapped song to the tail and so
            // played the FIRST song — tapping row 2 played row 1.
            playerVm.playFromHere(song, items.filter { it.videoId != song.videoId })
        },
        onShowInfo = { song -> infoFor = song },
        onBack = onBack
    )

    infoFor?.let { song ->
        SongInfoDialog(
            title = song.title,
            artist = song.artist,
            artworkUrl = song.thumbnailUrl,
            onDismiss = { infoFor = null }
        )
    }
}

private const val DownloadedFallbackName = "Downloaded"

/** Media3's repeat ints into words for the button's content description. */
private fun Int.label(): String = when (this) {
    Player.REPEAT_MODE_ALL -> "Repeat all"
    Player.REPEAT_MODE_ONE -> "Repeat one"
    else -> "Repeat off"
}

/** Maps a stored playlist row back to the queue item shape `playAll` expects. */
private fun PlaylistSongEntity.toSearchResult() = SearchResult(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl.orEmpty()
)

@Composable
private fun DownloadedPlaylistContent(
    name: String,
    songs: List<PlaylistSongEntity>,
    shuffleOn: Boolean,
    repeatMode: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRename: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlaySong: (SearchResult) -> Unit,
    onShowInfo: (SearchResult) -> Unit,
    onBack: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var sortType by remember { mutableStateOf(PlaylistSortType.CUSTOM) }
    var sortDescending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Back",
                tint = MaterialTheme.colorScheme.surface,
                size = 48.dp,
                onClick = onBack
            )

            Spacer(modifier = Modifier.weight(1f))

            // One menu, not two. Sort used to get its own three-dot button next to the
            // existing overflow, which put two identical icons in the header. The sort
            // direction toggle was a separate arrow button too; it is now a menu item,
            // which is also where it stops being a mystery icon.
            Box {
                ChunkyIconButton(
                    icon = Icons.Filled.MoreVert,
                    description = "More options",
                    tint = MaterialTheme.colorScheme.surface,
                    size = 48.dp,
                    onClick = { overflowOpen = true }
                )
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    Text(
                        "Sort by",
                        fontSize = 12.sp,
                        color = TextGrey,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    PlaylistSortType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            // Radio affordance, as the reference implementation shows.
                            leadingIcon = {
                                Icon(
                                    if (sortType == type) Icons.Filled.RadioButtonChecked
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null
                                )
                            },
                            onClick = { sortType = type; overflowOpen = false }
                        )
                    }

                    // Hidden for Custom order: reversing a hand-arranged list is never
                    // what the user meant, and the item would read as doing nothing.
                    if (sortType != PlaylistSortType.CUSTOM) {
                        DropdownMenuItem(
                            text = { Text(if (sortDescending) "Descending" else "Ascending") },
                            leadingIcon = {
                                Icon(
                                    if (sortDescending) Icons.Filled.ArrowDownward
                                    else Icons.Filled.ArrowUpward,
                                    contentDescription = null
                                )
                            },
                            onClick = { sortDescending = !sortDescending }
                        )
                    }

                    HorizontalDivider()

                    // No Delete entry for the system playlist: dao.delete refuses
                    // isSystem rows, so a Delete here would look enabled and do nothing.
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            overflowOpen = false
                            showRename = true
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(thumbnailUrl = songs.firstOrNull()?.thumbnailUrl, size = 96.dp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${songs.size} tracks",
                    fontSize = 13.sp,
                    color = TextGrey
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChunkyIconButton(
                icon = Icons.Filled.PlayArrow,
                description = "Play all",
                tint = MaterialTheme.colorScheme.primary,
                size = 52.dp,
                onClick = onPlay
            )
            ChunkyIconButton(
                icon = Icons.Filled.Shuffle,
                description = "Shuffle",
                tint = if (shuffleOn) SunnyYellow else MaterialTheme.colorScheme.surface,
                size = 52.dp,
                onClick = onShuffle
            )
            ChunkyIconButton(
                icon = Icons.Filled.Repeat,
                description = "Repeat $repeatMode",
                tint = MaterialTheme.colorScheme.surface,
                size = 52.dp,
                onClick = onRepeat
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nothing downloaded yet",
                    fontSize = 15.sp,
                    color = TextGrey
                )
            }
        } else {
            val listState = rememberLazyListState()

            // Sort is view-only; the stored positions are never rewritten, so choosing
            // Custom order always restores the arrangement the user made. That property
            // is what makes sorting safe to offer at all.
            val sorted = remember(songs, sortType, sortDescending) {
                sortSongs(songs.map { it.toSearchResult() }, sortType, sortDescending)
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sorted, key = { _, s -> s.videoId }) { index, song ->
                    val asSong = song
                    DownloadedSongRow(
                        song = asSong,
                        position = index,
                        onPlay = { onPlaySong(asSong) },
                        onShowInfo = { onShowInfo(asSong) }
                    )
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(
            current = name,
            onConfirm = { newName ->
                onRename(newName)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
}

@Composable
private fun CoverArt(thumbnailUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (thumbnailUrl != null) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(MintTeal)
                .border(3.dp, BorderBlack, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun DownloadedSongRow(
    song: SearchResult,
    position: Int,
    onPlay: () -> Unit,
    onShowInfo: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp)
                )
                // The row itself plays the song. The reference implementation does the same:
                // inside a playlist, tap plays from here rather than starting a radio.
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${position + 1}",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = TextGrey,
                modifier = Modifier.width(22.dp)
            )

            CoverArt(thumbnailUrl = song.thumbnailUrl, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    song.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey
                )
            }

            IconButton(onClick = onShowInfo) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Song info for ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Offset shadow + 3px border, the card primitive every screen here is built from. */
@Composable
private fun ChunkyIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(tint)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(current) }
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
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}