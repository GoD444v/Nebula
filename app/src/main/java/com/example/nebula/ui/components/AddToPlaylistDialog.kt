package com.example.nebula.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.nebula.data.db.dao.PlaylistWithCount
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.NeonPink
import com.example.nebula.ui.theme.SunnyYellow
import com.example.nebula.ui.theme.TextGrey

/**
 * "Add to playlist": a searchable list of the user's playlists, plus a row that makes a
 * new one and drops the songs straight in.
 *
 * The shape follows the reference implementation — search field, list, create-row, and a
 * create dialog that can be reached from inside the picker — because that ordering answers
 * the two questions a user actually has ("which one?" then "or a new one") without a
 * second trip through the Playlists tab.
 *
 * The Downloaded playlist appears in this list like any other. It is a normal row that
 * happens to be `isSystem`, so adding to it is allowed; only deleting is refused, and that
 * refusal lives in SQL.
 */
@Composable
fun AddToPlaylistDialog(
    songs: List<SearchResult>,
    playlists: List<PlaylistWithCount>,
    onAddToExisting: (playlistId: Long) -> Unit,
    onCreateWith: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (songs.isEmpty()) return
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    // Case-insensitive substring match on the name only. A user looking for a playlist is
    // typing words they remember, not matching metadata.
    val filtered = remember(query, playlists) {
        val q = query.trim()
        if (q.isEmpty()) playlists else playlists.filter { it.playlist.name.contains(q, ignoreCase = true) }
    }

    if (creating) {
        CreateAndAddDialog(
            onConfirm = { name ->
                creating = false
                onCreateWith(name)
            },
            onDismiss = { creating = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Create first, so it is the most reachable option when nothing matches.
                CreatePlaylistRow(onClick = { creating = true })

                Spacer(modifier = Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Text(
                        if (playlists.isEmpty()) "No playlists yet" else "No playlist matches \"$query\"",
                        fontSize = 13.sp,
                        color = TextGrey,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(filtered, key = { it.playlist.id }) { entry ->
                            PlaylistPickRow(
                                entry = entry,
                                onClick = { onAddToExisting(entry.playlist.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Create-the-playlist step.
 *
 * The songs go in with it. Creating an empty playlist and then hunting for where to add
 * them is the failure mode this avoids — and it is why the ViewModel exposes
 * `createWithSongs` rather than making the caller chain `create` then `addSongs`.
 */
@Composable
private fun CreateAndAddDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The selected songs go straight in.",
                    fontSize = 12.sp,
                    color = TextGrey
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreatePlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "New playlist",
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlaylistPickRow(entry: PlaylistWithCount, onClick: () -> Unit) {
    val accent = if (entry.playlist.isSystem) SunnyYellow else NeonPink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent)
                .border(3.dp, BorderBlack, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.playlist.name,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("${entry.songCount} tracks", fontSize = 12.sp, color = TextGrey)
        }
    }
}