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
class KeyboardModel {

    var page by mutableStateOf(KeyboardPage.LETTERS)
    var returnAction by mutableStateOf(ReturnKeyAction.NONE)

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
                editor.insertText(cap.text)
                // Punctuation on the numbers/symbols pages returns to the
                // letters page, like the native keyboard (sentence-ending
                // marks additionally arm Shift — Stage 3)
                if ((page == KeyboardPage.NUMBERS || page == KeyboardPage.SYMBOLS)
                    && cap.text in returnsToLetters
                ) {
                    page = KeyboardPage.LETTERS
                }
            }

            KeyCap.Space -> editor.insertText(" ")
            KeyCap.Return -> editor.performReturn()
            KeyCap.Backspace -> editor.deleteBackward()

            KeyCap.Numbers -> page = KeyboardPage.NUMBERS
            KeyCap.Symbols -> page = KeyboardPage.SYMBOLS
            KeyCap.Letters -> page = KeyboardPage.LETTERS

            // Shift arrives with Stage 3, the emoji page with Stage 8,
            // the settings panel with Stage 7.
            KeyCap.Shift, KeyCap.Emoji, KeyCap.Settings -> Unit
        }
    }
}
