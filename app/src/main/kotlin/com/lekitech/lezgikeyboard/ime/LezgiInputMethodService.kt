package com.lekitech.lezgikeyboard.ime

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.FakeSuggestionSource
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.model.ShiftState
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

    /** Stage 4 scaffolding; the real engine replaces it in Stage 5. */
    private val fakeSuggestions = FakeSuggestionSource()
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
                    // Vertical steps go through arrow-key events: the
                    // editor owns its layout, so wrapped lines and the
                    // visual column are handled correctly — something
                    // context math can never see (DECISIONS.md D-026).
                    repeat(abs(lines)) {
                        sendDownUpKeyEvents(
                            if (lines < 0) KeyEvent.KEYCODE_DPAD_UP
                            else KeyEvent.KEYCODE_DPAD_DOWN,
                        )
                    }
                },
                onLayoutVariant = { variant ->
                    model.layoutVariant = variant
                    preferences.edit().putString(LAYOUT_VARIANT_KEY, variant.prefValue).apply()
                },
                onSuggestion = ::acceptSuggestion,
                onSuggestionDelete = { word ->
                    fakeSuggestions.delete(word)
                    refreshSuggestionBar()
                },
            )
        }
        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        model.returnAction = EditorState.returnAction(editorInfo)
        model.autocapMode = EditorState.autocapMode(editorInfo)
        model.needsGlobe = needsGlobeKey()
        model.updateShiftFromContext(textEditor)
        // Keyboard name flashed on the spacebar for 1.5 s per appearance
        model.showsKeyboardName = true
        handler.removeCallbacks(hideKeyboardName)
        handler.postDelayed(hideKeyboardName, 1500)
        fakeSuggestions.reset()
        refreshSuggestionBar()
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
        if (cap == KeyCap.Globe) {
            // The Android convention (D-029): third-party keyboards
            // open the system picker, the one switching hub that works
            // everywhere — direct cycling strands users on keyboards
            // that offer no way back (Samsung/One UI).
            inputMethodManager.showInputMethodPicker()
            return
        }
        model.handleKey(cap, textEditor)
        // Deleting can cross a sentence boundary; the shift state follows
        if (cap == KeyCap.Backspace) {
            model.updateShiftFromContext(textEditor)
        }
        fakeSuggestions.onKey(cap)
        refreshSuggestionBar()
    }

    /**
     * Accepting a suggestion replaces the typed prefix with the word
     * plus a trailing space and refreshes the bar in the tap handler
     * itself — hosts may not echo the keyboard's own edits promptly.
     * The real replacement rule (max of context prefix and composed
     * word) arrives with the engine in Stage 5.
     */
    private fun acceptSuggestion(word: String) {
        repeat(fakeSuggestions.prefixLength()) { textEditor.deleteBackward() }
        textEditor.insertText("$word ")
        fakeSuggestions.accept()
        if (model.shiftState == ShiftState.ONCE) model.shiftState = ShiftState.OFF
        refreshSuggestionBar()
    }

    private fun refreshSuggestionBar() {
        val content = fakeSuggestions.content()
        model.suggestions = content.suggestions
        model.unrecognizedTyped = content.literal
        model.learnedDisplayWords = content.learned
        model.fallbackSuggestions = content.fallback
    }

    // MARK: - Input-method switching (globe key, D-027)
    //
    // The globe mirrors the iOS `needsInputModeSwitchKey` semantics:
    // it appears only when switching is wanted AND the system draws no
    // switcher of its own. Stock Android 15+ shows one in the IME
    // navigation band (`imeDrawsImeNavBar`); Samsung/One UI with
    // gesture navigation shows none, leaving Settings as the only way
    // back to this keyboard without the key.

    private fun needsGlobeKey(): Boolean {
        if (systemDrawsImeSwitcher()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val token = window?.window?.attributes?.token ?: return false
            @Suppress("DEPRECATION")
            inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
        }
    }

    private fun systemDrawsImeSwitcher(): Boolean {
        val id = resources.getIdentifier("config_imeDrawsImeNavBar", "bool", "android")
        return id != 0 && resources.getBoolean(id)
    }

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

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
