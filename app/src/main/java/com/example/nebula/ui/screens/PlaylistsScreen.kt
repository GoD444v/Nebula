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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.nebula.data.db.dao.PlaylistWithCount
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.NeonPink
import com.example.nebula.ui.theme.SunnyYellow
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.DownloadViewModel
import com.example.nebula.viewmodel.PlaylistsViewModel
import com.example.nebula.viewmodel.activeDownloadCount

/** Placeholder tile colour, cycling the same three accents the mockup used. */
private val tileAccents = listOf(NeonPink, SunnyYellow, MintTeal)

@Composable
/**
 * The playlist list.
 *
 * No card offers Delete or Rename. The built-in Downloaded playlist must not be
 * deletable at all, and `PlaylistDao.delete` enforces that in SQL with
 * `AND isSystem = 0` — so a Delete button on a system card would look enabled and then
 * silently do nothing. Omitting the control everywhere is the consistent answer, and it
 * spares the UI a branch on `isSystem` that the DAO already guards.
 *
 * [onOpenPlaylist] opens a playlist's detail screen, which is where Play lives.
 */
fun PlaylistsScreen(
    vm: PlaylistsViewModel = viewModel(),
    onOpenDownloads: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit = {}
) {
    val playlists by vm.playlists.collectAsState()
    val downloadsVm: DownloadViewModel = viewModel()
    val downloads by downloadsVm.downloads.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Banner + downloads button, so the list below is entirely playlists.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Playlists", color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Playlists", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Reachable at every list size, not just the empty state. Previously the only
            // create affordance was the "Create your first playlist" button, so once a
            // single playlist existed there was no way to make a second one.
            NewPlaylistButton(onClick = { showCreateDialog = true })

            Spacer(modifier = Modifier.width(12.dp))

            DownloadsIconButton(
                activeCount = activeDownloadCount(downloads),
                onClick = onOpenDownloads
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badge
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("${playlists.size} PLAYLISTS", color = BorderBlack, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(50.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "${playlists.size} PLAYLISTS",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { showCreateDialog = true }) {
                    Text("Create your first playlist")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(playlists) { index, playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        accent = tileAccents[index % tileAccents.size],
                        onClick = { onOpenPlaylist(playlist.playlist.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                vm.create(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * One row in the playlist list. Tapping it is the only affordance.
 *
 * There is deliberately no play button on the card. Two buttons on a row invites the
 * question "which one do I press", and a play button next to the name reads as "play this
 * one song" when it means "open this list". Opening the playlist and pressing Play there
 * is one unambiguous path, and the detail screen already has a Play button.
 */
@Composable
private fun PlaylistCard(
    playlist: PlaylistWithCount,
    accent: Color,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The first song's artwork, supplied by the query's subquery. The playlist
            // row's own thumbnailUrl column is never written by any call site, so
            // reading it here always yielded null and every card showed the lettered
            // accent tile. Accent tile now means only "this playlist has no songs".
            val thumbnail = playlist.coverThumbnailUrl
            if (!thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    // Crop, so a non-square cover fills the square rather than
                    // being letterboxed. Without this the same song looked right in
                    // search and wrong here.
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent)
                        .border(3.dp, BorderBlack, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.playlist.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${playlist.songCount} tracks",
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey()
                )
            }
        }
    }
}

/**
 * "New playlist" header button.
 *
 * Same offset-shadow / 3px border / 16dp corner anatomy as [DownloadsIconButton], so the
 * header reads as one row of controls rather than two competing styles.
 */
@Composable
private fun NewPlaylistButton(onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(SunnyYellow)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New playlist",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Header button into the download queue, pinned to the top-right of the banner.
 *
 * Built from the same primitives as [PlaylistCard] — offset shadow, 3px
 * [BorderBlack] border, 16dp corners — so it cannot drift from the VoxMusic look.
 * Deliberately NOT a full-width row: downloads are a queue with its own screen,
 * not a playlist, and a card-sized entry made it read as one.
 *
 * The badge counts downloads still in flight and is not composed at all when that
 * count is zero, rather than being drawn transparent.
 */
@Composable
fun DownloadsIconButton(activeCount: Int, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MintTeal)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Downloads",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )

            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(NeonPink)
                        .border(2.dp, BorderBlack, RoundedCornerShape(50.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "$activeCount",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = BorderBlack
                    )
                }
            }
        }
    }
}

private val BUTTON_SIZE = 52.dp
