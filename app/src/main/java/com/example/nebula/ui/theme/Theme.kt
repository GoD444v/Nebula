package com.example.nebula.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// DataStore instance for theme preferences — replaces SharedPreferences
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "nebula_theme")

// VoxMusic draws its 3D shadow in the border colour, not black (vox_widgets.dart:43).
// On a dark palette black-on-black is invisible, so dark palettes get a grey shadow.
private val DarkShadow = Color(0xFF2E2E2E)

// VoxMusic concept: 6 full palettes — each carries bg + accents, exactly like VoxMusic
data class VoxPalette(
    val id: String,
    val name: String,
    val yellow: Color,   // tertiary
    val pink: Color,     // primary
    val cyan: Color,     // secondary
    val bg: Color,       // colorScheme.background
    val surface: Color,  // colorScheme.surface
    val onBg: Color,     // colorScheme.onBackground
    val onSurface: Color, // colorScheme.onSurface
    val nav: Color = pink // bottom-nav pill (colorScheme.primaryContainer). Defaults to pink.
)

val VoxPalettes = listOf(
    VoxPalette(
        id = "classic", name = "Classic 80s Vox",
        yellow = Color(0xFFFFDE00), pink = Color(0xFFFF0080), cyan = Color(0xFF00FFFF),
        bg = Color(0xFFF4F4F6), surface = Color(0xFFFFFFFF),
        onBg = Color(0xFF111111), onSurface = Color(0xFF111111)
    ),
    VoxPalette(
        id = "cyberpunk", name = "Cyberpunk Neon Matrix",
        yellow = Color(0xFFE5FF00), pink = Color(0xFFE91E63), cyan = Color(0xFF00F0FF),
        bg = Color(0xFF161622), surface = Color(0xFF222234),
        onBg = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF),
        // Neon cyan, not the default pink: pink-on-navy made the active nav pill
        // read as the same accent as every other primary element. Cyan is the
        // other neon in this palette and stays legible on the dark bar.
        nav = Color(0xFF00F0FF)
    ),
    VoxPalette(
        id = "sunset", name = "Sunset Arcade 90s",
        yellow = Color(0xFFFFB800), pink = Color(0xFFFF4D80), cyan = Color(0xFF19E6BA),
        // bg was #FFF7EC — almost the same as Classic's #F4F4F6, so the two picker circles
        // looked identical. Warm peach now reads clearly as "sunset".
        bg = Color(0xFFFFE7D0), surface = Color(0xFFFFFFFF),
        onBg = Color(0xFF1A1A1A), onSurface = Color(0xFF1A1A1A),
        // Warm amber rather than the default pink: on a peach background the pink
        // pill was nearly the same hue as the bar it sits on, so the selected tab
        // barely stood out. Amber is the warm end of this palette.
        nav = Color(0xFFFFB800)
    ),
    VoxPalette(
        id = "mono", name = "Neo Brutalist Mono",
        yellow = Color(0xFFE0E0E0), pink = Color(0xFFCCCCCC), cyan = Color(0xFFB8B8B8),
        bg = Color(0xFFEAEAEA), surface = Color(0xFFFFFFFF),
        onBg = Color(0xFF000000), onSurface = Color(0xFF000000)
    ),
    VoxPalette(
        id = "amoled", name = "AMOLED Pure Black",
        yellow = Color(0xFFFFB800), pink = Color(0xFFD81B60), cyan = Color(0xFF00F0FF),
        bg = Color(0xFF000000), surface = Color(0xFF0A0A0A),
        onBg = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF)
    ),
    VoxPalette(
        id = "midnight", name = "Midnight Deep Blue",
        yellow = Color(0xFFFFD166), pink = Color(0xFFB56576), cyan = Color(0xFF118AB2),
        bg = Color(0xFF001A24), surface = Color(0xFF00293D),
        onBg = Color(0xFFE0E1DD), onSurface = Color(0xFFE0E1DD)
    )
)

// ---- Custom palette: the 7 colours the user picks in Settings, stored as ARGB ints ----
data class VoxCustom(
    val bg: Int = 0xFF101014.toInt(),
    val card: Int = 0xFF1C1C24.toInt(),
    val icons: Int = 0xFFFFFFFF.toInt(),
    val nav: Int = 0xFFFF6B9D.toInt(),
    val pink: Int = 0xFFFF6B9D.toInt(),
    val teal: Int = 0xFF00D4AA.toInt(),
    val yellow: Int = 0xFFFFC700.toInt()
)

fun VoxCustom.toPalette() = VoxPalette(
    id = "custom", name = "My Palette",
    yellow = Color(yellow), pink = Color(pink), cyan = Color(teal),
    bg = Color(bg), surface = Color(card),
    onBg = Color(icons), onSurface = Color(icons),
    nav = Color(nav)
)

// One lookup for every screen: the 6 built-ins plus the user's own palette.
fun paletteById(id: String): VoxPalette =
    if (id == "custom") ThemeStore.custom.toPalette()
    else VoxPalettes.firstOrNull { it.id == id } ?: VoxPalettes[0]

object ThemeStore {
    var mode by mutableStateOf("system")
        private set
    var paletteId by mutableStateOf("classic")
        private set
    var custom by mutableStateOf(VoxCustom())
        private set

    private var dataStore: DataStore<Preferences>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        dataStore = context.applicationContext.themeDataStore
        runBlocking {
            val prefs = dataStore!!.data.first()
            mode = prefs[stringPreferencesKey("theme_mode")] ?: "system"
            paletteId = prefs[stringPreferencesKey("palette_id")] ?: "classic"
            val d = VoxCustom()
            custom = VoxCustom(
                bg = prefs[intPreferencesKey("c_bg")] ?: d.bg,
                card = prefs[intPreferencesKey("c_card")] ?: d.card,
                icons = prefs[intPreferencesKey("c_icons")] ?: d.icons,
                nav = prefs[intPreferencesKey("c_nav")] ?: d.nav,
                pink = prefs[intPreferencesKey("c_pink")] ?: d.pink,
                teal = prefs[intPreferencesKey("c_teal")] ?: d.teal,
                yellow = prefs[intPreferencesKey("c_yellow")] ?: d.yellow
            )
        }
    }

    fun setCustom(context: Context, next: VoxCustom) {
        custom = next
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[intPreferencesKey("c_bg")] = next.bg
                prefs[intPreferencesKey("c_card")] = next.card
                prefs[intPreferencesKey("c_icons")] = next.icons
                prefs[intPreferencesKey("c_nav")] = next.nav
                prefs[intPreferencesKey("c_pink")] = next.pink
                prefs[intPreferencesKey("c_teal")] = next.teal
                prefs[intPreferencesKey("c_yellow")] = next.yellow
            }
        }
    }

    fun setMode(context: Context, next: String) {
        mode = next
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("theme_mode")] = next
            }
        }
    }

    fun setPalette(context: Context, next: String) {
        paletteId = next
        scope.launch {
            dataStore?.edit { prefs ->
                prefs[stringPreferencesKey("palette_id")] = next
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = NeonPink,
    secondary = MintTeal,
    tertiary = SunnyYellow,
    background = CreamBackground,
    surface = CreamSurface,
    onBackground = TextBlack,
    onSurface = TextBlack,
    onPrimary = TextWhite,
    onSecondary = TextBlack,
    onTertiary = TextBlack
)

private val DarkGrayColors = darkColorScheme(
    primary = NeonPink,
    secondary = MintTeal,
    tertiary = SunnyYellow,
    background = DarkSurface,
    surface = DarkSurface,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onPrimary = TextWhite,
    onSecondary = TextBlack,
    onTertiary = TextBlack
)

private val DarkColors = darkColorScheme(
    primary = NeonPink,
    secondary = MintTeal,
    tertiary = SunnyYellow,
    background = AmoledBlack,
    surface = AmoledBlack,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onPrimary = TextWhite,
    onSecondary = TextBlack,
    onTertiary = TextBlack
)

@Composable
fun NebulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    mode: String = ThemeStore.mode,
    paletteId: String = ThemeStore.paletteId,
    content: @Composable () -> Unit
) {
    val palette = paletteById(paletteId)
    val base = when (mode) {
        "light" -> LightColors
        "dark" -> DarkGrayColors
        "amoled" -> DarkColors
        else -> if (darkTheme) DarkColors else LightColors
    }
    // Each palette carries its OWN bg/surface/text — switching palettes changes the whole look,
    // exactly like VoxMusic's pal.bg / pal.cardBg / pal.text
    val colors = base.copy(
        primary = palette.pink,
        secondary = palette.cyan,
        tertiary = palette.yellow,
        // primaryContainer is our "Navigation" slot — read only by the bottom-nav pill
        primaryContainer = palette.nav,
        background = palette.bg,
        surface = palette.surface,
        onBackground = palette.onBg,
        onSurface = palette.onSurface,
        // outline = our shared 3D-shadow colour (unused by Material3 elsewhere in Nebula)
        outline = if (palette.bg.luminance() < 0.5f) DarkShadow else BorderBlack
    )
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
