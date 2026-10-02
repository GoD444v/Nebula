package com.example.nebula.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.nebula.data.models.SongStats
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.TextGrey

/**
 * Per-song information, opened from the info button on a playlist row.
 *
 * Deliberately a dialog rather than a screen: the user opened it from a list and is
 * looking at one song, so replacing the list to show a single song's details would lose
 * their place. Scrollable because the field list grows with play stats.
 *
 * Rows whose value is unknown are omitted rather than shown blank. "—" reads as a bug; an
 * absent row reads as "we don't know that yet", which is true for duration before the
 * player has reported it and for stats before a song has ever been played.
 */
@Composable
fun SongInfoDialog(
    title: String,
    artist: String,
    artworkUrl: String?,
    durationMs: Long? = null,
    stats: SongStats? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song info") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (artworkUrl != null) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MintTeal)
                                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            artist,
                            fontSize = 13.sp,
                            color = TextGrey()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration only once known: it is unavailable before the player reports
                // it, and "—" would suggest a song with no length at all.
                if (durationMs != null && durationMs > 0) {
                    InfoRow("Duration", formatDuration(durationMs))
                }

                if (stats != null && stats.playCount > 0) {
                    InfoRow("Play count", stats.playCount.toString())
                }
                if (stats != null && stats.totalPlayMs > 0) {
                    InfoRow("Time played", formatDuration(stats.totalPlayMs))
                }
                if (stats != null && stats.lastPlayedAtMs != null && stats.lastPlayedAtMs > 0) {
                    InfoRow("Last played", formatDate(stats.lastPlayedAtMs))
                }

                if (stats == null || (stats.playCount == 0 && stats.totalPlayMs == 0L)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Not played yet",
                        fontSize = 12.sp,
                        color = TextGrey()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = TextGrey()
        )
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Milliseconds to m:ss, or h:mm:ss past an hour. */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun formatDate(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMs))
}