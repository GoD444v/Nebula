package com.example.nebula.ui.theme

// ponytail: brand accents ported from VoxMusic (MIT) — see licenses/VOXMUSIC-MIT.txt
// SPDX-License-Identifier: GPL-3.0-or-later
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Backgrounds
val CreamBackground = Color(0xFFFFF6E9)
val CreamSurface = Color(0xFFFFFBF2)
val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF1A1A1A)

// Brand accents - VoxMusic classic values (voted over house pink)
val NeonPink = Color(0xFFFF0080)
val SunnyYellow = Color(0xFFFFDE00)
val MintTeal = Color(0xFF00FFFF)

// Borders and text
val BorderBlack = Color(0xFF000000)
val TextBlack = Color(0xFF111111)
val TextWhite = Color(0xFFFFFFFF)

/**
 * Secondary label colour: the ink of the current palette at reduced weight.
 *
 * NOT a fixed grey. It was `Color(0xFF6B6B6B)`, which cannot invert, so on AMOLED
 * (#0A0A0A), Cyberpunk (#222234) and Midnight (#00293D) it measured 2.9:1 — below the
 * 4.5:1 minimum — and every "N tracks" and artist line was hard to read on those three
 * palettes while looking fine on Classic. A fixed constant is the wrong shape for a value
 * whose job is "a quieter version of whatever ink this palette uses".
 *
 * @Composable because it reads the current palette, which is per-theme state. Call sites
 * are all already inside a composition, so this is a drop-in swap that needs no call-site
 * edit — which matters, because there are 26 of them and each is a chance to miss one.
 */
@Composable
fun TextGrey(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
