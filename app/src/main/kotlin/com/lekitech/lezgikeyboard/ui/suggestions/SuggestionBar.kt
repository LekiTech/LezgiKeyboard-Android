package com.lekitech.lezgikeyboard.ui.suggestions

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The suggestion bar (ANDROID_PORT_CONTEXT.md §6): content-sized cells
 * spread evenly with flexible gaps — no separators, the press capsule
 * is the only affordance; empty slots are dropped entirely. The whole
 * bar is one gesture surface: a touch goes to the cell with the
 * nearest center x, so the bar stays tappable edge to edge. A 0.5 s
 * hold on a *learned* suggestion replaces the bar with an inline
 * delete-confirmation row — never a dialog.
 */
@Composable
fun SuggestionBar(
    model: KeyboardModel,
    colors: KeyboardColors,
    onSuggestion: (String) -> Unit,
    onSuggestionDelete: (String) -> Unit,
) {
    var pendingDeleteWord by remember { mutableStateOf<String?>(null) }

    // Any change to the suggestions dismisses the confirmation row
    LaunchedEffect(model.suggestions) { pendingDeleteWord = null }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LezgiLayout.SUGGESTION_BAR_HEIGHT.dp),
    ) {
        val pending = pendingDeleteWord
        if (pending != null) {
            DeleteConfirmRow(
                word = pending,
                colors = colors,
                onKeep = { pendingDeleteWord = null },
                onDelete = {
                    pendingDeleteWord = null
                    onSuggestionDelete(pending)
                },
            )
        } else {
            SuggestionCells(
                model = model,
                colors = colors,
                onSuggestion = onSuggestion,
                onLongPressLearned = { pendingDeleteWord = it },
            )
        }
    }
}

@Composable
private fun SuggestionCells(
    model: KeyboardModel,
    colors: KeyboardColors,
    onSuggestion: (String) -> Unit,
    onLongPressLearned: (String) -> Unit,
) {
    val words = model.suggestions.ifEmpty { model.fallbackSuggestions }
    val visible = words.filter { it.isNotEmpty() }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    // Measured cell centers in bar coordinates — the cells are
    // content-sized, so dispatch works off these, not fixed thirds.
    val centers = remember(visible) { mutableStateListOf<Float>().apply { repeat(visible.size) { add(0f) } } }
    val widths = remember(visible) { mutableStateListOf<Float>().apply { repeat(visible.size) { add(0f) } } }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            visible.forEachIndexed { index, word ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            centers[index] = it.positionInParent().x + it.size.width / 2f
                            widths[index] = it.size.width.toFloat()
                        }
                        .drawBehind {
                            // The pressed capsule hugs the cell content,
                            // so it always fully contains the text
                            if (pressedIndex == index) {
                                drawRoundRect(
                                    color = colors.letterKey,
                                    topLeft = Offset(0f, 4.dp.toPx()),
                                    size = Size(size.width, size.height - 8.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                )
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MorphingWordText(
                        word = word,
                        quoted = word == model.unrecognizedTyped,
                        colors = colors,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Gesture layer: the whole bar, dispatching to the nearest cell
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visible) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (visible.isEmpty()) return@awaitEachGesture
                        var index = nearestCell(down.position.x, centers)
                        pressedIndex = index
                        var deadline = deleteDeadline(visible, index, model)
                        var fired = false
                        var upReceived = false
                        var cancelled = false
                        while (!upReceived && !cancelled && !fired) {
                            val wait = deadline?.let { it - SystemClock.uptimeMillis() }
                            if (wait != null && wait <= 0) {
                                fired = true
                                pressedIndex = null
                                index?.let { onLongPressLearned(visible[it]) }
                                continue
                            }
                            val event = if (wait == null) {
                                awaitPointerEvent(PointerEventPass.Main)
                            } else {
                                withTimeoutOrNull(wait) { awaitPointerEvent(PointerEventPass.Main) }
                            } ?: continue
                            val change = event.changes.first()
                            val next = nearestCell(change.position.x, centers)
                            if (next != index) {
                                // Moving into another cell re-targets and
                                // re-arms the learned-delete hold
                                index = next
                                pressedIndex = next
                                deadline = deleteDeadline(visible, next, model)
                            }
                            when {
                                change.changedToUp() -> upReceived = true
                                change.isConsumed -> cancelled = true
                            }
                        }
                        pressedIndex = null
                        if (upReceived && !fired) {
                            index?.let { onSuggestion(visible[it]) }
                        }
                    }
                },
        )
    }
}

private fun nearestCell(x: Float, centers: List<Float>): Int? =
    centers.indices.minByOrNull { abs(centers[it] - x) }

private fun deleteDeadline(visible: List<String>, index: Int?, model: KeyboardModel): Long? =
    index?.takeIf { model.isLearnedSuggestion(visible[it]) }
        ?.let { SystemClock.uptimeMillis() + DELETE_HOLD_MS }

/**
 * Inline confirmation shown in place of the suggestions after a
 * long-press on a learned word. Stays entirely inside the keyboard.
 */
@Composable
private fun DeleteConfirmRow(
    word: String,
    colors: KeyboardColors,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val questionSize = with(density) { Dp(15f).toSp() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "“$word” чӏурдани?",
            maxLines = 1,
            style = TextStyle(color = colors.label, fontSize = questionSize),
        )
        Spacer(modifier = Modifier.weight(1f))
        ConfirmPill(
            label = "Ваъ",
            emphasized = false,
            colors = colors,
            onTap = onKeep,
        )
        Spacer(modifier = Modifier.width(12.dp))
        ConfirmPill(
            label = "Чӏурун",
            emphasized = true,
            colors = colors,
            onTap = onDelete,
        )
    }
}

@Composable
private fun ConfirmPill(
    label: String,
    emphasized: Boolean,
    colors: KeyboardColors,
    onTap: () -> Unit,
) {
    val fontSize = with(LocalDensity.current) { Dp(14f).toSp() }
    val background = if (emphasized) DESTRUCTIVE_RED.copy(alpha = 0.12f) else colors.letterKey
    BasicText(
        text = label,
        style = TextStyle(
            color = if (emphasized) DESTRUCTIVE_RED else colors.label,
            fontSize = fontSize,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        ),
        modifier = Modifier
            .drawBehind {
                drawRoundRect(background, cornerRadius = CornerRadius(size.height / 2f))
            }
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var up = false
                    var cancelled = false
                    while (!up && !cancelled) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.first()
                        when {
                            change.changedToUp() -> up = true
                            change.isConsumed -> cancelled = true
                        }
                    }
                    if (up) onTap()
                }
            },
    )
}

private const val DELETE_HOLD_MS = 500L
private val DESTRUCTIVE_RED = Color(0xFFFF3B30)
