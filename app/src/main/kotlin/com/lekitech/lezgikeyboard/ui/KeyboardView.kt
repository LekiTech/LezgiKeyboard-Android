package com.lekitech.lezgikeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/**
 * Root keyboard view. The vertical budget is the fixed-height contract
 * (ANDROID_PORT_CONTEXT.md §3): 36 suggestion bar + 8 gap + 4×43 key
 * rows + 3×11 row gaps + 1 slack = 250. System navigation insets are
 * padded outside the 250dp content (D-009).
 *
 * Stage 1 renders the contract as placeholder blocks; the real key grid
 * replaces the placeholders in Stage 2.
 */
@Composable
fun KeyboardView() {
    val colors = KeyboardColors.resolve(isSystemInDarkTheme())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        ) {
            // Suggestion bar area: present from day one so the height
            // contract never changes when content arrives.
            Spacer(modifier = Modifier.height(36.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Key rows: 4 × 43 with 11 gaps, 6 side padding.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(43.dp)
                            .background(colors.letterKey, RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}
