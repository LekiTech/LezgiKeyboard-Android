package com.lekitech.lezgikeyboard.layout

/**
 * Single source of truth for key definitions, labels, callouts, and
 * sizing — the Android counterpart of the iOS `LezgiKeyboardLayout`.
 * To add or reorder keys, edit `baseLetterRows` only.
 *
 * The Lezgi palochka is the Cyrillic «ӏ» U+04CF everywhere — typed
 * text, dictionary, and learned store alike. Never the Latin I or the
 * digit 1.
 */

/** What a key does when tapped. */
sealed interface KeyCap {
    data class Character(val text: String) : KeyCap
    data object Shift : KeyCap
    data object Backspace : KeyCap
    data object Numbers : KeyCap   // switch to the «123» page
    data object Symbols : KeyCap   // switch to the «#+=» page
    data object Letters : KeyCap   // switch back to the alphabet
    data object Emoji : KeyCap     // emoji page (Stage 8)
    data object Settings : KeyCap  // settings panel (Stage 7), quick layout menu (Stage 3)
    data object Space : KeyCap
    data object Return : KeyCap
}

enum class KeyboardPage { LETTERS, NUMBERS, SYMBOLS, EMOJI }

/** Where the hard sign «ъ» lives — user-selectable from Stage 3 on. */
enum class LayoutVariant(val prefValue: String) {
    CLASSIC("classic"),  // «ъ» in the bottom row next to the space bar (default)
    TOP_ROW("topRow"),   // «ъ» as the 12th key of the top letter row
}

/**
 * Return-key actions reachable through `EditorInfo.imeOptions`. NONE
 * means "insert a newline" and renders the return-arrow icon; SEARCH
 * renders the magnifying-glass icon; the rest carry Lezgi labels.
 * PREVIOUS has no iOS counterpart and keeps the icon.
 */
enum class ReturnKeyAction { NONE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

object LezgiLayout {

    // MARK: - Geometry (ANDROID_PORT_CONTEXT.md §2–§3; all values dp)

    const val KEY_HEIGHT = 43
    const val ROW_SPACING = 11
    const val KEY_SPACING = 6
    const val SIDE_PADDING = 6
    const val SUGGESTION_BAR_HEIGHT = 36
    const val BAR_GAP = 8
    const val KEYBOARD_HEIGHT = 250

    // MARK: - Letter rows
    //
    // ↓↓↓  ADD OR REORDER KEYS HERE ONLY  ↓↓↓
    private val baseLetterRows: List<List<KeyCap>> = listOf(
        "йцукенгшӏзх".map { KeyCap.Character(it.toString()) },   // 11 keys
        "фывапролджэ".map { KeyCap.Character(it.toString()) },   // 11 keys
        listOf(KeyCap.Shift)
            + "ячсмитьбю".map { KeyCap.Character(it.toString()) }
            + listOf(KeyCap.Backspace),
    )
    // ↑↑↑  -----------------------------------  ↑↑↑

    /**
     * Letter rows for the chosen variant: CLASSIC keeps «ъ» in the
     * bottom row (added by `KeyboardModel.bottomRow`), TOP_ROW appends
     * it to the top letter row as a 12th key.
     */
    fun letterRows(variant: LayoutVariant): List<List<KeyCap>> {
        if (variant == LayoutVariant.TOP_ROW) {
            return listOf(baseLetterRows[0] + KeyCap.Character("ъ")) + baseLetterRows.drop(1)
        }
        return baseLetterRows
    }

    // MARK: - Number page («123») — standard set

    val numberRows: List<List<KeyCap>> = listOf(
        "1234567890".map { KeyCap.Character(it.toString()) },
        "-/:;()₽&@\"".map { KeyCap.Character(it.toString()) },
        listOf(KeyCap.Symbols)
            + ".,?!'".map { KeyCap.Character(it.toString()) }
            + listOf(KeyCap.Backspace),
    )

    // MARK: - Symbol page («#+=») — standard set

    val symbolRows: List<List<KeyCap>> = listOf(
        "[]{}#%^*+=".map { KeyCap.Character(it.toString()) },
        "_\\|~<>€£¥•".map { KeyCap.Character(it.toString()) },
        listOf(KeyCap.Numbers)
            + ".,?!'".map { KeyCap.Character(it.toString()) }
            + listOf(KeyCap.Backspace),
    )

    // MARK: - Long-press callouts (Stage 3)
    // Alternates are inserted as a whole string ("цӏ" = ц + palochka U+04CF).

    val callouts: Map<String, List<String>> = mapOf(
        "ц" to listOf("цӏ"),
        "у" to listOf("уь"),
        "к" to listOf("кӏ", "кь", "къ"),
        "е" to listOf("ё"),
        "г" to listOf("гь", "гъ"),
        "ш" to listOf("щ"),
        "х" to listOf("хь", "хъ"),
        "п" to listOf("пӏ"),
        "ч" to listOf("чӏ"),
        "т" to listOf("тӏ"),
    )

    // MARK: - Case helpers

    /** Capitalizes only the first character — correct for digraphs: "къ" → "Къ". */
    fun capitalizedFirst(s: String): String {
        if (s.isEmpty()) return s
        return s.take(1).uppercase() + s.drop(1)
    }

    /**
     * Applies shift state to a string. Use instead of `uppercase()`:
     * capsLock uppercases all ("къ" → "КЪ"), plain shift only the
     * first letter ("къ" → "Къ").
     */
    fun applyCase(s: String, capsLock: Boolean): String =
        if (capsLock) s.uppercase() else capitalizedFirst(s)

    // MARK: - Key labels
    //
    // Space and Return render their own content (name flash / action
    // label / icons) — they have no plain label here.

    fun label(cap: KeyCap, shifted: Boolean): String = when (cap) {
        is KeyCap.Character -> if (shifted) cap.text.uppercase() else cap.text
        KeyCap.Shift -> "⇧"
        KeyCap.Backspace -> "⌫"
        KeyCap.Numbers -> "123"
        KeyCap.Symbols -> "#+="
        KeyCap.Letters -> "АБВ"
        KeyCap.Emoji -> "😀"
        KeyCap.Settings, KeyCap.Space, KeyCap.Return -> ""
    }

    // MARK: - Key width weights (relative; rows normalize by their sum)

    fun weight(cap: KeyCap): Float = when (cap) {
        is KeyCap.Character -> 1.0f
        KeyCap.Shift, KeyCap.Backspace -> 1.0f
        // The «123» / gear / emoji cluster must be exactly equal-sized
        KeyCap.Settings, KeyCap.Emoji,
        KeyCap.Numbers, KeyCap.Symbols, KeyCap.Letters -> 1.2f
        KeyCap.Return -> 1.8f
        KeyCap.Space -> 4.5f
    }

    // MARK: - Return key

    /**
     * Action label for the return key, in Lezgi. SEARCH is empty on
     * purpose: it renders as the universal magnifying-glass icon — the
     * correct translation («Жагъурун») is too long for a key. An empty
     * label means "show an icon".
     */
    fun returnLabel(action: ReturnKeyAction): String = when (action) {
        ReturnKeyAction.GO -> "Фин"
        ReturnKeyAction.SEND -> "Ракъурун"
        ReturnKeyAction.DONE -> "Хьанва"
        ReturnKeyAction.NEXT -> "Къведайди"
        ReturnKeyAction.NONE, ReturnKeyAction.SEARCH, ReturnKeyAction.PREVIOUS -> ""
    }

    /**
     * Return-key width in row units, adapted to its label: icons and
     * short labels keep the native 1.8, longer Lezgi labels get more
     * room so the typography stays readable instead of shrinking. The
     * space bar absorbs the difference through weight normalization.
     */
    fun returnKeyWeight(action: ReturnKeyAction): Float =
        if (returnLabel(action).length <= 6) 1.8f else 2.3f

    // MARK: - Label sizes (dp; density-fixed — see DECISIONS.md D-020)

    /**
     * Size for the label rendered on a key. Lowercase letters are
     * optically much smaller than caps at the same size (x-height vs
     * cap height), so they get a 1.2× bump to match the reference
     * keyboard; digits and punctuation have no case and keep the base.
     */
    fun fontSize(cap: KeyCap, label: String): Float = when (cap) {
        is KeyCap.Character -> if (label != label.uppercase()) 22f * 1.2f else 22f
        KeyCap.Numbers, KeyCap.Symbols, KeyCap.Letters -> 18f
        else -> 20f
    }
}
