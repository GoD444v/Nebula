package nebula.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nebula.music.data.models.LyricHighlight
import nebula.music.data.models.LyricsStyleStore
import nebula.music.data.lyrics.LyricsProviderRegistry
import nebula.music.ui.theme.BorderBlack

private val ModeLabels = listOf(
    LyricHighlight.LINE to "Whole line", LyricHighlight.KARAOKE to "Karaoke",
    LyricHighlight.APPLE to "Apple", LyricHighlight.APPLE_V2 to "Apple V2",
    LyricHighlight.FADE to "Fade", LyricHighlight.GLOW to "Glow"
)

// Word-highlight palette. Ordering is a deliberate hue sweep, warm to cool
// (red -> orange -> yellow -> greens -> teals -> blues -> indigo -> purple ->
// magenta -> pink), so the wrapped grid reads left-to-right like a colour wheel,
// with the neutrals last. The brand entries are the real theme values from
// ui/theme/Color.kt (SunnyYellow, MintTeal, NeonPink, Cream, AmoledBlack), not
// the near-identical legacy literals this list used to hold - two dots you
// cannot tell apart is a defect in a picker. Hues sit ~20+ degrees apart and
// the greys are spaced by lightness, because the whole point of this row is
// telling the colours apart. Every entry is fully opaque 0xFF: a 0x00 alpha
// would be invisible here and 0 is what LyricsStyleStore reads as "not set".
private val WordSwatches = listOf(
    0xFFE53935.toInt(), // red
    0xFFFF6D00.toInt(), // orange
    0xFFFFDE00.toInt(), // SunnyYellow (brand)
    0xFF9CCC3C.toInt(), // lime
    0xFF43A047.toInt(), // green
    0xFF00E5A0.toInt(), // mint green
    0xFF00FFFF.toInt(), // MintTeal (brand)
    0xFF29B6F6.toInt(), // sky
    0xFF1565C0.toInt(), // blue
    0xFF3949AB.toInt(), // indigo
    0xFF9C27B0.toInt(), // purple
    0xFFFF0080.toInt(), // NeonPink (brand)
    0xFFFF4081.toInt(), // pink
    0xFFFFF6E9.toInt(), // Cream (brand)
    0xFFFFFFFF.toInt(), // white
    0xFFBDBDBD.toInt(), // light grey
    0xFF757575.toInt(), // mid grey
    0xFF000000.toInt()  // AmoledBlack (brand)
)

// The visual half of the old combined Lyrics window: highlight mode + word colour.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsStyleSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LyricsStyleStore.getMode(context)) }
    var customColor by remember { mutableStateOf(LyricsStyleStore.getCustomColor(context)) }
    val cardShape = RoundedCornerShape(16.dp)
    val rowShape = RoundedCornerShape(12.dp)
    val wordColor = customColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

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
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, cardShape)
        ) {
            // Grit ListItem summary: icon + name + current pick + colour dot.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lyrics style", fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Highlight style · ${ModeLabels.first { it.first == mode }.second}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(wordColor)
                        .border(3.dp, BorderBlack, CircleShape)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(BorderBlack))
            // The modes and the swatch grid used to hide behind a popup window; as
            // their own sub-screen they show directly on the page. The page scrolls,
            // so no window cap is needed any more.
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeLabels.forEach { (value, label) ->
                    val selected = mode == value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(3.dp, BorderBlack, rowShape)
                            .clickable {
                                LyricsStyleStore.setMode(context, value)
                                mode = value
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.onPrimary else Color.Transparent)
                                .border(3.dp, if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, fontWeight = FontWeight.Black, fontSize = 14.sp,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text("Word color", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                // 19 chips at 36dp + 10dp gaps overflows any phone width, so this
                // wraps. It deliberately does NOT scroll on its own any more: two
                // vertical scrolls in one window trap the drag — the grid swallows
                // the gesture and the window stops moving under the finger. The grid
                // takes its natural height and the whole group scrolls instead.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val defaultSelected = customColor == null
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .clip(rowShape)
                            .background(if (defaultSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(3.dp, BorderBlack, rowShape)
                            .clickable {
                                LyricsStyleStore.setCustomColor(context, null)
                                customColor = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Default", fontWeight = FontWeight.Black, fontSize = 13.sp,
                            color = if (defaultSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    WordSwatches.forEach { argb ->
                        val selected = customColor == argb
                        val swatch = Color(argb)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(if (selected) 4.dp else 3.dp, if (selected) MaterialTheme.colorScheme.primary else BorderBlack, CircleShape)
                                .clickable {
                                    LyricsStyleStore.setCustomColor(context, argb)
                                    customColor = argb
                                }
                        ) {
                            // PaletteDot's check + thicker ring, minus its 55% tint
                            // wash: on a 36dp colour dot that wash would cover the
                            // exact colour the user is trying to judge. Tint the
                            // glyph by luminance so it reads on black and on white.
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (swatch.luminance() > 0.5f) BorderBlack else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The functional half of the old combined Lyrics window: romanized reading,
 * English translation, and the provider priority list.
 */
@Composable
fun LyricsProvidersSection() {
    val context = LocalContext.current
    var translation by remember { mutableStateOf(LyricsStyleStore.getTranslation(context)) }
    var romanize by remember { mutableStateOf(LyricsStyleStore.getRomanize(context)) }
    var providerOrder by remember { mutableStateOf(LyricsProviderRegistry.orderedAll().map { it.id }) }
    var providerEnabled by remember {
        mutableStateOf(LyricsProviderRegistry.all.associate { it.id to LyricsProviderRegistry.isEnabled(it.id) })
    }
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
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, cardShape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // The two sub-lines that stack above the original share one labelled
            // group with the provider list below a divider, because they read in the
            // opposite direction to everything above them — and they are listed in
            // the order they are drawn: reading, then English, then the line itself.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Romanized reading", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    // Names the language and where the change lands. A Japanese song
                    // has no reading to show, so without saying "Korean" the empty
                    // result would look like a switch that does nothing.
                    Text("Korean songs only · from the next song", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = romanize,
                    onCheckedChange = { on ->
                        LyricsStyleStore.setRomanize(context, on)
                        romanize = on
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("English translation", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    // Says where it lands, because the switch cannot take effect on a
                    // song that has already been translated.
                    Text("Above the original · from the next song", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = translation,
                    onCheckedChange = { on ->
                        LyricsStyleStore.setTranslation(context, on)
                        translation = on
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
            // Providers — which lyric sources run, and in what priority order.
            // Order only breaks ties inside a quality tier; an off switch takes
            // a provider out of the race entirely.
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(BorderBlack))
            Text("Providers", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            providerOrder.forEachIndexed { index, id ->
                val info = LyricsProviderRegistry.all.first { it.id == id }
                val enabled = providerEnabled[id] ?: info.enabledByDefault
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        info.label,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                val next = providerOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                                providerOrder = next
                                LyricsProviderRegistry.setOrder(next)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (index < providerOrder.size - 1) {
                                val next = providerOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                                providerOrder = next
                                LyricsProviderRegistry.setOrder(next)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            LyricsProviderRegistry.setEnabled(id, on)
                            providerEnabled = providerEnabled.toMutableMap().apply { put(id, on) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}
