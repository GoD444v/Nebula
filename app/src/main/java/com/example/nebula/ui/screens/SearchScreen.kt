package com.example.nebula.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.example.nebula.data.SearchRepository
import com.example.nebula.ui.theme.AmoledBlack
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.ui.theme.TextWhite
import com.example.nebula.viewmodel.PlayerViewModel
import com.example.nebula.viewmodel.SearchViewModel

private data class FilterOption(val label: String, val param: String?)

// Chip order + labels exactly as Echo orders them (All first, playlists split in two)
private val FilterOptions = listOf(
    FilterOption("All", SearchRepository.SearchFilter.ALL),
    FilterOption("Songs", SearchRepository.SearchFilter.SONGS),
    FilterOption("Videos", SearchRepository.SearchFilter.VIDEOS),
    FilterOption("Albums", SearchRepository.SearchFilter.ALBUMS),
    FilterOption("Artists", SearchRepository.SearchFilter.ARTISTS),
    FilterOption("Community Playlists", SearchRepository.SearchFilter.COMMUNITY_PLAYLISTS),
    FilterOption("Featured Playlists", SearchRepository.SearchFilter.FEATURED_PLAYLISTS),
)

@Composable
fun SearchScreen(
    vm: PlayerViewModel = viewModel(),
    searchVm: SearchViewModel = viewModel(),
    onPlayDone: () -> Unit = {}
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val searchQuery by searchVm.searchQuery.collectAsState()
    val searchResults by searchVm.searchResults.collectAsState()
    val currentFilter by searchVm.filter.collectAsState()
    val isLoading by searchVm.isLoading.collectAsState()
    val isLoadingMore by searchVm.isLoadingMore.collectAsState()
    val errorMessage by searchVm.errorMessage.collectAsState()

    val listState = rememberLazyListState()

    // Auto-load more results when user scrolls near the bottom
    LaunchedEffect(listState, searchResults) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 3 && totalItems > 0
        }.collect { nearBottom ->
            if (nearBottom) {
                searchVm.loadMore()
            }
        }
    }

    val doSearch = {
        keyboard?.hide()
        searchVm.performSearch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Slanted Search Title Banner
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Search", color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Search", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchQuery,
                onValueChange = { searchVm.onQueryChange(it) },
                placeholder = { Text("Search songs, artists, albums...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .border(3.dp, BorderBlack, RoundedCornerShape(16.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = TextGrey,
                    unfocusedPlaceholderColor = TextGrey
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                IconButton(
                    onClick = { doSearch() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips Row with Chunky 3D Shadow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterOptions.forEach { option ->
                val isSelected = currentFilter == option.param
                FilterChip(
                    label = option.label,
                    selected = isSelected,
                    onClick = {
                        keyboard?.hide()
                        searchVm.setFilter(option.param)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Content Area
        when {
            isLoading -> Box(
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
                    Text("Loading results...", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                }
            }
            errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            }
            searchResults.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Type a query above to start discovering music", color = MaterialTheme.colorScheme.onBackground)
            }
            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults, key = { it.videoId }) { r ->
                    ResultCard(
                        title = r.title,
                        artist = r.artist,
                        thumbnailUrl = r.thumbnailUrl,
                        onPlay = {
                            // Starts a radio seeded by this result rather than replacing the
                            // queue with one song, matching how tapping a song behaves on
                            // Home, an album or an artist. One song and nothing after it is
                            // the wrong outcome from a search list.
                            vm.startRadioFrom(r)
                        }
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        // 3D Shadow Layer
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outline)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = BorderBlack)
        }
        // Main Chip Surface
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(2.5.dp, BorderBlack, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = textColor
            )
        }
    }
}

@Composable
private fun ResultCard(title: String, artist: String, thumbnailUrl: String, onPlay: () -> Unit) {
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
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintTeal)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl.isBlank()) {
                    Text(
                        title.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Text(
                                title.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        error = {
                            Text(
                                title.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text(artist, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            }
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
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Search Light")
@Composable
private fun SearchPreviewLight() {
    NebulaTheme(darkTheme = false) {
        SearchScreen(vm = PlayerViewModel(), searchVm = SearchViewModel())
    }
}
