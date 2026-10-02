package com.example.nebula.ui.screens

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.example.nebula.data.DetailPage
import com.example.nebula.data.SearchRepository
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.PlayerViewModel

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
    onBack: () -> Unit
) {
    val repo = remember { SearchRepository() }
    var page by remember(browseId) { mutableStateOf<DetailPage?>(null) }

    LaunchedEffect(browseId) {
        page = repo.getDetail(browseId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // Back button — chunky Vox square with 3D shadow
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

        Spacer(modifier = Modifier.height(14.dp))

        val current = page
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
                    DetailHeader(page = current, fallbackTitle = title, vm = vm)
                }
                if (current.tracks.isEmpty()) {
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
                        current.tracks,
                        key = { index, track -> "$index-${track.videoId}" }
                    ) { _, track ->
                        TrackRow(
                            track = track,
                            isPlaying = track.videoId == vm.currentVideoId,
                            onPlay = {
                                vm.playYouTubeSong(
                                    track.videoId, track.title, track.artist, track.thumbnailUrl
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Big square art + title + subtitle + chunky yellow Play All button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailHeader(page: DetailPage, fallbackTitle: String, vm: PlayerViewModel) {
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
                    BigLetter(page.title.ifBlank { fallbackTitle })
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
            Text(
                text = page.title.ifBlank { fallbackTitle },
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = page.subtitle,
                fontSize = 12.sp,
                maxLines = 1,
                color = TextGrey(),
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (page.tracks.isNotEmpty()) {
                Box(modifier = Modifier.clickable { vm.playAll(page.tracks) }) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "PLAY ALL",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = BorderBlack
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "PLAY ALL",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** One track: small square thumb, marquee title/artist, small play button right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(track: SearchResult, isPlaying: Boolean, onPlay: () -> Unit) {
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

            // Small chunky play button
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
