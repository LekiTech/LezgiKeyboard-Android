package com.lekitech.lezgikeyboard.ime

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.ui.platform.ComposeView
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.model.TextEditor
import com.lekitech.lezgikeyboard.ui.KeyboardView
import kotlin.math.abs

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
    private val handler = Handler(Looper.getMainLooper())
    private val hideKeyboardName = Runnable { model.showsKeyboardName = false }

    /** The default preferences file — iOS UserDefaults analog (D-012). */
    private val preferences: SharedPreferences
        get() = getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner = ImeLifecycleOwner()
        imeLifecycleOwner.onCreate()
        model.layoutVariant = LayoutVariant.entries.firstOrNull {
            it.prefValue == preferences.getString(LAYOUT_VARIANT_KEY, null)
        } ?: LayoutVariant.CLASSIC
    }

    private var inputView: View? = null

    override fun onCreateInputView(): View {
        // Compose resolves the window Recomposer from the window root
        // (the decor view), not from the ComposeView, so the lifecycle
        // owners must be reachable from there — otherwise the first
        // window traversal aborts and the keyboard never appears.
        window?.window?.decorView?.let(imeLifecycleOwner::attach)
        val view = ComposeView(this)
        inputView = view
        imeLifecycleOwner.attach(view)
        view.setContent {
            KeyboardView(
                model = model,
                onKey = ::handleKey,
                onCursorMove = { textEditor.moveCursor(it) },
                onCursorLineMove = { lines ->
                    repeat(abs(lines)) { model.moveCursorLine(up = lines < 0, textEditor) }
                },
                onLayoutVariant = { variant ->
                    model.layoutVariant = variant
                    preferences.edit().putString(LAYOUT_VARIANT_KEY, variant.prefValue).apply()
                },
            )
        }
        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        model.returnAction = EditorState.returnAction(editorInfo)
        model.autocapMode = EditorState.autocapMode(editorInfo)
        model.updateShiftFromContext(textEditor)
        // Keyboard name flashed on the spacebar for 1.5 s per appearance
        model.showsKeyboardName = true
        handler.removeCallbacks(hideKeyboardName)
        handler.postDelayed(hideKeyboardName, 1500)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        handler.removeCallbacks(hideKeyboardName)
        model.showsKeyboardName = false
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

    /**
     * The window's top strip (`OVERLAY_HEADROOM`) exists only for
     * transient overlays: the host app lays out against the keyboard
     * content below it, and touches in the strip pass through to the
     * app — so the visible keyboard keeps the iOS 250 dp contract
     * while previews and callouts float above it (DECISIONS.md D-025).
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = inputView ?: return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val stripPx = (LezgiLayout.OVERLAY_HEADROOM * resources.displayMetrics.density).toInt()
        val contentTop = location[1] + stripPx
        outInsets.contentTopInsets = contentTop
        outInsets.visibleTopInsets = contentTop
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        // Everything below the strip stays touchable, including the
        // framework's own navigation band under the input view.
        val decor = window?.window?.decorView ?: return
        outInsets.touchableRegion.set(0, contentTop, decor.width, decor.height)
    }

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

        override fun textAfterCursor(maxLength: Int): CharSequence? =
            currentInputConnection?.getTextAfterCursor(maxLength, 0)

        override fun moveCursor(offset: Int) {
            val ic = currentInputConnection ?: return
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val base = extracted.startOffset + extracted.selectionStart
            val max = extracted.startOffset + (extracted.text?.length ?: 0)
            val target = (base + offset).coerceIn(0, max)
            ic.setSelection(target, target)
        }

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

/** iOS-parity preference key (D-012). */
private const val LAYOUT_VARIANT_KEY = "layoutVariant"
