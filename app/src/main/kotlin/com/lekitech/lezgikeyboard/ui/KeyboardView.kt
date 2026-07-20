package com.lekitech.lezgikeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.ui.keys.KeyRow
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/**
 * Root keyboard view. The vertical budget is the fixed-height contract
 * (ANDROID_PORT_CONTEXT.md §3): 36 suggestion bar + 8 gap + 4×43 key
 * rows + 3×11 row gaps + 1 slack = 250. System navigation insets are
 * padded outside the 250 dp content (D-009). All state decisions live
 * in `KeyboardModel`; this view draws and reports key taps.
 */
@Composable
fun KeyboardView(
    model: KeyboardModel,
    onKey: (KeyCap) -> Unit,
    onCursorMove: (Int) -> Unit,
    onCursorLineMove: (Int) -> Unit,
    onLayoutVariant: (LayoutVariant) -> Unit,
) {
    val colors = KeyboardColors.resolve(isSystemInDarkTheme())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            .padding(bottom = systemBottomReserve()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(LezgiLayout.KEYBOARD_HEIGHT.dp),
        ) {
            // Suggestion bar area: reserved from day one so the total
            // height never changes when content arrives (Stage 4).
            Spacer(modifier = Modifier.height(LezgiLayout.SUGGESTION_BAR_HEIGHT.dp))
            Spacer(modifier = Modifier.height(LezgiLayout.BAR_GAP.dp))

            val rows = model.rows()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LezgiLayout.SIDE_PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(LezgiLayout.ROW_SPACING.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    KeyRow(
                        row = row,
                        rowIndex = index,
                        totalRows = rows.size,
                        model = model,
                        colors = colors,
                        onKey = onKey,
                        onCursorMove = onCursorMove,
                        onCursorLineMove = onCursorLineMove,
                        onLayoutVariant = onLayoutVariant,
                    )
                }
            }
        }
    }
}
