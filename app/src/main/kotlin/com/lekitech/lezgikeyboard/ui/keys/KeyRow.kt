package com.lekitech.lezgikeyboard.ui.keys

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.ShiftState
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One key row: a visual layer of `KeyButton`s laid out by weight, a
 * single transparent gesture surface expanded ±half the row gap (no
 * dead bands; horizontal gap touches go to the nearest key by center
 * x), and the per-key overlays — the press bubble and the long-press
 * callout. The key is chosen at touch-down and never re-targets while
 * the finger slides; keys commit on touch-up. While a callout shows,
 * horizontal drags select an option and release inserts it as a whole
 * string.
 */
@Composable
fun KeyRow(
    row: List<KeyCap>,
    rowIndex: Int,
    totalRows: Int,
    returnAction: ReturnKeyAction,
    shiftState: ShiftState,
    colors: KeyboardColors,
    onKey: (KeyCap) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(LezgiLayout.KEY_HEIGHT.dp),
    ) {
        val spacing = LezgiLayout.KEY_SPACING.dp
        val weights = row.map { keyWeight(it, returnAction) }
        val unit = (maxWidth - spacing * (row.size - 1)) / weights.sum()
        val rowWidth = maxWidth

        // Key frames — one source of truth for visuals, touch, overlays.
        val frames = buildList {
            var x = 0.dp
            for (w in weights) {
                val keyWidth = unit * w
                add(KeyFrame(x, keyWidth))
                x += keyWidth + spacing
            }
        }

        var pressedIndex by remember { mutableStateOf<Int?>(null) }
        var calloutOptions by remember { mutableStateOf<List<String>?>(null) }
        var calloutSelected by remember { mutableIntStateOf(0) }

        row.forEachIndexed { index, cap ->
            KeyButton(
                cap = cap,
                returnAction = returnAction,
                shiftState = shiftState,
                isPressed = pressedIndex == index,
                hideLabel = pressedIndex == index && calloutOptions == null
                    && cap is KeyCap.Character,
                colors = colors,
                modifier = Modifier
                    .offset(x = frames[index].x)
                    .width(frames[index].width)
                    .fillMaxHeight(),
            )
        }

        // Row-gap expansion of the tap zone (not past the outer rows).
        val halfGap = (LezgiLayout.ROW_SPACING / 2f).dp
        val topExpand = if (rowIndex == 0) 0.dp else halfGap
        val bottomExpand = if (rowIndex == totalRows - 1) 0.dp else halfGap

        Box(
            modifier = Modifier
                .offset(y = -topExpand)
                .fillMaxWidth()
                .height(LezgiLayout.KEY_HEIGHT.dp + topExpand + bottomExpand)
                .pointerInput(row, returnAction, shiftState) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val index = keyIndexAt(down.position.x.toDp(), frames)
                        val cap = index?.let { row[it] }
                        pressedIndex = index
                        val alternates = (cap as? KeyCap.Character)
                            ?.let { LezgiLayout.callouts[it.text.lowercase()] }

                        var upReceived = false
                        var cancelled = false
                        // The callout appears after the hold delay; until
                        // then the loop just tracks up/cancel.
                        while (!upReceived && !cancelled) {
                            val event = withTimeoutOrNull(
                                if (alternates != null && calloutOptions == null) {
                                    CALLOUT_DELAY_MS
                                } else Long.MAX_VALUE / 2,
                            ) { awaitPointerEvent(PointerEventPass.Main) }

                            if (event == null) {
                                // Hold elapsed → open the callout: base
                                // character first, duplicates removed
                                val base = (cap as KeyCap.Character).text.lowercase()
                                calloutOptions =
                                    listOf(base) + alternates!!.filter { it.lowercase() != base }
                                calloutSelected = 0
                                continue
                            }
                            val change = event.changes.first()
                            if (calloutOptions != null && index != null) {
                                val left = calloutLeft(
                                    frames[index],
                                    CALLOUT_OPTION_WIDTH * calloutOptions!!.size,
                                    rowWidth,
                                )
                                val local = change.position.x.toDp() - left
                                calloutSelected = (local / CALLOUT_OPTION_WIDTH).toInt()
                                    .coerceIn(0, calloutOptions!!.size - 1)
                            }
                            when {
                                change.changedToUp() -> upReceived = true
                                change.isConsumed -> cancelled = true
                            }
                        }

                        val options = calloutOptions
                        pressedIndex = null
                        calloutOptions = null
                        if (upReceived) {
                            if (options != null) {
                                onKey(KeyCap.Character(options[calloutSelected]))
                            } else if (cap != null) {
                                onKey(cap)
                            }
                        }
                    }
                },
        )

        pressedIndex?.let { index ->
            val cap = row[index]
            if (calloutOptions == null && cap is KeyCap.Character) {
                KeyPreviewBubble(
                    label = LezgiLayout.label(cap, shifted = shiftState != ShiftState.OFF),
                    frame = frames[index],
                    rowTopInKeyboard = rowTopInKeyboard(rowIndex),
                    colors = colors,
                )
            }
            calloutOptions?.let { options ->
                CalloutBubble(
                    options = options,
                    selectedIndex = calloutSelected,
                    frame = frames[index],
                    rowWidth = rowWidth,
                    shiftState = shiftState,
                    colors = colors,
                )
            }
        }
    }
}

/** Callout hold delay; user-adjustable from Stage 7 (0.2/0.3/0.45 s). */
private const val CALLOUT_DELAY_MS = 300L

/** Distance from the keyboard top to this row's top (bar + gap + rows above). */
private fun rowTopInKeyboard(rowIndex: Int): Dp =
    (LezgiLayout.SUGGESTION_BAR_HEIGHT + LezgiLayout.BAR_GAP +
        rowIndex * (LezgiLayout.KEY_HEIGHT + LezgiLayout.ROW_SPACING)).dp

/** The return key adapts its width to its label; everything else is static. */
private fun keyWeight(cap: KeyCap, returnAction: ReturnKeyAction): Float =
    if (cap == KeyCap.Return) LezgiLayout.returnKeyWeight(returnAction)
    else LezgiLayout.weight(cap)

/** A touch inside a key's frame wins; gap touches go to the nearest center x. */
private fun keyIndexAt(x: Dp, frames: List<KeyFrame>): Int? {
    frames.forEachIndexed { index, frame ->
        if (x >= frame.x && x <= frame.x + frame.width) return index
    }
    return frames.indices.minByOrNull { index ->
        abs((frames[index].x + frames[index].width / 2 - x).value)
    }
}
