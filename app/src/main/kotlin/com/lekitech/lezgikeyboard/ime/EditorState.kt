package com.lekitech.lezgikeyboard.ime

import android.content.ClipDescription
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.AutocapMode

/**
 * Pure mapping of host `EditorInfo` to keyboard concepts. The password
 * flag joins in Stage 5 with the suggestion pipeline.
 */
object EditorState {

    /**
     * Whether the field accepts sticker images through the Commit
     * Content API (D-031): it must declare a content mime type
     * matching the pack's WebP — or PNG, which the service converts
     * to on the fly. Fields that declare nothing (all plain text
     * fields, password fields) simply get no sticker section.
     */
    fun acceptsStickers(info: EditorInfo?): Boolean {
        val mimeTypes = info?.contentMimeTypes ?: return false
        return mimeTypes.any {
            ClipDescription.compareMimeTypes("image/webp", it) ||
                ClipDescription.compareMimeTypes("image/png", it)
        }
    }

    /**
     * Password and no-personalized-learning fields (D-015): the bar
     * shows nothing and nothing is ever learned there.
     */
    fun isPrivateField(info: EditorInfo?): Boolean {
        if (info == null) return false
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
        val inputType = info.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** The field's autocapitalization request (text classes only). */
    fun autocapMode(info: EditorInfo?): AutocapMode {
        val inputType = info?.inputType ?: return AutocapMode.NONE
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) {
            return AutocapMode.NONE
        }
        return when {
            inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> AutocapMode.CHARACTERS
            inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> AutocapMode.WORDS
            inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> AutocapMode.SENTENCES
            else -> AutocapMode.NONE
        }
    }

    /**
     * The field's return-key action. `IME_FLAG_NO_ENTER_ACTION` (set by
     * multiline fields) means enter must insert a newline regardless of
     * the declared action (DECISIONS.md D-016).
     */
    fun returnAction(info: EditorInfo?): ReturnKeyAction {
        if (info == null) return ReturnKeyAction.NONE
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            return ReturnKeyAction.NONE
        }
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> ReturnKeyAction.GO
            EditorInfo.IME_ACTION_SEARCH -> ReturnKeyAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> ReturnKeyAction.SEND
            EditorInfo.IME_ACTION_NEXT -> ReturnKeyAction.NEXT
            EditorInfo.IME_ACTION_DONE -> ReturnKeyAction.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> ReturnKeyAction.PREVIOUS
            else -> ReturnKeyAction.NONE
        }
    }

    /** The `EditorInfo` action id to perform for a non-NONE action. */
    fun imeActionId(action: ReturnKeyAction): Int = when (action) {
        ReturnKeyAction.GO -> EditorInfo.IME_ACTION_GO
        ReturnKeyAction.SEARCH -> EditorInfo.IME_ACTION_SEARCH
        ReturnKeyAction.SEND -> EditorInfo.IME_ACTION_SEND
        ReturnKeyAction.NEXT -> EditorInfo.IME_ACTION_NEXT
        ReturnKeyAction.DONE -> EditorInfo.IME_ACTION_DONE
        ReturnKeyAction.PREVIOUS -> EditorInfo.IME_ACTION_PREVIOUS
        ReturnKeyAction.NONE -> EditorInfo.IME_ACTION_UNSPECIFIED
    }
}
