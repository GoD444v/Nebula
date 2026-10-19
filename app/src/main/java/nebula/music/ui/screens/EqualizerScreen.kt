package nebula.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import nebula.music.player.NebulaEqualizer
import nebula.music.ui.components.ImmersiveMode
import nebula.music.ui.theme.BorderBlack

/**
 * The Vox-style studio equalizer, full screen: dark-ink top bar with a yellow
 * banner title and a reset button, the preset chip shelf, a chunky EQ card
 * with square vertical faders (pink/yellow alternating thumbs), and the
 * spatial-audio card with surround + hall-reverb dials. Opened from the
 * player's Equalizer card; the whole thing is one immersive dialog, exactly
 * like the fullscreen lyrics page.
 *
 * Colors come from MaterialTheme's scheme (tertiary = yellow, primary = pink,
 * secondary = cyan) with [BorderBlack] as the ink, so it is correct in every
 * palette without the caller knowing the brand mapping.
 */
@Composable
fun EqualizerScreen(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The dialog owns its window, so immersion is set on that window (same
        // as the fullscreen lyrics page); the paddings below collapse to 0.
        ImmersiveMode(enabled = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EqIconBtn(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Back",
                    bg = MaterialTheme.colorScheme.surface,
                    onClick = onDismiss
                )
                VoxBanner(text = "STUDIO EQUALIZER")
                EqIconBtn(
                    icon = Icons.Filled.Refresh,
                    description = "Reset",
                    bg = MaterialTheme.colorScheme.secondary,
                    onClick = { NebulaEqualizer.resetEq() }
                )
            }

            Text(
                "SELECT EQ PRESET",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NebulaEqualizer.presets.forEach { name ->
                    EqChip(
                        label = name,
                        selected = NebulaEqualizer.activePreset == name,
                        onClick = { NebulaEqualizer.selectPreset(name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            EqCard()
            Spacer(modifier = Modifier.height(16.dp))
            SpatialCard()
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Yellow banner title, Vox's studio-label badge. */
@Composable
private fun VoxBanner(text: String) {
    val shape = RoundedCornerShape(14.dp)
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(shape)
                .background(BorderBlack)
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.tertiary)
                .border(3.dp, BorderBlack, shape)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                maxLines = 1,
                color = BorderBlack
            )
        }
    }
}

/** Square chunky icon button for the top bar: extrusion shadow, black border. */
@Composable
private fun EqIconBtn(icon: ImageVector, description: String, bg: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .offset(x = 4.dp, y = 4.dp)
                .clip(shape)
                .background(BorderBlack)
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(bg)
                .border(3.dp, BorderBlack, shape)
        ) {
            // Ink follows onSurface, not BorderBlack: on the AMOLED theme the
            // button's surface is pure black and a near-black icon vanished.
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Preset pill: shadow drops away exactly on the selected one, like Vox's chips. */
@Composable
private fun EqChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box {
        if (!selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(shape)
                    .background(BorderBlack)
            )
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surface
                )
                .border(2.5.dp, BorderBlack, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                label.uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                maxLines = 1,
                color = if (selected) BorderBlack else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EqCard() {
    val bands = NebulaEqualizer.bands
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BorderBlack)
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${bands.size}-BAND EQUALIZER",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, BorderBlack, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        NebulaEqualizer.activePreset.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = NebulaEqualizer.enabled,
                    onCheckedChange = { NebulaEqualizer.toggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            // Bands are Nebula's own fixed set (the EQ is in-pipeline DSP, not an OS
            // effect), so they are always present - there is no "no song" empty state.
            run {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    bands.forEachIndexed { i, band ->
                        Column(
                            modifier = Modifier.width(56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "%+.1f".format(band.levelMb / 100f),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            EqFader(
                                value = normF(band.levelMb),
                                thumb = if (i % 2 == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                                vertical = true,
                                modifier = Modifier.size(width = 48.dp, height = 150.dp),
                                onValueChange = { v ->
                                    NebulaEqualizer.setBand(i, mBOf(v))
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                freqLabel(band.freqHz),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * VoxSlider's nebula twin: white square track, square thumb with an extrusion
 * shadow, gain fill in thumb color at 40%. Written in the orientation it
 * draws in — no rotation tricks.
 */
@Composable
private fun EqFader(
    value: Float,
    thumb: Color,
    vertical: Boolean,
    modifier: Modifier,
    onValueChange: (Float) -> Unit
) {
    val fraction = value.coerceIn(0f, 1f)
    Box(
        modifier
            .pointerInput(onValueChange) {
                detectTapGestures { pos ->
                    onValueChange(
                        (if (vertical) 1f - pos.y / size.height else pos.x / size.width)
                            .coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(onValueChange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange(
                        (if (vertical) 1f - change.position.y / size.height
                        else change.position.x / size.width)
                            .coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        if (vertical) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .border(2.5.dp, BorderBlack, RectangleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .align(Alignment.BottomCenter)
                        .background(thumb.copy(alpha = 0.4f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .align(Alignment.BottomCenter)
                ) {
                    // Round 3D knob: bordered disc over a circle extrusion.
                    // requiredSize, not size: near the bottom the holder box has
                    // ~0 height, and size() would squash the disc flat.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-14).dp)
                            .requiredSize(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .offset(x = 3.dp, y = 3.dp)
                                .clip(CircleShape)
                                .background(BorderBlack)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(thumb)
                                .border(3.dp, BorderBlack, CircleShape)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.Center)
                    .border(2.5.dp, BorderBlack, RectangleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .align(Alignment.CenterStart)
                        .background(thumb.copy(alpha = 0.4f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .align(Alignment.CenterStart)
                ) {
                    // Round 3D knob for the horizontal dials (requiredSize — see
                    // the vertical branch).
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 14.dp)
                            .requiredSize(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .offset(x = 3.dp, y = 3.dp)
                                .clip(CircleShape)
                                .background(BorderBlack)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(thumb)
                                .border(3.dp, BorderBlack, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The purple card in Vox; Nebula's palette has no purple accent, so the teal
 * (secondary) carries it with white ink and pink/yellow thumbs.
 */
@Composable
private fun SpatialCard() {
    Text(
        "SPACE & SPATIAL AUDIO FX",
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(10.dp))
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BorderBlack)
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondary)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "3D VIRTUAL SURROUND SOUND",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%d%%".format((NebulaEqualizer.surroundStrength * 100).toInt()),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            EqFader(
                value = NebulaEqualizer.surroundStrength,
                thumb = MaterialTheme.colorScheme.tertiary,
                vertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onValueChange = { NebulaEqualizer.applySurroundStrength(it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONCERT HALL REVERB",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%d%%".format((NebulaEqualizer.reverbLevel * 100).toInt()),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            EqFader(
                value = NebulaEqualizer.reverbLevel,
                thumb = MaterialTheme.colorScheme.primary,
                vertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onValueChange = { NebulaEqualizer.setReverb(it) }
            )
        }
    }
}

/** mB range → the 0..1 the fader speaks. */
private fun normF(mb: Int): Float {
    val span = (NebulaEqualizer.levelMax - NebulaEqualizer.levelMin).coerceAtLeast(1)
    return ((mb - NebulaEqualizer.levelMin).toFloat() / span).coerceIn(0f, 1f)
}

/** …and back, rounded in the millibel unit the device speaks. */
private fun mBOf(f: Float): Int =
    (NebulaEqualizer.levelMin + f * (NebulaEqualizer.levelMax - NebulaEqualizer.levelMin))
        .roundToInt()

/**
 * The device's real band frequency, split onto two lines like Vox's labels
 * ("62 over Hz", "1k over kHz") — hardware facts, never the reference list.
 */
private fun freqLabel(hz: Int): String =
    if (hz >= 1000) {
        val khz = hz / 1000.0
        val t = if (khz == khz.toLong().toDouble()) khz.toLong().toString() else khz.toString()
        "$t\nkHz"
    } else {
        "$hz\nHz"
    }
