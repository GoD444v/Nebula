package nebula.music.ui.screens

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
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import nebula.music.data.db.entities.PlaylistSongEntity
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.models.SearchResult
import nebula.music.ui.components.SongInfoDialog
import nebula.music.ui.components.AddToPlaylistDialog
import nebula.music.ui.components.AddSongsDialog
import nebula.music.ui.components.DownloadAllDialog
import nebula.music.ui.components.SongMenuSheet
import android.widget.Toast
import kotlinx.coroutines.launch
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.SunnyYellow
import nebula.music.ui.theme.TextGrey
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.PlaylistSortChoice
import nebula.music.viewmodel.PlayerViewModel

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
    playlistId: Long? = null,
    onListenTogether: () -> Unit = {}
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
    val pinnedPlaylists by vm.pinnedPlaylists.collectAsState()

    // A rename must not break sync: the DAO's lookups key on isSystem, never on the name,
    // so showing the live row here is safe.
    val playlistEntity = playlists.firstOrNull { it.playlist.id == selectedId }?.playlist
    // Songs can only be taken out of a playlist the user made. The built-in Downloaded
    // list mirrors the download index and is rebuilt from it, so a song removed from it
    // would simply reappear.
    val canRemoveFromPlaylist = playlistEntity?.isSystem == false
    val name = playlistEntity?.name ?: DownloadedFallbackName
    // Guard against YouTube IDs leaking through as playlist names (e.g. from imports).
    val displayName = if (name.matches(Regex("^[a-zA-Z0-9_-]{8,20}$"))) "Playlist" else name
    val playlistThumbnail = playlistEntity?.thumbnailUrl
    val items = songs.map { it.toSearchResult() }
    var infoFor by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<List<SearchResult>?>(null) }
    // The search-and-pick sheet for adding songs to this playlist.
    var addingSongs by remember { mutableStateOf(false) }

    // Shuffle and repeat belong to this playlist and nothing else. Named playlistScope
    // because `scope` above is the coroutine scope the dialogs launch in. Null until a
    // playlist is selected, and the header only offers these buttons with a real one,
    // so a null key can never match [playerVm.queueScope] and light the buttons.
    val playlistScope = selectedId?.let { "playlist:$it" }

    DownloadedPlaylistContent(
        name = name,
        playlistThumbnail = playlistThumbnail,
        playlistId = selectedId,
        // Read from the ViewModel so the choice outlives this composable. collectAsState
        // on a map keyed by id means switching playlists shows that playlist's own choice.
        sortChoice = sorts[selectedId] ?: PlaylistSortChoice(),
        onSortChange = { choice -> selectedId?.let { vm.setSort(it, choice) } },
        songs = songs,
        shuffleOn = playlistScope?.let { playerVm.shuffleFor(it) } == true,
        repeatMode = playerVm.repeatFor(playlistScope).label(),
        repeatOn = playerVm.repeatFor(playlistScope) != Player.REPEAT_MODE_OFF,
        onPlay = { playerVm.playAll(items, playlistScope) },
        // Shuffle PLAYS: it starts the list at a random song with shuffle on, rather
        // than flipping a flag and leaving whatever was playing alone. Tapping again
        // turns shuffle off and lets the music keep running.
        onShuffle = { playerVm.shufflePlayAll(items, playlistScope) },
        onRepeat = { playerVm.toggleRepeat(playlistScope) },
        onRename = { newName -> selectedId?.let { vm.rename(it, newName) } },
        // Delete, then leave. Staying on the screen would leave it showing a playlist row
        // that no longer exists — the name falls back to "Downloaded" and the song list
        // drains to empty, which looks like the app lost everything.
        onDelete = {
            selectedId?.let { vm.delete(it) }
            onBack()
        },
        canDelete = playlists.firstOrNull { it.playlist.id == selectedId }?.playlist?.isSystem == false,
        // Songs can only be added to or taken out of a playlist the user made. The
        // built-in Downloaded list mirrors the download index and is rebuilt from it, so
        // a song taken out of it would simply reappear.
        canEditSongs = playlists.firstOrNull { it.playlist.id == selectedId }?.playlist?.isSystem == false,
        onPlayNext = { playerVm.playNext(items) },
        onAddToQueue = { playerVm.addToQueue(items) },
        onAddToPlaylist = { addToPlaylistFor = items },
        onAddSongs = { addingSongs = true },
        isPinned = selectedId?.let { id -> pinnedPlaylists.any { it.playlistId == id } } == true,
        onTogglePin = {
            selectedId?.let { id ->
                if (pinnedPlaylists.any { it.playlistId == id }) vm.unpinFromSpeedDial(id)
                else vm.pinToSpeedDial(id)
            }
        },
        onMove = { from, to -> selectedId?.let { vm.reorder(it, from, to) } },
        // Inside a playlist, tapping a song plays that song and keeps the rest of the
        // playlist queued behind it — the opposite of startRadioFrom, which is for tapping
        // a song in a search or browse list where the surrounding rows are not a set.
        onPlaySong = { song ->
            // Queue = tapped song + only the songs after it in the displayed
            // (sorted) order. Previously the whole list minus the tapped song was
            // passed in playlist order, so tapping row 5 queued row 1 second and
            // Next played the first song. Index-based: a duplicated videoId keeps
            // its own position instead of dropping every copy. Same playlistScope as
            // the header buttons, so tapping a song here does not reset the
            // playlist's shuffle — this is still this playlist.
            val idx = items.indexOfFirst { it.videoId == song.videoId }
            playerVm.playFromHere(song, if (idx == -1) emptyList() else items.drop(idx + 1), playlistScope)
        },
        onShowInfo = { song -> infoFor = song },
        onBack = onBack
    )

    // The search-and-pick sheet for adding songs to this playlist. It stays open as you
    // add several — a picker that closed after one song would mean a trip back to the
    // overflow for every single track.
    if (addingSongs) {
        AddSongsDialog(
            onPick = { song ->
                selectedId?.let { id ->
                    scope.launch {
                        val r = vm.addToPlaylist(id, listOf(song))
                        toastResult(context, r)
                    }
                }
            },
            onDismiss = { addingSongs = false }
        )
    }

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
            onAddToPlaylist = { addToPlaylistFor = listOfNotNull(infoFor); infoFor = null },
            onShare = {
                infoFor?.let { s -> shareSong(context, s) }
                infoFor = null
            },
            onDownload = { infoFor?.let(NebulaDownloads::enqueue); infoFor = null },
            onRemoveDownload = { infoFor?.let { NebulaDownloads.remove(it.videoId) }; infoFor = null },
            // Taking the song out of THIS playlist — the reason you opened its menu while
            // looking at the playlist. Only offered on the user's own playlists, not on
            // the built-in Downloaded one, which mirrors the download index and is
            // rebuilt from it.
            onRemoveFromPlaylist = if (selectedId != null && canRemoveFromPlaylist) {
                {
                    infoFor?.let { song ->
                        selectedId?.let { id -> vm.removeSong(id, song.videoId) }
                    }
                    infoFor = null
                }
            } else {
                null
            },
            onShowInfo = { infoFor = null },
            allowNavigation = false,
            onListenTogether = {
                infoFor = null
                onListenTogether()
            },
            onRefetch = {
                infoFor?.let { nebula.music.data.SearchRepository.invalidateStream(it.videoId) }
                infoFor = null
                android.widget.Toast.makeText(context, "Refetched", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    addToPlaylistFor?.let { songsToAdd ->
        playlists?.let { p ->
            AddToPlaylistDialog(
                songs = songsToAdd,
                playlists = p,
                onAddToExisting = { id ->
                    scope.launch {
                        val r = vm.addToPlaylist(id, songsToAdd)
                        addToPlaylistFor = null
                        toastResult(context, r)
                    }
                },
                onCreateWith = { n ->
                    scope.launch {
                        vm.createWithSongs(n, songsToAdd)
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

/**
 * Playlist interchange: Title,Artist,Album,Duration,URL. Duration is blank —
 * playlist rows carry no duration column — but the header keeps the shape
 * Echo-compatible so files round-trip between the two apps.
 */
private fun playlistCsv(name: String, songs: List<PlaylistSongEntity>): String {
    fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
    return buildString {
        appendLine("Title,Artist,Album,Duration,URL")
        for (s in songs) {
            appendLine(
                listOf(
                    cell(s.title),
                    cell(s.artist),
                    cell(""),
                    cell(""),
                    cell("https://music.youtube.com/watch?v=${s.videoId}")
                ).joinToString(",")
            )
        }
    }
}

@Composable
private fun DownloadedPlaylistContent(
    name: String,
    playlistThumbnail: String? = null,
    playlistId: Long?,
    sortChoice: PlaylistSortChoice,
    onSortChange: (PlaylistSortChoice) -> Unit,
    songs: List<PlaylistSongEntity>,
    shuffleOn: Boolean,
    repeatMode: String,
    repeatOn: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    // Whole-playlist queue actions. Take no argument: the content composable only ever
    // shows THIS playlist's rows, so passing the list back in would be a chance to pass
    // the wrong one.
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    // Delete is offered only when this is a playlist the user made. `dao.delete` refuses
    // isSystem rows, so a Delete on the built-in Downloaded list would look live and do
    // nothing — the same guard HomeScreen and PlaylistsScreen already apply.
    canDelete: Boolean,
    // Whether songs may be added to or removed from this playlist. False for the
    // built-in Downloaded list, which mirrors the download index.
    canEditSongs: Boolean,
    onAddSongs: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlaySong: (SearchResult) -> Unit,
    onShowInfo: (SearchResult) -> Unit,
    onBack: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDownload by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // System file picker: the SAF handles storage permission, no new manifest entry.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(playlistCsv(name, songs).toByteArray())
            }
        }
    }

    // Owned by the ViewModel, not by this composable: `remember` here reset to Custom
    // on every visit, so a Name-sorted list reverted the moment the user went back and
    // returned. See [PlaylistsViewModel.sorts].
    val sortType = sortChoice.type
    val sortDescending = sortChoice.descending
    // Guard against YouTube IDs leaking through as playlist names.
    val displayName = if (name.matches(Regex("^[a-zA-Z0-9_-]{8,20}$"))) "Playlist" else name

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
            // 3-dots overflow menu, in the top right corner of the header, opposite
            // the Back button. It belongs to the screen rather than to the title, so
            // it sits above everything instead of interrupting the name and the cover.
            // The same chunky button the Local screen uses.
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

                    // The whole playlist, queued. These are the same two actions the
                    // per-song menu offers, widened from one song to the list — which is
                    // the point: playing "this playlist" after the current song is a
                    // decision you make once, not 80 times.
                    DropdownMenuItem(
                        text = { Text("Play next", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            overflowOpen = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            overflowOpen = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Add to playlist", color = MaterialTheme.colorScheme.onSurface)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            overflowOpen = false
                            onAddToPlaylist()
                        }
                    )
                    // Add songs to THIS playlist. "Add to playlist" above adds the whole
                    // playlist to another one; this is the opposite direction, and without
                    // it there is no way to build a playlist from the page you are on.
                    if (canEditSongs) {
                        DropdownMenuItem(
                            text = {
                                Text("Add songs", color = MaterialTheme.colorScheme.onSurface)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                onAddSongs()
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)

                    // Speed dial: Echo's `pin_to_speed_dial` / `unpin_from_speed_dial`.
                    // Hidden on the Downloaded playlist like Rename and Delete, because
                    // pinning a list that mirrors the download index gives a permanent tile
                    // to something the app already puts on Home.
                    if (canDelete) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isPinned) "Unpin from speed dial"
                                    else "Pin to speed dial",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                onTogglePin()
                            }
                        )
                    }

                    // Delete is hidden for the built-in Downloaded list: dao.delete refuses isSystem rows,
                    // so a Delete there would look enabled and do nothing.
                    DropdownMenuItem(
                        text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            overflowOpen = false
                            showRename = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export CSV", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            overflowOpen = false
                            exportLauncher.launch("${name}.csv")
                        }
                    )
                    // Share moved to the inline button below the Play all row.
                    // Download all, above Delete: it is additive (nothing is lost, cancel is one tap away)
                    // where Delete is permanent. Offer it only when there is something to
                    // fetch — a menu item reading "Download all" over an empty list is a
                    // dead control.
                    if (songs.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Download all", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                overflowOpen = false
                                confirmingDownload = true
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                    // Delete playlist, last in the menu and in the error colour so it does not
                    // read as one of the harmless ones. It was missing here even though the
                    // Home card's overflow has had Delete all along, which left the one screen
                    // you open a playlist from as the one place you could not remove it.
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                overflowOpen = false
                                confirmingDelete = true
                            }
                        )
                    }
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
                thumbnailUrl = playlistThumbnail ?: songs.firstOrNull()?.thumbnailUrl,
                size = 104.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Play all stretched to the full width.
                    // Row 2: Shuffle + Share, icon only, each half the width.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoxPlayAllButton(onClick = onPlay, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoxPillButton(
                            icon = Icons.Filled.Shuffle,
                            accent = if (shuffleOn) SunnyYellow else MaterialTheme.colorScheme.surface,
                            onClick = onShuffle,
                            modifier = Modifier.weight(1f)
                        )
                        VoxPillButton(
                            icon = Icons.Filled.Share,
                            accent = MaterialTheme.colorScheme.surface,
                            onClick = {
                                val text = songs.joinToString("\n") {
                                    "${it.title} - ${it.artist} https://music.youtube.com/watch?v=${it.videoId}"
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$name\n\n$text")
                                        },
                                        null
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                if (sortType == PlaylistSortType.DATE_ADDED) {
                    // The one sort that needs the stored row's addedAt rather than a
                    // title or an artist, so it sorts the rows and not the fetched list.
                    val byDate = songs.sortedBy { it.addedAt }
                    (if (sortDescending) byDate.reversed() else byDate).map { it.toSearchResult() }
                } else {
                    sortRemoteTracks(songs.map { it.toSearchResult() }, sortType, sortDescending)
                }
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

    // Confirmation for a fan-out download, same shared dialog the Home rows and YouTube
    // playlist pages use. The count is what is still MISSING, not the list length, so
    // running it twice says so instead of promising the same download again.
    if (confirmingDownload) {
        DownloadAllDialog(
            count = songs.count { !NebulaDownloads.isDownloaded(it.videoId) },
            onConfirm = {
                confirmingDownload = false
                val queued = NebulaDownloads.enqueueAll(songs.map { it.toSearchResult() })
                Toast.makeText(
                    context,
                    if (queued == 0) "Already downloaded" else "Downloading $queued songs",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { confirmingDownload = false }
        )
    }

    // Confirmation before the delete, never straight from the menu item: it is the one
    // action here that destroys work, and a mis-tap on a 3-dot menu is exactly how a
    // playlist with 200 songs gets lost. Same wording as the Home card's dialog, so the
    // two places that delete read identically.
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "\"$name\" and its ${songs.size} tracks will be removed. " +
                        "Downloaded songs stay on disk."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
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
 * The caller's modifier sizes the pill itself (`weight(1f)` → full row width), and the
 * shadow is drawn behind that same Box, so the two can never disagree. They used to be
 * siblings: the shadow took `matchParentSize` of the row slot while the visible pill
 * only wrapped its content, and the uncovered remainder read as a black slab beside the
 * pill. Drawing the shadow behind the pill removes the size mismatch by construction.
 *
 * Height is pinned to [PLAY_ALL_HEIGHT] to match the shuffle and repeat buttons beside
 * it. Sized by its own vertical padding it came out shorter than the 44dp square chips,
 * so the row read as three differently sized controls rather than one set.
 */
@Composable
internal fun VoxPlayAllButton(onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = SunnyYellow) {
    val shape = RoundedCornerShape(14.dp)
    val shadow = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .height(PLAY_ALL_HEIGHT)
            .drawBehind {
                drawRoundRect(
                    color = shadow,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            }
            .clip(shape)
            .background(accent)
            .border(3.dp, BorderBlack, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

/**
 * Half-width pill used in the second control row (Shuffle + Share). Same height and
 * 3D shadow as VoxPlayAllButton so the two rows read as one set. Icon only — both call
 * sites dropped their text, so the label param is gone.
 */
@Composable
internal fun VoxPillButton(
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val shadow = MaterialTheme.colorScheme.outline
    // Surface-filled pills carry the theme ink, not BorderBlack: a black icon on a
    // near-black face disappears in every dark palette (Shuffle / Share in AMOLED).
    // Same luminance rule the theme itself uses for its on-colors.
    val ink = if (accent.luminance() > 0.5f) BorderBlack else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(PLAY_ALL_HEIGHT)
            .drawBehind {
                drawRoundRect(
                    color = shadow,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            }
            .clip(shape)
            .background(accent)
            .border(3.dp, BorderBlack, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp)
        )
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

/**
 * Offset shadow + 3px border, the card primitive every screen here is built from.
 *
 * `internal` rather than `private` because AlbumDetailScreen and LocalSongsScreen now put
 * the same three controls beside their headers; duplicating this shape per screen is how
 * three near-identical buttons drift apart.
 */
@Composable
internal fun ChunkyIconButton(
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
