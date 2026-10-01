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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.nebula.data.models.SearchResult
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.MintTeal
import com.example.nebula.ui.theme.TextGrey

/**
 * The per-song overflow menu, as a bottom sheet.
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Pinned to `surface`, not the default `surfaceContainerLow`. The Vox palettes
        // only define surface/background, so the default container resolved to a colour
        // the palette never chose — and under Classic 80s Vox (a near-white surface) the
        // default's dark container left the onSurface text unreadable. surface and
        // onSurface are a designed pair in every palette, so pinning both is safe.
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            SheetHeader(song)

            // Transport and sharing.
            MenuItem("Start radio", Icons.Filled.Radio, onStartRadio)
            MenuItem("Add to playlist", Icons.Filled.PlaylistAdd, onAddToPlaylist)
            MenuItem("Share", Icons.Filled.Share, onShare)

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Queue. Play next and Add to queue are genuinely different: one inserts
            // after the playing track, the other appends.
            MenuItem("Play next", Icons.Filled.PlaylistPlay, onPlayNext)
            MenuItem("Add to queue", Icons.Filled.QueueMusic, onAddToQueue)

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Offline. The label flips rather than the row disappearing, so the row never
            // moves position under the user's thumb.
            if (isDownloaded) {
                MenuItem("Remove download", Icons.Filled.Delete, onRemoveDownload, destructive = true)
            } else {
                MenuItem("Download", Icons.Filled.Download, onDownload)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

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
                // contentColor, not TextGrey: TextGrey is a fixed constant that does not
                // invert with the palette, so on a dark sheet it disappeared.
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

