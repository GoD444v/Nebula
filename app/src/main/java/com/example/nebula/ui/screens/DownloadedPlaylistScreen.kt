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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.components.SongInfoDialog
import com.example.nebula.ui.components.AddToPlaylistDialog
import com.example.nebula.ui.components.SongMenuSheet
import kotlinx.coroutines.launch
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.SunnyYellow
import com.example.nebula.ui.theme.TextGrey
import com.example.nebula.viewmodel.PlaylistsViewModel
import com.example.nebula.viewmodel.PlaylistSortChoice
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val songs by vm.songs.collectAsState()
    val selectedId by vm.selectedPlaylistId.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val sorts by vm.sorts.collectAsState()

    // A rename must not break sync: the DAO's lookups key on isSystem, never on the name,
    // so showing the live row here is safe.
    val name = playlists.firstOrNull { it.playlist.id == selectedId }?.playlist?.name
        ?: DownloadedFallbackName
    val items = songs.map { it.toSearchResult() }
    var infoFor by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<SearchResult?>(null) }

    DownloadedPlaylistContent(
        name = name,
        playlistId = selectedId,
        // Read from the ViewModel so the choice outlives this composable. collectAsState
        // on a map keyed by id means switching playlists shows that playlist's own choice.
        sortChoice = sorts[selectedId] ?: PlaylistSortChoice(),
        onSortChange = { choice -> selectedId?.let { vm.setSort(it, choice) } },
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
        // Same sheet the search rows use, so a song's menu behaves identically wherever
        // it appears. Song info is one of its items rather than a separate button.
        SongMenuSheet(
            song = song,
            isDownloaded = NebulaDownloads.isDownloaded(song.videoId),
            onDismiss = { infoFor = null },
            onPlayNext = { infoFor?.let(playerVm::playNext); infoFor = null },
            onAddToQueue = { infoFor?.let(playerVm::addToQueue); infoFor = null },
            onStartRadio = { infoFor?.let(playerVm::startRadioFrom); infoFor = null },
            onAddToPlaylist = { addToPlaylistFor = infoFor; infoFor = null },
            onShare = {
                infoFor?.let { s -> shareSong(context, s) }
                infoFor = null
            },
            onDownload = { infoFor?.let(NebulaDownloads::enqueue); infoFor = null },
            onRemoveDownload = { infoFor?.let { NebulaDownloads.remove(it.videoId) }; infoFor = null },
            onShowInfo = { infoFor = null }
        )
    }

    addToPlaylistFor?.let { song ->
        playlists?.let { p ->
            AddToPlaylistDialog(
                songs = listOf(song),
                playlists = p,
                onAddToExisting = { id ->
                    scope.launch {
                        val r = vm.addToPlaylist(id, listOf(song))
                        addToPlaylistFor = null
                        toastResult(context, r)
                    }
                },
                onCreateWith = { n ->
                    scope.launch {
                        vm.createWithSongs(n, listOf(song))
                        addToPlaylistFor = null
                    }
                },
                onDismiss = { addToPlaylistFor = null }
            )
        } ?: run { addToPlaylistFor = null }
    }
}

private const val DownloadedFallbackName = "Downloaded"

/** Media3's repeat ints into words for the button's content description. */
private fun Int.label(): String = when (this) {
    Player.REPEAT_MODE_ALL -> "Repeat all"
    Player.REPEAT_MODE_ONE -> "Repeat one"
    else -> "Repeat off"
}

/**
 * Maps a stored playlist row back to the queue item shape `playAll` expects.
 *
 * `addedAt` is carried across because the Date Added sort reads it, and it lives on the
 * entity rather than on [SearchResult] for everything that is not a playlist row.
 */
private fun PlaylistSongEntity.toSearchResult() = SearchResult(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl.orEmpty(),
    addedAt = addedAt
)

@Composable
private fun DownloadedPlaylistContent(
    name: String,
    playlistId: Long?,
    sortChoice: PlaylistSortChoice,
    onSortChange: (PlaylistSortChoice) -> Unit,
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

    // Owned by the ViewModel, not by this composable: `remember` here reset to Custom
    // on every visit, so a Name-sorted list reverted the moment the user went back and
    // returned. See [PlaylistsViewModel.sorts].
    val sortType = sortChoice.type
    val sortDescending = sortChoice.descending

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
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                    // Pinned to `surface`. A bare DropdownMenu takes Material's default
                    // `surfaceContainer`, which no Vox palette defines — so the panel
                    // resolved to a colour no palette chose, while the item text used the
                    // palette's `onSurface`. Under Classic80s Vox (onSurface #111111) that
                    // put near-black text on a dark panel: the sort items were unreadable.
                    // containerColor alone is enough; Material derives the content colour
                    // for it, and every item below also sets its colour explicitly.
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "Sort by",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    PlaylistSortType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label, color = MaterialTheme.colorScheme.onSurface) },
                            // Radio affordance, as the reference implementation shows.
                            leadingIcon = {
                                Icon(
                                    if (sortType == type) Icons.Filled.RadioButtonChecked
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { onSortChange(sortChoice.copy(type = type)); overflowOpen = false }
                        )
                    }

                    // Hidden for Custom order: reversing a hand-arranged list is never
                    // what the user meant, and the item would read as doing nothing.
                    if (sortType != PlaylistSortType.CUSTOM) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (sortDescending) "Descending" else "Ascending",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (sortDescending) Icons.Filled.ArrowDownward
                                    else Icons.Filled.ArrowUpward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { onSortChange(sortChoice.copy(descending = !sortDescending)) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)

                    // No Delete entry for the system playlist: dao.delete refuses
                    // isSystem rows, so a Delete here would look enabled and do nothing.
                    DropdownMenuItem(
                        text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            overflowOpen = false
                            showRename = true
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Artwork on the left with the name set over it, text and controls on the right —
        // the header shape the reference design uses. The artwork is still the first
        // song's cover, which is deliberate: for the Downloaded playlist there is no
        // playlist-level image to show, and a mosaic would be four unrelated square
        // thumbnails rather than one recognisable cover.
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(
                thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                size = 104.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Playlist · ${songs.size} tracks",
                    fontSize = 12.sp,
                    color = TextGrey()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoxPlayAllButton(onClick = onPlay)
                    ChunkyIconButton(
                        icon = Icons.Filled.Shuffle,
                        description = "Shuffle",
                        tint = if (shuffleOn) SunnyYellow else MaterialTheme.colorScheme.surface,
                        size = 44.dp,
                        onClick = onShuffle
                    )
                    ChunkyIconButton(
                        icon = Icons.Filled.Repeat,
                        description = "Repeat $repeatMode",
                        tint = MaterialTheme.colorScheme.surface,
                        size = 44.dp,
                        onClick = onRepeat
                    )
                }
            }
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
                    color = TextGrey()
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

            // Reorder is arrows only. The drag gesture was tried and could not be verified
            // off-device, and a gesture that silently does nothing is worse than a
            // visible button. Arrows hide when reordering makes no sense: under Name or
            // Artist the visible order is not the stored order, so an arrow index would
            // refer to the wrong rows.
            val canReorder = sortType == PlaylistSortType.CUSTOM

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sorted, key = { _, s -> s.videoId }) { index, song ->
                    val asSong = song
                    DownloadedSongRow(
                        song = asSong,
                        canMoveUp = canReorder && index > 0,
                        canMoveDown = canReorder && index < sorted.lastIndex,
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
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

/**
 * One song's artwork in a row.
 *
 * `contentScale = Crop` is load-bearing, not decoration. Coil's default is `Fit`, which
 * fits the WHOLE image inside the square and therefore letterboxes any non-square
 * thumbnail — bars of empty background top and bottom, which reads as "the artwork does
 * not fit". Search results already used Crop, which is why the same song looked correct
 * there and wrong here. It is not a per-song problem: every non-square cover is affected,
 * and square ones simply hide it.
 */
@Composable
private fun CoverArt(thumbnailUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (thumbnailUrl != null) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            // Square box, filled. See the note above.
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            // No track number. It was read as a fixed ordinal, so after a reorder the numbers
            // disagreed with the arrows the user had just pressed and it looked like the
            // list had reverted. The row order is the order; a number restating it adds
            // a second, contradicting one.
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
                    color = TextGrey()
                )
            }

            IconButton(onClick = onShowInfo) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options for ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Vox-style reorder arrows: offset shadow behind a bordered chip, the same
            // primitive as the header buttons, so the row does not sprout a second
            // visual language. Hidden rather than disabled at the ends of the list — an
            // arrow that cannot do anything reads as broken.
            if (canMoveUp) {
                VoxArrow(
                    icon = Icons.Filled.KeyboardArrowUp,
                    description = "Move ${song.title} up",
                    onClick = onMoveUp
                )
            }
            if (canMoveDown) {
                VoxArrow(
                    icon = Icons.Filled.KeyboardArrowDown,
                    description = "Move ${song.title} down",
                    onClick = onMoveDown
                )
            }
        }
    }
}

/**
 * Playlist cover: the artwork, cropped to a square.
 *
 * No title and no scrim over it. The name is set beside this in 20sp Black already, so
 * repeating it across the artwork said the same thing twice in two places, and the
 * gradient needed to make that overlay readable darkened the very artwork it sat on.
 * The name beside it is the single place it appears.
 */
@Composable
private fun PlaylistCover(thumbnailUrl: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(MintTeal)
            .border(3.dp, BorderBlack, RoundedCornerShape(18.dp))
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                // Crop, so a non-square cover fills the square instead of being letterboxed.
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }
    }
}

/**
 * Vox "PLAY ALL" pill: filled, black-bordered, offset shadow.
 *
 * The shadow layer is `matchParentSize` on purpose. It was an empty `Box` with only a
 * background, so it measured zero and the 3D effect simply was not drawn — which is
 * what the screenshot showed: a flat yellow pill next to two properly extruded chips.
 * `matchParentSize` makes the shadow exactly the pill's size, offset down-right.
 *
 * Height is pinned to [BUTTON] to match the shuffle and repeat buttons beside it. Sized
 * by its own vertical padding it came out shorter than the 44dp square chips, so the row
 * read as three differently sized controls rather than one set.
 */
@Composable
private fun VoxPlayAllButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier = Modifier.height(PLAY_ALL_HEIGHT)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .height(PLAY_ALL_HEIGHT)
                .clip(shape)
                .background(SunnyYellow)
                .border(3.dp, BorderBlack, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = BorderBlack,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "PLAY ALL",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = BorderBlack
            )
        }
    }
}

/** One height for all three transport controls, so the row reads as a set. */
private val PLAY_ALL_HEIGHT = 44.dp

/**
 * Small Vox-style arrow chip: offset shadow behind a 3px-bordered box.
 *
 * Matches [ChunkyIconButton] at a smaller scale so the song row's reorder controls read
 * as part of the same design language as the header buttons.
 */
@Composable
private fun VoxArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val size = 30.dp
    Box(modifier = Modifier.padding(start = 2.dp)) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiary)
                .border(2.5.dp, BorderBlack, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
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