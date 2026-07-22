package com.lekitech.lezgikeyboard.ime

import android.content.ClipDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.core.content.FileProvider
import com.lekitech.lezgikeyboard.BuildConfig
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.model.KeyboardModel
import com.lekitech.lezgikeyboard.model.ShiftState
import com.lekitech.lezgikeyboard.model.TextEditor
import com.lekitech.lezgikeyboard.settings.KeyboardSettings
import com.lekitech.lezgikeyboard.stickers.StickerFiles
import com.lekitech.lezgikeyboard.stickers.StickerPack
import com.lekitech.lezgikeyboard.store.LearnedWords
import com.lekitech.lezgikeyboard.store.WordSuggestions
import com.lekitech.lezgikeyboard.ui.KeyboardView
import java.io.File
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
        model.recentEmojis = preferences.getString(RECENT_EMOJIS_KEY, null)
            ?.split('\n')?.filter { it.isNotEmpty() }
            ?: emptyList()
        model.wordSuggestions = WordSuggestions.open(this)
        model.learnedWords = LearnedWords.open(this)
        // After the stores: updateSettings syncs the learned store's
        // visibility threshold with the saved learning speed.
        model.updateSettings(KeyboardSettings.load(preferences))
        // Local quality metrics baseline: visible in logcat, debug
        // builds only; nothing is transmitted (spec §11).
        if (BuildConfig.DEBUG) {
            Log.d("kb-metrics", model.metricsLine())
        }
    }

    /** Applies a panel change and persists it under the iOS keys (D-012). */
    private fun applySettings(settings: KeyboardSettings) {
        model.updateSettings(settings)
        settings.save(preferences)
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
                onSuggestionDelete = { model.deleteLearnedWord(it, textEditor) },
                onUpdateSettings = ::applySettings,
                onLearnedReset = { model.resetLearnedWords(textEditor) },
                onEmojiInsert = ::insertEmoji,
                onStickerInsert = ::insertSticker,
            )
        }
        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        model.returnAction = EditorState.returnAction(editorInfo)
        model.autocapMode = EditorState.autocapMode(editorInfo)
        model.needsGlobe = needsGlobeKey()
        model.isPrivateField = EditorState.isPrivateField(editorInfo)
        model.stickersAvailable = EditorState.acceptsStickers(editorInfo)
        model.updateShiftFromContext(textEditor)
        // Keyboard name flashed on the spacebar for 1.5 s per appearance
        model.showsKeyboardName = true
        handler.removeCallbacks(hideKeyboardName)
        handler.postDelayed(hideKeyboardName, 1500)
        model.syncComposedWord(textEditor)
        model.refreshFallbackSuggestions(textEditor)
        model.updateSuggestions(textEditor)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        handler.removeCallbacks(hideKeyboardName)
        model.showsKeyboardName = false
    }

    // The host-confirmed state change — the `textDidChange` analog.
    // Pipeline order is parity-critical: resync the composed word,
    // re-evaluate shift, refresh suggestions (host-clear learning
    // slots in first in Stage 6).
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        model.syncComposedWord(textEditor)
        model.updateShiftFromContext(textEditor)
        model.updateSuggestions(textEditor)
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
        model.wordSuggestions?.close()
        model.wordSuggestions = null
        model.learnedWords?.close()
        model.learnedWords = null
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun handleKey(cap: KeyCap) {
        if (cap == KeyCap.Globe) {
            // The Android convention (D-029): third-party keyboards
            // open the system picker, the one switching hub that works
            // everywhere — direct cycling strands users on keyboards
            // that offer no way back (Samsung/One UI).
            // On iOS the globe consumes the auto-space swallow flag
            // like any other key; it never reaches the model here.
            model.disarmAutoSpaceSwallow()
            inputMethodManager.showInputMethodPicker()
            return
        }
        model.handleKey(cap, textEditor)
        // Deleting can cross a sentence boundary; the shift state follows
        if (cap == KeyCap.Backspace) {
            model.updateShiftFromContext(textEditor)
        }
        model.updateSuggestions(textEditor)
    }

    /**
     * Accepting a suggestion replaces the active prefix with the word
     * plus a trailing space (user-disableable in the settings panel).
     * The prefix length comes from whichever source saw more — the
     * host context or the locally composed word (the context lags
     * behind fast typing) — and the bar refreshes in the tap handler
     * itself: hosts may not echo the keyboard's own edits promptly,
     * and the stale context cannot resurface the word because the
     * prefix comes from the composed word, not the host.
     */
    private fun acceptSuggestion(word: String) {
        // The previous word must be captured before the prefix is
        // replaced — it feeds the bigram for the picked word.
        val previous = model.previousWord(textEditor)
        val prefixLength =
            maxOf(model.wordPrefix(textEditor).length, model.composedWord.length)
        repeat(prefixLength) { textEditor.deleteBackward() }
        val addsSpace = model.settings.autoSpaceAfterSuggestion
        textEditor.insertText(if (addsSpace) "$word " else word)
        model.recordPickedSuggestion(word, previous, insertedSpace = addsSpace)
        if (model.shiftState == ShiftState.ONCE) model.shiftState = ShiftState.OFF
        model.updateSuggestions(textEditor)
    }

    /**
     * Inserts an emoji from the page and records it into recents —
     * persisted immediately under the iOS `recentEmojis` key (D-012;
     * newline-joined, an emoji sequence never contains a newline). The
     * host echoes the edit through `onUpdateSelection`, which resyncs
     * the composed word and the bar.
     */
    private fun insertEmoji(emoji: String) {
        textEditor.insertText(emoji)
        model.recordRecentEmoji(emoji)
        preferences.edit()
            .putString(RECENT_EMOJIS_KEY, model.recentEmojis.joinToString("\n"))
            .apply()
    }

    // MARK: - Sticker insertion (Commit Content API, D-031)

    /**
     * Commits a sticker image into the focused editor. The pack ships
     * as WebP; when the editor accepts only PNG the image is converted
     * once and cached. The cache copy is served through the scoped
     * FileProvider with a read grant — the standard image-keyboard
     * path (the same one Gboard uses), which iOS keyboards have no
     * equivalent of.
     */
    private fun insertSticker(name: String) {
        val mimeTypes = currentInputEditorInfo?.contentMimeTypes ?: return
        val ic = currentInputConnection ?: return
        val webpAccepted = mimeTypes.any {
            ClipDescription.compareMimeTypes("image/webp", it)
        }
        val (file, mime) = stickerFile(name, webpAccepted) ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.stickers", file)
        val info = InputContentInfo(uri, ClipDescription(name, arrayOf(mime)))
        ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
    }

    /** The shareable cache copy of a sticker, in a mime the editor takes. */
    private fun stickerFile(name: String, webpAccepted: Boolean): Pair<File, String>? = try {
        if (webpAccepted) {
            StickerFiles.sticker(this, name)?.let { it to "image/webp" }
        } else {
            val file = File(StickerFiles.directory(this), "$name.png")
            if (!file.exists()) {
                val bitmap = assets.open(StickerPack.assetPath(name))
                    .use(BitmapFactory::decodeStream) ?: return null
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            file to "image/png"
        }
    } catch (_: Exception) {
        null
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

        override fun hasText(): Boolean {
            val ic = currentInputConnection ?: return false
            return !ic.getTextBeforeCursor(1, 0).isNullOrEmpty() ||
                !ic.getTextAfterCursor(1, 0).isNullOrEmpty()
        }

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

/** iOS-parity preference keys (D-012). */
private const val LAYOUT_VARIANT_KEY = "layoutVariant"
private const val RECENT_EMOJIS_KEY = "recentEmojis"
