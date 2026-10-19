package nebula.music.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.entities.PlayHistoryEntity
import nebula.music.data.models.SearchResult
import nebula.music.ui.theme.BorderBlack
import nebula.music.viewmodel.PlayerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Listening history, grouped by day. Reached from the Home top bar — Echo's
 * history button, Nebula's header banner.
 *
 * Tapping a row plays it with everything below it queued behind, so the
 * history doubles as a "play my recent stuff" list. Unlike the other screens
 * this one both reads and writes the database directly through the DAO: there
 * is no ViewModel state to hold — the Room Flow is the state.
 */
@Composable
fun HistoryScreen(
    playerVm: PlayerViewModel,
    onBack: () -> Unit = {},
    onPlayDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember(context) { NebulaDatabase.getDatabase(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<PlayHistoryEntity>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(db) {
        db.playHistoryDao().observeAll().collect { entries = it }
    }

    val groups = remember(entries) { historyBuckets(entries) }
    // Flattened display order — what "play from here" queues.
    val rows = remember(groups) { groups.flatMap { it.second } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                SectionBanner(
                    title = "History",
                    isFirst = true,
                    actions = {
                        if (entries.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.5.dp, BorderBlack, RoundedCornerShape(10.dp))
                                    .clickable { confirmClear = true }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "Clear all",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing played yet — your listening history shows up here",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                groups.forEachIndexed { groupIndex, (label, groupRows) ->
                    item(key = "head-$label") {
                        SectionBanner(title = label, isFirst = groupIndex == 0)
                    }
                    items(groupRows, key = { "$label-${it.id}" }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onPlay = {
                                val index = rows.indexOfFirst { it.id == entry.id }
                                if (index >= 0) {
                                    val songs = rows.map { it.toSearchResult() }
                                    playerVm.playFromHere(songs[index], songs.drop(index + 1))
                                    onPlayDone()
                                }
                            },
                            onDelete = { scope.launch { db.playHistoryDao().delete(entry) } }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?", fontWeight = FontWeight.Black) },
            text = { Text("Every play in your listening history will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { db.playHistoryDao().clear() }
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Day buckets, newest first: Today, Yesterday, then one bucket per older day.
 * A song played several times in one day shows once — the newest play wins —
 * but stays in every other day it appears in (Echo's per-bucket dedup).
 * Pure function of the rows, so it is unit-tested; [entries] usually arrives
 * sorted newest-first from the DAO, but any order works — the sort below makes
 * both the bucket order and "newest play wins" dedup input-order-proof.
 */
internal fun historyBuckets(
    entries: List<PlayHistoryEntity>,
    nowMs: Long = System.currentTimeMillis()
): List<Pair<String, List<PlayHistoryEntity>>> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val dayLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val grouped = LinkedHashMap<String, MutableList<PlayHistoryEntity>>()
    for (entry in entries.sortedByDescending { it.playedAt }) {
        val date = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalDate()
        val label = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayLabel)
        }
        grouped.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return grouped.map { (label, rows) -> label to rows.distinctBy { it.videoId } }
}

private fun PlayHistoryEntity.toSearchResult() = SearchResult(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl.orEmpty()
)

@Composable
private fun HistoryRow(
    entry: PlayHistoryEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val letterFallback: @Composable () -> Unit = {
        Text(
            entry.title.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Box {
        // 3D shadow layer, same as every other card in the app.
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.thumbnailUrl.isNullOrBlank()) {
                    letterFallback()
                } else {
                    SubcomposeAsyncImage(
                        model = entry.thumbnailUrl,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { letterFallback() },
                        error = { letterFallback() }
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    entry.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Its own tap target, so removing a song never plays it.
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove ${entry.title} from history",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
