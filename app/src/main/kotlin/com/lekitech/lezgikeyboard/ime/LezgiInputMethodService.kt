package com.lekitech.lezgikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.model.TextEditor
import com.lekitech.lezgikeyboard.ui.KeyboardView

/**
 * IME entry point: hosts the Compose keyboard, owns the fixed-height
 * contract and the `InputConnection`, and wires the view's key reports
 * into `KeyboardModel` through the narrow `TextEditor` surface. The
 * host-sync pipeline (composed word, shift, suggestions) arrives with
 * Stages 3–5.
 */
class LezgiInputMethodService : InputMethodService() {

    private lateinit var imeLifecycleOwner: ImeLifecycleOwner
    private val model = KeyboardModel()

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner = ImeLifecycleOwner()
        imeLifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        // Compose resolves the window Recomposer from the window root
        // (the decor view), not from the ComposeView, so the lifecycle
        // owners must be reachable from there — otherwise the first
        // window traversal aborts and the keyboard never appears.
        window?.window?.decorView?.let(imeLifecycleOwner::attach)
        val view = ComposeView(this)
        imeLifecycleOwner.attach(view)
        view.setContent {
            KeyboardView(model = model, onKey = ::handleKey)
        }
        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        model.returnAction = EditorState.returnAction(editorInfo)
        model.autocapMode = EditorState.autocapMode(editorInfo)
        model.updateShiftFromContext(textEditor)
    }

    // The host-confirmed state change — the `textDidChange` analog. The
    // full sync pipeline (composed word, suggestions) joins in Stage 5.
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        model.updateShiftFromContext(textEditor)
    }

    // Never use fullscreen extract mode: the platform default turns it on
    // in landscape, replacing the host field with a fullscreen editor and
    // breaking the fixed-height contract. The keyboard is always just the
    // keyboard (DECISIONS.md D-018).
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun handleKey(cap: KeyCap) {
        model.handleKey(cap, textEditor)
        // Deleting can cross a sentence boundary; the shift state follows
        if (cap == KeyCap.Backspace) {
            model.updateShiftFromContext(textEditor)
        }
    }

    private val textEditor = object : TextEditor {

        override fun insertText(text: String) {
            currentInputConnection?.commitText(text, 1)
        }

        override fun deleteBackward() {
            val ic = currentInputConnection ?: return
            // An active selection deletes as a whole, like the hardware
            // delete key would.
            if (!ic.getSelectedText(0).isNullOrEmpty()) {
                ic.commitText("", 1)
                return
            }
            // deleteSurroundingText counts UTF-16 units — remove both
            // halves of a surrogate pair so astral characters (emoji
            // typed by other keyboards) are never split.
            val before = ic.getTextBeforeCursor(2, 0)
            val step = if (
                before != null && before.length >= 2 &&
                Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])
            ) 2 else 1
            ic.deleteSurroundingText(step, 0)
        }

        override fun textBeforeCursor(maxLength: Int): CharSequence? =
            currentInputConnection?.getTextBeforeCursor(maxLength, 0)

        override fun performReturn() {
            val ic = currentInputConnection ?: return
            val action = model.returnAction
            if (action == ReturnKeyAction.NONE) {
                ic.commitText("\n", 1)
            } else {
                ic.performEditorAction(EditorState.imeActionId(action))
            }
        }
    }

}
