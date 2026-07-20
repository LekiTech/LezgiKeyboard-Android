package com.lekitech.lezgikeyboard.ui

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Bottom space the keyboard must leave to the system (DECISIONS.md
 * D-021). With gesture navigation the framework draws its own back and
 * IME-switcher buttons **inside the IME window** (`imeDrawsImeNavBar`)
 * in a 48 dp band, while reporting only the 24 dp `navigationBars`
 * inset — content in the difference gets shadowed by the buttons' touch
 * targets. The band height is the platform's `navigation_bar_frame_height`;
 * in other navigation modes the plain inset applies.
 */
@Composable
fun systemBottomReserve(): Dp {
    val navigationBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LocalConfiguration.current  // re-resolve on navigation-mode changes
    val context = LocalContext.current
    val density = LocalDensity.current
    val frameHeight = with(density) { imeNavigationBarFrameHeightPx(context).toDp() }
    return max(navigationBars, frameHeight)
}

/** Gesture-navigation mode as the platform defines it. */
private const val NAV_BAR_MODE_GESTURAL = 2

private fun imeNavigationBarFrameHeightPx(context: Context): Int {
    val resources = context.resources
    val modeId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
    if (modeId == 0 || resources.getInteger(modeId) != NAV_BAR_MODE_GESTURAL) return 0
    val frameId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
    return if (frameId == 0) 0 else resources.getDimensionPixelSize(frameId)
}
