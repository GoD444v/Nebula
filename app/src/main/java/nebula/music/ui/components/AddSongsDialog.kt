package nebula.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nebula.music.data.SearchRepository
import nebula.music.data.models.SearchResult
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.TextGrey

/**
 * Pick songs to add to a playlist, by searching.
 *
 * The inverse of [AddToPlaylistDialog]: that one picks a playlist for a song, this one
 * picks songs for a playlist. Without it, the only way to add a song to a playlist you are
 * looking at is to find that song somewhere else first and use its menu — which is not a
 * way to build a playlist.
 *
 * Deliberately a search and not a browsable catalogue: the app has no full song list to
 * browse, and typing a title is the fastest way to name a song the user already has in
 * mind. Songs already in the playlist are still listed (with a tick) and tapping them
 * does nothing, because a duplicate add is silently ignored by the database.
 */
@Composable
fun AddSongsDialog(
    onPick: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val repo = remember { SearchRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var added by remember { mutableStateOf(emptySet<String>()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced: a search per keystroke is a request per letter, and YouTube's search
    // endpoint is not fast. 350ms is long enough to skip the middle of a word.
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        val found = repo.search(query.trim()).items
        results = found
        searching = false
    }

    ChunkyWindow(title = "Add songs", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search for a song to add") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (query.trim().length < 2) {
                Text(
                    "Type at least two characters",
                    fontSize = 12.sp,
                    color = TextGrey(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else if (searching && results.isEmpty()) {
                Text(
                    "Searching…",
                    fontSize = 12.sp,
                    color = TextGrey(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else if (results.isEmpty()) {
                Text(
                    "No songs found",
                    fontSize = 12.sp,
                    color = TextGrey(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(results, key = { it.videoId }) { song ->
                        val isAdded = song.videoId in added
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isAdded) return@clickable
                                    added = added + song.videoId
                                    onPick(song)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontWeight = FontWeight.Black,
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
                            if (isAdded) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Added",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
