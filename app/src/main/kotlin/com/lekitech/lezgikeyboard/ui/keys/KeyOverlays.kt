package com.lekitech.lezgikeyboard.ui.keys

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.model.ShiftState
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/** Key frame in row coordinates (x, width of the key). */
internal data class KeyFrame(val x: Dp, val width: Dp)

/**
 * Places an overlay at (x, y) in row coordinates, measured **without**
 * the row's constraints: the 43 dp row would otherwise cap an
 * overlay's height and crop bubbles and menus (Compose children may
 * draw outside bounds but never measure beyond them). Reports zero
 * size so the row layout is unaffected.
 */
internal fun Modifier.overlayAt(x: Dp, y: Dp): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.placeRelative(x.roundToPx(), y.roundToPx()) }
}

internal val CALLOUT_OPTION_WIDTH = 44.dp
internal val BUBBLE_HEIGHT = 54.dp
private val NECK_ABOVE_KEY = 11.dp
private val CALLOUT_TAIL = 16.dp

/**
 * Native-style key preview: rounded bubble above the key, sides curving
 * inward to a neck exactly the key width, flat bottom aligned with the
 * key bottom (the pressed key's label hides underneath). Render-only.
 */
@Composable
internal fun KeyPreviewBubble(
    label: String,
    frame: KeyFrame,
    rowTopInKeyboard: Dp,
    colors: KeyboardColors,
) {
    val keyHeight = LezgiLayout.KEY_HEIGHT.dp
    val width = maxOf(frame.width, 44.dp)
    // Capped so top-row bubbles stay inside the keyboard view
    val totalHeight = minOf(
        BUBBLE_HEIGHT + NECK_ABOVE_KEY + keyHeight,
        rowTopInKeyboard + keyHeight,
    )
    val fontSize = with(LocalDensity.current) { Dp(28f).toSp() }
    Box(
        modifier = Modifier
            .overlayAt(x = frame.x + frame.width / 2 - width / 2, y = keyHeight - totalHeight)
            .size(width, totalHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.size(width, totalHeight)) {
            val inset = ((size.width - frame.width.toPx()) / 2f).coerceAtLeast(0f)
            val bubbleBottom = BUBBLE_HEIGHT.toPx()
            val r = 8.dp.toPx()
            val curveStart = bubbleBottom - 6.dp.toPx()
            val curveEnd = bubbleBottom + 6.dp.toPx()
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                quadraticBezierTo(w, 0f, w, r)
                lineTo(w, curveStart)
                cubicTo(w, bubbleBottom, w - inset, bubbleBottom, w - inset, curveEnd)
                lineTo(w - inset, h - r)
                quadraticBezierTo(w - inset, h, w - inset - r, h)
                lineTo(inset + r, h)
                quadraticBezierTo(inset, h, inset, h - r)
                lineTo(inset, curveEnd)
                cubicTo(inset, bubbleBottom, 0f, bubbleBottom, 0f, curveStart)
                lineTo(0f, r)
                quadraticBezierTo(0f, 0f, r, 0f)
                close()
            }
            drawPath(path, colors.letterKeyPressed, style = Fill)
        }
        Box(
            modifier = Modifier.size(width, BUBBLE_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = label, style = TextStyle(color = colors.label, fontSize = fontSize))
        }
    }
}

/**
 * Long-press callout: a horizontal bubble of alternates (44 per option,
 * base character first), the selected option white on blue, with a
 * key-width neck overlapping the key top by 9. Options display
 * case-adjusted; the raw string is dispatched through the normal
 * key-handling path. Clamped to the row edges.
 */
@Composable
internal fun CalloutBubble(
    options: List<String>,
    selectedIndex: Int,
    frame: KeyFrame,
    rowWidth: Dp,
    rowTopInKeyboard: Dp,
    shiftState: ShiftState,
    colors: KeyboardColors,
) {
    val totalWidth = CALLOUT_OPTION_WIDTH * options.size
    val bubbleLeft = calloutLeft(frame, totalWidth, rowWidth)
    val fontSize = with(LocalDensity.current) { Dp(24f).toSp() }
    // Clamped to the keyboard top: the IME window cannot draw above
    // itself, so top-row callouts slide down over the pressed key
    // instead of clipping — the reference keyboard clamps the same way.
    val bubbleTop = maxOf(-(BUBBLE_HEIGHT + CALLOUT_TAIL) + 9.dp, -rowTopInKeyboard)

    // Key-width neck centered under the pressed key
    Box(
        modifier = Modifier
            .overlayAt(x = frame.x, y = bubbleTop + BUBBLE_HEIGHT)
            .size(frame.width, CALLOUT_TAIL)
            .drawBehind { drawRect(colors.letterKeyPressed) },
    )
    Row(
        modifier = Modifier
            .overlayAt(x = bubbleLeft, y = bubbleTop)
            .height(BUBBLE_HEIGHT)
            .drawBehind {
                drawRoundRect(
                    colors.letterKeyPressed,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            },
    ) {
        options.forEachIndexed { index, option ->
            val display = if (shiftState != ShiftState.OFF) {
                LezgiLayout.applyCase(option, shiftState == ShiftState.CAPS_LOCK)
            } else option
            Box(
                modifier = Modifier
                    .size(CALLOUT_OPTION_WIDTH, BUBBLE_HEIGHT)
                    .drawBehind {
                        if (index == selectedIndex) {
                            drawRoundRect(
                                Color(0xFF007AFF),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = display,
                    style = TextStyle(
                        color = if (index == selectedIndex) Color.White else colors.label,
                        fontSize = fontSize,
                    ),
                )
            }
        }
    }
}

internal fun calloutLeft(frame: KeyFrame, totalWidth: Dp, rowWidth: Dp): Dp {
    val centered = frame.x + frame.width / 2 - totalWidth / 2
    val min = 0.dp
    val max = rowWidth - totalWidth
    return centered.coerceIn(minOf(min, max), maxOf(min, max))
}

internal val MENU_WIDTH = 196.dp
internal val MENU_ROW_HEIGHT = 52.dp
internal val MENU_GAP = 8.dp

/** Menu card x in row coordinates (screen margins ≈ 4 dp = row −2 dp). */
internal fun layoutMenuLeft(frame: KeyFrame, rowWidth: Dp): Dp =
    frame.x.coerceIn((-2).dp, rowWidth - MENU_WIDTH + 2.dp)

/**
 * Quick layout switcher shown above the gear key on long press: two
 * rows (the current variant always on top, marked by its accent-tinted
 * title), the row under the finger highlighted. Releasing over a row
 * applies it; releasing outside changes nothing. Render-only.
 */
@Composable
internal fun LayoutMenuBubble(
    variants: List<LayoutVariant>,
    highlightedIndex: Int?,
    currentVariant: LayoutVariant,
    frame: KeyFrame,
    rowWidth: Dp,
    colors: KeyboardColors,
) {
    val left = layoutMenuLeft(frame, rowWidth)
    val titleSize = with(LocalDensity.current) { Dp(15f).toSp() }
    val subtitleSize = with(LocalDensity.current) { Dp(11f).toSp() }
    Column(
        modifier = Modifier
            .overlayAt(x = left, y = -(MENU_GAP + MENU_ROW_HEIGHT * variants.size))
            .width(MENU_WIDTH)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(colors.menuCard, cornerRadius = CornerRadius(12.dp.toPx()))
            },
    ) {
        variants.forEachIndexed { index, variant ->
            Box(
                modifier = Modifier
                    .size(MENU_WIDTH, MENU_ROW_HEIGHT)
                    .drawBehind {
                        if (highlightedIndex == index) {
                            drawRoundRect(
                                colors.menuAccentTint,
                                cornerRadius = CornerRadius(12.dp.toPx()),
                            )
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    modifier = Modifier.offset(x = 13.dp),
                ) {
                    BasicText(
                        text = if (variant == LayoutVariant.CLASSIC)
                            "Ъ — арадин къвалаг" else "Ъ — вини жергеда",
                        style = TextStyle(
                            color = if (variant == currentVariant) colors.menuAccent else colors.label,
                            fontSize = titleSize,
                        ),
                    )
                    BasicText(
                        text = if (variant == LayoutVariant.CLASSIC)
                            "Сад лагьай вариант" else "Кьвед лагьай вариант",
                        style = TextStyle(color = colors.menuSecondary, fontSize = subtitleSize),
                    )
                }
            }
        }
    }
}
