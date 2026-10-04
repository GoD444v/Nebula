package nebula.music.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nebula.music.data.models.SearchResult
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.MintTeal


/**
 * The per-song overflow menu, in the app's own window idiom.
 *
 * Was a Material ModalBottomSheet: surfaceContainer background the Vox palettes
 * never defined, purple-tinted ink, no border. Now a ChunkyWindow — 3px border,
 * offset shadow, palette surface — with the same grouped items, so the menu
 * reads as part of Nebula instead of part of Material.
 *
 * Grouped the way the reference implementation groups it: transport and sharing, then
 * queue, then offline, then navigation and diagnostics. The grouping encodes priority —
 * what you reach for most is closest to the top — and a flat list of a dozen items would
 * lose that.
 *
 * Every item here does real work. Items the reference has that need things Nebula does not
 * (library toggling, Hide from Home, speed-dial pinning, ringtone, MP3 export) are absent
 * rather than present-and-broken, because a menu item that silently does nothing is worse
 * than a missing one.
 */
@Composable
fun SongMenuSheet(
    song: SearchResult,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onViewArtist: () -> Unit = {},
    onViewAlbum: () -> Unit = {},
    onRefetch: () -> Unit = {},
    onShowInfo: () -> Unit = {}
) {
    ChunkyWindow(
        title = song.title.ifBlank { "Song options" },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SheetHeader(song)

            // Transport and sharing.
            MenuItem("Start radio", Icons.Filled.Radio, onStartRadio)
            MenuItem("Add to playlist", Icons.Filled.PlaylistAdd, onAddToPlaylist)
            MenuItem("Share", Icons.Filled.Share, onShare)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )

            // Queue. Play next and Add to queue are genuinely different: one inserts
            // after the playing track, the other appends.
            MenuItem("Play next", Icons.Filled.PlaylistPlay, onPlayNext)
            MenuItem("Add to queue", Icons.Filled.QueueMusic, onAddToQueue)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )

            // Offline. The label flips rather than the row disappearing, so the row never
            // moves position under the user's thumb.
            if (isDownloaded) {
                MenuItem("Remove download", Icons.Filled.Delete, onRemoveDownload, destructive = true)
            } else {
                MenuItem("Download", Icons.Filled.Download, onDownload)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )

            MenuItem("View artist", Icons.Filled.Person, onViewArtist)
            MenuItem("View album", Icons.Filled.Album, onViewAlbum)
            MenuItem("Song info", Icons.Filled.Info, onShowInfo)
            MenuItem("Refetch", Icons.Filled.Refresh, onRefetch)
        }
    }
}

@Composable
private fun SheetHeader(song: SearchResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl.takeIf { it.isNotBlank() },
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MintTeal)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                song.artist,
                fontSize = 13.sp,
                maxLines = 1,
                // Same value TextGrey() now returns, written out so this line needs no
                // import for a secondary label.
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, BorderBlack, RoundedCornerShape(10.dp))
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            label,
            fontSize = 15.sp,
            color = tint
        )
    }
}

