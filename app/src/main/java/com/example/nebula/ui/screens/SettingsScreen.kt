package com.example.nebula.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nebula.NebulaApplication
import com.example.nebula.R
import com.example.nebula.data.download.DownloadPrefs
import com.example.nebula.data.download.NebulaDownloads
import com.example.nebula.ui.components.ChunkyAction
import com.example.nebula.ui.components.ChunkyWindow
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.NebulaTheme
import com.example.nebula.ui.theme.ThemeStore
import com.example.nebula.ui.theme.VoxCustom
import com.example.nebula.ui.theme.VoxPalette
import com.example.nebula.ui.theme.VoxPalettes
import com.example.nebula.ui.theme.paletteById
import com.example.nebula.ui.theme.toPalette

// VoxMusic pattern: every screen reads the palette directly — no MaterialTheme dependency
@Composable
fun SettingsScreen(onCustomizeTabs: () -> Unit = {}) {
    val context = LocalContext.current
    val picked = ThemeStore.paletteId
    val pal = paletteById(picked)
    var editorOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ThemeStore.custom) }
    val wifiOnly by DownloadPrefs.wifiOnly.collectAsState(initial = false)

    // The page is taller than the viewport (CLEAR CACHE sat below the fold, so the tap
    // never landed and its window never opened), hence the verticalScroll.
    // Order matters: .background() stays OUTSIDE the scroll so it still paints the full
    // screen when the content is shorter than the viewport, and .padding() stays inside
    // it so the 16dp gutter scrolls away with the content. fillMaxSize() before
    // verticalScroll is what gives the scroll node its finite height — measure the scroll
    // node with an infinite maxHeight and it throws outright.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title banner — uses palette bg for shadow, pink for highlight
        Box {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Settings", color = pal.onBg)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.pink)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Settings", fontWeight = FontWeight.Black, fontSize = 16.sp, color = pal.onBg)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Palette group — M3 connected list (Grit: leading shape on top, end shape on
        // the bottom, one 3dp divider) and compact, instead of the old full-height card.
        Box {
            // matchParentSize → shadow always matches the real card.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                val groupShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape)
                        .background(pal.surface)
                        .border(3.dp, BorderBlack, groupShape)
                ) {
                    // Header row — Grit ListItem: leading icon + headline + supporting text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = pal.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Theme Palette",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = pal.onSurface
                            )
                            Text(
                                "Selected: ${pal.name}",
                                fontSize = 11.sp,
                                color = pal.onSurface
                            )
                        }
                    }
                    // One shared divider, like Grit's connected items
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(BorderBlack)
                    )

                    // Grit-style picker: each palette is a 6-slice pie of its own colours.
                    // 5 per row, 40dp swatches → the whole block stays compact.
                    val options = VoxPalettes + ThemeStore.custom.toPalette()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.chunked(5).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                line.forEach { palette ->
                                    PaletteDot(
                                        palette = palette,
                                        selected = picked == palette.id,
                                        tickColor = pal.onBg,
                                        onClick = {
                                            ThemeStore.setPalette(context, palette.id)
                                            // Choosing "My Palette" opens the editor window with a preview
                                            if (palette.id == "custom") {
                                                draft = ThemeStore.custom
                                                editorOpen = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hint lives OUTSIDE the shadow Box: the shadow uses matchParentSize, so it
        // stretched over the hint too and printed a black band across it.
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Tap My Palette to open the editor with a live preview.",
            fontSize = 11.sp,
            color = pal.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        LyricsStyleSection()

        Spacer(modifier = Modifier.height(16.dp))

        // Customize Navigation Tabs button
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
                    .background(pal.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = pal.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Navigation Tabs",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = pal.onSurface
                        )
                        Text(
                            "Reorder & hide tabs",
                            fontSize = 11.sp,
                            color = pal.onSurface
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(BorderBlack)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pal.yellow)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .clickable { onCustomizeTabs() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CUSTOMIZE TABS", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pal.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App icon switcher — the launcher icon is one of three activity-aliases in
        // AndroidManifest.xml, so enabling an alias literally swaps the home-screen
        // icon. Alias 1 (Icon 1) is the default; the picker lives in a window so
        // the three thumbnails do not bloat this list.
        var icon by remember { mutableStateOf(currentIcon(context)) }
        var showIconPicker by remember { mutableStateOf(false) }
        val iconShape = RoundedCornerShape(16.dp)
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(iconShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(iconShape)
                    .background(pal.surface)
                    .border(3.dp, BorderBlack, iconShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIconPicker = true }
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = pal.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "App icon",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = pal.onSurface
                        )
                        Text(
                            "Selected: Icon $icon",
                            fontSize = 11.sp,
                            color = pal.onSurface
                        )
                    }
                    // Chunky chevron — same idiom as the player's controls, and it
                    // is the only hint that the whole row is tappable.
                    Box {
                        Box(
                            modifier = Modifier
                                .offset(x = 3.dp, y = 3.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(pal.yellow)
                                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = pal.onSurface,
                                modifier = Modifier.size(22.dp)
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
            }
        }

        if (showIconPicker) {
            ChunkyWindow(
                title = "App icon",
                onDismissRequest = { showIconPicker = false }
            ) {
                // The plain PNGs, NOT @mipmap/ic_icon1: on API 26+ that resolves to
                // the adaptive-icon XML, which painterResource refuses to draw and
                // crashes Settings with "Only VectorDrawables and rasterized asset..."
                val iconImages = listOf(
                    R.drawable.ic_icon1_art,
                    R.drawable.ic_icon2_art,
                    R.drawable.ic_icon3_art
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    iconImages.forEachIndexed { i, res ->
                        val active = icon == i + 1
                        Box(contentAlignment = Alignment.Center) {
                            // Extrusion behind the tile, active tile lifted off it.
                            Box(
                                modifier = Modifier
                                    .offset(x = 3.dp, y = 3.dp)
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pal.bg)
                                    .border(
                                        if (active) 4.dp else 3.dp,
                                        if (active) pal.pink else BorderBlack,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        setAppIcon(context, i + 1)
                                        icon = i + 1
                                    }
                            ) {
                                Image(
                                    painter = painterResource(res),
                                    contentDescription = "Icon ${i + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                )
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = pal.onBg,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                ChunkyAction(
                    label = "Done",
                    color = pal.yellow,
                    onClick = { showIconPicker = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Cache — shows current cache size and a chunky clear button.
        // Clearing asks first (mini warning window) and runs off the main thread.
        var cacheSize by remember { mutableStateOf(calculateCacheSize()) }
        var cacheCleared by remember { mutableStateOf(false) }
        var showClearWarn by remember { mutableStateOf(false) }
        val cacheScope = rememberCoroutineScope()
        val cacheShape = RoundedCornerShape(16.dp)
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(cacheShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cacheShape)
                    .background(pal.surface)
                    .border(3.dp, BorderBlack, cacheShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = pal.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Storage & Cache",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = pal.onSurface
                        )
                        Text(
                            "Cache size: $cacheSize MB",
                            fontSize = 11.sp,
                            color = pal.onSurface
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(BorderBlack)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pal.yellow)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .clickable { showClearWarn = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = pal.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("CLEAR CACHE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = pal.onSurface)
                        }
                    }
                }
                if (cacheCleared) {
                    Text(
                        "Cache cleared!",
                        fontSize = 11.sp,
                        color = pal.onSurface,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // WiFi-only downloads toggle — prevents downloads on mobile data
        val downloadScope = rememberCoroutineScope()
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
                    .background(pal.surface)
                    .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = pal.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "WiFi-only downloads",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = pal.onSurface
                        )
                        Text(
                            "Only download on WiFi",
                            fontSize = 11.sp,
                            color = pal.onSurface
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { enabled ->
                            downloadScope.launch {
                                DownloadPrefs.setWifiOnly(enabled)
                                NebulaDownloads.setWifiOnly(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pal.yellow,
                            checkedTrackColor = pal.yellow.copy(alpha = 0.5f),
                            uncheckedThumbColor = pal.onSurface,
                            uncheckedTrackColor = pal.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Mini warning window before wiping downloaded song data. ChunkyWindow, not
        // AlertDialog — this is the app's own window idiom.
        if (showClearWarn) {
            ChunkyWindow(
                title = "Clear cache?",
                onDismissRequest = { showClearWarn = false }
            ) {
                Text(
                    "Cached songs will re-download on next play. Playback and playlists are unaffected.",
                    fontSize = 13.sp,
                    color = pal.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChunkyAction(
                    label = "Clear",
                    color = pal.pink,
                    onClick = {
                        showClearWarn = false
                        cacheScope.launch(Dispatchers.IO) {
                            NebulaApplication.clearCache()
                            cacheSize = 0
                            cacheCleared = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChunkyAction(
                    label = "Cancel",
                    // pal.bg, not pal.surface: the window itself is surface-coloured, so a
                    // surface Cancel would disappear into the background.
                    color = pal.bg,
                    onClick = { showClearWarn = false }
                )
            }
        }
    }

    if (editorOpen) {
        MyPaletteDialog(
            draft = draft,
            onDraftChange = { draft = it },
            onDismiss = { editorOpen = false },
            onSave = {
                ThemeStore.setCustom(context, draft)
                editorOpen = false
            }
        )
    }
}

// ---- App icon switcher ----
// The three launcher icons are activity-aliases declared in AndroidManifest.xml.
// Alias 1 is enabled there, so "Icon 1" is the default out of the box.
private val appIconAliases = listOf(
    "com.example.nebula.LauncherIcon1",
    "com.example.nebula.LauncherIcon2",
    "com.example.nebula.LauncherIcon3"
)

// 1-based. PackageManager remembers the choice, so no extra preference file.
private fun currentIcon(context: Context): Int {
    val pm = context.packageManager
    appIconAliases.forEachIndexed { i, name ->
        when (pm.getComponentEnabledSetting(ComponentName(context, name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> return i + 1
            // DEFAULT only returns here for alias 1, which the manifest enables.
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> if (i == 0) return 1
        }
    }
    return 1
}

// Enable the picked alias FIRST, so the launcher never sees a moment with zero icons.
private fun setAppIcon(context: Context, chosen: Int) {
    val pm = context.packageManager
    pm.setComponentEnabledSetting(
        ComponentName(context, appIconAliases[chosen - 1]),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
    appIconAliases.forEachIndexed { i, name ->
        if (i != chosen - 1) pm.setComponentEnabledSetting(
            ComponentName(context, name),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}

// Calculate total cache size in MB from the cache directory
private fun calculateCacheSize(): Long {
    val dir = NebulaApplication.getCacheDirectory()
    if (!dir.exists()) return 0
    val bytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    return bytes / (1024 * 1024)
}

// Grit-style swatch: a 6-slice pie of the palette's own colours.
// Concept from Grit's PaletteStylePicker — arcs drawn with useCenter = true.
@Composable
private fun PaletteDot(
    palette: VoxPalette,
    selected: Boolean,
    tickColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val slices = listOf(
                palette.pink, palette.bg, palette.yellow,
                palette.surface, palette.cyan, palette.nav
            )
            val sweep = 360f / slices.size
            slices.forEachIndexed { i, colour ->
                drawArc(
                    color = colour,
                    startAngle = i * sweep,
                    sweepAngle = sweep,
                    useCenter = true
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.pink.copy(alpha = 0.55f))
            )
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = palette.name,
                tint = tickColor,
                modifier = Modifier.size(20.dp)
            )
        }
        // Border last, so the ring paints on top of the pie.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(if (selected) 3.dp else 1.5.dp, BorderBlack, CircleShape)
        )
    }
}

// ---- My Palette: the 7 colours the user picks ----
// Presets = every colour the built-in palettes use, plus neutrals.
private val PresetColours = listOf(
    0xFF000000, 0xFF101014, 0xFF1C1C24, 0xFF2E2E2E, 0xFF4A4A4A, 0xFF8A8A8A, 0xFFB8B8B8, 0xFFEAEAEA,
    0xFFFFFFFF, 0xFFF4F4F6, 0xFFFFE7D0, 0xFFFFF7EC, 0xFFFFC700, 0xFFFFB800, 0xFFFF6B9D, 0xFFFF4D80,
    0xFF00D4AA, 0xFF00FFFF, 0xFF00F0FF, 0xFF118AB2, 0xFF6B48FF, 0xFFB026FF, 0xFFE02A4C, 0xFFFF3333
).map { it.toInt() }

private fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

// Accepts "#RRGGBB" or "RRGGBB"; returns null while the text is incomplete.
private fun parseHex(raw: String): Int? {
    val t = raw.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return (0xFF000000L or v).toInt()
}

// The 7 editable slots, shared by the editor window and the colour dialog.
private fun slotsOf(c: VoxCustom) = listOf(
    "Background" to c.bg,
    "Card" to c.card,
    "Icons & text" to c.icons,
    "Navigation" to c.nav,
    "Pink (primary)" to c.pink,
    "Teal (secondary)" to c.teal,
    "Yellow (tertiary)" to c.yellow
)

private fun withSlot(c: VoxCustom, index: Int, value: Int): VoxCustom = when (index) {
    0 -> c.copy(bg = value)
    1 -> c.copy(card = value)
    2 -> c.copy(icons = value)
    3 -> c.copy(nav = value)
    4 -> c.copy(pink = value)
    5 -> c.copy(teal = value)
    else -> c.copy(yellow = value)
}

// My Palette editor window. Edits a throwaway draft, so Cancel discards everything,
// and the preview at the top redraws on every change before you commit.
@Composable
private fun MyPaletteDialog(
    draft: VoxCustom,
    onDraftChange: (VoxCustom) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var editing by remember { mutableStateOf(-1) }
    val bg = Color(draft.bg)
    val ink = Color(draft.icons)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg,
        titleContentColor = ink,
        textContentColor = ink,
        title = { Text("My Palette", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PalettePreview(draft)
                Text("Tap a colour to change it", fontSize = 11.sp, color = ink)
                slotsOf(draft).forEachIndexed { index, (label, argb) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { editing = index }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(3.dp, BorderBlack, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = ink)
                            Text(hexOf(argb), fontSize = 11.sp, color = ink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save", color = ink, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ink, fontWeight = FontWeight.Black)
            }
        }
    )

    if (editing in slotsOf(draft).indices) {
        ColourDialog(
            initial = slotsOf(draft)[editing].second,
            container = bg,
            textColor = ink,
            onDismiss = { editing = -1 },
            onConfirm = { c ->
                onDraftChange(withSlot(draft, editing, c))
                editing = -1
            }
        )
    }
}

// Mini mock-up of the app drawn with the draft colours: page bg, a card with a real
// 3D shadow, accent art, and the bottom-nav pill. Live, before you hit Save.
@Composable
private fun PalettePreview(c: VoxCustom) {
    val bg = Color(c.bg)
    val card = Color(c.card)
    val ink = Color(c.icons)
    val navColour = Color(c.nav)
    // Same rule NebulaTheme uses for shadow colour
    val shadow = if (bg.luminance() < 0.5f) Color(0xFF2E2E2E) else Color.Black
    val navInk = if (navColour.luminance() > 0.5f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(3.dp, BorderBlack, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text("Preview", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ink)
        Spacer(modifier = Modifier.height(8.dp))

        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shadow)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(card)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(c.pink))
                        .border(3.dp, BorderBlack, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Song title", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ink)
                    Text("Artist name", fontSize = 10.sp, color = Color(c.teal))
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(c.yellow))
                        .border(3.dp, BorderBlack, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(card)
                .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.3f))
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(navColour)
                    .border(2.5.dp, BorderBlack, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("NAV", fontWeight = FontWeight.Black, fontSize = 9.sp, color = navInk)
            }
        }
    }
}

@Composable
private fun ColourDialog(
    initial: Int,
    container: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(hexOf(initial)) }
    val parsed = parseHex(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = container,
        titleContentColor = textColor,
        textContentColor = textColor,
        title = { Text("Pick a colour", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetColours.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(2.dp, BorderBlack, CircleShape)
                                    .clickable { text = hexOf(c) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Hex  #RRGGBB") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        cursorColor = textColor,
                        focusedContainerColor = container,
                        unfocusedContainerColor = container,
                        focusedBorderColor = BorderBlack,
                        unfocusedBorderColor = BorderBlack,
                        focusedLabelColor = textColor,
                        unfocusedLabelColor = textColor
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text("Save", color = textColor, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Preview(showBackground = true, name = "Settings Light")
@Composable
private fun SettingsPreviewLight() {
    NebulaTheme(darkTheme = false) {
        SettingsScreen()
    }
}

@Preview(showBackground = true, name = "Settings AMOLED")
@Composable
private fun SettingsPreviewAmoled() {
    NebulaTheme(darkTheme = true, paletteId = "amoled") {
        SettingsScreen()
    }
}
