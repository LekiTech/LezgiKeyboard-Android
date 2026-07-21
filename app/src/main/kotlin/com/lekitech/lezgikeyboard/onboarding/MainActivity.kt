package com.lekitech.lezgikeyboard.onboarding

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekitech.lezgikeyboard.R
import com.lekitech.lezgikeyboard.stickers.StickerPack

/**
 * The app page — the Android counterpart of the iOS `ContentView`
 * (D-033 supersedes the earlier minimal screen): header, install
 * steps, the sticker pack with WhatsApp/Telegram export, feature
 * cards, footer. The keyboard itself never opens this page; it exists
 * for installation and the sticker export only.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Status-bar icons follow the page background, not the theme
        // default (the window theme is appearance-neutral).
        val dark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !dark
        setContent {
            AppPage(
                onOpenSettings = {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
                onSelectKeyboard = {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                },
                onWhatsApp = {
                    if (!StickerSharing.addToWhatsApp(this)) notInstalled("WhatsApp")
                },
                onTelegram = {
                    if (!StickerSharing.addToTelegram(this)) notInstalled("Telegram")
                },
            )
        }
    }

    private fun notInstalled(messenger: String) {
        Toast.makeText(
            this, getString(R.string.stickers_not_installed, messenger), Toast.LENGTH_SHORT,
        ).show()
    }
}

/** iOS system tints used by the reference page. */
private val TintBlue = Color(0xFF007AFF)
private val TintIndigo = Color(0xFF5856D6)
private val TintOrange = Color(0xFFFF9500)
private val TintGreen = Color(0xFF34C759)
private val TintPurple = Color(0xFFAF52DE)
private val WhatsAppGreen = Color(0xFF25D366)
private val TelegramBlue = Color(0xFF2AABEE)

private class PageColors(dark: Boolean) {
    val background = if (dark) Color(0xFF151517) else Color(0xFFF4F4F8)
    val card = if (dark) Color(0xFF1F1F24) else Color.White
    val label = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color(0xFF9E9EA7) else Color(0xFF6E6E76)
    val tertiary = if (dark) Color(0x4DEBEBF5) else Color(0x4C3C3C43)
    val accent = if (dark) Color(0xFF8B88FF) else Color(0xFF5B57E0)
    val accentTint = if (dark) Color(0xFF262541) else Color(0xFFECEBFB)
}

@Composable
private fun AppPage(
    onOpenSettings: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onWhatsApp: () -> Unit,
    onTelegram: () -> Unit,
) {
    val colors = PageColors(isSystemInDarkTheme())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Header(colors)
        InstallCard(colors, onOpenSettings, onSelectKeyboard)
        StickersCard(colors, onWhatsApp, onTelegram)
        Features(colors)
        BasicText(
            text = stringResource(R.string.onboarding_footer),
            modifier = Modifier.padding(bottom = 8.dp),
            style = TextStyle(color = colors.tertiary, fontSize = 13.sp),
        )
    }
}

// MARK: - Header

@Composable
private fun Header(colors: PageColors) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(60.dp).drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(TintBlue, TintIndigo),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_panel_keyboard),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        BasicText(
            text = stringResource(R.string.onboarding_title),
            style = TextStyle(
                color = colors.label, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            ),
        )
        BasicText(
            text = stringResource(R.string.onboarding_subtitle),
            style = TextStyle(color = colors.secondary, fontSize = 15.sp),
        )
    }
}

// MARK: - Cards

@Composable
private fun Card(colors: PageColors, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun InstallCard(
    colors: PageColors,
    onOpenSettings: () -> Unit,
    onSelectKeyboard: () -> Unit,
) {
    Card(colors) {
        BasicText(
            text = stringResource(R.string.onboarding_how_to),
            style = TextStyle(
                color = colors.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(16.dp))
        StepRow("1", stringResource(R.string.onboarding_step_open_settings), colors)
        Spacer(Modifier.height(12.dp))
        StepRow("2", stringResource(R.string.onboarding_step_enable), colors)
        Spacer(Modifier.height(12.dp))
        StepRow("3", stringResource(R.string.onboarding_step_switch), colors)
        Spacer(Modifier.height(16.dp))
        FilledButton(
            label = stringResource(R.string.onboarding_open_settings),
            background = colors.accent,
            textColor = Color.White,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(10.dp))
        FilledButton(
            label = stringResource(R.string.onboarding_select_keyboard),
            background = colors.accentTint,
            textColor = colors.accent,
            onClick = onSelectKeyboard,
        )
    }
}

@Composable
private fun StepRow(number: String, text: String, colors: PageColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = number,
                style = TextStyle(
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = text,
            style = TextStyle(color = colors.label, fontSize = 15.sp),
        )
    }
}

@Composable
private fun FilledButton(
    label: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

// MARK: - Stickers

@Composable
private fun StickersCard(
    colors: PageColors,
    onWhatsApp: () -> Unit,
    onTelegram: () -> Unit,
) {
    Card(colors) {
        BasicText(
            text = stringResource(R.string.stickers_title),
            style = TextStyle(
                color = colors.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = stringResource(R.string.stickers_subtitle),
            style = TextStyle(color = colors.secondary, fontSize = 14.sp),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // The same preview five the iOS page shows
            listOf("salam", "loveyou", "lezginka", "khinkal", "lezgiflag").forEach { name ->
                StickerThumb(name)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                FilledButton(
                    label = stringResource(R.string.stickers_whatsapp),
                    background = WhatsAppGreen,
                    textColor = Color.White,
                    onClick = onWhatsApp,
                )
            }
            Box(Modifier.weight(1f)) {
                FilledButton(
                    label = stringResource(R.string.stickers_telegram),
                    background = TelegramBlue,
                    textColor = Color.White,
                    onClick = onTelegram,
                )
            }
        }
    }
}

@Composable
private fun StickerThumb(name: String) {
    val context = LocalContext.current
    val image = remember(name) {
        runCatching {
            context.assets.open(StickerPack.assetPath(name)).use {
                BitmapFactory.decodeStream(
                    it, null,
                    BitmapFactory.Options().apply { inSampleSize = 4 },
                )
            }
        }.getOrNull()?.asImageBitmap()
    }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
        )
    }
}

// MARK: - Feature cards

@Composable
private fun Features(colors: PageColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard(
            tint = TintBlue,
            title = stringResource(R.string.feature_alphabet_title),
            subtitle = stringResource(R.string.feature_alphabet_subtitle),
            colors = colors,
        ) {
            BasicText(
                text = "ӏ",
                style = TextStyle(
                    color = TintBlue, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        FeatureCard(
            tint = TintOrange,
            title = stringResource(R.string.feature_suggestions_title),
            subtitle = stringResource(R.string.feature_suggestions_subtitle),
            colors = colors,
        ) {
            FeatureIcon(R.drawable.ic_panel_dictionary, TintOrange)
        }
        FeatureCard(
            tint = TintGreen,
            title = stringResource(R.string.feature_native_title),
            subtitle = stringResource(R.string.feature_native_subtitle),
            colors = colors,
        ) {
            FeatureIcon(R.drawable.ic_panel_keyboard, TintGreen)
        }
        FeatureCard(
            tint = TintPurple,
            title = stringResource(R.string.feature_privacy_title),
            subtitle = stringResource(R.string.feature_privacy_subtitle),
            colors = colors,
        ) {
            FeatureIcon(R.drawable.ic_app_lock, TintPurple)
        }
    }
}

@Composable
private fun FeatureIcon(resId: Int, tint: Color) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun FeatureCard(
    tint: Color,
    title: String,
    subtitle: String,
    colors: PageColors,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = colors.secondary, fontSize = 14.sp),
            )
        }
    }
}
