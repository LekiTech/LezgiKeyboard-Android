package com.lekitech.lezgikeyboard.ui.keys

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lekitech.lezgikeyboard.R
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.ShiftState
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/**
 * Single key: visual only — gestures live on the row's shared surface.
 * Key shape: corner radius 8, hairline bottom shadow (black 30%,
 * offset y=1, no blur), uniform key color, pressed state in the
 * pressed color (character keys switch to the preview bubble in
 * Stage 3).
 */
@Composable
fun KeyButton(
    cap: KeyCap,
    returnAction: ReturnKeyAction,
    shiftState: ShiftState,
    isPressed: Boolean,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
    hideLabel: Boolean = false,
    spaceFlash: Boolean = false,
    spaceHint: Boolean = true,
) {
    // While a character key shows its preview bubble, the key's own
    // label hides underneath it, like the native keyboard. The space
    // bar is highlighted while it shows the keyboard name.
    val background = if ((isPressed && !hideLabel) || (cap == KeyCap.Space && spaceFlash)) {
        colors.letterKeyPressed
    } else colors.letterKey
    Box(
        modifier = modifier.drawBehind {
            val radius = CornerRadius(8.dp.toPx())
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.3f),
                topLeft = Offset(0f, 1.dp.toPx()),
                cornerRadius = radius,
            )
            drawRoundRect(color = background, cornerRadius = radius)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (!hideLabel) {
            if (cap == KeyCap.Space) {
                SpaceContent(spaceFlash, spaceHint, colors)
            } else {
                KeyLabel(cap, returnAction, shiftState, colors)
            }
        }
    }
}

/**
 * Space-bar dressing: the «ЛЕЗГ» corner hint (hidden while the name
 * shows, and permanently when its setting is off) and the keyboard
 * name «Лезги чӏал» flashed centered after appearance, fading 0.25 s.
 */
@Composable
private fun BoxScope.SpaceContent(flash: Boolean, hint: Boolean, colors: KeyboardColors) {
    val flashAlpha by animateFloatAsState(
        targetValue = if (flash) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "spaceFlash",
    )
    val hintSize = with(LocalDensity.current) { Dp(10f).toSp() }
    val nameSize = with(LocalDensity.current) { Dp(16f).toSp() }
    BasicText(
        text = "ЛЕЗГ",
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 4.dp)
            .alpha(if (hint) 1f - flashAlpha else 0f),
        style = TextStyle(color = colors.spaceHint, fontSize = hintSize),
    )
    if (flashAlpha > 0.01f) {
        BasicText(
            text = "Лезги чӏал",
            modifier = Modifier.align(Alignment.Center).alpha(flashAlpha),
            style = TextStyle(color = colors.label, fontSize = nameSize),
        )
    }
}

/**
 * Character keys target the reference keyboard's look: between Regular
 * and Medium, slightly condensed, letters reading tall (spec §4 —
 * weight 0.19 on a 0.0→0.23 regular→medium scale, width −0.1). Matched
 * by eye against the iOS build; adjust these constants only.
 *
 * Lowercase labels render 1.2× larger (the x-height bump), and larger
 * glyphs at the same weight have proportionally thicker strokes. SF
 * compensates automatically through optical sizing; Roboto has no
 * optical axis, so the bumped lowercase takes a lighter weight to keep
 * the perceived stroke weight equal across the case switch.
 */
private val characterWeightUpper = FontWeight(480)
private val characterWeightLower = FontWeight(430)
private val characterSpacing = (-0.01).em

@Composable
private fun KeyLabel(
    cap: KeyCap,
    returnAction: ReturnKeyAction,
    shiftState: ShiftState,
    colors: KeyboardColors,
) {
    when (cap) {
        KeyCap.Backspace -> KeyIcon(R.drawable.ic_key_backspace, 22.dp, colors)
        KeyCap.Shift -> KeyIcon(
            resId = when (shiftState) {
                ShiftState.OFF -> R.drawable.ic_key_shift
                ShiftState.ONCE -> R.drawable.ic_key_shift_on
                ShiftState.CAPS_LOCK -> R.drawable.ic_key_capslock
            },
            size = 22.dp,
            colors = colors,
        )
        KeyCap.Settings -> KeyIcon(R.drawable.ic_key_settings, 24.dp, colors)
        KeyCap.Emoji -> KeyIcon(R.drawable.ic_key_emoji, 24.dp, colors)
        KeyCap.Globe -> KeyIcon(R.drawable.ic_key_globe, 22.dp, colors)


        KeyCap.Return -> when {
            returnAction == ReturnKeyAction.SEARCH ->
                KeyIcon(R.drawable.ic_key_search, 22.dp, colors)
            LezgiLayout.returnLabel(returnAction).isEmpty() ->
                KeyIcon(R.drawable.ic_key_return, 22.dp, colors)
            else -> KeyText(
                text = LezgiLayout.returnLabel(returnAction),
                sizeDp = 14f,
                color = colors.label,
            )
        }

        else -> {
            val label = LezgiLayout.label(cap, shifted = shiftState != ShiftState.OFF)
            KeyText(
                text = label,
                sizeDp = LezgiLayout.fontSize(cap, label),
                color = colors.label,
                character = cap is KeyCap.Character,
            )
        }
    }
}

@Composable
private fun KeyText(text: String, sizeDp: Float, color: Color, character: Boolean = false) {
    // Label sizes are density-fixed (dp, not scaled sp): the keyboard's
    // geometry is a fixed contract and labels must never outgrow their
    // keys with the system font-size setting (DECISIONS.md D-020).
    val fontSize = with(LocalDensity.current) { Dp(sizeDp).toSp() }
    val characterWeight =
        if (text != text.uppercase()) characterWeightLower else characterWeightUpper
    BasicText(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = if (character) characterWeight else FontWeight.Normal,
            letterSpacing = if (character) characterSpacing else TextStyle.Default.letterSpacing,
        ),
    )
}

@Composable
private fun KeyIcon(resId: Int, size: Dp, colors: KeyboardColors) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(colors.label),
    )
}
