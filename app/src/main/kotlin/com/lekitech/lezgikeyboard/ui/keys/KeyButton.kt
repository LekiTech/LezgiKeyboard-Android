package com.lekitech.lezgikeyboard.ui.keys

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isPressed: Boolean,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
) {
    val background = if (isPressed) colors.letterKeyPressed else colors.letterKey
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
        KeyLabel(cap, returnAction, colors)
    }
}

/**
 * Character keys target the reference keyboard's look: between Regular
 * and Medium, slightly condensed, letters reading tall (spec §4 —
 * weight 0.19 on a 0.0→0.23 regular→medium scale, width −0.1). Matched
 * by eye against the iOS build; adjust these two constants only.
 */
private val characterWeight = FontWeight(480)
private val characterSpacing = (-0.01).em

@Composable
private fun KeyLabel(cap: KeyCap, returnAction: ReturnKeyAction, colors: KeyboardColors) {
    when (cap) {
        KeyCap.Backspace -> KeyIcon(R.drawable.ic_key_backspace, 22.dp, colors)
        KeyCap.Shift -> KeyIcon(R.drawable.ic_key_shift, 22.dp, colors)
        KeyCap.Settings -> KeyIcon(R.drawable.ic_key_settings, 24.dp, colors)
        KeyCap.Emoji -> KeyIcon(R.drawable.ic_key_emoji, 24.dp, colors)

        KeyCap.Space -> Unit  // «ЛЕЗГ» corner label and the name flash arrive with Stage 3

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
            val label = LezgiLayout.label(cap, shifted = false)
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
