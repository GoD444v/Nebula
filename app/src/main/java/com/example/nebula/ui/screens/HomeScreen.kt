package com.example.nebula.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.example.nebula.data.models.HomeCard
import com.example.nebula.data.models.HomeChip
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.HomeViewModel
import com.example.nebula.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: PlayerViewModel = viewModel(),
    homeVm: HomeViewModel = viewModel(),
    onBrowseClick: (browseId: String, title: String) -> Unit = { _, _ -> }
) {
    val feed by homeVm.feed.collectAsState()
    val isLoading by homeVm.isLoading.collectAsState()
    val errorMessage by homeVm.errorMessage.collectAsState()
    val selectedChip by homeVm.selectedChip.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // Header banner — every screen gets one
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Home", color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "Home",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            // First load in flight — search-style loading pill
            isLoading && feed == null -> Box(
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
                            "Loading your feed...",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Feed never came back — retry card instead of a blank screen
            feed == null || feed!!.sections.isEmpty() -> ErrorCard(
                message = errorMessage ?: "Couldn't load your home feed",
                onRetry = homeVm::retry
            )

            else -> {
                val currentFeed = feed!!
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = homeVm::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Chip row — tap filters the feed, tap again to go back to default
                    if (currentFeed.chips.isNotEmpty()) {
                        item(key = "chips") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                currentFeed.chips.forEach { chip ->
                                    HomeFilterChip(
                                        label = chip.label,
                                        selected = chip.params == selectedChip,
                                        onClick = { homeVm.onChipSelected(chip) }
                                    )
                                }
                            }
                        }
                    }

                    currentFeed.sections.forEachIndexed { sectionIndex, section ->
                        item(key = "title-$sectionIndex") {
                            SectionBanner(
                                title = section.title,
                                isFirst = sectionIndex == 0
                            )
                        }
                        itemsIndexed(
                            section.cards,
                            key = { cardIndex, _ -> "$sectionIndex-$cardIndex" }
                        ) { _, card ->
                            FeedRow(
                                card = card,
                                isPlaying = card.isSong && card.videoId == vm.currentVideoId,
                                onPlay = if (card.isSong) {
                                    {
                                        vm.playYouTubeSong(
                                            card.videoId, card.title, card.subtitle, card.thumbnailUrl
                                        )
                                    }
                                } else null,
                                // Album / playlist / artist cards open their detail page
                                onBrowse = if (!card.isSong && card.browseId.isNotBlank()) {
                                    { onBrowseClick(card.browseId, card.title) }
                                } else null
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/** Section title — the screen banner scaled down. */
@Composable
private fun SectionBanner(title: String, isFirst: Boolean) {
    Box(modifier = Modifier.padding(top = if (isFirst) 0.dp else 18.dp)) {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outline)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            // Mirror the front text exactly: default 16sp made the shadow WIDER than
            // the 14sp front on long titles ("Dancing on your own" showed a fat black block)
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiary)
                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** One card in the feed: song rows get a play button, album/playlist rows wait for their pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedRow(card: HomeCard, isPlaying: Boolean, onPlay: (() -> Unit)?, onBrowse: (() -> Unit)?) {
    Box {
        // Side-extrusion 3D shadow
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
                .then(
                    when {
                        onPlay != null -> Modifier.clickable(onClick = onPlay)
                        onBrowse != null -> Modifier.clickable(onClick = onBrowse)
                        else -> Modifier
                    }
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with letter fallback
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintTeal)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (card.thumbnailUrl.isBlank()) {
                    CardLetter(card.title)
                } else {
                    SubcomposeAsyncImage(
                        model = card.thumbnailUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { CardLetter(card.title) },
                        error = { CardLetter(card.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = card.subtitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey(),
                    modifier = Modifier.basicMarquee()
                )
            }

            // Chunky play button — songs only
            if (onPlay != null) {
                Box {
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = 3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Big letter used while a thumbnail loads or when there is none. */
@Composable
private fun CardLetter(title: String) {
    Text(
        title.firstOrNull()?.uppercase() ?: "?",
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** VoxMusic filter chip — same look as the Search screen's. */
@Composable
private fun HomeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outline)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = BorderBlack)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(2.5.dp, BorderBlack, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = textColor)
        }
    }
}

/** The old empty-state card, now the failure state: note + reason + a Retry button. */
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .padding(32.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = BorderBlack,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                        .padding(32.dp)
                    ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Check your connection and tap Retry",
                fontSize = 13.sp,
                color = TextGrey()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.clickable(onClick = onRetry)) {
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Retry", fontWeight = FontWeight.Black, fontSize = 14.sp, color = BorderBlack)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Retry",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Home Light")
@Composable
private fun HomePreviewLight() {
    NebulaTheme(darkTheme = false) {
        HomeScreen(vm = PlayerViewModel(), homeVm = HomeViewModel())
    }
}
