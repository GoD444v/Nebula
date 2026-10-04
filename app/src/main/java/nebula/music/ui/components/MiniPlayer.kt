package nebula.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.NebulaTheme

import nebula.music.viewmodel.PlayerViewModel

// ponytail: extracted from MainActivity verbatim so the call site stays one line
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 3D Shadow Layer
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        // Main Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (vm.currentVideoId.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = "https://i.ytimg.com/vi/${vm.currentVideoId}/mqdefault.jpg",
                        contentDescription = vm.currentSongTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { MiniLetter(vm.currentSongTitle) },
                        error = { MiniLetter(vm.currentSongTitle) }
                    )
                } else {
                    MiniLetter(vm.currentSongTitle)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(vm.currentSongTitle, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, color = onSurface, modifier = Modifier.basicMarquee())
                Text(
                    vm.currentArtist.ifBlank { "Unknown artist" },
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = onSurface,
                    modifier = Modifier.basicMarquee()
                )
            }
            // Chunky play/pause in the app's own idiom (3px border + 4dp extrusion).
            // Filled with `secondary` (the palette's cyan/teal), NOT `primary`:
            // Theme.kt maps primary to palette.pink, so a primary fill made this the
            // one solid pink block on every palette. secondary is cyan on Cyberpunk,
            // teal on Sunset, and never collides with the pink nav pill.
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                IconButton(
                    onClick = { vm.togglePlay() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (vm.isPlaying) "Pause" else "Play",
                        // onSecondary is the palette's black/white for ink on teal,
                        // so the glyph stays legible on a light cyan.
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = onSurface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MiniLetter(title: String) {
    Text(
        title.firstOrNull()?.uppercase() ?: "?",
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun MiniPlayerPreview() {
    NebulaTheme(darkTheme = false) {
        MiniPlayer(vm = PlayerViewModel(), onOpen = {})
    }
}

@Composable
fun MiniPlayerPreviewAmoled() {
    NebulaTheme(darkTheme = true, paletteId = "amoled") {
        MiniPlayer(vm = PlayerViewModel(), onOpen = {})
    }
}