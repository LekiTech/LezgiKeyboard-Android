package com.lekitech.lezgikeyboard.ui.suggestions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors
import kotlinx.coroutines.delay

/**
 * Suggestion-cell text with the reference bar's per-glyph motion
 * (ANDROID_PORT_CONTEXT.md §6):
 *
 * 1. **Prefix morph** — the word was extended or shortened while
 *    typing (old and new non-empty, one a prefix of the other):
 *    retained glyphs slide to their new centered positions while the
 *    added/removed glyph fades, easeOut 0.2 s; the cell width animates
 *    in the same motion.
 * 2. **New word** — any other change: glyphs take their final layout
 *    instantly, then settle left→right from transparent, scale 1.15,
 *    y +1.5, easeOut 0.2 s with a 22 ms per-letter stagger.
 * 3. Different candidate sets therefore snap with no bar sliding.
 *
 * The «…» guillemets around a literal candidate render outside the
 * glyph run, so glyph identity and the morph are unaffected. Purely
 * visual — gestures live on the bar's own layer.
 */
@Composable
fun MorphingWordText(
    word: String,
    quoted: Boolean,
    colors: KeyboardColors,
) {
    // `base` is the backing glyph string: it may keep a hidden tail
    // beyond `word` while a shortened glyph animates out (the stale
    // glyphs occupy zero width once collapsed). `generation` bumps on
    // every non-morph change, remounting the glyph run for the ripple.
    var base by remember { mutableStateOf(word) }
    var generation by remember { mutableIntStateOf(0) }
    val previous = remember { mutableStateOf(word) }
    if (previous.value != word) {
        val old = previous.value
        val morphs = old.isNotEmpty() && word.isNotEmpty() &&
            (word.startsWith(old) || old.startsWith(word))
        if (!morphs) {
            base = word
            generation++
        } else if (!base.startsWith(word)) {
            base = word
        }
        previous.value = word
    }

    val fontSize = with(LocalDensity.current) { Dp(BAR_TEXT_SIZE).toSp() }
    val style = TextStyle(color = colors.label, fontSize = fontSize)

    // Overlong words fall back to a plain label (no morph), shrinking
    // slightly before truncating — Lezgi words run long. Approximated
    // by glyph count until the real engine brings real words (Stage 5).
    if (word.length > OVERFLOW_GLYPHS) {
        BasicText(
            text = if (quoted) "«$word»" else word,
            maxLines = 1,
            style = style,
        )
        return
    }

    Row {
        if (quoted) BasicText(text = "«", style = style)
        key(generation) {
            val initialLength = remember { base.length }
            base.forEachIndexed { index, glyph ->
                // Glyphs present at (re)mount skip the enter animation —
                // the ripple below owns their appearance; glyphs added
                // by a later prefix morph fade/expand in instead.
                val visibility = remember {
                    MutableTransitionState(index < initialLength)
                }
                visibility.targetState = index < word.length
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(MORPH_MS, easing = EaseOut)) +
                        expandHorizontally(tween(MORPH_MS, easing = EaseOut)),
                    exit = fadeOut(tween(MORPH_MS, easing = EaseOut)) +
                        shrinkHorizontally(tween(MORPH_MS, easing = EaseOut)),
                ) {
                    val settle = remember { Animatable(if (index < initialLength) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (settle.value < 1f) {
                            delay(STAGGER_MS * index)
                            settle.animateTo(1f, tween(MORPH_MS, easing = EaseOut))
                        }
                    }
                    val settleOffset = with(LocalDensity.current) { 1.5.dp.toPx() }
                    BasicText(
                        text = glyph.toString(),
                        style = style,
                        modifier = Modifier.graphicsLayer {
                            alpha = settle.value
                            val scale = 1.15f - 0.15f * settle.value
                            scaleX = scale
                            scaleY = scale
                            translationY = settleOffset * (1f - settle.value)
                        },
                    )
                }
            }
        }
        if (quoted) BasicText(text = "»", style = style)
    }
}

/** Bar text size in dp (density-fixed, D-020); chosen on device on iOS. */
internal const val BAR_TEXT_SIZE = 18f

private const val MORPH_MS = 200
private const val STAGGER_MS = 22L
private const val OVERFLOW_GLYPHS = 18
