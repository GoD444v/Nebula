package nebula.music.ui.screens

import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import nebula.music.data.DetailPage
import nebula.music.data.SearchRepository
import nebula.music.data.db.dao.PlaylistWithCount
import nebula.music.ui.components.ChunkyAction
import nebula.music.ui.components.ChunkyWindow
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.NeonPink
import nebula.music.ui.theme.SunnyYellow
import nebula.music.ui.theme.TextGrey
import nebula.music.viewmodel.DownloadViewModel
import nebula.music.viewmodel.PlaylistsViewModel
import nebula.music.viewmodel.activeDownloadCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Placeholder tile colour, cycling the same three accents the mockup used. */
private val tileAccents = listOf(NeonPink, SunnyYellow, MintTeal)

@Composable
/**
 * The playlist list.
 *
 * No card offers Delete or Rename. The built-in Downloaded playlist must not be
 * deletable at all, and `PlaylistDao.delete` enforces that in SQL with
 * `AND isSystem = 0` — so a Delete button on a system card would look enabled and then
 * silently do nothing. Omitting the control everywhere is the consistent answer, and it
 * spares the UI a branch on `isSystem` that the DAO already guards.
 *
 * [onOpenPlaylist] opens a playlist's detail screen, which is where Play lives.
 */
fun PlaylistsScreen(
    vm: PlaylistsViewModel = viewModel(),
    onOpenDownloads: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit = {}
) {
    val playlists by vm.playlists.collectAsState()
    val downloadsVm: DownloadViewModel = viewModel()
    val downloads by downloadsVm.downloads.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SearchRepository() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Banner + downloads button, so the list below is entirely playlists.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Playlists", color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Playlists", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // One entry for create + all four import sources. Previously separate
            // + and import buttons opened two dialogs; a playlist starts either
            // empty or from somewhere, so one window covers both.
            NewPlaylistButton(onClick = { showAddDialog = true })

            Spacer(modifier = Modifier.width(12.dp))

            DownloadsIconButton(
                activeCount = activeDownloadCount(downloads),
                onClick = onOpenDownloads
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badge
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("${playlists.size} PLAYLISTS", color = BorderBlack, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(50.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "${playlists.size} PLAYLISTS",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { showAddDialog = true }) {
                    Text("Create your first playlist")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(playlists) { index, playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        accent = tileAccents[index % tileAccents.size],
                        onClick = { onOpenPlaylist(playlist.playlist.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistWindow(
            busy = busy,
            onCreate = { name ->
                vm.create(name)
                showAddDialog = false
            },
            onImportUrl = { url, name ->
                scope.launch {
                    busy = true
                    val msg = importPlaylistUrl(repo, vm, url, name)
                    busy = false
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            },
            onImportCsv = { name, text ->
                scope.launch {
                    busy = true
                    val count = vm.importCsv(name.ifBlank { "Imported songs" }, text)
                    busy = false
                    Toast.makeText(
                        context,
                        if (count == 0) "No songs matched" else "Imported $count songs",
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddDialog = false
                }
            },
            onImportList = { name, text ->
                scope.launch {
                    busy = true
                    val count = vm.importTracklist(name.ifBlank { "Imported songs" }, text.lines())
                    busy = false
                    Toast.makeText(
                        context,
                        if (count == 0) "No songs matched" else "Imported $count songs",
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddDialog = false
                }
            },
            onDismiss = { if (!busy) showAddDialog = false }
        )
    }
}

internal const val CHOSIC_URL = "https://www.chosic.com/spotify-playlist-exporter/"
internal const val TUNEMYMUSIC_URL = "https://www.tunemymusic.com/transfer/spotify-to-file"

/**
 * Numbered steps with an optional tappable site link underneath. One helper
 * because the same steps render in the Add window and onboarding.
 */
@Composable
internal fun HelpText(text: String, linkUrl: String? = null) {
    val context = LocalContext.current
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    if (linkUrl != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            linkUrl,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(linkUrl))
                )
            }
        )
    }
}

/**
 * Shared YouTube-URL import: extract the list ID, resolve anonymously (raw ID
 * first, VL-prefixed fallback), save under the given or playlist title.
 * Returns the toast message. Used by the Playlists dialog and onboarding.
 */
internal suspend fun importPlaylistUrl(
    repo: SearchRepository,
    vm: PlaylistsViewModel,
    url: String,
    name: String
): String {
    val id = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)
    val page = if (id == null) {
        DetailPage("")
    } else {
        val first = repo.getDetail(id)
        if (first.tracks.isNotEmpty()) first
        else repo.getDetail(if (id.startsWith("VL")) id else "VL$id")
    }
    if (page.tracks.isEmpty()) return "Couldn't import that playlist"
    vm.createWithSongs(
        name.ifBlank { page.title.ifBlank { "Imported playlist" } },
        page.tracks
    )
    return "Imported ${page.tracks.size} songs"
}

/**
 * One row in the playlist list. Tapping it is the only affordance.
 *
 * There is deliberately no play button on the card. Two buttons on a row invites the
 * question "which one do I press", and a play button next to the name reads as "play this
 * one song" when it means "open this list". Opening the playlist and pressing Play there
 * is one unambiguous path, and the detail screen already has a Play button.
 */
@Composable
private fun PlaylistCard(
    playlist: PlaylistWithCount,
    accent: Color,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The first song's artwork, supplied by the query's subquery. The playlist
            // row's own thumbnailUrl column is never written by any call site, so
            // reading it here always yielded null and every card showed the lettered
            // accent tile. Accent tile now means only "this playlist has no songs".
            val thumbnail = playlist.coverThumbnailUrl
            if (!thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    // Crop, so a non-square cover fills the square rather than
                    // being letterboxed. Without this the same song looked right in
                    // search and wrong here.
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent)
                        .border(3.dp, BorderBlack, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.playlist.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${playlist.songCount} tracks",
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = TextGrey()
                )
            }
        }
    }
}

/**
 * "New playlist" header button.
 *
 * Same offset-shadow / 3px border / 16dp corner anatomy as [DownloadsIconButton], so the
 * header reads as one row of controls rather than two competing styles.
 */
@Composable
private fun NewPlaylistButton(onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(SunnyYellow)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New playlist",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * One window for create + all four import sources. Previously a + button and
 * an import button opened two windows; a playlist starts either empty or from
 * somewhere, so one window with a source row covers both. Sources: a YouTube
 * link, a CSV file (Nebula's own export, Spotify via Chosic, anything via
 * TuneMyMusic), or a pasted song list. No accounts anywhere.
 */
private enum class AddSource { NEW, NEBULA, YOUTUBE, SPOTIFY, OTHERS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPlaylistWindow(
    busy: Boolean,
    onCreate: (name: String) -> Unit,
    onImportUrl: (url: String, name: String) -> Unit,
    onImportCsv: (name: String, text: String) -> Unit,
    onImportList: (name: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    var source by remember { mutableStateOf(AddSource.NEW) }
    var name by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // One picker for every file source — Nebula, Spotify and Others all import
    // CSV. Content routes by source at pick time.
    val pickCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText().orEmpty()
            withContext(Dispatchers.Main) { onImportCsv(name, text) }
        }
    }
    ChunkyWindow(
        title = "Add playlist",
        onDismissRequest = onDismiss
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeFilterChip(label = "New", selected = source == AddSource.NEW, onClick = { source = AddSource.NEW })
            HomeFilterChip(label = "Nebula", selected = source == AddSource.NEBULA, onClick = { source = AddSource.NEBULA })
            HomeFilterChip(label = "YouTube", selected = source == AddSource.YOUTUBE, onClick = { source = AddSource.YOUTUBE })
            HomeFilterChip(label = "Spotify", selected = source == AddSource.SPOTIFY, onClick = { source = AddSource.SPOTIFY })
            HomeFilterChip(label = "Others", selected = source == AddSource.OTHERS, onClick = { source = AddSource.OTHERS })
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (source) {
            AddSource.NEW -> {
                HelpText("An empty playlist, ready to fill from any song menu.")
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = "Create",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (name.isNotBlank() && !busy) onCreate(name) }
                )
            }
            AddSource.NEBULA -> {
                HelpText(
                    "On any playlist → 3-dots → Export CSV, then pick the file " +
                        "here. Exact restore, no re-matching."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = if (busy) "Importing..." else "Pick file",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (!busy) pickCsv.launch(arrayOf("text/csv", "text/*")) }
                )
            }
            AddSource.YOUTUBE -> {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("YouTube playlist link") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HelpText(
                    "1. Open the playlist in YouTube / YouTube Music\n" +
                        "2. Share → Copy link → paste it here\n" +
                        "3. Public playlists only, no sign-in needed"
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = if (busy) "Importing..." else "Import",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (input.isNotBlank() && !busy) onImportUrl(input, name) }
                )
            }
            AddSource.SPOTIFY -> {
                HelpText(
                    "1. On the Spotify playlist, tap the 3 dots → copy the link\n" +
                        "2. Log in with Spotify at the exporter site and download the CSV\n" +
                        "3. Pick the file here",
                    CHOSIC_URL
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = if (busy) "Importing..." else "Pick file",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (!busy) pickCsv.launch(arrayOf("text/csv", "text/*")) }
                )
            }
            AddSource.OTHERS -> {
                HelpText(
                    "1. In your app, copy the playlist link\n" +
                        "2. Paste it at the transfer site and download the CSV\n" +
                        "3. Pick the file here — or paste " +
                        "one Artist - Title per line below",
                    TUNEMYMUSIC_URL
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChunkyAction(
                    label = if (busy) "Importing..." else "Pick file",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (!busy) pickCsv.launch(arrayOf("text/csv", "text/*")) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    minLines = 3,
                    label = { Text("One Artist - Title per line") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = if (busy) "Importing..." else "Import list",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { if (input.isNotBlank() && !busy) onImportList(name, input) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ChunkyAction(
            label = "Cancel",
            color = MaterialTheme.colorScheme.surface,
            onClick = onDismiss
        )
    }
}
/**
 * Header button into the download queue, pinned to the top-right of the banner.
 *
 * Built from the same primitives as [PlaylistCard] — offset shadow, 3px
 * [BorderBlack] border, 16dp corners — so it cannot drift from the VoxMusic look.
 * Deliberately NOT a full-width row: downloads are a queue with its own screen,
 * not a playlist, and a card-sized entry made it read as one.
 *
 * The badge counts downloads still in flight and is not composed at all when that
 * count is zero, rather than being drawn transparent.
 */
@Composable
fun DownloadsIconButton(activeCount: Int, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(BUTTON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MintTeal)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Downloads",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )

            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(NeonPink)
                        .border(2.dp, BorderBlack, RoundedCornerShape(50.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "$activeCount",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = BorderBlack
                    )
                }
            }
        }
    }
}

private val BUTTON_SIZE = 52.dp
