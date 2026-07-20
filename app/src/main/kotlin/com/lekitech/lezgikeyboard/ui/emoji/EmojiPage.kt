package com.lekitech.lezgikeyboard.ui.emoji

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekitech.lezgikeyboard.R
import com.lekitech.lezgikeyboard.layout.EmojiData
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.stickers.StickerPack
import com.lekitech.lezgikeyboard.ui.theme.KeyboardColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fullscreen emoji page replacing the whole keyboard area (no
 * suggestion bar): a horizontally scrolling flat lazy row of small
 * uniform 5-emoji columns grouped in sections — recents first (id −1)
 * when present, then the eight generated categories, then the sticker
 * section (id −2, Android-only, D-031) when the field accepts images.
 * Nested lazy grids defeat laziness and blew the iOS extension's
 * memory limit; the same flat structure is kept here. The bottom
 * category bar has an «АБВ» letters-return zone, one icon per section,
 * and a repeating backspace zone; a fixed title strip above the grid
 * names the section under the leading edge.
 */
@Composable
fun EmojiPage(
    model: KeyboardModel,
    colors: KeyboardColors,
    onKey: (KeyCap) -> Unit,
    onEmojiInsert: (String) -> Unit,
    onStickerInsert: (String) -> Unit,
) {
    val columns = remember(model.recentEmojis, model.stickersAvailable) {
        makeColumns(model.recentEmojis, model.stickersAvailable)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentSection by remember(columns) {
        derivedStateOf {
            columns.getOrNull(listState.firstVisibleItemIndex)?.category ?: 0
        }
    }
    val titleSize = with(LocalDensity.current) { Dp(13f).toSp() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed title strip showing the current section, like native
        BasicText(
            text = sectionTitle(currentSection),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            style = TextStyle(
                color = colors.menuSecondary,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            items(columns.size, key = { it }) { index ->
                val column = columns[index]
                // Visual gap between sections
                val endPadding = if (column.endsCategory) 10.dp else 0.dp
                if (column.category == STICKERS_ID) {
                    StickerColumn(column.cells, colors, endPadding, onStickerInsert)
                } else {
                    EmojiColumn(column.cells, colors, endPadding, onEmojiInsert)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        EmojiCategoryBar(
            columns = columns,
            currentSection = currentSection,
            colors = colors,
            onKey = onKey,
            onJump = { category ->
                val target = columns.indexOfFirst { it.category == category }
                if (target >= 0) scope.launch { listState.scrollToItem(target) }
            },
        )
    }
}

internal const val RECENTS_ID = -1

/** The sticker section — Android-only, no iOS counterpart (D-031). */
internal const val STICKERS_ID = -2

/**
 * One column of the flat lazy grid: up to five emoji, or up to two
 * stickers when `category == STICKERS_ID` (`cells` holds asset names).
 */
private class PageColumn(
    val category: Int,
    val endsCategory: Boolean,
    val cells: List<String>,
)

/**
 * The generated catalog minus emoji this device has no glyph for —
 * Android emoji fonts trail the Unicode releases far more than iOS,
 * so a tofu-free grid needs the filter. Computed once per process.
 */
private val filteredCategories by lazy {
    val paint = Paint()
    EmojiData.categories
        .map { category ->
            com.lekitech.lezgikeyboard.layout.EmojiCategory(
                title = category.title,
                iconRes = category.iconRes,
                emojis = category.emojis.filter(paint::hasGlyph),
            )
        }
        .filter { it.emojis.isNotEmpty() }
}

private fun sectionTitle(category: Int): String = when (category) {
    RECENTS_ID -> "Эхиримжибур"
    STICKERS_ID -> "Стикерар"
    else -> filteredCategories[category].title
}

private fun makeColumns(recents: List<String>, stickers: Boolean): List<PageColumn> {
    val groups = buildList {
        if (recents.isNotEmpty()) add(RECENTS_ID to recents)
        filteredCategories.forEachIndexed { index, category ->
            add(index to category.emojis)
        }
        if (stickers) add(STICKERS_ID to StickerPack.names)
    }
    val columns = mutableListOf<PageColumn>()
    for ((category, cells) in groups) {
        val perColumn = if (category == STICKERS_ID) 2 else 5
        val chunks = cells.chunked(perColumn)
        chunks.forEachIndexed { index, chunk ->
            columns.add(
                PageColumn(
                    category = category,
                    endsCategory = index == chunks.size - 1,
                    cells = chunk,
                ),
            )
        }
    }
    return columns
}

// MARK: - Grid cells

@Composable
private fun EmojiColumn(
    emojis: List<String>,
    colors: KeyboardColors,
    endPadding: Dp,
    onEmojiInsert: (String) -> Unit,
) {
    val glyphSize = with(LocalDensity.current) { Dp(26f).toSp() }
    Column(
        modifier = Modifier.padding(end = endPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        emojis.forEach { emoji ->
            var pressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(38.dp, 33.dp)
                    .drawBehind {
                        // Press flash like a key, radius 6
                        if (pressed) {
                            drawRoundRect(
                                colors.letterKeyPressed,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                            )
                        }
                    }
                    .pointerInput(emoji) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onEmojiInsert(emoji) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = emoji, style = TextStyle(fontSize = glyphSize))
            }
        }
    }
}

@Composable
private fun StickerColumn(
    names: List<String>,
    colors: KeyboardColors,
    endPadding: Dp,
    onStickerInsert: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(end = endPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        names.forEach { name ->
            var pressed by remember { mutableStateOf(false) }
            // Decoded at quarter resolution (512 → 128 px is plenty at
            // 78 dp) and only while the lazy row keeps the column alive.
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
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .drawBehind {
                        if (pressed) {
                            drawRoundRect(
                                colors.letterKeyPressed,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                            )
                        }
                    }
                    .pointerInput(name) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onStickerInsert(name) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.size(78.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

// MARK: - Category bar

/**
 * Bottom strip like native: «АБВ» + one icon per section + delete, no
 * key backgrounds. A visual layer plus a full-width gesture layer
 * dispatching by x — the zone is chosen at touch-down and holds for
 * the whole gesture; the delete zone repeats while held like the
 * letters-page backspace.
 */
@Composable
private fun EmojiCategoryBar(
    columns: List<PageColumn>,
    currentSection: Int,
    colors: KeyboardColors,
    onKey: (KeyCap) -> Unit,
    onJump: (Int) -> Unit,
) {
    val sections = remember(columns) {
        columns.map { it.category }.distinct()
    }
    val lettersSize = with(LocalDensity.current) { Dp(15f).toSp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp, 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "АБВ",
                    style = TextStyle(
                        color = colors.label,
                        fontSize = lettersSize,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            sections.forEach { section ->
                Box(
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val selected = currentSection == section
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .drawBehind {
                                if (selected) {
                                    drawCircle(colors.letterKeyPressed)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(sectionIcon(section)),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            colorFilter = ColorFilter.tint(
                                if (selected) colors.label else colors.menuSecondary,
                            ),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(44.dp, 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_key_backspace),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    colorFilter = ColorFilter.tint(colors.label),
                )
            }
        }

        // Gesture layer: covers the bar edge to edge, dispatching by x
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sections) {
                    val sideZone = 48.dp.toPx() - 4.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        val zone = barZone(down.position.x, width, sideZone, sections)

                        var repeatInterval = BACKSPACE_FIRST_REPEAT_MS
                        var deadline: Long? =
                            if (zone is BarZone.Backspace) now() + HOLD_DELAY_MS else null
                        var upReceived = false
                        var cancelled = false
                        while (!upReceived && !cancelled) {
                            val wait = deadline?.let { it - now() }
                            if (wait != null && wait <= 0) {
                                onKey(KeyCap.Backspace)
                                deadline = now() + repeatInterval
                                repeatInterval = maxOf(
                                    BACKSPACE_FLOOR_MS,
                                    (repeatInterval * BACKSPACE_DECAY).toLong(),
                                )
                                continue
                            }
                            val event = if (wait == null) {
                                awaitPointerEvent(PointerEventPass.Main)
                            } else {
                                withTimeoutOrNull(wait) {
                                    awaitPointerEvent(PointerEventPass.Main)
                                }
                            } ?: continue
                            val change = event.changes.first()
                            when {
                                change.changedToUp() -> upReceived = true
                                change.isConsumed -> cancelled = true
                            }
                        }
                        if (upReceived) {
                            when (zone) {
                                BarZone.Letters -> onKey(KeyCap.Letters)
                                BarZone.Backspace -> onKey(KeyCap.Backspace)
                                is BarZone.Category -> onJump(zone.id)
                                null -> Unit
                            }
                        }
                    }
                },
        )
    }
}

private sealed interface BarZone {
    data object Letters : BarZone
    data object Backspace : BarZone
    data class Category(val id: Int) : BarZone
}

private fun barZone(x: Float, width: Float, side: Float, sections: List<Int>): BarZone? {
    if (x < side) return BarZone.Letters
    if (x > width - side) return BarZone.Backspace
    if (sections.isEmpty()) return null
    val iconWidth = (width - side * 2) / sections.size
    if (iconWidth <= 0) return null
    val index = ((x - side) / iconWidth).toInt().coerceIn(0, sections.size - 1)
    return BarZone.Category(sections[index])
}

private fun sectionIcon(section: Int): Int = when (section) {
    RECENTS_ID -> R.drawable.ic_emoji_recents
    STICKERS_ID -> R.drawable.ic_emoji_stickers
    else -> filteredCategories[section].iconRes
}

private fun now(): Long = android.os.SystemClock.uptimeMillis()

/** Same repeat curve as the letters-page backspace. */
private const val HOLD_DELAY_MS = 400L
private const val BACKSPACE_FIRST_REPEAT_MS = 100L
private const val BACKSPACE_FLOOR_MS = 30L
private const val BACKSPACE_DECAY = 0.85
