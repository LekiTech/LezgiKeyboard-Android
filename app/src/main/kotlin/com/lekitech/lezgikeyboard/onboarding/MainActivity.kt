package com.lekitech.lezgikeyboard.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import com.lekitech.lezgikeyboard.R

/**
 * Minimal onboarding: explain how to enable the keyboard and switch to
 * it. Deliberately reduced from the iOS container app — everything else
 * (stickers, feature cards) is out of scope (DECISIONS.md D-003).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnboardingScreen(
                onOpenSettings = {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
                onSelectKeyboard = {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                },
            )
        }
    }
}

@Composable
private fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onSelectKeyboard: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF151517) else Color(0xFFF4F4F8)
    val textColor = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color(0xFF9E9EA7) else Color(0xFF6E6E76)
    val accent = if (isDark) Color(0xFF8B88FF) else Color(0xFF5B57E0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = stringResource(R.string.onboarding_title),
            style = TextStyle(color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = stringResource(R.string.onboarding_subtitle),
            style = TextStyle(color = secondary, fontSize = 16.sp),
        )
        Spacer(Modifier.height(36.dp))
        BasicText(
            text = stringResource(R.string.onboarding_how_to),
            style = TextStyle(color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(16.dp))
        StepButton(
            caption = stringResource(R.string.onboarding_step_enable),
            label = stringResource(R.string.onboarding_open_settings),
            accent = accent,
            secondary = secondary,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(20.dp))
        StepButton(
            caption = stringResource(R.string.onboarding_step_switch),
            label = stringResource(R.string.onboarding_select_keyboard),
            accent = accent,
            secondary = secondary,
            onClick = onSelectKeyboard,
        )
    }
}

@Composable
private fun StepButton(
    caption: String,
    label: String,
    accent: Color,
    secondary: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = caption,
            style = TextStyle(color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier
                .background(accent, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}
