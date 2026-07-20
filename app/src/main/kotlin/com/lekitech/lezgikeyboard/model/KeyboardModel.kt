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

    /** Tap cycles off → once → capsLock → off; "once" consumes itself. */
    var shiftState by mutableStateOf(ShiftState.ONCE)
    var autocapMode = AutocapMode.SENTENCES

    val isShifted: Boolean get() = shiftState != ShiftState.OFF
    val isCapsLock: Boolean get() = shiftState == ShiftState.CAPS_LOCK

    /** Space long-press trackpad mode; key labels hide while active. */
    var isSpaceCursorMode by mutableStateOf(false)

    private var lastSpaceTapNanos: Long? = null

    /** User-selectable from Stage 3 (gear menu) / Stage 7 (panel). */
    val layoutVariant = LayoutVariant.CLASSIC

    /** Punctuation that returns from the numbers/symbols pages to letters. */
    private val returnsToLetters = setOf(".", ",", "?", "!", "'")

    // Input-method switching is the system's job: Android always offers
    // its own switcher when several keyboards are enabled (DECISIONS.md
    // D-022), so no page carries a globe key.
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
            add(KeyCap.Space)
            // «ъ» sits next to the space bar in the classic variant and
            // moves to the top letter row in the topRow variant
            if (layoutVariant == LayoutVariant.CLASSIC) add(KeyCap.Character("ъ"))
            add(KeyCap.Return)
        }
        KeyboardPage.NUMBERS, KeyboardPage.SYMBOLS ->
            listOf(KeyCap.Letters, KeyCap.Space, KeyCap.Return)
        KeyboardPage.EMOJI -> emptyList()
    }

    fun handleKey(cap: KeyCap, editor: TextEditor) {
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
            // with Stage 7.
            KeyCap.Emoji, KeyCap.Settings -> Unit
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

    // MARK: - Cursor line jumps (space trackpad mode)

    /**
     * Moves the caret to the previous/next newline-separated line,
     * keeping the column when the neighboring line is visible in the
     * host context. Hosts truncate the context at paragraph boundaries,
     * so without a visible newline one is still crossed blindly: up
     * lands at the end of the previous line, down at the start of the
     * next (hosts clamp at the document edges). Visual wraps of long
     * lines are invisible to input methods.
     */
    fun moveCursorLine(up: Boolean, editor: TextEditor) {
        if (up) {
            val before = editor.textBeforeCursor(1024)?.toString() ?: ""
            val lines = before.split("\n")
            val column = lines.last().length
            if (lines.size >= 2) {
                val prevLen = lines[lines.size - 2].length
                editor.moveCursor(-(column + 1 + maxOf(prevLen - column, 0)))
            } else {
                editor.moveCursor(-(column + 1))
            }
        } else {
            val after = editor.textAfterCursor(1024)?.toString() ?: ""
            val lines = after.split("\n")
            val restOfCurrent = lines[0].length
            if (lines.size >= 2) {
                val nextLen = lines[1].length
                val column = (editor.textBeforeCursor(1024)?.toString() ?: "")
                    .split("\n").last().length
                editor.moveCursor(restOfCurrent + 1 + minOf(column, nextLen))
            } else {
                editor.moveCursor(restOfCurrent + 1)
            }
        }
    }
}
