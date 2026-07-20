package com.lekitech.lezgikeyboard.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.KeyboardPage
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction

/**
 * The keyboard's brain — the Android counterpart of the iOS
 * `KeyboardModel`. Stage 2 slice: page state, row assembly, and plain
 * key handling through the `TextEditor` proxy. Shift state,
 * auto-capitalization, gestures, and the suggestion pipeline arrive
 * with Stages 3–6. Rendering never makes decisions; it observes this
 * model and reports gestures.
 */
enum class ShiftState { OFF, ONCE, CAPS_LOCK }

/** The field's autocapitalization request, mapped by `EditorState`. */
enum class AutocapMode { NONE, SENTENCES, WORDS, CHARACTERS }

class KeyboardModel {

    var page by mutableStateOf(KeyboardPage.LETTERS)
    var returnAction by mutableStateOf(ReturnKeyAction.NONE)

    /**
     * The globe key shows only when the OS offers no switcher of its
     * own (D-027) — the exact iOS `needsInputModeSwitchKey` semantics.
     */
    var needsGlobe by mutableStateOf(false)

    /** Tap cycles off → once → capsLock → off; "once" consumes itself. */
    var shiftState by mutableStateOf(ShiftState.ONCE)
    var autocapMode = AutocapMode.SENTENCES

    val isShifted: Boolean get() = shiftState != ShiftState.OFF
    val isCapsLock: Boolean get() = shiftState == ShiftState.CAPS_LOCK

    /** Space long-press trackpad mode; key labels hide while active. */
    var isSpaceCursorMode by mutableStateOf(false)

    /** Keyboard name flashed on the spacebar right after appearance. */
    var showsKeyboardName by mutableStateOf(false)

    // MARK: - Suggestion bar state (fed by the engine from Stage 5;
    // Stage 4 drives it from scaffolding so the bar is exercisable)

    var suggestions by mutableStateOf(listOf<String>())

    /** Random dictionary words shown while the bar has no real content. */
    var fallbackSuggestions by mutableStateOf(listOf<String>())

    /**
     * The raw typed word leading the bar as a literal candidate because
     * no source recognizes it. Presentation-only marker: the view alone
     * wraps this word in «…»; the stored value stays undecorated.
     */
    var unrecognizedTyped by mutableStateOf<String?>(null)

    /** Displayed suggestions that came from the learned store — the
     * only ones long-press deletion applies to. */
    var learnedDisplayWords by mutableStateOf(setOf<String>())

    fun isLearnedSuggestion(display: String): Boolean = display in learnedDisplayWords

    private var lastSpaceTapNanos: Long? = null

    /**
     * Where «ъ» lives; set from the gear menu (and the panel in
     * Stage 7). The service persists changes — the model stays
     * storage-free.
     */
    var layoutVariant by mutableStateOf(LayoutVariant.CLASSIC)

    /** Punctuation that returns from the numbers/symbols pages to letters. */
    private val returnsToLetters = setOf(".", ",", "?", "!", "'")

    fun rows(): List<List<KeyCap>> {
        val main = when (page) {
            KeyboardPage.LETTERS -> LezgiLayout.letterRows(layoutVariant)
            KeyboardPage.NUMBERS -> LezgiLayout.numberRows
            KeyboardPage.SYMBOLS -> LezgiLayout.symbolRows
            KeyboardPage.EMOJI -> return emptyList()
        }
        return main + listOf(bottomRow())
    }

    private fun bottomRow(): List<KeyCap> = when (page) {
        KeyboardPage.LETTERS -> buildList {
            add(KeyCap.Numbers)
            add(KeyCap.Settings)
            add(KeyCap.Emoji)
            if (needsGlobe) add(KeyCap.Globe)
            add(KeyCap.Space)
            // «ъ» sits next to the space bar in the classic variant and
            // moves to the top letter row in the topRow variant
            if (layoutVariant == LayoutVariant.CLASSIC) add(KeyCap.Character("ъ"))
            add(KeyCap.Return)
        }
        KeyboardPage.NUMBERS, KeyboardPage.SYMBOLS -> buildList {
            add(KeyCap.Letters)
            if (needsGlobe) add(KeyCap.Globe)
            add(KeyCap.Space)
            add(KeyCap.Return)
        }
        KeyboardPage.EMOJI -> emptyList()
    }

    fun handleKey(cap: KeyCap, editor: TextEditor) {
        // First keystroke dismisses the keyboard name, like native
        showsKeyboardName = false

        when (cap) {
            is KeyCap.Character -> {
                val text =
                    if (isShifted) LezgiLayout.applyCase(cap.text, isCapsLock) else cap.text
                editor.insertText(text)
                if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
                // Punctuation on the numbers/symbols pages returns to the
                // letters page, like the native keyboard; sentence-ending
                // marks capitalize the next letter
                if ((page == KeyboardPage.NUMBERS || page == KeyboardPage.SYMBOLS)
                    && cap.text in returnsToLetters
                ) {
                    page = KeyboardPage.LETTERS
                    if (cap.text in sentenceEnders && shiftState != ShiftState.CAPS_LOCK) {
                        shiftState = ShiftState.ONCE
                    }
                }
            }

            // Quick double space after a word turns into ". " with a
            // capital next (user-disableable from Stage 7)
            KeyCap.Space -> {
                val now = System.nanoTime()
                val last = lastSpaceTapNanos
                val before = editor.textBeforeCursor(2)
                if (last != null && now - last < 350_000_000L &&
                    before != null && before.length >= 2 && before.last() == ' ' &&
                    before[before.length - 2].isLetterOrDigit()
                ) {
                    editor.deleteBackward()
                    editor.insertText(". ")
                    if (shiftState != ShiftState.CAPS_LOCK) shiftState = ShiftState.ONCE
                    lastSpaceTapNanos = null
                } else {
                    editor.insertText(" ")
                    lastSpaceTapNanos = now
                }
            }

            KeyCap.Return -> {
                editor.performReturn()
                // A new paragraph starts a new sentence, like the native
                // keyboard; the same side effect runs for action fields
                // (D-016) — the field decides what return did
                if (shiftState != ShiftState.CAPS_LOCK && autocapMode != AutocapMode.NONE) {
                    shiftState = ShiftState.ONCE
                }
            }

            KeyCap.Backspace -> editor.deleteBackward()

            KeyCap.Shift -> shiftState = when (shiftState) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> ShiftState.CAPS_LOCK
                ShiftState.CAPS_LOCK -> ShiftState.OFF
            }

            KeyCap.Numbers -> page = KeyboardPage.NUMBERS
            KeyCap.Symbols -> page = KeyboardPage.SYMBOLS
            KeyCap.Letters -> page = KeyboardPage.LETTERS

            // The emoji page arrives with Stage 8, the settings panel
            // with Stage 7; the globe never reaches the model (the
            // service switches input methods directly).
            KeyCap.Emoji, KeyCap.Settings, KeyCap.Globe -> Unit
        }
    }

    // MARK: - Auto-capitalization

    /**
     * Re-evaluates the shift state from the field's autocapitalization
     * request and the text before the cursor. Called on every host text
     * or cursor change and after backspace; Caps Lock always wins over
     * the automatic rules.
     */
    fun updateShiftFromContext(editor: TextEditor) {
        if (shiftState == ShiftState.CAPS_LOCK) return
        val shouldShift = when (autocapMode) {
            AutocapMode.NONE -> false
            AutocapMode.CHARACTERS -> true
            AutocapMode.WORDS -> {
                val before = editor.textBeforeCursor(1)
                before.isNullOrEmpty() || before.last().isWhitespace()
            }
            AutocapMode.SENTENCES -> isCursorAtSentenceStart(editor)
        }
        shiftState = if (shouldShift) ShiftState.ONCE else ShiftState.OFF
    }

    /**
     * Sentence start: empty or whitespace-only context, a fresh new
     * line, or a sentence delimiter as the last non-whitespace
     * character. A trailing space is not required, matching the native
     * period behavior.
     */
    private fun isCursorAtSentenceStart(editor: TextEditor): Boolean {
        val before = editor.textBeforeCursor(48)
        if (before.isNullOrEmpty()) return true
        if (before.last() == '\n') return true
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.last() in ".!?"
    }

    private val sentenceEnders = setOf(".", "?", "!")
}
