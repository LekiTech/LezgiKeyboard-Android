package com.lekitech.lezgikeyboard.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.R
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.settings.KeyboardSettings
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors

/**
 * Slide-up settings panel inside the keyboard, opened by the gear key
 * in the bottom row — the Android counterpart of the iOS
 * `SettingsPanelView`. Pages: home, layout variant, input behavior,
 * theme, learned-words dictionary, about. Everything stays inside the
 * keyboard window — no dialogs, no activities; the delete-all
 * confirmation is an in-panel sheet. Lezgi titles are primary and
 * verbatim; only the small explanatory subtitles localize (ru/en).
 *
 * The saved-words list is fetched when the panel enters composition
 * (every open — the host removes it once fully hidden) and after
 * deletes, never on page navigation; the home counter derives from
 * the same list, so the two cannot disagree.
 */
@Composable
fun SettingsPanelView(
    model: KeyboardModel,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    onLayoutVariant: (LayoutVariant) -> Unit,
    onDeleteWord: (String) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stack by remember { mutableStateOf(listOf(Page.HOME)) }
    var showsDeleteAllSheet by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(model.savedWords()) }
    val page = stack.last()

    Box(
        modifier = modifier
            .background(colors.panelBackground)
            // The panel owns every touch: without a handler the plain
            // background is not hit-testable and touches would fall
            // through to the key surfaces underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Decorative drag capsule — no drag-to-close.
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(36.dp, 5.dp).drawBehind {
                        drawRoundRect(
                            colors.panelSeparator,
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    },
                )
            }

            PanelHeader(
                title = pageTitle(page),
                isRoot = stack.size == 1,
                colors = colors,
                onBack = {
                    if (stack.size > 1) {
                        stack = stack.dropLast(1)
                    } else {
                        model.showsSettings = false
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    Page.HOME -> HomePage(
                        model = model,
                        colors = colors,
                        wordCount = words.size,
                        onNavigate = { stack = stack + it },
                    )
                    Page.LAYOUT -> LayoutPage(model, colors, onLayoutVariant)
                    Page.INPUT -> InputPage(model.settings, colors, onUpdateSettings)
                    Page.THEME -> ThemePage(model.settings, colors, onUpdateSettings)
                    Page.DICTIONARY -> DictionaryPage(
                        words = words,
                        colors = colors,
                        onDelete = { word ->
                            onDeleteWord(word)
                            words = model.savedWords()
                        },
                        onDeleteAll = { showsDeleteAllSheet = true },
                    )
                    Page.ABOUT -> AboutPage(colors)
                }
            }
        }

        if (showsDeleteAllSheet) {
            DeleteAllSheet(
                colors = colors,
                onConfirm = {
                    showsDeleteAllSheet = false
                    onResetAll()
                    words = model.savedWords()
                },
                onDismiss = { showsDeleteAllSheet = false },
            )
        }
    }
}

private enum class Page { HOME, LAYOUT, INPUT, THEME, DICTIONARY, ABOUT }

private fun pageTitle(page: Page): String = when (page) {
    Page.HOME -> ""
    Page.LAYOUT -> "Раскладка"
    Page.INPUT -> "Кхьин"
    Page.THEME -> "Тема"
    Page.DICTIONARY -> "Гафарган"
    Page.ABOUT -> "Клавиатурадикай"
}

private fun themeTitle(theme: KeyboardSettings.Theme): String = when (theme) {
    KeyboardSettings.Theme.SYSTEM -> "Системадин"
    KeyboardSettings.Theme.LIGHT -> "Экуь"
    KeyboardSettings.Theme.DARK -> "Мичӏи"
}

// MARK: - Header

@Composable
private fun PanelHeader(
    title: String,
    isRoot: Boolean,
    colors: KeyboardColors,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.widthIn(min = 110.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .tappable(onBack)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PanelIcon(R.drawable.ic_panel_chevron_left, 17.dp, colors.menuAccent)
                BasicText(
                    text = if (isRoot) "Клавиатура" else "Кьулухъ",
                    style = TextStyle(
                        color = colors.menuAccent,
                        fontSize = dpFontSize(16f),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = colors.label,
                fontSize = dpFontSize(16f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = Modifier.widthIn(min = 110.dp))
    }
}

// MARK: - Pages

@Composable
private fun HomePage(
    model: KeyboardModel,
    colors: KeyboardColors,
    wordCount: Int,
    onNavigate: (Page) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            text = "ЛЕЗГИ КЛАВИАТУРА",
            style = TextStyle(
                color = colors.menuSecondary,
                fontSize = dpFontSize(11f),
                fontWeight = FontWeight.Bold,
                letterSpacing = dpFontSize(1.4f),
            ),
        )
        BasicText(
            text = "Параметрар",
            style = TextStyle(
                color = colors.label,
                fontSize = dpFontSize(24f),
                fontWeight = FontWeight.Bold,
            ),
        )
    }

    PanelGroup(colors) {
        NavRow(
            iconRes = R.drawable.ic_panel_keyboard,
            title = "Раскладка",
            value = if (model.layoutVariant == LayoutVariant.CLASSIC) {
                "Ъ — арадин къвалаг"
            } else {
                "Ъ — вини жергеда"
            },
            colors = colors,
            onTap = { onNavigate(Page.LAYOUT) },
        )
        PanelDivider(colors)
        NavRow(
            iconRes = R.drawable.ic_panel_input,
            title = "Кхьин",
            value = null,
            colors = colors,
            onTap = { onNavigate(Page.INPUT) },
        )
        PanelDivider(colors)
        NavRow(
            iconRes = R.drawable.ic_panel_theme,
            title = "Тема",
            value = themeTitle(model.settings.theme),
            colors = colors,
            onTap = { onNavigate(Page.THEME) },
        )
        PanelDivider(colors)
        NavRow(
            iconRes = R.drawable.ic_panel_dictionary,
            title = "Гафарган",
            value = "$wordCount",
            colors = colors,
            onTap = { onNavigate(Page.DICTIONARY) },
        )
    }

    PanelGroup(colors) {
        NavRow(
            iconRes = R.drawable.ic_panel_about,
            title = "Клавиатурадикай",
            value = appVersion(),
            colors = colors,
            onTap = { onNavigate(Page.ABOUT) },
        )
    }
}

@Composable
private fun LayoutPage(
    model: KeyboardModel,
    colors: KeyboardColors,
    onLayoutVariant: (LayoutVariant) -> Unit,
) {
    SectionLabel("Кӏеви лишандин («ъ») чка", colors)
    PanelGroup(colors) {
        RadioRow(
            title = "Арадин къвалаг",
            subtitle = "Вини жергеда 11 клавиша",
            selected = model.layoutVariant == LayoutVariant.CLASSIC,
            colors = colors,
            onTap = { onLayoutVariant(LayoutVariant.CLASSIC) },
        )
        PanelDivider(colors)
        RadioRow(
            title = "Вини жергеда",
            subtitle = "«ъ» — «х»-дин къвалаг",
            selected = model.layoutVariant == LayoutVariant.TOP_ROW,
            colors = colors,
            onTap = { onLayoutVariant(LayoutVariant.TOP_ROW) },
        )
    }
}

/**
 * Input behavior: suggestions, key behaviors, learning speed, and the
 * long-press callout delay. Only features the keyboard already has —
 * every control maps 1:1 to a `KeyboardSettings` field.
 */
@Composable
private fun InputPage(
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
) {
    SectionLabel("Теклифар — " + stringResource(R.string.settings_suggestions), colors)
    PanelGroup(colors) {
        ToggleRow(
            title = "Гафарин теклифар",
            subtitle = stringResource(R.string.settings_word_suggestions),
            checked = settings.wordSuggestions,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(wordSuggestions = it)) },
        )
        PanelDivider(colors)
        ToggleRow(
            title = "Къведай гафунин теклиф",
            subtitle = stringResource(R.string.settings_next_word),
            checked = settings.nextWordSuggestions,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(nextWordSuggestions = it)) },
        )
        PanelDivider(colors)
        ToggleRow(
            title = "Теклифдилай гуьгъуьниз ара",
            subtitle = stringResource(R.string.settings_auto_space),
            checked = settings.autoSpaceAfterSuggestion,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(autoSpaceAfterSuggestion = it)) },
        )
    }

    SectionLabel("Клавишар", colors)
    PanelGroup(colors) {
        ToggleRow(
            title = "Кьве ара — нукьта",
            subtitle = stringResource(R.string.settings_double_space),
            checked = settings.doubleSpacePeriod,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(doubleSpacePeriod = it)) },
        )
        PanelDivider(colors)
        ToggleRow(
            title = "Арадалди курсор",
            subtitle = stringResource(R.string.settings_space_cursor),
            checked = settings.spaceCursor,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(spaceCursor = it)) },
        )
        PanelDivider(colors)
        ToggleRow(
            title = "Арадал «лезг»",
            subtitle = stringResource(R.string.settings_space_label),
            checked = settings.spaceLabel,
            colors = colors,
            onToggle = { onUpdateSettings(settings.copy(spaceLabel = it)) },
        )
    }

    SectionLabel("Чирун — " + stringResource(R.string.settings_learning), colors)
    PanelGroup(colors) {
        LearnSpeedRow(
            speed = KeyboardSettings.LearnSpeed.FAST,
            title = "Фад",
            subtitle = "1 сефер · " + stringResource(R.string.settings_after_1),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        LearnSpeedRow(
            speed = KeyboardSettings.LearnSpeed.NORMAL,
            title = "Адетдин",
            subtitle = "3 сефер · " + stringResource(R.string.settings_after_3),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        LearnSpeedRow(
            speed = KeyboardSettings.LearnSpeed.CONSERVATIVE,
            title = "Яваш",
            subtitle = "5 сефер · " + stringResource(R.string.settings_after_5),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
    }

    SectionLabel(
        "Алава гьарфар (ӏ, кь, къ…) — " + stringResource(R.string.settings_long_press),
        colors,
    )
    PanelGroup(colors) {
        CalloutDelayRow(
            delay = KeyboardSettings.CalloutDelay.SHORT,
            title = "Куьруь ял",
            subtitle = "0,2 с · " + stringResource(R.string.settings_delay_short),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        CalloutDelayRow(
            delay = KeyboardSettings.CalloutDelay.NORMAL,
            title = "Адетдин ял",
            subtitle = "0,3 с · " + stringResource(R.string.settings_delay_normal),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        CalloutDelayRow(
            delay = KeyboardSettings.CalloutDelay.LONG,
            title = "Яргъи ял",
            subtitle = "0,45 с · " + stringResource(R.string.settings_delay_long),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
    }
}

@Composable
private fun LearnSpeedRow(
    speed: KeyboardSettings.LearnSpeed,
    title: String,
    subtitle: String,
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
) {
    RadioRow(
        title = title,
        subtitle = subtitle,
        selected = settings.learnSpeed == speed,
        colors = colors,
        onTap = { onUpdateSettings(settings.copy(learnSpeed = speed)) },
    )
}

@Composable
private fun CalloutDelayRow(
    delay: KeyboardSettings.CalloutDelay,
    title: String,
    subtitle: String,
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
) {
    RadioRow(
        title = title,
        subtitle = subtitle,
        selected = settings.calloutDelay == delay,
        colors = colors,
        onTap = { onUpdateSettings(settings.copy(calloutDelay = delay)) },
    )
}

/**
 * A tap applies instantly: the keyboard root derives its palette from
 * `settings.theme`, recoloring everything (panel included) live, with
 * nothing to close or reopen.
 */
@Composable
private fun ThemePage(
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
) {
    SectionLabel("Тема — " + stringResource(R.string.settings_theme), colors)
    PanelGroup(colors) {
        ThemeRow(
            theme = KeyboardSettings.Theme.SYSTEM,
            subtitle = stringResource(R.string.settings_theme_system),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        ThemeRow(
            theme = KeyboardSettings.Theme.LIGHT,
            subtitle = stringResource(R.string.settings_theme_light),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
        PanelDivider(colors)
        ThemeRow(
            theme = KeyboardSettings.Theme.DARK,
            subtitle = stringResource(R.string.settings_theme_dark),
            settings = settings, colors = colors, onUpdateSettings = onUpdateSettings,
        )
    }
}

@Composable
private fun ThemeRow(
    theme: KeyboardSettings.Theme,
    subtitle: String,
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onUpdateSettings: (KeyboardSettings) -> Unit,
) {
    RadioRow(
        title = themeTitle(theme),
        subtitle = subtitle,
        selected = settings.theme == theme,
        colors = colors,
        onTap = { onUpdateSettings(settings.copy(theme = theme)) },
    )
}

@Composable
private fun DictionaryPage(
    words: List<String>,
    colors: KeyboardColors,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            text = "${words.size}",
            style = TextStyle(
                color = colors.label,
                fontSize = dpFontSize(36f),
                fontWeight = FontWeight.Bold,
            ),
        )
        BasicText(
            text = "чирнавай гафар",
            style = TextStyle(color = colors.menuSecondary, fontSize = dpFontSize(13f)),
        )
    }

    if (words.isEmpty()) {
        PanelGroup(colors) {
            BasicText(
                text = "Гьеле гафар авач",
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                style = TextStyle(
                    color = colors.menuSecondary,
                    fontSize = dpFontSize(14f),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    } else {
        PanelGroup(colors) {
            words.forEachIndexed { index, word ->
                if (index > 0) PanelDivider(colors)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .padding(start = 13.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = word,
                        style = TextStyle(color = colors.label, fontSize = dpFontSize(15f)),
                    )
                    Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))
                    Box(
                        modifier = Modifier.size(34.dp).tappable { onDelete(word) },
                        contentAlignment = Alignment.Center,
                    ) {
                        PanelIcon(R.drawable.ic_panel_xmark, 13.dp, colors.labelTertiary)
                    }
                }
            }
        }

        PanelGroup(colors) {
            BasicText(
                text = "Вири чирнавай гафар чӏурун",
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable(onDeleteAll)
                    .padding(vertical = 13.dp),
                style = TextStyle(
                    color = DESTRUCTIVE_RED,
                    fontSize = dpFontSize(15f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun AboutPage(colors: KeyboardColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF7D7AFF), Color(0xFF5B57E0)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Л",
                style = TextStyle(
                    color = Color.White,
                    fontSize = dpFontSize(28f),
                    fontWeight = FontWeight.W800,
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = "Лезги клавиатура",
                style = TextStyle(
                    color = colors.label,
                    fontSize = dpFontSize(17f),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            BasicText(
                text = "Версия ${appVersion()} · LekiTech",
                style = TextStyle(color = colors.menuSecondary, fontSize = dpFontSize(12f)),
            )
        }
    }
}

// MARK: - Delete-all sheet (in-panel, never a dialog)

@Composable
private fun DeleteAllSheet(
    colors: KeyboardColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .tappable(onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 10.dp)
                .drawBehind {
                    drawRoundRect(colors.menuCard, cornerRadius = CornerRadius(16.dp.toPx()))
                },
        ) {
            BasicText(
                text = "Вири чирнавай гафар чӏурдани?",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                style = TextStyle(
                    color = colors.label,
                    fontSize = dpFontSize(15f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
            SheetDivider(colors)
            BasicText(
                text = "Чӏурун",
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable(onConfirm)
                    .padding(vertical = 12.dp),
                style = TextStyle(
                    color = DESTRUCTIVE_RED,
                    fontSize = dpFontSize(16f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
            SheetDivider(colors)
            BasicText(
                text = "Ваъ",
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable(onDismiss)
                    .padding(vertical = 12.dp),
                style = TextStyle(
                    color = colors.menuAccent,
                    fontSize = dpFontSize(16f),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

// MARK: - Building blocks

/** Rounded row-card container (radius 14). */
@Composable
private fun PanelGroup(colors: KeyboardColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(colors.menuCard, cornerRadius = CornerRadius(14.dp.toPx()))
        },
        content = content,
    )
}

@Composable
private fun PanelDivider(colors: KeyboardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp)
            .height(1.dp)
            .background(colors.panelSeparator),
    )
}

@Composable
private fun SheetDivider(colors: KeyboardColors) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.panelSeparator))
}

@Composable
private fun SectionLabel(text: String, colors: KeyboardColors) {
    BasicText(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = TextStyle(
            color = colors.menuSecondary,
            fontSize = dpFontSize(12f),
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun NavRow(
    iconRes: Int,
    title: String,
    value: String?,
    colors: KeyboardColors,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .tappable(onTap)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).drawBehind {
                drawRoundRect(colors.menuAccentTint, cornerRadius = CornerRadius(8.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            PanelIcon(iconRes, 15.dp, colors.menuAccent)
        }
        BasicText(
            text = title,
            style = TextStyle(color = colors.label, fontSize = dpFontSize(15f)),
        )
        Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))
        if (value != null) {
            BasicText(
                text = value,
                maxLines = 1,
                style = TextStyle(color = colors.menuSecondary, fontSize = dpFontSize(14f)),
            )
        }
        PanelIcon(R.drawable.ic_panel_chevron_right, 13.dp, colors.labelTertiary)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    colors: KeyboardColors,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(
                text = title,
                style = TextStyle(color = colors.label, fontSize = dpFontSize(15f)),
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = TextStyle(color = colors.menuSecondary, fontSize = dpFontSize(12f)),
                )
            }
        }
        ToggleSwitch(checked, colors, onToggle)
    }
}

@Composable
private fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    colors: KeyboardColors,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .tappable(onTap)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = if (selected) colors.menuAccent else colors.label,
                    fontSize = dpFontSize(15f),
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
            )
            BasicText(
                text = subtitle,
                style = TextStyle(color = colors.menuSecondary, fontSize = dpFontSize(12f)),
            )
        }
        if (selected) {
            PanelIcon(R.drawable.ic_panel_checkmark, 14.dp, colors.menuAccent)
        }
    }
}

/** iOS-switch-styled toggle: 51×31 capsule track, 27 white thumb. */
@Composable
private fun ToggleSwitch(
    checked: Boolean,
    colors: KeyboardColors,
    onToggle: (Boolean) -> Unit,
) {
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "toggle",
    )
    val track = lerp(colors.panelSeparator, colors.menuAccent, fraction)
    Box(
        modifier = Modifier
            .size(51.dp, 31.dp)
            .drawBehind {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
                val thumbRadius = 13.5.dp.toPx()
                val inset = 2.dp.toPx()
                val x = inset + thumbRadius +
                    (size.width - 2 * (inset + thumbRadius)) * fraction
                drawCircle(
                    color = Color.Black.copy(alpha = 0.08f),
                    radius = thumbRadius,
                    center = Offset(x, size.height / 2f + 1.dp.toPx()),
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(x, size.height / 2f),
                )
            }
            .tappable { onToggle(!checked) },
    )
}

@Composable
private fun PanelIcon(resId: Int, size: Dp, tint: Color) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * Panel text sizes are density-fixed (dp, not scaled sp) like every
 * keyboard label: the panel lives inside the fixed-height window and
 * must never outgrow it with the system font-size setting (D-020).
 */
@Composable
private fun dpFontSize(sizeDp: Float): TextUnit =
    with(LocalDensity.current) { Dp(sizeDp).toSp() }

/** The versionName of this build, shown on home and about. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}

/**
 * Tap recognition with the handler always current: rows recompose with
 * fresh settings copies, and a stale captured closure would re-apply
 * an old snapshot.
 */
private fun Modifier.tappable(onTap: () -> Unit): Modifier = composed {
    val current = rememberUpdatedState(onTap)
    pointerInput(Unit) { detectTapGestures(onTap = { current.value.invoke() }) }
}

private val DESTRUCTIVE_RED = Color(0xFFFF3B30)
