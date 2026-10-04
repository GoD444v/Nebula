package nebula.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import nebula.music.data.GenreItem
import nebula.music.data.SearchRepository
import nebula.music.data.models.SearchResult
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.OnboardingStore
import nebula.music.ui.theme.ThemeStore
import nebula.music.ui.theme.VoxPalettes
import nebula.music.ui.theme.paletteById
import nebula.music.ui.theme.toPalette
import nebula.music.viewmodel.PlaylistsViewModel
import kotlinx.coroutines.launch

/**
 * First-launch flow: genres → import → theme → artists. Each page persists
 * its own data immediately, so killing the app mid-flow never loses earlier
 * picks — only the unfinished page repeats. Skip on any page jumps to the end
 * (marking done, so onboarding never loops).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var page by remember { mutableStateOf(0) }
    val context = LocalContext.current
    when (page) {
        0 -> GenrePage(
            onSkip = {
                OnboardingStore.markDone(context)
                onComplete()
            },
            onContinue = { picked ->
                OnboardingStore.saveGenres(
                    context, picked.map { Triple(it.title, it.browseId, it.params) }
                )
                page = 1
            }
        )
        1 -> ImportPage(
            onSkip = { page = 2 },
            onContinue = { page = 2 }
        )
        2 -> ThemePage(
            onSkip = { page = 3 },
            onContinue = { page = 3 }
        )
        else -> ArtistPage(
            onSkip = {
                OnboardingStore.markDone(context)
                onComplete()
            },
            onDone = { seeds ->
                OnboardingStore.saveArtists(context, seeds)
                OnboardingStore.markDone(context)
                onComplete()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePage(
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    // Read live: setPalette flips this state, NebulaTheme repaints this very
    // screen, so the picker previews for real instead of showing swatches.
    val picked = ThemeStore.paletteId
    val pal = paletteById(picked)
    val options = VoxPalettes + ThemeStore.custom.toPalette()
    OnboardingShell(
        title = "Make it yours",
        subtitle = "Pick a palette — the whole app repaints live, including this screen.",
        skipLabel = "Skip",
        onSkip = onSkip,
        actionLabel = "Continue",
        onAction = onContinue
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { palette ->
                PaletteDot(
                    palette = palette,
                    selected = picked == palette.id,
                    tickColor = pal.onBg,
                    onClick = { ThemeStore.setPalette(context, palette.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenrePage(
    onSkip: () -> Unit,
    onContinue: (selectedGenres: List<GenreItem>) -> Unit
) {
    var genres by remember { mutableStateOf<List<GenreItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        launch {
            genres = SearchRepository().moodAndGenres()
            loading = false
        }
    }

    OnboardingShell(
        title = "Welcome to Nebula",
        subtitle = "Pick your vibes — we'll shape your home feed around them.",
        skipLabel = "Skip",
        onSkip = onSkip,
        actionLabel = "Continue",
        onAction = { onContinue(genres.filter { it.title in selected }) }
    ) {
        if (loading) {
            Text(
                "Loading genres...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                genres.forEach { genre ->
                    val isSelected = genre.title in selected
                    HomeFilterChip(
                        label = genre.title,
                        selected = isSelected,
                        onClick = {
                            selected = if (isSelected) selected - genre.title
                            else selected + genre.title
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPage(
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SearchRepository() }
    val playlistsVm: PlaylistsViewModel = viewModel()
    var url by remember { mutableStateOf("") }
    var listText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    OnboardingShell(
        title = "Bring your music",
        subtitle = "Import a YouTube playlist or paste a song list. No accounts, ever.",
        skipLabel = "Skip",
        onSkip = onSkip,
        actionLabel = "Continue",
        onAction = onContinue
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OnboardCard(title = "YouTube link") {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Playlist link") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HelpText(
                    "Share → Copy link in YouTube / YouTube Music, paste it " +
                        "here. Public playlists only."
                )
                Spacer(modifier = Modifier.height(8.dp))
                PinkButton(
                    label = if (busy) "Importing..." else "Import link",
                    enabled = url.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val msg = importPlaylistUrl(repo, playlistsVm, url, "")
                            busy = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            OnboardCard(title = "Spotify song list") {
                HelpText(
                    "In Spotify, select the songs → copy, then open the exporter " +
                        "site to download them as CSV — or paste one " +
                        "Artist - Title per line below.",
                    CHOSIC_URL
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = listText,
                    onValueChange = { listText = it },
                    minLines = 3,
                    label = { Text("One Artist - Title per line") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PinkButton(
                    label = if (busy) "Importing..." else "Import list",
                    enabled = listText.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val count = playlistsVm.importTracklist("Imported songs", listText.lines())
                            busy = false
                            Toast.makeText(
                                context,
                                if (count == 0) "No songs matched" else "Imported $count songs",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            OnboardCard(title = "Anything else") {
                HelpText(
                    "Copy the playlist link in your app, convert it at the " +
                        "transfer site, then import the CSV file from " +
                        "Playlists → + → Others.",
                    TUNEMYMUSIC_URL
                )
            }
        }
    }
}

/** Chunky card container for onboarding sections. */
@Composable
private fun OnboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
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
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistPage(
    onSkip: () -> Unit,
    onDone: (seeds: List<Pair<String, List<String>>>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SearchRepository() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<Pair<String, List<String>>>>(emptyList()) }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            results = try {
                repo.search(q, SearchRepository.SearchFilter.ARTISTS).items
            } catch (_: Exception) {
                emptyList()
            }
            searching = false
        }
    }

    OnboardingShell(
        title = "Favorite artists?",
        subtitle = "We'll mix in songs related to theirs. Optional — skip freely.",
        skipLabel = "Skip",
        onSkip = onSkip,
        actionLabel = "Done",
        onAction = { onDone(picked) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Artist name") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = ::doSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search artists")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (searching) {
            Text("Searching...", fontSize = 14.sp)
        }
        results.forEach { artist ->
            if (picked.none { it.first == artist.title }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val vids = try {
                                    repo.search(artist.title, SearchRepository.SearchFilter.SONGS)
                                        .items.take(2).map { it.videoId }.filter { it.isNotBlank() }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                if (vids.isEmpty()) {
                                    Toast.makeText(
                                        context, "No songs found for ${artist.title}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    picked = picked + (artist.title to vids)
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        artist.title,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.Add, contentDescription = "Add ${artist.title}")
                }
            }
        }
        if (picked.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                picked.forEach { (name, _) ->
                    HomeFilterChip(
                        label = "$name ×",
                        selected = true,
                        onClick = { picked = picked.filter { it.first != name } }
                    )
                }
            }
        }
    }
}

/** Shared onboarding chrome: title, subtitle, scrollable body, Skip + pink action. */
@Composable
private fun OnboardingShell(
    title: String,
    subtitle: String,
    skipLabel: String,
    onSkip: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            title,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                skipLabel,
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(12.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            PinkButton(label = actionLabel, onClick = onAction)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** The chunky pink action button, shared by all onboarding pages. */
@Composable
private fun PinkButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(3.dp, BorderBlack, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
