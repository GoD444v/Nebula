package nebula.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import nebula.music.data.SearchRepository
import nebula.music.data.models.SearchResult
import nebula.music.ui.components.SongInfoDialog
import nebula.music.ui.components.SongMenuSheet
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.TextGrey
import nebula.music.viewmodel.PlayerViewModel

/**
 * Artist detail page: header art + top songs + album carousel.
 * browseId is an artist browse id ("UC...").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    browseId: String,
    title: String,
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String, String) -> Unit = { _, _ -> },
    onBrowse: (String, String) -> Unit = { _, _ -> },
    onListenTogether: () -> Unit = {}
) {
    val repo = remember { SearchRepository() }
    var page by remember(browseId) { mutableStateOf<SearchRepository.ArtistDetailPage?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuFor by remember { mutableStateOf<SearchResult?>(null) }
    var showingInfoFor by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(browseId) {
        page = repo.getArtistDetail(browseId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val current = page
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", fontWeight = FontWeight.Black, color = TextGrey())
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth()) {
                        Box {
                            Box(
                                modifier = Modifier.offset(x = 5.dp, y = 5.dp).size(120.dp).clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            Box(
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(3.dp, BorderBlack, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (current.thumbnailUrl.isBlank()) {
                                    Text(current.title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Black, fontSize = 40.sp)
                                } else {
                                    SubcomposeAsyncImage(
                                        model = current.thumbnailUrl, contentDescription = current.title,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.title.ifBlank { title }, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                            Spacer(Modifier.height(4.dp))
                            Text(current.subtitle, fontSize = 12.sp, maxLines = 1, color = TextGrey(), modifier = Modifier.basicMarquee())
                        }
                    }
                }
                if (current.topSongs.isNotEmpty()) {
                    item(key = "songs-head") { SectionBanner(title = "Top songs", isFirst = false) }
                    itemsIndexed(current.topSongs, key = { i, t -> "$i-${t.videoId}" }) { _, track ->
                        ArtistTrackRow(track = track, isPlaying = track.videoId == vm.currentVideoId,
                            onPlay = { vm.playFromHere(track, current.topSongs) },
                            onShowMenu = { menuFor = track })
                    }
                }
                if (current.albums.isNotEmpty()) {
                    item(key = "albums-head") { SectionBanner(title = "Albums", isFirst = false) }
                    item(key = "albums-row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(current.albums.size) { i ->
                                val a = current.albums[i]
                                Column(
                                    modifier = Modifier.width(120.dp).clickable { onOpenAlbum(a.browseId.ifBlank { a.browseId }, a.title) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(2.dp, BorderBlack, RoundedCornerShape(16.dp))) {
                                        if (a.thumbnailUrl.isBlank()) {
                                            Text(a.title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Black, fontSize = 32.sp)
                                        } else {
                                            SubcomposeAsyncImage(model = a.thumbnailUrl, contentDescription = a.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(a.title, fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Bold, modifier = Modifier.basicMarquee())
                                }
                            }
                        }
                    }
                }
                if (current.topSongs.isEmpty() && current.albums.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("Nothing to show here yet", fontWeight = FontWeight.Black, fontSize = 14.sp, color = TextGrey())
                        }
                    }
                }
            }
        }

        menuFor?.let { song ->
            SongMenuSheet(
                song = song,
                isDownloaded = nebula.music.data.download.NebulaDownloads.isDownloaded(song.videoId),
                onDismiss = { menuFor = null },
                onPlayNext = { vm.playNext(song); menuFor = null },
                onAddToQueue = { vm.addToQueue(song); menuFor = null },
                onViewArtist = {
                    menuFor = null
                    val bid = song.artistBrowseId
                    if (bid.isNotBlank()) onBrowse(bid, song.artist)
                    else scope.launch {
                        val found = nebula.music.data.SearchRepository().search(song.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                            .items.firstOrNull { it.browseId.isNotBlank() && it.title.equals(song.artist, ignoreCase = true) }
                            ?: nebula.music.data.SearchRepository().search(song.artist, nebula.music.data.SearchRepository.SearchFilter.ARTISTS)
                                .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { song.artist })
                    }
                },
                onViewAlbum = {
                    menuFor = null
                    val bid = song.albumBrowseId
                    if (bid.isNotBlank()) onBrowse(bid, song.title)
                    else scope.launch {
                        val found = nebula.music.data.SearchRepository().search(song.title, nebula.music.data.SearchRepository.SearchFilter.ALBUMS)
                            .items.firstOrNull { it.browseId.isNotBlank() }
                        if (found != null) onBrowse(found.browseId, found.title.ifBlank { song.title })
                    }
                },
                onRefetch = {
                    menuFor = null
                    nebula.music.data.SearchRepository.invalidateStream(song.videoId)
                    if (song.videoId == vm.currentVideoId) vm.retryPlayback()
                    android.widget.Toast.makeText(context, "Refetched", android.widget.Toast.LENGTH_SHORT).show()
                },
                onShowInfo = {
                    menuFor = null
                    showingInfoFor = song
                },
                onListenTogether = {
                    menuFor = null
                    onListenTogether()
                }
            )

        showingInfoFor?.let { s ->
            nebula.music.ui.components.SongInfoDialog(
                title = s.title, artist = s.artist, artworkUrl = s.thumbnailUrl, videoId = s.videoId,
                onDismiss = { showingInfoFor = null }
            )
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTrackRow(
    track: SearchResult,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShowMenu: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier.offset(x = 4.dp, y = 4.dp).fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp))
                .background(if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp)).clickable(onClick = onPlay).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondary)
                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (track.thumbnailUrl.isBlank()) {
                    Text(track.title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Black, fontSize = 22.sp)
                } else {
                    SubcomposeAsyncImage(model = track.thumbnailUrl, contentDescription = track.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(track.artist, fontSize = 12.sp, maxLines = 1, color = TextGrey(), modifier = Modifier.basicMarquee())
            }
            IconButton(onClick = onShowMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${track.title}", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SectionBanner(title: String, isFirst: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = if (isFirst) 0.dp else 8.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
