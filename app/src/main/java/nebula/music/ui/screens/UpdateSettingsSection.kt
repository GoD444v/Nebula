package nebula.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nebula.music.data.UpdatePrefs
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.VoxPalette
import nebula.music.updater.autoClearOldApks
import nebula.music.updater.clearDownloadedApks
import nebula.music.updater.getDownloadedApkCount
import nebula.music.data.UpdateChecker
import nebula.music.data.UpdateResult
import nebula.music.updater.downloadmanager.UpdateDownloadWorker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * The "App updates" settings section — the settings half of the updater.
 *
 * Shows the installed version (or a red "new update available" flag written
 * by the updater engine), a CHECK FOR UPDATES button, the auto-check and
 * notification switches, and the downloaded-installer count with clearing.
 * Same card idiom as the WiFi toggle above it.
 *
 * The System update row and the CHECK FOR UPDATES button both open the
 * update window (UpdateDialog), which runs the GitHub release check and
 * reports the verdict.
 */
@Composable
fun UpdateSettingsSection(
    pal: VoxPalette
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoCheck by UpdatePrefs.autoCheck.collectAsState()
    val notifications by UpdatePrefs.notifications.collectAsState()
    val available by UpdatePrefs.updateAvailable.collectAsState()
    val availableVersion by UpdatePrefs.availableVersion.collectAsState()
    val lastChecked by UpdatePrefs.lastChecked.collectAsState()
    var apkCount by remember { mutableStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    val runCheck: () -> Unit = {
        scope.launch {
            checking = true
            updateResult = null
            updateResult = UpdateChecker.check(context)
            checking = false
        }
    }
    // No BuildConfig in this module — read the installed version instead.
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    LaunchedEffect(Unit) {
        // Idempotent: safe even once MainActivity starts calling init too.
        UpdatePrefs.init(context)
        autoClearOldApks(context)
        apkCount = getDownloadedApkCount(context)
    }

    // Run the check when the update window opens.
    LaunchedEffect(showUpdateDialog) {
        if (showUpdateDialog) runCheck()
    }

    // Section label — identical look to SettingsScreen's private header,
    // inlined so this file stands alone.
    Text(
        "APP UPDATES",
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        color = pal.yellow,
        modifier = Modifier.padding(start = 4.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    val cardShape = RoundedCornerShape(16.dp)
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(pal.surface)
                .border(3.dp, BorderBlack, cardShape)
        ) {
            // Status row — installed version, or the engine's red flag.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
                    .clickable { showUpdateDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Autorenew,
                    contentDescription = null,
                    tint = pal.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "System update",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = pal.onSurface
                    )
                    if (available) {
                        Text(
                            if (availableVersion.isNotBlank()) "New update available: $availableVersion"
                            else "New update available",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Version $currentVersion",
                            fontSize = 11.sp,
                            color = pal.onSurface
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(BorderBlack)
            )
            // Manual check — the engine fills this hook in later.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.yellow)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .clickable { showUpdateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "CHECK FOR UPDATES",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = pal.onSurface
                    )
                }
            }
            if (lastChecked.isNotBlank()) {
                Text(
                    "Last checked: $lastChecked",
                    fontSize = 11.sp,
                    color = pal.onSurface,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(BorderBlack)
            )
            // Auto-check toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-check for updates",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = pal.onSurface
                    )
                    Text(
                        "Check when the app starts",
                        fontSize = 11.sp,
                        color = pal.onSurface
                    )
                }
                Switch(
                    checked = autoCheck,
                    onCheckedChange = { enabled ->
                        scope.launch { UpdatePrefs.setAutoCheck(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = pal.yellow,
                        checkedTrackColor = pal.yellow.copy(alpha = 0.5f),
                        uncheckedThumbColor = pal.onSurface,
                        uncheckedTrackColor = pal.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
            // Notifications toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Update notifications",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = pal.onSurface
                    )
                    Text(
                        "Notify when an update is ready",
                        fontSize = 11.sp,
                        color = pal.onSurface
                    )
                }
                Switch(
                    checked = notifications,
                    onCheckedChange = { enabled ->
                        scope.launch { UpdatePrefs.setNotifications(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = pal.yellow,
                        checkedTrackColor = pal.yellow.copy(alpha = 0.5f),
                        uncheckedThumbColor = pal.onSurface,
                        uncheckedTrackColor = pal.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(BorderBlack)
            )
            // Downloaded installers, with clearing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Downloaded installers",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = pal.onSurface
                    )
                    Text(
                        if (apkCount == 0) "No installers kept"
                        else "$apkCount installer${if (apkCount == 1) "" else "s"} kept",
                        fontSize = 11.sp,
                        color = pal.onSurface
                    )
                }
                Text(
                    "CLEAR",
                    modifier = Modifier
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                clearDownloadedApks(context)
                                apkCount = getDownloadedApkCount(context)
                            }
                        }
                        .padding(8.dp),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = pal.pink
                )
            }
        }
    }

    // The update check window — opens on the System update row / CHECK
    // FOR UPDATES tap.
    if (showUpdateDialog) {
        UpdateDialog(
            pal = pal,
            checking = checking,
            result = updateResult,
            onDismiss = { showUpdateDialog = false },
            onRetry = runCheck,
            onDownload = { available ->
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "nebula_update",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                        .setInputData(
                            workDataOf(
                                "apk_url" to available.apkUrl,
                                "version" to available.latestVersion,
                                "file_size" to available.fileSize
                            )
                        )
                        .build()
                )
                showUpdateDialog = false
            }
        )
    }
}

/**
 * The update check window. Runs [UpdateChecker] once on open, then shows
 * the verdict: latest (message + close), newer (version + size + download),
 * or failed (message + retry).
 */
@Composable
private fun UpdateDialog(
    pal: VoxPalette,
    checking: Boolean,
    result: UpdateResult?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (UpdateResult.UpdateAvailable) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // 3D extrusion
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(pal.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "System update",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = pal.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                when {
                    checking -> Text("Checking for updates…", fontSize = 15.sp, color = pal.onSurface)
                    result is UpdateResult.UpToDate -> {
                        Text(
                            "You are in the latest version",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = pal.onSurface
                        )
                        Text("Version ${result.currentVersion}", fontSize = 12.sp, color = pal.onSurface)
                    }
                    result is UpdateResult.UpdateAvailable -> {
                        Text(
                            "New update available",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text("${result.latestVersion} · ${result.fileSize}", fontSize = 13.sp, color = pal.onSurface)
                    }
                    result is UpdateResult.Error -> {
                        Text(result.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                if (!checking && result != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (result is UpdateResult.UpdateAvailable && result.apkUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(pal.yellow)
                                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                                    .clickable { onDownload(result) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("DOWNLOAD", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pal.onSurface)
                            }
                        }
                        if (result is UpdateResult.Error) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(pal.yellow)
                                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                                    .clickable { onRetry() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("RETRY", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pal.onSurface)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(pal.surface)
                                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("CLOSE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pal.onSurface)
                        }
                    }
                }
            }
        }
    }
}
