package com.example.nebula.ui.screens

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.nebula.data.models.LyricHighlight
import com.example.nebula.data.models.LyricsStyleStore
import com.example.nebula.ui.components.ChunkyAction
import com.example.nebula.ui.components.ChunkyWindow
import com.example.nebula.ui.theme.BorderBlack

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsStyleSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LyricsStyleStore.getMode(context)) }
    var customColor by remember { mutableStateOf(LyricsStyleStore.getCustomColor(context)) }
    var open by remember { mutableStateOf(false) }
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
            // Grit ListItem summary: icon + name + current pick + colour dot. The six
            // modes used to sit inline here and pushed the rest of Settings off-screen,
            // so the row just opens the mini window below. Tapping again closes it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
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
                    Text("Lyrics", fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
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
        }
    }

    // Sibling of the card Box on purpose: the card's shadow uses matchParentSize, and
    // a Dialog renders in its own window anyway, so nesting it in the card is noise.
    if (open) {
        ChunkyWindow(title = "Lyrics", onDismissRequest = { open = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                // wraps. ChunkyWindow's own Column is neither scrollable nor
                // height-capped, so an uncapped grid would just push the Done
                // button off-screen instead of growing the window: cap the grid
                // at 3 lines (36*3 + 10*2 = 128dp) and let it scroll past that.
                // Shorter grids never scroll, so this is invisible on tall screens.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 128.dp)
                        .verticalScroll(rememberScrollState()),
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
            // Picking a mode or a colour applies immediately and deliberately keeps the
            // window open (style + colour are two separate decisions), so Done closes it.
            Spacer(modifier = Modifier.height(12.dp))
            ChunkyAction(label = "Done", onClick = { open = false })
        }
    }
}
