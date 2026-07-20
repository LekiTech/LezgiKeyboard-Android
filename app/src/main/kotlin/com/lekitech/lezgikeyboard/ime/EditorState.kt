package com.lekitech.lezgikeyboard.ime

import android.view.inputmethod.EditorInfo
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction

/**
 * Pure mapping of host `EditorInfo` to keyboard concepts. Stage 2
 * parses the return-key action; the autocapitalization mode and
 * password flag join in the stages that consume them (3 and 5).
 */
object EditorState {

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
