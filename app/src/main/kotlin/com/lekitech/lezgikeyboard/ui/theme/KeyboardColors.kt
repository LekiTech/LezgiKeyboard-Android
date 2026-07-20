package com.lekitech.lezgikeyboard.ui.theme

import android.content.Context
import android.os.Build
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
    val spaceHint: Color,
    val menuAccent: Color,
    val menuAccentTint: Color,
    val menuCard: Color,
    val menuSecondary: Color,
) {
    companion object {
        private val light = KeyboardColors(
            keyboardBackground = Color(red = 0.820f, green = 0.827f, blue = 0.851f),
            letterKey = Color.White,
            letterKeyPressed = Color(red = 0.82f, green = 0.84f, blue = 0.87f),
            label = Color.Black,
            spaceHint = Color(0xFFAEAEB2),
            menuAccent = Color(0xFF5B57E0),
            menuAccentTint = Color(0xFFECEBFB),
            menuCard = Color.White,
            menuSecondary = Color(0xFF6E6E76),
        )

        /**
         * Dark surfaces are Android-native (D-028): dynamic system
         * neutrals on 31+, Material dark grays below — not the iOS
         * grays, so the keyboard blends with the system chrome the way
         * native keyboards do. Light mode and everything non-surface
         * keep the shared product palette.
         */
        private fun dark(context: Context): KeyboardColors {
            val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            fun surface(resId: Int, fallback: Long): Color =
                if (dynamic) Color(context.getColor(resId)) else Color(fallback)
            return KeyboardColors(
                keyboardBackground = surface(android.R.color.system_neutral1_900, 0xFF1B1B1F),
                letterKey = surface(android.R.color.system_neutral1_800, 0xFF303034),
                letterKeyPressed = surface(android.R.color.system_neutral1_600, 0xFF54565B),
                label = Color.White,
                spaceHint = Color(0xFF8E9099),
                menuAccent = Color(0xFF8B88FF),
                menuAccentTint = Color(0xFF262541),
                menuCard = Color(0xFF1F1F24),
                menuSecondary = Color(0xFF9E9EA7),
            )
        }

        fun resolve(isDark: Boolean, context: Context): KeyboardColors =
            if (isDark) dark(context) else light
    }
}
