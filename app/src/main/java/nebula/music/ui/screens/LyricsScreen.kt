package nebula.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nebula.music.data.models.LyricHighlight
import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricWord
import nebula.music.data.models.LyricsStyleStore
import nebula.music.ui.theme.BorderBlack
import nebula.music.viewmodel.PlayerViewModel
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synced lyrics panel: highlight style + word color come from LyricsStyleStore
 * (Settings → Lyrics). `expanded` removes the card height caps for fullscreen.
 * `onFullScreen` — when non-null, draws the chunky full-screen button in the
 * panel's top-right corner; pass it only where full screen is reachable.
 * Plain-text fallback when no timestamps exist. VoxMusic card: 3px border + extrusion.
 */
@Composable
fun LyricsScreen(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onFullScreen: (() -> Unit)? = null
) {
    val response by vm.lyrics.collectAsState()
    val activeIndex by vm.lyricIndex.collectAsState()
    val isLoadingLyrics by vm.isLoadingLyrics.collectAsState()
    // NOTE: the playback position is deliberately NOT read here. LyricsScreen's
    // scope is the parent of the LazyColumn, so a read at this level would
    // recompose every visible lyric line 30x/sec. ActiveLine reads it instead —
    // see the note on ActiveLine.
    // context/style/accent get a fresh read each composition, so Settings changes
    // apply the next time this draws.
    val context = LocalContext.current
    val style = LyricsStyleStore.getMode(context)
    val accent = LyricsStyleStore.getCustomColor(context)?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Box(modifier = modifier.fillMaxWidth()) {
        // Side-extrusion 3D shadow
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxSize() else Modifier.heightIn(min = 120.dp, max = 420.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxSize() else Modifier.heightIn(min = 120.dp, max = 420.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, BorderBlack, RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val res = response
            when {
                // Fetch in flight — the only state that may show the spinner.
                isLoadingLyrics -> Text(
                    "Finding lyrics…",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                vm.currentVideoId.isBlank() -> Text(
                    "Play a song to see its lyrics",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                // Fetch finished with no match — terminal state, never the spinner.
                //
                // Distinguishes "no provider has this song" from "we could not ask".
                // All four providers are network APIs, so offline a downloaded song lands
                // here, and claiming no lyrics exist when the truth is we had no way to
                // look is the message that makes this read as a bug.
                res == null -> Text(
                    if (vm.isOffline) {
                        "No lyrics for this song.\nConnect to look them up."
                    } else {
                        "No lyrics found for this song"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                // Plain-text fallback: same card, scrollable for long lyrics
                !res.isSynced && res.plainText.isNotBlank() -> Text(
                    res.plainText,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                )
                !res.isSynced -> Text(
                    "No lyrics found for this song",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                else -> SyncedList(
                    vm = vm,
                    lines = res.lines,
                    activeIndex = activeIndex,
                    style = style,
                    accent = accent,
                    dim = dim,
                    expanded = expanded,
                    onSeek = vm::seekTo
                )
            }
        }

        // Full-screen lives INSIDE the panel, so the Lyrics card itself is a
        // single tap target. Overlaid rather than given its own header row: a
        // header would push the lyrics down and the panel is already short.
        if (onFullScreen != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                IconButton(
                    onClick = onFullScreen,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "Full screen",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedList(
    vm: PlayerViewModel,
    lines: List<LyricLine>,
    activeIndex: Int,
    style: LyricHighlight,
    accent: Color,
    dim: Color,
    expanded: Boolean,
    onSeek: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // Keep the sung line visible: scroll so it sits a couple of rows below the top.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.heightIn(max = 396.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val active = i == activeIndex
            if (!active) {
                // Static text. Reads no playback state, so it never recomposes
                // while the song plays. Non-active lines do no per-word work at
                // all — a line is a single plain Text until it is the sung one.
                Text(
                    line.text,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                    color = dim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.timeMs) }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else {
                ActiveLine(vm = vm, line = line, style = style, accent = accent, dim = dim, onSeek = { onSeek(line.timeMs) })
            }
        }
    }
}

/**
 * The sung line in the user's chosen style.
 *
 * A line is ONE `Text` whose text is an [AnnotatedString]: one [SpanStyle] per
 * word. That is the whole point of this file. Per-word composables used to mean
 * N text nodes per line, and a font weight flip across N nodes is N remeasures
 * per frame — so the old code froze weight and every style degenerated into an
 * alpha ramp, which is why they all looked the same. Inside a single text node a
 * weight change is one layout pass, so the three axes (alpha buckets, weight
 * ladder, per-word shadow/brush) can all move and the styles are visibly
 * distinct from each other.
 *
 * The ~30fps `lyricsPositionMs` read lives HERE and nowhere above it. Compose
 * scopes a state read to the enclosing restartable function, so a 30fps tick
 * invalidates ActiveLine's own group and nothing else: the LazyColumn, the item
 * lambda and the static non-active lines are not in that group, so they keep
 * their composition. Exactly one line's worth of text layout is rebuilt per frame.
 */
@Composable
private fun ActiveLine(
    vm: PlayerViewModel,
    line: LyricLine,
    style: LyricHighlight,
    accent: Color,
    dim: Color,
    onSeek: () -> Unit
) {
    // No word timings → every style degrades to the whole-line look. This early
    // return happens BEFORE the position read, so a LINE-style user pays nothing
    // for the frame-rate ticker.
    if (line.words.isEmpty() || style == LyricHighlight.LINE) {
        Text(
            line.text,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            color = accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSeek() }
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )
        return
    }
    val positionMs = vm.lyricsPositionMs
    val text = when (style) {
        LyricHighlight.APPLE -> appleLine(line, positionMs, accent, dim)
        LyricHighlight.APPLE_V2 -> appleV2Line(line, positionMs, accent, dim)
        LyricHighlight.FADE -> fadeLine(line, positionMs, accent, dim)
        LyricHighlight.GLOW -> glowLine(line, positionMs, accent, dim)
        else -> karaokeLine(line, positionMs, accent, dim) // KARAOKE
    }
    // color is the neutral the unstyled separators inherit; every real word
    // carries its own explicit colour from its SpanStyle.
    Text(
        text = text,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        textAlign = TextAlign.Center,
        color = dim,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeek() }
            .padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

/** 0 before the word starts, 1 once it is over, fractional while it is sung. */
private fun wordProgress(w: LyricWord, positionMs: Long): Float {
    val span = (w.endMs - w.startMs).toFloat()
    if (span <= 0f) return if (w.startMs <= positionMs) 1f else 0f
    return ((positionMs - w.startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** Eased 0..1: flat where the word starts and where it lands, steep in between. */
private fun smoothstep(p: Float): Float = p * p * (3f - 2f * p)

/**
 * One word's three axes collapsed into a [SpanStyle]. A word gets exactly one
 * fill: [brush] for styles that sweep inside the word, otherwise a flat
 * [color]. Omitted axes fall back to the parent Text's style.
 */
private fun spanOf(
    weight: FontWeight,
    color: Color? = null,
    brush: Brush? = null,
    shadow: Shadow? = null
): SpanStyle =
    // Compose 1.8's SpanStyle has a separate constructor for a brush fill, so
    // the two axes cannot be passed together. Pick the branch instead.
    if (brush != null) {
        SpanStyle(brush = brush, fontWeight = weight, shadow = shadow)
    } else {
        SpanStyle(color = color ?: Color.Unspecified, fontWeight = weight, shadow = shadow)
    }

/**
 * Fill brush with its front edge parked at [p] (0..1) *inside a single word*:
 * full accent behind the edge, an [edge]-wide falloff to [ahead] in front of it.
 * Gives the fill a leading edge instead of a hard per-word boundary.
 *
 * A SpanStyle brush maps 0..1 across that span's own width, so this can only
 * ever sweep one word. That is a real limit, not a bug to work around: a
 * line-wide sweep would mean going back to per-character text nodes.
 */
private fun leadingEdge(accent: Color, ahead: Color, p: Float, edge: Float): Brush =
    Brush.horizontalGradient(
        0f to accent,
        (p * (1f - edge)).coerceAtLeast(0f) to accent,
        p to accent,
        (p + edge).coerceAtMost(1f) to ahead,
        1f to ahead
    )

/**
 * The word loop every style shares: one styled span per word, one PLAIN
 * separator between them.
 *
 * [resolve] is handed the word index, the word and its 0..1 progress and returns
 * that word's SpanStyle — the per-style three-axis decision lives there.
 */
private fun buildWordLine(
    line: LyricLine,
    positionMs: Long,
    resolve: (index: Int, word: LyricWord, p: Float) -> SpanStyle
): AnnotatedString = buildAnnotatedString {
    line.words.forEachIndexed { i, w ->
        if (w.text.isBlank()) {
            // Kugou hands over separator "words" (see the spaceAfter note). They
            // are appended unstyled: whitespace never takes colour, brush,
            // weight or shadow.
            append(w.text)
        } else {
            withStyle(resolve(i, w, wordProgress(w, positionMs))) { append(w.text) }
        }
        spaceAfter(w)
    }
}

/**
 * Separator between words — appended only when the word does not already end in
 * whitespace. `KugouLyrics.kt:133` stores the raw KRC payload, whose per-word
 * text carries its own trailing space, so appending unconditionally turned every
 * Kugou line into double-spaced text. Providers that trim per word (Unison) still
 * get the gap here. The separator is appended OUTSIDE any withStyle block, so it
 * is unstyled — a space must never light up.
 */
private fun AnnotatedString.Builder.spaceAfter(word: LyricWord) {
    if (!word.text.isBlank() && !word.text.last().isWhitespace()) append(" ")
}

/**
 * KARAOKE — the flat baseline. Binary per-word snap and nothing else: one colour
 * per word, one weight for the whole line, no brush, no shadow, no within-word
 * motion. Every other style is deliberately unlike this one, and it is unlike
 * them because it does less, not because it does the same thing more.
 *
 * Axes: alpha 1 bucket (0.40 dim future / 1.0 accent sung), weight SemiBold
 * everywhere, no shadow. The flip is on the word's START, as karaoke always has.
 */
private fun karaokeLine(line: LyricLine, positionMs: Long, accent: Color, dim: Color) =
    buildWordLine(line, positionMs) { _, w, _ ->
        val sung = w.startMs <= positionMs
        spanOf(
            weight = FontWeight.SemiBold,
            color = if (sung) accent else dim.copy(alpha = 0.40f)
        )
    }

/**
 * APPLE — the current word is *filled in* left to right through a gradient front
 * edge, and it is visibly heavier than its neighbours, so the fill has somewhere
 * to go. Karaoke instead snaps each whole word dim→solid in one step.
 *
 * Axes: alpha 3 buckets (0.28 future / gradient sweep on the active word / 1.0
 * passed), weight ladder Medium → Black → SemiBold, soft shadow on the active
 * word only and NO afterimage on the words behind it (that is GLOW's signature).
 */
private fun appleLine(line: LyricLine, positionMs: Long, accent: Color, dim: Color) =
    buildWordLine(line, positionMs) { _, _, p ->
        val e = smoothstep(p)
        when {
            p >= 1f -> spanOf(weight = FontWeight.SemiBold, color = accent)
            p > 0f -> spanOf(
                weight = FontWeight.Black,
                brush = leadingEdge(accent, dim.copy(alpha = 0.28f), e, edge = 0.18f),
                // Tight pop on the leading edge, back to nothing as it lands.
                shadow = Shadow(accent.copy(alpha = 0.18f + 0.42f * e), Offset.Zero, 8f + 10f * e)
            )
            else -> spanOf(weight = FontWeight.Medium, color = dim.copy(alpha = 0.28f))
        }
    }

/**
 * APPLE_V2 — per-character fill inside the active word: the word's own duration
 * is spread over its letters, so the accent lands letter by letter in visible
 * steps instead of as one continuous sweep. Steps of a *lighter* weight ladder
 * than Apple, a much harder (narrow) front edge, and no shadow left behind.
 *
 * Axes: alpha 3 buckets (0.35 future / per-char gradient / 1.0 passed), weight
 * ladder Normal → Black → Medium, shadow on the filling character only.
 */
private fun appleV2Line(line: LyricLine, positionMs: Long, accent: Color, dim: Color): AnnotatedString {
    val ahead = dim.copy(alpha = 0.35f)
    return buildAnnotatedString {
        line.words.forEach { w ->
            val p = wordProgress(w, positionMs)
            when {
                // Separator "words" — see buildWordLine.
                w.text.isBlank() -> append(w.text)
                // The one word doing per-character work. Each letter takes its
                // slice of the word's duration, and that slice is the leading
                // edge of a brush scoped to the letter itself.
                p > 0f && p < 1f -> {
                    val n = w.text.length
                    val e = smoothstep(p)
                    w.text.forEachIndexed { i, ch ->
                        if (ch.isWhitespace()) {
                            append(ch)
                        } else {
                            val ce = smoothstep(((p - i.toFloat() / n) * n).coerceIn(0f, 1f))
                            withStyle(
                                if (ce >= 1f) {
                                    spanOf(weight = FontWeight.Medium, color = accent)
                                } else {
                                    spanOf(
                                        weight = FontWeight.Black,
                                        brush = leadingEdge(accent, ahead, ce, edge = 0.10f),
                                        shadow = Shadow(
                                            accent.copy(alpha = 0.16f + 0.30f * ce * e),
                                            Offset.Zero,
                                            4f + 6f * ce * e
                                        )
                                    )
                                }
                            ) { append(ch) }
                        }
                    }
                }
                p >= 1f -> withStyle(spanOf(weight = FontWeight.Medium, color = accent)) { append(w.text) }
                else -> withStyle(spanOf(weight = FontWeight.Normal, color = ahead)) { append(w.text) }
            }
            spaceAfter(w)
        }
    }
}

/**
 * FADE — the signature axis is DISTANCE, not time. Future words fall off in
 * steps away from the frontier word, losing both alpha and a weight step per
 * step, so the line reads as a horizon. The current word eases its own alpha in
 * over its duration, and there is no afterimage behind the singer.
 *
 * Axes: alpha 4 buckets (0.46 → 0.37 → 0.28 → 0.16 by distance, plus the
 * active word's 0.45→1.0 sweep and 1.0 passed), weight ladder SemiBold →
 * Medium → Light by distance with the active word at Black, shadow on the active
 * word only, scaling to zero.
 */
private fun fadeLine(line: LyricLine, positionMs: Long, accent: Color, dim: Color): AnnotatedString {
    val frontier = line.words.indexOfLast { it.startMs <= positionMs }
    return buildWordLine(line, positionMs) { i, _, p ->
        val e = smoothstep(p)
        when {
            p >= 1f -> spanOf(weight = FontWeight.Medium, color = accent)
            p > 0f -> spanOf(
                weight = FontWeight.Black,
                color = accent.copy(alpha = 0.45f + 0.55f * e),
                shadow = Shadow(accent.copy(alpha = 0.30f * e), Offset.Zero, 6f * e)
            )
            else -> {
                val d = (i - frontier - 1).coerceAtLeast(0)
                spanOf(
                    weight = when (d) {
                        0 -> FontWeight.SemiBold
                        1 -> FontWeight.Medium
                        else -> FontWeight.Light
                    },
                    color = dim.copy(alpha = (0.46f - 0.09f * d).coerceAtLeast(0.16f))
                )
            }
        }
    }
}

/**
 * GLOW — a halo that LEADS the fill: the shadow term runs ahead of the fill
 * progress, so the light arrives at a word before the word is full, and it
 * breathes on a 1.6s cycle driven by the playback clock (so it freezes when
 * paused). Words behind keep a soft afterimage that never quite goes away.
 *
 * Axes: alpha 3 buckets (0.35 future / 0.45→1.0 brightness ramp on the active
 * word / 1.0 passed), weight ladder Medium → Black → Bold, a 0.30/12f
 * afterimage on every passed word and a breath-modulated halo on the active one.
 */
private fun glowLine(line: LyricLine, positionMs: Long, accent: Color, dim: Color): AnnotatedString {
    val ahead = dim.copy(alpha = 0.5f)
    val breath = (sin((positionMs % 1600L) / 1600f * 2f * PI.toFloat()) * 0.5f + 0.5f)
    return buildWordLine(line, positionMs) { _, _, p ->
        val e = smoothstep(p)
        when {
            p >= 1f -> spanOf(
                weight = FontWeight.Bold,
                color = accent,
                shadow = Shadow(accent.copy(alpha = 0.30f), Offset.Zero, 12f)
            )
            p > 0f -> {
                // Halo runs ahead of the fill: it is full before the word is.
                val lead = (e * 1.25f).coerceAtMost(1f)
                spanOf(
                    weight = FontWeight.Black,
                    brush = leadingEdge(accent, ahead, e, edge = 0.32f),
                    shadow = Shadow(
                        color = accent.copy(alpha = (0.35f + 0.45f * lead) * (0.72f + 0.28f * breath)),
                        offset = Offset.Zero,
                        blurRadius = 14f + 20f * lead
                    )
                )
            }
            else -> spanOf(weight = FontWeight.Medium, color = dim.copy(alpha = 0.35f))
        }
    }
}
