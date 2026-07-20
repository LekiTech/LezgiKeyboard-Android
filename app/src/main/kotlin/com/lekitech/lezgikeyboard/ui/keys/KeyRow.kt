package com.lekitech.lezgikeyboard.ui.keys

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors
import kotlin.math.abs

/**
 * One key row: a visual layer of `KeyButton`s laid out by weight, and a
 * single transparent gesture surface covering the row — expanded by
 * half the row gap into the gaps above/below (except the outermost
 * rows) so the whole key area is tappable with no dead bands.
 * Horizontal gap touches go to the nearest key by center x. The key is
 * chosen at touch-down and never re-targets while the finger slides;
 * keys commit on touch-up.
 */
@Composable
fun KeyRow(
    row: List<KeyCap>,
    rowIndex: Int,
    totalRows: Int,
    returnAction: ReturnKeyAction,
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

        // Key frames (x, width) in row coordinates — one source of truth
        // for both the visual layout and the touch dispatch.
        val frames = buildList {
            var x = 0.dp
            for (w in weights) {
                val keyWidth = unit * w
                add(x to keyWidth)
                x += keyWidth + spacing
            }
        }

        var pressedIndex by remember { mutableStateOf<Int?>(null) }

        row.forEachIndexed { index, cap ->
            KeyButton(
                cap = cap,
                returnAction = returnAction,
                isPressed = pressedIndex == index,
                colors = colors,
                modifier = Modifier
                    .offset(x = frames[index].first)
                    .width(frames[index].second)
                    .fillMaxHeight(),
            )
        }

        // The tap zone extends by half the row gap into the gaps above
        // and below (not past the outermost rows).
        val halfGap = (LezgiLayout.ROW_SPACING / 2f).dp
        val topExpand = if (rowIndex == 0) 0.dp else halfGap
        val bottomExpand = if (rowIndex == totalRows - 1) 0.dp else halfGap

        Box(
            modifier = Modifier
                .offset(y = -topExpand)
                .fillMaxWidth()
                .height(LezgiLayout.KEY_HEIGHT.dp + topExpand + bottomExpand)
                .pointerInput(row, returnAction) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val xDp = down.position.x.toDp()
                        val index = keyIndexAt(xDp, frames)
                        pressedIndex = index
                        val up = waitForUpOrCancellation()
                        pressedIndex = null
                        if (up != null && index != null) onKey(row[index])
                    }
                },
        )
    }
}

/** The return key adapts its width to its label; everything else is static. */
private fun keyWeight(cap: KeyCap, returnAction: ReturnKeyAction): Float =
    if (cap == KeyCap.Return) LezgiLayout.returnKeyWeight(returnAction)
    else LezgiLayout.weight(cap)

/** A touch inside a key's frame wins; gap touches go to the nearest center x. */
private fun keyIndexAt(x: Dp, frames: List<Pair<Dp, Dp>>): Int? {
    frames.forEachIndexed { index, (start, width) ->
        if (x >= start && x <= start + width) return index
    }
    return frames.indices.minByOrNull { index ->
        val (start, width) = frames[index]
        abs((start + width / 2 - x).value)
    }
}
