package nebula.music.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The confirm that stands in front of "download every song in this list".
 *
 * Shared by all three places that offer it — a created playlist, a playlist page from
 * YouTube, and a Home recommendation row — because the decision is the same everywhere and
 * three copies of a dialog that guards a few hundred megabytes of someone's data is three
 * places for the wording to drift apart.
 *
 * [count] is the number of songs still to fetch, not the size of the list: songs already
 * on disk are skipped, and quoting the raw list length would overstate the download every
 * time it ran twice.
 *
 * No size estimate on purpose. Bytes are only known after a stream resolves, and a guessed
 * "about 300 MB" would be a number the app made up.
 */
@Composable
fun DownloadAllDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Download 1 song?" else "Download $count songs?") },
        text = {
            Text(
                if (count == 1) {
                    "The song will be available offline."
                } else {
                    "They will be queued in order and made available offline. " +
                        "Downloads continue in the background."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}