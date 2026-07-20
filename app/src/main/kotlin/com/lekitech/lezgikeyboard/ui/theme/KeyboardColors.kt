package com.lekitech.lezgikeyboard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Keyboard palette, one value set per appearance. Colors match the iOS
 * implementation; the background stand-ins are always painted on Android
 * because an IME window has no host-provided backdrop (DECISIONS.md
 * D-017). The palette grows with the stages — only roles in actual use
 * are defined. Theme forcing (Экуь / Мичӏи) arrives with the settings
 * panel; until then resolution follows the system appearance.
 */
class KeyboardColors private constructor(
    val keyboardBackground: Color,
    val letterKey: Color,
    val letterKeyPressed: Color,
    val label: Color,
) {
    companion object {
        private val light = KeyboardColors(
            keyboardBackground = Color(red = 0.820f, green = 0.827f, blue = 0.851f),
            letterKey = Color.White,
            letterKeyPressed = Color(red = 0.82f, green = 0.84f, blue = 0.87f),
            label = Color.Black,
        )

        private val dark = KeyboardColors(
            keyboardBackground = Color(red = 0.169f, green = 0.169f, blue = 0.169f),
            letterKey = Color(red = 0.227f, green = 0.227f, blue = 0.235f),
            letterKeyPressed = Color(red = 0.36f, green = 0.36f, blue = 0.37f),
            label = Color.White,
        )

        fun resolve(isDark: Boolean): KeyboardColors = if (isDark) dark else light
    }
}
