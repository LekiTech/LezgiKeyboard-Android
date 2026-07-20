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
     * The return key: performs the field's editor action, or inserts a
     * newline when the field has none (DECISIONS.md D-016).
     */
    fun performReturn()
}
