package nebula.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.NebulaTheme

import nebula.music.viewmodel.PlayerViewModel

@Composable
fun NowPlayingScreen(vm: PlayerViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            ArtBox(title = vm.currentSongTitle, videoId = vm.currentVideoId)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    vm.currentSongTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

        if (vm.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text("Loading...", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        } else if (vm.currentArtist.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(vm.currentArtist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChunkyButton(onClick = { }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }
            ChunkyButton(
                onClick = { vm.togglePlay() },
                highlight = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play",
tint = MaterialTheme.colorScheme.onSurface
                )
            }
            ChunkyButton(onClick = { }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (vm.upNext.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Up Next",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(vm.upNext, key = { it.videoId }) { item ->
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.playQueueItem(item) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MintTeal)
                                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Text(
                                        item.title.firstOrNull()?.uppercase() ?: "?",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                error = {
                                    Text(
                                        item.title.firstOrNull()?.uppercase() ?: "?",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                        Text(
                            item.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            item.artist,
                            fontSize = 10.sp,
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

// ponytail: maxres photo with letter fallback; error slot covers videos lacking hi-res
@Composable
private fun ArtBox(title: String, videoId: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MintTeal)
            .border(4.dp, BorderBlack, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (videoId.isBlank()) {
            LetterArt(title)
        } else {
            SubcomposeAsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { LetterArt(title) },
                error = { LetterArt(title) }
            )
        }
    }
}

@Composable
private fun LetterArt(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MintTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.firstOrNull()?.uppercase() ?: "Art",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChunkyButton(
    onClick: () -> Unit,
    highlight: Color = MaterialTheme.colorScheme.surface,
    icon: @Composable () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(highlight)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
        ) {
            icon()
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun NowPlayingPreviewLight() {
    NebulaTheme(darkTheme = false) {
        NowPlayingScreen(vm = PlayerViewModel())
    }
}

@Preview(showBackground = true, name = "Dark AMOLED")
@Composable
private fun NowPlayingPreviewDark() {
    NebulaTheme(darkTheme = true) {
        // ponytail: preview reuses real VM with one toggle, no fake/mock class
        NowPlayingScreen(vm = PlayerViewModel().apply { togglePlay() })
    }
}
