package com.example.nebula.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import coil3.compose.SubcomposeAsyncImage
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.ui.components.ImmersiveMode
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.ui.theme.NeonPink
import com.example.nebula.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.map

// ponytail: scrollable sheet; queue taps replay (playAt arrives with playlists)
@androidx.media3.common.util.UnstableApi
@Composable
fun FullSheetPlayer(vm: PlayerViewModel, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsFull by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // Without these the close button sat under the status bar clock and
            // the queue list sat under the gesture bar.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            ChunkyBtn(Icons.Filled.KeyboardArrowDown, "Close", MaterialTheme.colorScheme.surface, 48.dp, onClose)
        }

        Spacer(modifier = Modifier.height(12.dp))

        ArtBox(280.dp, vm.currentSongTitle, vm.currentVideoId)

        Spacer(modifier = Modifier.height(16.dp))

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(vm.currentSongTitle, color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    vm.currentSongTitle,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (vm.isLoading) {
            Text("Loading...", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        } else if (vm.currentArtist.isNotBlank()) {
            Text(vm.currentArtist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val duration = vm.durationMs.coerceAtLeast(1L)
        Slider(
            value = vm.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(vm.positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(fmtTime(vm.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChunkyBtn(
                Icons.Filled.Shuffle, "Shuffle",
                if (vm.shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, 52.dp,
                onClick = { vm.toggleShuffle() }
            )
            ChunkyBtn(Icons.Filled.SkipPrevious, "Previous", MaterialTheme.colorScheme.surface, 56.dp, onClick = { vm.previous() })
            ChunkyBtn(
                if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play",
                MaterialTheme.colorScheme.primary, 72.dp, onClick = { vm.togglePlay() }
            )
            ChunkyBtn(Icons.Filled.SkipNext, "Next", MaterialTheme.colorScheme.surface, 56.dp, onClick = { vm.next() })
            ChunkyBtn(
                if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Repeat",
                if (vm.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                52.dp,
                onClick = { vm.toggleRepeat() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Radio — Echo's one-tap radio: fresh related songs replace
        // everything after the current one.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Start Radio", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { vm.startRadio() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vm.isLoadingRadio) {
                    Text(
                        "Finding songs...",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Start Radio", tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Start Radio",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics — one tap opens the panel. Full screen is a button INSIDE the
        // panel (LyricsScreen's onFullScreen), so this card is a single target
        // and needs no second affordance of its own.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Lyrics", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { showLyrics = !showLyrics }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showLyrics) "Hide Lyrics" else "Lyrics",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showLyrics) {
            Spacer(modifier = Modifier.height(8.dp))
            LyricsScreen(vm = vm, onFullScreen = { showLyricsFull = true })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save offline. Media3 does the work in the background via
        // NebulaDownloadService; this only enqueues and reads back the state so
        // the card can say "Downloaded" instead of offering the action again.
        val currentDownload by NebulaDownloads.downloads
            .map { it[vm.currentVideoId] }
            .collectAsState(initial = null)
        val downloadState = currentDownload?.state
        val alreadySaved = downloadState == Download.STATE_COMPLETED

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    if (alreadySaved) "Downloaded" else "Download",
                    color = BorderBlack
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable(enabled = !alreadySaved && vm.currentVideoId.isNotBlank()) {
                        NebulaDownloads.enqueue(vm.currentVideoId, vm.currentSongTitle)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (alreadySaved) Icons.Filled.Check else Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        alreadySaved -> "Downloaded"
                        downloadState == Download.STATE_DOWNLOADING ->
                            "Downloading… ${currentDownload?.percentDownloaded?.toInt() ?: 0}%"
                        downloadState == Download.STATE_QUEUED -> "Queued"
                        downloadState == Download.STATE_FAILED -> "Failed — tap to retry"
                        else -> "Download"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Fullscreen lyrics: edge-to-edge dialog (under the status bar too),
        // same panel with no height caps. Back closes the dialog first.
        if (showLyricsFull) {
            BackHandler { showLyricsFull = false }
            Dialog(
                onDismissRequest = { showLyricsFull = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                // The dialog owns its own window, so the bars have to be hidden
                // on that window, not just the Activity's. The status/nav
                // padding below collapses to 0 once they are.
                ImmersiveMode(enabled = showLyricsFull)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        ChunkyBtn(Icons.Filled.KeyboardArrowDown, "Close", MaterialTheme.colorScheme.surface, 48.dp, onClick = { showLyricsFull = false })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LyricsScreen(vm = vm, expanded = true)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Queue (${vm.queueList.size})", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { showQueue = !showQueue }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Queue (${vm.queueList.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showQueue) {
            Spacer(modifier = Modifier.height(8.dp))
            vm.queueList.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            vm.playYouTubeSong(item.videoId, item.title, item.artist, item.thumbnailUrl)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = if (item.videoId == vm.currentVideoId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            item.artist,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ArtBox(side: Dp, title: String, videoId: String) {
    Box(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(24.dp))
            .background(MintTeal)
            .border(4.dp, BorderBlack, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (videoId.isBlank()) {
            SheetLetter(title, 64)
        } else {
            SubcomposeAsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { SheetLetter(title, 64) },
                error = { SheetLetter(title, 64) }
            )
        }
    }
}

@Composable
private fun ChunkyBtn(
    icon: ImageVector,
    description: String,
    bg: Color,
    size: Dp,
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
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
        ) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SheetLetter(title: String, fontSize: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MintTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
