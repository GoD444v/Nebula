package nebula.music.ui.components

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nebula.music.ui.theme.BorderBlack

/**
 * Immersive "real full screen": status bar AND nav bar hidden. Everything is
 * restored on dispose, so leaving the screen (back gesture, sheet close, config
 * change) always brings the bars back.
 *
 * Works both on the Activity window and inside a Compose [Dialog] — the dialog
 * case is what full-screen lyrics uses, and a Dialog owns its own window, so
 * hiding bars on the Activity alone would leave the dialog's bars visible.
 *
 * `lockPortrait` is OFF by default and must stay off for any caller inside a
 * Dialog. Writing `requestedOrientation` from a Dialog mutates the *Activity*,
 * and if the device is actually in landscape that change RECREATES the
 * Activity: every `remember` in MainActivity resets (current tab back to home,
 * sheet closed) and the dialog is destroyed while it is open. The user is
 * silently returned to a different screen, and the next back press finds no
 * handler and exits to the launcher. Only lock portrait from a caller that owns
 * the whole Activity and can afford the recreation.
 */
@Composable
fun ImmersiveMode(enabled: Boolean, lockPortrait: Boolean = false) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, lockPortrait, view, activity) {
        if (!enabled) return@DisposableEffect onDispose { }
        // DialogWindowProvider only exists inside a Dialog; otherwise use the Activity.
        val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val wasPortrait = activity?.requestedOrientation

        // Swipe from the edge brings the bars back temporarily instead of
        // stranding the user with no way out.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (lockPortrait) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (lockPortrait) {
                activity?.requestedOrientation =
                    wasPortrait ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Mini window in the app's own idiom: 3px black border, side-extrusion 3D
 * shadow, no Material chrome. Replaces AlertDialog for the small
 * pick-one / warn-then-act prompts.
 */
@Composable
fun ChunkyWindow(
    title: String,
    onDismissRequest: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        Box {
            // matchParentSize -> shadow always matches the real card, and room
            // inside the window keeps the 6dp offset from being cut off.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, BorderBlack, shape)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Back arrow in the window's top-left, matching the player.
                    if (onBack != null) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .offset(x = 3.dp, y = 3.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(2.5.dp, BorderBlack, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Full-width extruded action inside a [ChunkyWindow] or a settings card. */
@Composable
fun ChunkyAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    icon: ImageVector? = null,
    height: Dp = 48.dp
) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(color)
                .border(3.dp, BorderBlack, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
