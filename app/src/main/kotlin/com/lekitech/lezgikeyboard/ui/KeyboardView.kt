package com.lekitech.lezgikeyboard.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.KeyboardPage
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.settings.KeyboardSettings
import com.lekitech.lezgikeyboard.ui.emoji.EmojiPage
import com.lekitech.lezgikeyboard.ui.keys.KeyRow
import com.lekitech.lezgikeyboard.ui.settings.SettingsPanelView
import com.lekitech.lezgikeyboard.ui.suggestions.SuggestionBar
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/**
 * Root keyboard view. The vertical budget is the fixed-height contract
 * (ANDROID_PORT_CONTEXT.md §3): 36 suggestion bar + 8 gap + 4×43 key
 * rows + 3×11 row gaps + 1 slack = 250, plus the Android-specific
 * overlay headroom above the bar (D-024). System navigation insets are
 * padded outside the content (D-009). All state decisions live in
 * `KeyboardModel`; this view draws and reports key taps.
 */
@Composable
fun KeyboardView(
    model: KeyboardModel,
    onKey: (KeyCap) -> Unit,
    onCursorMove: (Int) -> Unit,
    onCursorLineMove: (Int) -> Unit,
    onLayoutVariant: (LayoutVariant) -> Unit,
    onSuggestion: (String) -> Unit,
    onSuggestionDelete: (String) -> Unit,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    onLearnedReset: () -> Unit,
    onEmojiInsert: (String) -> Unit,
    onStickerInsert: (String) -> Unit,
) {
    // The theme setting decides the effective appearance; SYSTEM
    // follows the host. A forced theme re-resolves the whole palette
    // in place, recoloring the keyboard (panel included) live — and
    // Android always paints its own background (D-017), so the forced
    // case needs nothing extra.
    val isDark = when (model.settings.theme) {
        KeyboardSettings.Theme.SYSTEM -> isSystemInDarkTheme()
        KeyboardSettings.Theme.LIGHT -> false
        KeyboardSettings.Theme.DARK -> true
    }
    val colors = KeyboardColors.resolve(isDark, LocalContext.current)
    Column(modifier = Modifier.fillMaxWidth()) {
        // Transparent overlay strip: part of the window but not of the
        // visible keyboard — the service excludes it from the content
        // and touchable insets (D-025), so the host app lays out
        // against the 250 dp contract and taps here pass through.
        // Top-row previews and callouts draw upward into it.
        Spacer(modifier = Modifier.height(LezgiLayout.OVERLAY_HEADROOM.dp))
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
            if (model.page == KeyboardPage.EMOJI) {
                // The emoji page replaces the entire keyboard area —
                // no suggestion bar (spec §12).
                EmojiPage(
                    model = model,
                    colors = colors,
                    onKey = onKey,
                    onEmojiInsert = onEmojiInsert,
                    onStickerInsert = onStickerInsert,
                )
            } else {
                SuggestionBar(
                    model = model,
                    colors = colors,
                    onSuggestion = onSuggestion,
                    onSuggestionDelete = onSuggestionDelete,
                )
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

        // In-keyboard settings panel (gear key): covers the whole
        // fixed keyboard and slides up over it, easeOut 0.28 like
        // iOS. It stays composed through the slide-out and leaves
        // composition only when fully hidden, so every reopen starts
        // a fresh panel (home page, saved words re-fetched) — the
        // exact iOS lifecycle.
        val slide by animateFloatAsState(
            targetValue = if (model.showsSettings) 0f else 1f,
            animationSpec = tween(durationMillis = 280, easing = panelEasing),
            label = "settingsPanel",
        )
        if (model.showsSettings || slide < 1f) {
            Box(modifier = Modifier.matchParentSize().clipToBounds()) {
                SettingsPanelView(
                    model = model,
                    colors = colors,
                    onUpdateSettings = onUpdateSettings,
                    onLayoutVariant = onLayoutVariant,
                    onDeleteWord = onSuggestionDelete,
                    onResetAll = onLearnedReset,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { translationY = slide * size.height },
                )
            }
        }
        }
    }
}

/** The SwiftUI `.easeOut` curve the iOS panel slides with. */
private val panelEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
