package nebula.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import nebula.music.data.SearchRepository
import nebula.music.data.models.SongStats
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal
import nebula.music.ui.theme.TextGrey

/**
 * Per-song information, mirroring Echo's ShowMediaInfo field set in Nebula's 3D
 * style: two fields per row, chunky window, scrollable.
 */
@Composable
fun SongInfoDialog(
    title: String,
    artist: String,
    artworkUrl: String?,
    videoId: String = "",
    durationMs: Long? = null,
    volumePercent: Int? = null,
    stats: SongStats? = null,
    onDismiss: () -> Unit
) {
    var mediaInfo by remember { mutableStateOf<SearchRepository.MediaInfo?>(null) }
    LaunchedEffect(videoId) {
        if (videoId.isNotBlank()) {
            mediaInfo = SearchRepository().getMediaInfo(videoId)
        }
    }

    ChunkyWindow(title = "Song info", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artworkUrl != null && artworkUrl.isNotBlank()) {
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
                        Text("?", fontWeight = FontWeight.Black, fontSize = 28.sp)
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(artist, fontSize = 13.sp, color = TextGrey())
                }
            }

            Spacer(Modifier.height(16.dp))

            // Two fields per row, Echo's layout.
            InfoPair(
                "Duration", durationMs?.let { if (it > 0) formatDuration(it) else null },
                "Media ID", videoId.takeIf { it.isNotBlank() }
            )
            InfoPair(
                "Views", mediaInfo?.viewCount?.toIntOrNull()?.takeIf { it > 0 }?.toString(),
                "Itag", mediaInfo?.itag?.toString()
            )
            InfoPair(
                "Mime type", mediaInfo?.mimeType,
                "Bitrate", mediaInfo?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} Kbps" }
            )
            InfoPair(
                "Sample rate", mediaInfo?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" },
                "File size", mediaInfo?.contentLength?.takeIf { it > 0 }?.let { formatBytes(it) }
            )
            InfoPair(
                "Loudness", mediaInfo?.loudnessDb?.let { String.format("%.1f dB", it) },
                "Volume", volumePercent?.let { "$it%" }
            )

            if (stats != null && (stats.playCount > 0 || stats.totalPlayMs > 0L)) {
                Spacer(Modifier.height(4.dp))
                Text("Play stats", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TextGrey())
                InfoPair(
                    "Play count", if (stats.playCount > 0) stats.playCount.toString() else null,
                    "Time played", if (stats.totalPlayMs > 0) formatDuration(stats.totalPlayMs) else null
                )
                stats.lastPlayedAtMs?.takeIf { it > 0 }?.let {
                    InfoRow("Last played", formatDate(it))
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("Not played yet", fontSize = 12.sp, color = TextGrey())
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun InfoPair(label1: String, value1: String?, label2: String, value2: String?) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            if (value1 != null) InfoRow(label1, value1)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            if (value2 != null) InfoRow(label2, value2)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = TextGrey())
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}