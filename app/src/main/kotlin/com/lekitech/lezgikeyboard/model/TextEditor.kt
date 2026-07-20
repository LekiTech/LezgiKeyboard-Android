package com.lekitech.lezgikeyboard.model

/**
 * The narrow text-editing surface the model is allowed to touch — the
 * Android counterpart of the iOS document proxy. Implemented by the
 * service over the current `InputConnection`; the model never sees
 * framework types, which keeps every text decision in one testable
 * place.
 */
interface TextEditor {
    fun insertText(text: String)
    fun deleteBackward()

    /**
     * Text before the cursor, up to `maxLength` characters — the analog
     * of the iOS document context, with the same caveat: it can lag
     * behind fast typing and is only a resync source, never the truth
     * for the active composition.
     */
    fun textBeforeCursor(maxLength: Int): CharSequence?

    /** Text after the cursor, up to `maxLength` characters. */
    fun textAfterCursor(maxLength: Int): CharSequence?

    /** Moves the caret by `offset` characters (negative = left). */
    fun moveCursor(offset: Int)

    /**
     * The return key: performs the field's editor action, or inserts a
     * newline when the field has none (DECISIONS.md D-016).
     */
    fun performReturn()
}
