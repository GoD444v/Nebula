package nebula.music.ui.screens

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import coil3.compose.SubcomposeAsyncImage
import nebula.music.data.HomePrefs
import nebula.music.data.download.NebulaDownloads
import nebula.music.ui.components.AddToPlaylistDialog
import nebula.music.ui.components.ImmersiveMode
import nebula.music.ui.components.SongMenuSheet
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.NebulaTheme
import nebula.music.ui.theme.NeonPink
import nebula.music.viewmodel.PlayerViewModel
import nebula.music.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ponytail: scrollable sheet; queue taps replay (playAt arrives with playlists)
@androidx.media3.common.util.UnstableApi
@Composable
fun FullSheetPlayer(
    vm: PlayerViewModel,
    onClose: () -> Unit,
    playlistsVm: PlaylistsViewModel? = null,
    onOpenQueue: () -> Unit = {},
    onListenTogether: () -> Unit = {},
    onBrowse: (String, String) -> Unit = { _, _ -> }
) {
    var showSongMenu by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsFull by remember { mutableStateOf(false) }
    // Volume is a button-then-slider: tap reveals it.
    var volumeOpen by remember { mutableStateOf(false) }
    var eqOpen by remember { mutableStateOf(false) }

    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val volMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var vol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // Without these the close button sat under the status bar clock and
            // the queue list sat under the gesture bar.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChunkyBtn(Icons.Filled.KeyboardArrowDown, "Close", MaterialTheme.colorScheme.surface, 48.dp, onClick = onClose)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (HomePrefs.equalizerEnabled) {
                    ChunkyBtn(Icons.Filled.Equalizer, "Equalizer", MaterialTheme.colorScheme.surface, 48.dp, onClick = { eqOpen = true })
                }
                // The same overflow the search rows and playlist rows use.
                if (vm.currentSong() != null) {
                    ChunkyBtn(
                        Icons.Filled.MoreVert, "More options",
                        MaterialTheme.colorScheme.surface, 48.dp,
                        onClick = { showSongMenu = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val artAlpha by animateFloatAsState(
            targetValue = if (showLyrics) 0f else 1f,
            animationSpec = tween(500),
            label = "artAlpha"
        )
        // Thumbnail is 280dp, lyrics 360dp. The box grows/shrinks so the title always
        // sits right under the artwork (no dead space) while keeping the gap from the
        // top icon row constant. Artwork is top-aligned, not centred, for the same reason.
        //
        // Keyed off showLyrics, NOT off artAlpha. artAlpha only reaches its settled value
        // on the LAST frame of the fade, so `showLyrics || artAlpha < 1f` stayed true for
        // the whole hide: the lyrics faded out with the box stuck at 360dp, then the box
        // shrank on a SECOND 500ms tween after they were already gone — hide ran twice as
        // long as show, with a dead pause between the two halves. Off the state, the
        // shrink and the crossfade start on the same frame, so hide is show backwards.
        val boxHeight by animateDpAsState(
            targetValue = if (showLyrics) 360.dp else 280.dp,
            animationSpec = tween(500),
            label = "boxHeight"
        )
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(boxHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            ArtBox(
                280.dp,
                vm.currentSongTitle,
                vm.currentVideoId,
                vm.currentSong()?.thumbnailUrl,
                modifier = Modifier.alpha(artAlpha)
            )
            if (showLyrics || artAlpha < 1f) {
                LyricsScreen(
                    vm = vm,
                    onFullScreen = { showLyricsFull = true },
                    // Same animated height as the box, so the card shrinks WITH it on
                    // hide instead of hanging 80dp past the container and over the title.
                    height = boxHeight,
                    modifier = Modifier.alpha(1f - artAlpha)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(vm.currentSongTitle, color = MaterialTheme.colorScheme.onSurface)
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

        Spacer(modifier = Modifier.height(8.dp))

        if (vm.isLoading) {
            Text("Loading...", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        } else if (vm.currentArtist.isNotBlank()) {
            Text(vm.currentArtist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val duration = vm.durationMs.coerceAtLeast(1L)
        Slider(
            value = vm.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(vm.positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(fmtTime(vm.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChunkyBtn(
                Icons.Filled.Shuffle, "Shuffle",
                if (vm.shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, 52.dp,
                onClick = { vm.toggleShuffle() }
            )
            ChunkyBtn(Icons.Filled.SkipPrevious, "Previous", MaterialTheme.colorScheme.surface, 56.dp, onClick = { vm.previous() })
            // secondary + onSecondary, exactly as the MiniPlayer draws this same action.
            // Was primary + onSurface, which was both a different colour from the
            // MiniPlayer and low-contrast ink for a light pink background.
            ChunkyBtn(
                if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play",
                MaterialTheme.colorScheme.secondary, 72.dp,
                tint = MaterialTheme.colorScheme.onSecondary,
                onClick = { vm.togglePlay() }
            )
            ChunkyBtn(Icons.Filled.SkipNext, "Next", MaterialTheme.colorScheme.surface, 56.dp, onClick = { vm.next() })
            ChunkyBtn(
                if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Repeat",
                if (vm.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                52.dp,
                onClick = { vm.toggleRepeat() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Radio — Echo's one-tap radio: fresh related songs replace
        // everything after the current one.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Start Radio", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { vm.startRadio() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vm.isLoadingRadio) {
                    Text(
                        "Finding songs...",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Start Radio", tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Start Radio",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics — one tap opens the panel. Full screen is a button INSIDE the
        // panel (LyricsScreen's onFullScreen), so this card is a single target
        // and needs no second affordance of its own.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Lyrics", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { showLyrics = !showLyrics }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showLyrics) "Hide Lyrics" else "Lyrics",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save offline. Media3 does the work in the background via
        // NebulaDownloadService; this only enqueues and reads back the state so
        // the card can say "Downloaded" instead of offering the action again.
        val currentDownload by NebulaDownloads.downloads
            .map { it[vm.currentVideoId] }
            .collectAsState(initial = null)
        val downloadState = currentDownload?.state
        val alreadySaved = downloadState == Download.STATE_COMPLETED

        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    if (alreadySaved) "Downloaded" else "Download",
                    color = BorderBlack
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable(enabled = !alreadySaved && vm.currentSong() != null) {
                        // The whole song, so artist and artwork reach the download index
                        // and can populate the Downloaded playlist later.
                        vm.currentSong()?.let { NebulaDownloads.enqueue(it) }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (alreadySaved) Icons.Filled.Check else Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        alreadySaved -> "Downloaded"
                        downloadState == Download.STATE_DOWNLOADING ->
                            "Downloading… ${currentDownload?.percentDownloaded?.toInt() ?: 0}%"
                        downloadState == Download.STATE_QUEUED -> "Queued"
                        downloadState == Download.STATE_FAILED -> "Failed — tap to retry"
                        else -> "Download"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Volume: button-then-slider.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text("Volume", color = BorderBlack) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { volumeOpen = !volumeOpen }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (volumeOpen) "Hide volume" else "Volume",
                    fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // Volume slider moved below "Up Next" (see bottom of sheet).
        Spacer(modifier = Modifier.height(16.dp))

        // The per-song overflow for whatever is playing. Reads currentSong() again here
        // rather than caching it in a variable above, so the sheet and the header button
        // can never disagree about which song is playing.
        val menuSong = vm.currentSong()
        if (showSongMenu && menuSong != null) {
            SongMenuSheet(
                song = menuSong,
                isDownloaded = NebulaDownloads.isDownloaded(menuSong.videoId),
                onDismiss = { showSongMenu = false },
                onPlayNext = { vm.playNext(menuSong); showSongMenu = false },
                onAddToQueue = { vm.addToQueue(menuSong); showSongMenu = false },
                // Same picker as search rows: the menu closes first so nothing stacks.
                onAddToPlaylist = {
                    showSongMenu = false
                    addingToPlaylist = true
                },
                // Already playing, so a radio from here would only replace it with
                // something else. Left off rather than wired to a surprising action.
                onShare = {
                    showSongMenu = false
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${menuSong.videoId}"
                                )
                            },
                            null
                        )
                    )
                },
                onDownload = { NebulaDownloads.enqueue(menuSong); showSongMenu = false },
                onRemoveDownload = { NebulaDownloads.remove(menuSong.videoId); showSongMenu = false }
            )
        }

        if (addingToPlaylist && menuSong != null && playlistsVm != null) {
            val allPlaylists by playlistsVm.playlists.collectAsState()
            AddToPlaylistDialog(
                songs = listOf(menuSong),
                playlists = allPlaylists,
                onAddToExisting = { id ->
                    scope.launch {
                        val result = playlistsVm.addToPlaylist(id, listOf(menuSong))
                        addingToPlaylist = false
                        toastResult(context, result)
                    }
                },
                onCreateWith = { name ->
                    scope.launch {
                        playlistsVm.createWithSongs(name, listOf(menuSong))
                        addingToPlaylist = false
                        Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { addingToPlaylist = false }
            )
        }

        // Fullscreen lyrics: edge-to-edge dialog (under the status bar too),
        // same panel with no height caps. Back closes the dialog first.
        if (showLyricsFull) {
            BackHandler { showLyricsFull = false }
            Dialog(
                onDismissRequest = { showLyricsFull = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                // The dialog owns its own window, so the bars have to be hidden
                // on that window, not just the Activity's. The status/nav
                // padding below collapses to 0 once they are.
                ImmersiveMode(enabled = showLyricsFull)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        ChunkyBtn(Icons.Filled.KeyboardArrowDown, "Close", MaterialTheme.colorScheme.surface, 48.dp, onClick = { showLyricsFull = false })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LyricsScreen(vm = vm, expanded = true)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The queue list that used to live here was destructive: tapping a row called
        // playYouTubeSong -> play() -> queue.clear(); history.clear(), so every tap deleted
        // the entire queue. It also could not scroll independently (a non-lazy forEach inside
        // this verticalScroll Column) and highlighted by videoId, lighting up every copy of a
        // duplicated song. Replaced by the shared QueueSheet.
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Up Next (${vm.queueList.size})", color = BorderBlack)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                    .clickable { onOpenQueue() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Up Next (${vm.queueList.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (volumeOpen) {
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = vol.toFloat(),
                onValueChange = { v ->
                    vol = v.toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0)
                },
                valueRange = 0f..volMax.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Equalizer studio: a full-screen Dialog, kept OUT of the scrollable Column so it
    // is not nested inside verticalScroll (which can stop the dialog window showing).
    if (eqOpen) {
        EqualizerScreen(onDismiss = { eqOpen = false })
    }
}



@Composable
private fun ArtBox(side: Dp, title: String, videoId: String, thumbnailUrl: String? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    // modifier rides the OUTER box so the alpha fades the art and its extrusion together.
    Box(modifier = modifier.size(side)) {
        // The extrusion every other chunky element in this sheet has. The artwork was the
        // one that lacked it, which is why the big art read flat next to the buttons.
        Box(
            modifier = Modifier
                .offset(x = 6.dp, y = 6.dp)
                .size(side)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .size(side)
                .clip(shape)
                .background(MintTeal)
                .border(4.dp, BorderBlack, shape),
            contentAlignment = Alignment.Center
        ) {
            ArtImage(artUrls(videoId, thumbnailUrl), title)
            // The black frame, redrawn ON TOP. A child draws after the parent's own draw
            // modifiers, so the border above was painted over by the fillMaxSize photo and
            // by the letter tile — the art had no black edge at all. Sibling, declared last.
            Box(modifier = Modifier.fillMaxSize().clip(shape).border(4.dp, BorderBlack, shape))
        }
    }
}

/**
 * Album-art URLs, best first. [ArtImage] renders the first one that loads.
 *
 * The queue's own [thumbnailUrl] is an InnerTube `lh3.googleusercontent.com`
 * thumbnail that tops out around 544px, and it was being tried FIRST — so a 280dp
 * box upscaled a ~544px image and the art read soft. `maxresdefault.jpg` is
 * 1280x720 and has to lead. It does not exist for every track, so the queue
 * thumbnail stays as the second try rather than the art falling to a letter tile.
 *
 * Local songs are the exception and keep the queue URL first: their videoId is
 * `local:content://…`, so i.ytimg.com has nothing for them and the MediaStore uri
 * is the only artwork that exists.
 */
internal fun artUrls(videoId: String, thumbnailUrl: String?): List<String> {
    if (videoId.isBlank()) return emptyList()
    val queueArt = thumbnailUrl?.takeIf { it.isNotBlank() }
    return if (videoId.startsWith("local:")) listOfNotNull(queueArt)
    else listOfNotNull("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg", queueArt)
}

/**
 * First candidate that loads wins; a song with no usable art lands on the letter tile.
 * The next URL renders itself straight out of the error slot, so there is no retry state
 * to reset and no request goes out until the one before it has actually failed.
 */
@Composable
private fun ArtImage(urls: List<String>, title: String) {
    val url = urls.firstOrNull()
    if (url == null) {
        SheetLetter(title, 64)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { SheetLetter(title, 64) },
        error = { ArtImage(urls.drop(1), title) }
    )
}

@Composable
/**
 * A chunky extruded button.
 *
 * [tint] is the ink drawn on [bg], defaulting to `onSurface`. It was previously hardcoded,
 * which meant any non-`onSurface` background got `onSurface` ink regardless of whether
 * that was readable -- the play/pause button is the case that mattered: on `primary` pink
 * in Classic80s Vox that is 3.8:1, while the MiniPlayer's same action on `secondary` is
 * well clear. Passing the pair keeps the two screens identical by construction.
 */
private fun ChunkyBtn(
    icon: ImageVector,
    description: String,
    bg: Color,
    size: Dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
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
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
        ) {
            Icon(icon, contentDescription = description, tint = tint)
        }
    }
}

@Composable
private fun SheetLetter(title: String, fontSize: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MintTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
