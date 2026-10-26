package nebula.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import nebula.music.data.HomePrefs
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.models.HomeCard
import nebula.music.data.SearchRepository
import nebula.music.data.db.dao.PinnedPlaylist
import nebula.music.data.models.SearchResult
import nebula.music.ui.components.AddToPlaylistDialog
import nebula.music.ui.components.DownloadAllDialog
import nebula.music.ui.components.SongInfoDialog
import nebula.music.ui.components.SongMenuSheet
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.NebulaTheme
import nebula.music.ui.theme.TextGrey
import nebula.music.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.launch
import nebula.music.viewmodel.HomeViewModel
import nebula.music.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: PlayerViewModel = viewModel(),
    homeVm: HomeViewModel = viewModel(),
    playlistsVm: PlaylistsViewModel? = null,
    onBrowseClick: (browseId: String, title: String) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onListenTogether: () -> Unit = {}
) {
    val feed by homeVm.feed.collectAsState()
    val isLoading by homeVm.isLoading.collectAsState()
    val errorMessage by homeVm.errorMessage.collectAsState()
    val isLoadingMore by homeVm.isLoadingMore.collectAsState()
    val selectedChip by homeVm.selectedChip.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Which song the overflow menu, the playlist picker or the info dialog is acting on.
    // Same pattern as SearchScreen: declared out here, not in a list item scope.
    var menuFor by remember { mutableStateOf<SearchResult?>(null) }
    var addingToPlaylist by remember { mutableStateOf<SearchResult?>(null) }
    var showingInfoFor by remember { mutableStateOf<SearchResult?>(null) }

    // Hoisted out of the LazyColumn on purpose. Its content lambda is LazyListScope, not
    // @Composable, so collectAsState cannot be called in there -- and a local val also
    // gives the lambdas below a non-null receiver to smart-cast against.
    val playlists: PlaylistsViewModel? = playlistsVm
    val myPlaylists = playlists?.playlists?.collectAsState()?.value.orEmpty()
    // The speed dial: playlists the user pinned for one-tap access. Same reason it is
    // hoisted � collectAsState cannot be called inside a LazyListScope block.
    val pinnedPlaylists = playlists?.pinnedPlaylists?.collectAsState()?.value.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // Header banner � every screen gets one. The right-hand History button
        // is Echo's top-bar history icon; Nebula's top bar is this banner row.
        val horizontalMode = HomePrefs.horizontalScrollLayout
        if (horizontalMode) {
            // Slim top row: chunky "Home" title on the left, actions on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Home", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Home", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = onListenTogether) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = "Listen together",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "Play history",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = onListenTogether) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = "Listen together",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "Play history",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            // First load in flight � search-style loading pill
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

            // Feed never came back � retry card instead of a blank screen
            feed == null || feed!!.sections.isEmpty() -> ErrorCard(
                message = errorMessage ?: "Couldn't load your home feed",
                onRetry = homeVm::retry
            )

            else -> {
                val currentFeed = feed!!
                val lazyListState = rememberLazyListState()
                val horizontalMode = HomePrefs.horizontalScrollLayout
                val heroSection = if (horizontalMode) currentFeed.sections.firstOrNull { sec -> sec.cards.any { it.isSong } } else null
                LaunchedEffect(lazyListState) {
                    snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { last ->
                            val total = lazyListState.layoutInfo.totalItemsCount
                            if (last != null && total > 0 && last >= total - 3 && !homeVm.isLoadingMore.value) {
                                homeVm.loadMoreYouTubeItems(homeVm.feed.value?.continuation)
                            }
                        }
                }
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = homeVm::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Chip row � tap filters the feed, tap again to go back to default
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

                    // The speed dial, above everything else: these are the playlists the
                    // user chose to keep one tap away, so they outrank both the feed and
                    // the full playlist list. Nothing renders when nothing is pinned �
                    // an empty dial is just a gap.
                    if (pinnedPlaylists.isNotEmpty()) {
                        item(key = "speed-dial") {
                            SpeedDialRow(
                                pinned = pinnedPlaylists,
                                onOpen = { id -> onOpenPlaylist(id) }
                            )
                        }
                    }

                    // Your playlists first, above the YouTube feed. The reference app orders it this
                    // way too: AccountPlaylists renders before the community sections
                    // (Echo HomeScreen.kt:806-809), because what the user made outranks
                    // what YouTube suggests.
                    //
                    // Nothing renders when the list is empty, rather than an empty
                    // banner: a heading over nothing reads as a broken section.
                    // Visibility comes from HomePrefs: system rows (Downloads)
                    // default off, everything else default on.
                    val visiblePlaylists = myPlaylists.filter {
                        HomePrefs.isVisible(it.playlist.id, it.playlist.isSystem)
                    }
                    if (playlists != null && HomePrefs.showYourPlaylists && visiblePlaylists.isNotEmpty()) {
                        item(key = "your-playlists-title") {
                            SectionBanner(title = "Your playlists", isFirst = true)
                        }
                        itemsIndexed(
                            visiblePlaylists,
                            key = { _, p -> "mine-${p.playlist.id}" }
                        ) { _, entry ->
                            MyPlaylistRow(
                                entry = entry,
                                onOpen = { onOpenPlaylist(entry.playlist.id) },
                                onPlayAll = {
                                    scope.launch {
                                        // One-shot read; nothing subscribes here. The
                                        // scope key is the playlist's own, so playing it
                                        // from Home behaves exactly like playing it from
                                        // the Playlists tab instead of dropping that
                                        // playlist's shuffle and repeat.
                                        vm.playAll(
                                            playlists.songsOf(entry.playlist.id),
                                            "playlist:${entry.playlist.id}"
                                        )
                                    }
                                },
                                onRename = { newName ->
                                    playlists.rename(entry.playlist.id, newName)
                                },
                                onDelete = {
                                    playlists.delete(entry.playlist.id)
                                }
                            )
                        }
                    }

                    // QuickPicks hero carousel (Echo style) when the toggle is on.
                    if (heroSection != null) {
                        item(key = "quickpicks-hero") {
                            QuickPicksHeroCarousel(
                                cards = heroSection.cards,
                                vm = vm,
                                onMore = { card ->
                                    menuFor = SearchResult(card.videoId, card.title, card.subtitle, card.thumbnailUrl)
                                }
                            )
                        }
                    }

                    currentFeed.sections.forEachIndexed { sectionIndex, section ->
                        if (heroSection != null && section === heroSection) return@forEachIndexed
                        item(key = "title-$sectionIndex") {
                            SectionBanner(
                                title = section.title,
                                isFirst = sectionIndex == 0,
                                actions = { SectionMenu(cards = section.cards, vm = vm) }
                            )
                        }
                        if (horizontalMode) {
                            val songsOnly = section.cards.isNotEmpty() && section.cards.all { it.isSong }
                            if (songsOnly) {
                                // 4 rows x N columns, Echo style. The spacing IS the 3D
                                // budget: each cell reserves room for the card's offset
                                // shadow, so a cell never clips its neighbour.
                                item(key = "hgrid-$sectionIndex") {
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(4),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            start = 6.dp, end = 16.dp, top = 6.dp, bottom = 12.dp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // 4 rows of 76dp cards + 3 gaps + the top/bottom
                                            // content padding. Cell spacing is what reserves
                                            // room for each card's 5dp offset shadow.
                                            .height(76.dp * 4 + 20.dp * 3 + 18.dp)
                                    ) {
                                        items(
                                            section.cards,
                                            key = { it.videoId.ifBlank { it.title } }
                                        ) { card ->
                                            HorizontalSongRow(
                                                card = card,
                                                isPlaying = card.isSong && card.videoId == vm.currentVideoId,
                                                onPlay = {
                                                    if (card.isSong) vm.playYouTubeSong(card.videoId, card.title, card.subtitle, card.thumbnailUrl)
                                                },
                                                onMore = {
                                                    menuFor = SearchResult(card.videoId, card.title, card.subtitle, card.thumbnailUrl)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                item(key = "hrow-$sectionIndex") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(section.cards, key = { i, _ -> "hr-$sectionIndex-$i" }) { _, card ->
                                            HorizontalFeedCard(
                                                card = card,
                                                isPlaying = card.isSong && card.videoId == vm.currentVideoId,
                                                onPlay = if (card.isSong) {
                                                    { vm.playYouTubeSong(card.videoId, card.title, card.subtitle, card.thumbnailUrl) }
                                                } else null,
                                                onBrowse = if (!card.isSong && card.browseId.isNotBlank()) {
                                                    { onBrowseClick(card.browseId, card.title) }
                                                } else null,
                                                onMore = if (card.isSong) {
                                                    { menuFor = SearchResult(card.videoId, card.title, card.subtitle, card.thumbnailUrl) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
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
                                    onBrowse = if (!card.isSong && card.browseId.isNotBlank()) {
                                        { onBrowseClick(card.browseId, card.title) }
                                    } else null,
                                    onMore = if (card.isSong) {
                                        {
                                            menuFor = SearchResult(
                                                card.videoId, card.title, card.subtitle, card.thumbnailUrl
                                            )
                                        }
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

    // One sheet at a time: the menu closes before the picker or the info dialog
    // opens, so nothing stacks. Same wiring as SearchScreen.
    menuFor?.let { song ->
        SongMenuSheet(
            song = song,
            isDownloaded = NebulaDownloads.isDownloaded(song.videoId),
            onDismiss = { menuFor = null },
            onPlayNext = { vm.playNext(song); menuFor = null },
            onAddToQueue = { vm.addToQueue(song); menuFor = null },
            onStartRadio = { vm.startRadioFrom(song); menuFor = null },
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
            onShowInfo = {
                menuFor = null
                showingInfoFor = song
            },
            onListenTogether = {
                menuFor = null
                onListenTogether()
            },
            onViewArtist = {
                menuFor = null
                val bid = song.artistBrowseId
                if (bid.isNotBlank()) {
                    onBrowseClick(bid, song.artist)
                } else {
                    scope.launch {
                        val results = SearchRepository().search(song.artist, SearchRepository.SearchFilter.ARTISTS).items
                        val found = results.firstOrNull { it.browseId.isNotBlank() && it.title.equals(song.artist, ignoreCase = true) }
                            ?: results.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) {
                            onBrowseClick(found.browseId, found.title.ifBlank { song.artist })
                        } else {
                            android.widget.Toast.makeText(context, "No artist found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onViewAlbum = {
                menuFor = null
                val bid = song.albumBrowseId
                if (bid.isNotBlank()) {
                    onBrowseClick(bid, song.title)
                } else {
                    scope.launch {
                        val found = SearchRepository().search(song.title, SearchRepository.SearchFilter.ALBUMS)
                            .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowseClick(found.browseId, found.title.ifBlank { song.title })
                    }
                }
            },
            onRefetch = {
                menuFor = null
                nebula.music.data.SearchRepository.invalidateStream(song.videoId)
                if (song.videoId == vm.currentVideoId) vm.retryPlayback()
                android.widget.Toast.makeText(context, "Refetched", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    val toAdd = addingToPlaylist
    if (toAdd != null && playlists != null) {
        val allPlaylists by playlists.playlists.collectAsState()
        AddToPlaylistDialog(
            songs = listOf(toAdd),
            playlists = allPlaylists,
            onAddToExisting = { id ->
                scope.launch {
                    val result = playlists.addToPlaylist(id, listOf(toAdd))
                    addingToPlaylist = null
                    toastResult(context, result)
                }
            },
            onCreateWith = { name ->
                scope.launch {
                    playlists.createWithSongs(name, listOf(toAdd))
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
            videoId = song.videoId,
            onDismiss = { showingInfoFor = null }
        )
    }
}

/**
 * One of the user's own playlists on Home.
 *
 * Shaped like the feed rows already on this screen � cover, name, track count � so Home
 * keeps one card language. The reference app uses a horizontal `LazyRow` here; this is a
 * vertical list because that is what every other section on this screen already does, and
 * a second layout on one screen reads as a mistake.
 *
 * Tap OPENS the playlist, which is what the reference app's card tap does too
 * (Echo HomeScreen.kt:1329). Play lives in the overflow rather than on the tap, so a tap
 * can never start the wrong thing by accident.
 */
@Composable
private fun MyPlaylistRow(
    entry: nebula.music.data.db.dao.PlaylistWithCount,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, shape)
                .clickable(onClick = onOpen)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = entry.coverThumbnailUrl
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MintTeal)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!cover.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.playlist.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${entry.songCount} tracks",
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey()
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options for ${entry.playlist.name}",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    // Pinned to `surface`: the default `surfaceContainer` is not a colour
                    // any Vox palette defines, so the text on it was unreadable.
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text("Play all", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { menuOpen = false; onPlayAll() }
                    )
                    // Rename and Delete are absent for the built-in Downloaded playlist.
                    // `dao.delete` refuses `isSystem` rows, so offering them would present
                    // controls that look live and silently do nothing.
                    if (!entry.playlist.isSystem) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuOpen = false; renaming = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmingDelete = true }
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        RenamePlaylistDialog(
            current = entry.playlist.name,
            onConfirm = { newName ->
                onRename(newName)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "\"${entry.playlist.name}\" and its ${entry.songCount} tracks will be " +
                        "removed. Downloaded songs stay on disk.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmingDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenamePlaylistDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
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
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Section title � the screen banner scaled down. Shared with search result groups. */
@Composable
internal fun SectionBanner(
    title: String,
    isFirst: Boolean,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 0.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
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
        actions()
    }
}

/**
 * The speed dial: the playlists the user pinned, as one tap away.
 *
 * Echo-Music's speed dial (its `speed_dial_item` table and `ShowSpeedDialKey`), reduced to
 * what Nebula has: pinned playlists, in pin order. Horizontal and scrollable because the
 * list is whatever the user chose to pin � there is no sensible maximum to design around.
 */
@Composable
private fun SpeedDialRow(
    pinned: List<PinnedPlaylist>,
    onOpen: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pinned.forEach { p ->
            val shape = RoundedCornerShape(16.dp)
            Box {
                // The 4dp offset shadow, the app's card primitive.
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .matchParentSize()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, BorderBlack, shape)
                        .clickable { onOpen(p.playlistId) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            p.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${p.songCount} songs",
                            fontSize = 11.sp,
                            color = TextGrey()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section overflow: play, shuffle, repeat and download for a whole feed
 * section. Songs are the playable cards (videoId set); browse cards carry no
 * audio and are skipped rather than enqueued as dead rows.
 */
@Composable
@androidx.media3.common.util.UnstableApi
private fun SectionMenu(
    cards: List<HomeCard>,
    vm: PlayerViewModel
) {
    var open by remember { mutableStateOf(false) }
    // The list the user agreed to download, held between the menu item and the confirm.
    // null = no confirm showing.
    var confirmingDownload by remember { mutableStateOf<List<SearchResult>?>(null) }
    val context = LocalContext.current
    val songs = remember(cards) {
        cards.filter { it.videoId.isNotBlank() }.map {
            SearchResult(it.videoId, it.title, it.subtitle, it.thumbnailUrl)
        }
    }
    if (songs.isEmpty()) return
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Section options",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = { Text("Play all", color = MaterialTheme.colorScheme.onSurface) },
                onClick = { vm.playAll(songs); open = false }
            )
            DropdownMenuItem(
                text = { Text("Shuffle play", color = MaterialTheme.colorScheme.onSurface) },
                onClick = { vm.playAll(songs.shuffled()); open = false }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        when (vm.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat: One"
                            Player.REPEAT_MODE_ALL -> "Repeat: All"
                            else -> "Repeat: Off"
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { vm.toggleRepeat(); open = false }
            )
            DropdownMenuItem(
                text = { Text("Download playlist", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    open = false
                    // Confirm before a fan-out that can be hundreds of megabytes, and say
                    // how many are actually NEW � songs already on disk are skipped, so the
                    // row count would be a lie every second time this was used.
                    confirmingDownload = songs
                }
            )
        }
    }

    if (confirmingDownload != null) {
        val pending = confirmingDownload ?: emptyList()
        DownloadAllDialog(
            count = pending.count { !NebulaDownloads.isDownloaded(it.videoId) },
            onConfirm = {
                confirmingDownload = null
                val queued = NebulaDownloads.enqueueAll(pending)
                Toast.makeText(
                    context,
                    if (queued == 0) "Already downloaded" else "Downloading $queued songs",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { confirmingDownload = null }
        )
    }
}

/** One card in the feed: song rows get a play button, album/playlist rows wait for their pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedRow(
    card: HomeCard,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onBrowse: (() -> Unit)?,
    onMore: (() -> Unit)? = null
) {
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
                    AsyncImage(
                        model = card.thumbnailUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
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

            // Row tap plays (wired above) � no separate play button. A pink button
            // on every card duplicated the tap target and crowded the row.
            // Its own tap target, so opening the menu does not also start playback.
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options for ${card.title}",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Big letter used while a thumbnail loads or when there is none. */
@Composable
private fun CardLetter(title: String) {
    Text(
        title.firstOrNull()?.uppercase() ?: "\u266A",
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** VoxMusic filter chip � same look as the Search screen's. Shared with onboarding + settings. */
@Composable
internal fun HomeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QuickPicksHeroCarousel(
    cards: List<HomeCard>,
    vm: PlayerViewModel,
    onMore: (HomeCard) -> Unit
) {
    if (cards.isEmpty()) return
    val distinct = cards.distinctBy { it.videoId.ifBlank { it.title } }
    val pagerState = rememberPagerState(pageCount = { distinct.size })
    LaunchedEffect(pagerState) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (distinct.isNotEmpty() && !pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % distinct.size
                pagerState.animateScrollToPage(next)
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
    ) { page ->
        val card = distinct[page]
        val isActive = card.isSong && card.videoId == vm.currentVideoId
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            // 3D shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = {
                            if (!card.isSong) return@combinedClickable
                            if (isActive) vm.togglePlay() else vm.playYouTubeSong(card.videoId, card.title, card.subtitle, card.thumbnailUrl)
                        },
                        onLongClick = { if (card.isSong) onMore(card) }
                    )
            ) {
                if (card.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = card.thumbnailUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MintTeal), contentAlignment = Alignment.Center) {
                        CardLetter(card.title)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        card.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1
                    )
                    Text(
                        card.subtitle,
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .border(2.dp, BorderBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One song row inside the 4-row horizontal grid.
 *
 * Fixed 260.dp width so the song name AND artist fit on a line, and the offset
 * shadow is drawn at full strength. The grid's 20dp cell spacing is what
 * reserves the room for the 5dp shadow, so it never clips into a neighbour.
 */
@Composable
private fun HorizontalSongRow(
    card: HomeCard,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit
) {
    Box(modifier = Modifier.width(260.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MintTeal)
                    .border(2.5.dp, BorderBlack, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (card.thumbnailUrl.isBlank()) {
                    CardLetter(card.title)
                } else {
                    AsyncImage(
                        model = card.thumbnailUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    card.subtitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey(),
                    modifier = Modifier.basicMarquee()
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options for ${card.title}",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HorizontalFeedCard(
    card: HomeCard,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onBrowse: (() -> Unit)?,
    onMore: (() -> Unit)?
) {
    // Outer padding so the 4dp offset shadow is not clipped by the LazyRow cell.
    Box(modifier = Modifier.width(150.dp).padding(bottom = 5.dp, end = 5.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .then(
                    when {
                        onPlay != null -> Modifier.clickable(onClick = onPlay)
                        onBrowse != null -> Modifier.clickable(onClick = onBrowse)
                        else -> Modifier
                    }
                )
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MintTeal)
                    .border(2.dp, BorderBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (card.thumbnailUrl.isBlank()) {
                    CardLetter(card.title)
                } else {
                    AsyncImage(
                        model = card.thumbnailUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                card.title,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.basicMarquee()
            )
            Text(
                card.subtitle,
                fontSize = 10.sp,
                maxLines = 1,
                color = TextGrey(),
                modifier = Modifier.basicMarquee()
            )
            if (onMore != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onMore, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                    }
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
