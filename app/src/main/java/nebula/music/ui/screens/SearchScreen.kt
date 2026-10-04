package nebula.music.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.models.SearchResult
import nebula.music.ui.components.AddToPlaylistDialog
import nebula.music.ui.components.SongInfoDialog
import nebula.music.ui.components.SongMenuSheet
import nebula.music.viewmodel.AddToPlaylistResult
import nebula.music.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.launch

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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import nebula.music.data.SearchRepository
import nebula.music.ui.theme.AmoledBlack
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.NebulaTheme
import nebula.music.ui.theme.TextGrey
import nebula.music.ui.theme.TextWhite
import nebula.music.viewmodel.PlayerViewModel
import nebula.music.viewmodel.SearchViewModel

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
    onPlayDone: () -> Unit = {},
    playlistsVm: PlaylistsViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val searchQuery by searchVm.searchQuery.collectAsState()
    val searchResults by searchVm.searchResults.collectAsState()
    val currentFilter by searchVm.filter.collectAsState()
    val isLoading by searchVm.isLoading.collectAsState()
    val isLoadingMore by searchVm.isLoadingMore.collectAsState()
    val errorMessage by searchVm.errorMessage.collectAsState()

    val listState = rememberLazyListState()

    // Which song the overflow menu, the playlist picker or the info dialog is acting on.
    // Declared out here rather than inside the LazyColumn: a MutableState created in a
    // list item's scope is rebuilt per item and does not survive the item.
    var menuFor by remember { mutableStateOf<SearchResult?>(null) }
    var addingToPlaylist by remember { mutableStateOf<SearchResult?>(null) }
    var showingInfoFor by remember { mutableStateOf<SearchResult?>(null) }
    // Playlist/album drill-down: detail renders in place so the results survive back.
    var browseFor by remember { mutableStateOf<Pair<String, String>?>(null) }

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

    // Playlist/album tap lands here, not on another tab — back returns to results.
    val browse = browseFor
    if (browse != null) {
        BackHandler { browseFor = null }
        AlbumDetailScreen(
            browseId = browse.first,
            title = browse.second,
            vm = vm,
            playlistsVm = playlistsVm,
            onBack = { browseFor = null }
        )
        return
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
                    focusedPlaceholderColor = TextGrey(),
                    unfocusedPlaceholderColor = TextGrey()
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
        // Unfiltered results group by type with fixed-order headings (Echo's
        // pattern); a chip filter keeps the flat list. Pure function of the
        // list, so pagination appends regroup for free.
        val groups = remember(searchResults, currentFilter) {
            if (currentFilter == null) groupSearchResults(searchResults)
            else listOf("" to searchResults)
        }
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

                groups.forEachIndexed { groupIndex, (heading, rows) ->
                    if (heading.isNotBlank()) {
                        item(key = "head-$heading") {
                            SectionBanner(title = heading, isFirst = groupIndex == 0)
                        }
                    }
                    items(
                        rows,
                        key = { r -> r.videoId.ifBlank { r.browseId + r.title } }
                    ) { r ->
                        ResultCard(
                            title = r.title,
                            artist = r.artist,
                            thumbnailUrl = r.thumbnailUrl,
                            onPlay = {
                                if (r.videoId.isBlank()) {
                                    // Playlist/album/artist: detail page in place.
                                    browseFor = detailBrowseId(r) to r.title
                                } else {
                                    // Tapping the card plays the song, the same as a playlist row.
                                    // A search result is a single song, not a set, so there is no
                                    // "rest" to queue behind it — it plays on its own.
                                    vm.play(r)
                                    // Then reveal the player. The MiniPlayer is the player surface
                                    // in this app — there is no player tab — so "open the player"
                                    // means opening the full-screen sheet, which the user asked
                                    // for after hitting play from a search result.
                                    onPlayDone()
                                }
                            },
                            // Songs get the overflow; browse rows open on tap and the
                            // detail screen carries its own per-track menus.
                            onMore = if (r.videoId.isBlank()) null else ({ menuFor = r })
                        )
                    }
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

        // One sheet at a time: the menu closes before the picker or the info dialog
        // opens, so nothing stacks.
        menuFor?.let { song ->
            SongMenuSheet(
                song = song,
                isDownloaded = NebulaDownloads.isDownloaded(song.videoId),
                onDismiss = { menuFor = null },
                onPlayNext = { vm.playNext(song); menuFor = null },
                onAddToQueue = { vm.addToQueue(song); menuFor = null },
                onStartRadio = { vm.startRadioFrom(song); menuFor = null; onPlayDone() },
                onAddToPlaylist = {
                    menuFor = null
                    addingToPlaylist = song
                },
                onShare = {
                    menuFor = null
                    shareSong(context, song)
                },
                onDownload = {
                    NebulaDownloads.enqueue(song)
                    menuFor = null
                },
                onRemoveDownload = {
                    NebulaDownloads.remove(song.videoId)
                    menuFor = null
                },
                // Artist and album browsing need a per-song browseId, which a search
                // result does not carry. Omitted rather than wired to something that
                // navigates nowhere.
                onShowInfo = {
                    menuFor = null
                    showingInfoFor = song
                }
            )
        }

        val toAdd = addingToPlaylist
        if (toAdd != null && playlistsVm != null) {
            val playlists by playlistsVm.playlists.collectAsState()
            AddToPlaylistDialog(
                songs = listOf(toAdd),
                playlists = playlists,
                onAddToExisting = { id ->
                    scope.launch {
                        val result = playlistsVm.addToPlaylist(id, listOf(toAdd))
                        addingToPlaylist = null
                        toastResult(context, result)
                    }
                },
                onCreateWith = { name ->
                    scope.launch {
                        playlistsVm.createWithSongs(name, listOf(toAdd))
                        addingToPlaylist = null
                        Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { addingToPlaylist = null }
            )
        }

        showingInfoFor?.let { song ->
            SongInfoDialog(
                title = song.title,
                artist = song.artist,
                artworkUrl = song.thumbnailUrl,
                onDismiss = { showingInfoFor = null }
            )
        }
    }
}

/** Shares a plain text link, as the reference implementation does — no file attachment. */
internal fun shareSong(context: Context, song: SearchResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.videoId}")
    }
    context.startActivity(Intent.createChooser(intent, null))
}

internal fun toastResult(context: Context, result: AddToPlaylistResult) {
    val message = when {
        result.added == 0 -> "Already in that playlist"
        result.duplicates == 0 -> "Added ${result.added}"
        else -> "Added ${result.added}, ${result.duplicates} already there"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
private fun ResultCard(
    title: String,
    artist: String,
    thumbnailUrl: String,
    onPlay: () -> Unit,
    onMore: (() -> Unit)? = null
) {
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
                // The card plays, matching the playlist rows. A play button beside the
                // title read as "play this one song" while the row did something else.
                .clickable(onClick = onPlay)
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
            // Its own tap target, so opening the menu does not also start playback.
            // Browse rows carry none — tap opens the detail page instead.
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options for $title",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Unfiltered results split by type into fixed-order headings, Echo's pattern. */
private fun groupSearchResults(items: List<SearchResult>): List<Pair<String, List<SearchResult>>> {
    val songs = items.filter { it.videoId.isNotBlank() && !it.isVideoSong }
    val videos = items.filter { it.videoId.isNotBlank() && it.isVideoSong }
    val playlists =
        items.filter { it.videoId.isBlank() && (it.isPlaylist || it.pageType.contains("PLAYLIST")) }
    val albums =
        items.filter { it.videoId.isBlank() && !it.isPlaylist && it.pageType.contains("ALBUM") }
    val artists =
        items.filter { it.videoId.isBlank() && !it.isPlaylist && it.pageType.contains("ARTIST") }
    val seen = (songs + videos + playlists + albums + artists).toSet()
    val rest = items.filter { it !in seen && it.browseId.isNotBlank() }
    return listOf(
        "Songs" to songs,
        "Videos" to videos,
        "Playlists" to playlists,
        "Albums" to albums,
        "Artists" to artists,
        "More" to rest
    ).filter { it.second.isNotEmpty() }
}

/** Detail target: raw browseId, or VL-prefixed when only a playlistId survived. */
private fun detailBrowseId(r: SearchResult): String =
    r.browseId.ifBlank { "VL${r.playlistId}" }

@Preview(showBackground = true, name = "Search Light")
@Composable
private fun SearchPreviewLight() {
    NebulaTheme(darkTheme = false) {
        SearchScreen(vm = PlayerViewModel(), searchVm = SearchViewModel())
    }
}
