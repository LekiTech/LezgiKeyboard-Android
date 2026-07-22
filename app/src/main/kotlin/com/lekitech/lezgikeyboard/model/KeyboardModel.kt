package com.lekitech.lezgikeyboard.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lekitech.lezgikeyboard.layout.KeyCap
import com.lekitech.lezgikeyboard.layout.KeyboardPage
import com.lekitech.lezgikeyboard.layout.LayoutVariant
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import com.lekitech.lezgikeyboard.layout.ReturnKeyAction
import com.lekitech.lezgikeyboard.settings.KeyboardSettings
import com.lekitech.lezgikeyboard.store.LearnedWords
import com.lekitech.lezgikeyboard.store.WordSuggestions

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

    /**
     * The globe key shows only when the OS offers no switcher of its
     * own (D-027) — the exact iOS `needsInputModeSwitchKey` semantics.
     */
    var needsGlobe by mutableStateOf(false)

    /** Tap cycles off → once → capsLock → off; "once" consumes itself. */
    var shiftState by mutableStateOf(ShiftState.ONCE)
    var autocapMode = AutocapMode.SENTENCES

    val isShifted: Boolean get() = shiftState != ShiftState.OFF
    val isCapsLock: Boolean get() = shiftState == ShiftState.CAPS_LOCK

    /** Space long-press trackpad mode; key labels hide while active. */
    var isSpaceCursorMode by mutableStateOf(false)

    /** Keyboard name flashed on the spacebar right after appearance. */
    var showsKeyboardName by mutableStateOf(false)

    /** The in-keyboard settings panel (opened by the gear key) is visible. */
    var showsSettings by mutableStateOf(false)

    /**
     * User-adjustable behavior (settings panel, Stage 7); defaults keep
     * the pre-settings behavior. The service loads and persists (the
     * model stays storage-free); every change goes through
     * `updateSettings` so its model-side effects apply.
     */
    var settings by mutableStateOf(KeyboardSettings())
        private set

    /**
     * Applies a settings change; effects that have model-side state are
     * synced here so the change is felt immediately. Persistence is the
     * caller's concern.
     */
    fun updateSettings(new: KeyboardSettings) {
        settings = new
        learnedWords?.minVisibleUses = new.learnSpeed.minUses
        if (!new.wordSuggestions) {
            suggestions = emptyList()
            learnedDisplayWords = emptySet()
            fallbackSuggestions = emptyList()
        }
    }

    // MARK: - Suggestion engine

    /** The bundled dictionary; null degrades to a bar without candidates. */
    var wordSuggestions: WordSuggestions? = null

    /** The personal learned store; null degrades to dictionary-only. */
    var learnedWords: LearnedWords? = null

    /**
     * The most recent completed word of the current sentence, tracked
     * synchronously (the host context lags) — the source for next-word
     * suggestions and bigram learning. Null right after `. ! ?` or
     * return, so neither ever crosses a sentence boundary.
     */
    private var lastCompletedWord: String? = null

    /**
     * Password and no-personalized-learning fields: the bar shows no
     * content of any kind and nothing is ever learned (D-015).
     */
    var isPrivateField = false

    /**
     * The word being typed, tracked locally: the host context lags
     * behind fast typing (it round-trips through the host app), so a
     * suggestion tap must not rely on it alone. Resynced from the
     * settled context on every host update.
     */
    var composedWord = ""
        private set

    private var fallbackWords = listOf<String>()

    /**
     * Whether the last update left the bar idle (no prefix). A fresh
     * random trio rolls only on the transition into idle — never
     * continuously while idle.
     */
    private var barWasIdle = true

    var suggestions by mutableStateOf(listOf<String>())

    /** Random dictionary words shown while the bar has no real content. */
    var fallbackSuggestions by mutableStateOf(listOf<String>())

    /**
     * The raw typed word leading the bar as a literal candidate because
     * no source recognizes it. Presentation-only marker: the view alone
     * wraps this word in «…»; the stored value stays undecorated.
     */
    var unrecognizedTyped by mutableStateOf<String?>(null)

    /** Displayed suggestions that came from the learned store — the
     * only ones long-press deletion applies to. */
    var learnedDisplayWords by mutableStateOf(setOf<String>())

    fun isLearnedSuggestion(display: String): Boolean = display in learnedDisplayWords

    /** Realigns the local buffers once the host has confirmed its state. */
    fun syncComposedWord(editor: TextEditor) {
        learnWordCommittedByHostClear(editor)
        composedWord = wordPrefix(editor)
        lastCompletedWord = previousWord(editor)
        // The cursor moved on from the word just accepted from the bar —
        // the acceptance settles as final (metrics, best effort).
        if (pendingAcceptedWord != null && lastCompletedWord != pendingAcceptedWord) {
            pendingAcceptedWord = null
        }
        // A host-confirmed state change disarms the auto-space swallow —
        // unless it is just our own acceptance edit landing (the context
        // still ends with the word + space). Same best-effort pattern as
        // above.
        val autoSpaced = autoSpacedAcceptedWord
        if (autoSpaced != null &&
            editor.textBeforeCursor(autoSpaced.length + 1)?.toString() != "$autoSpaced "
        ) {
            autoSpacedAcceptedWord = null
        }
    }

    /**
     * The word just inserted by a suggestion-bar tap together with its
     * automatic trailing space — predictive and literal taps alike.
     * Armed only for the very next key event: any other key or a host
     * resync clears it, so a manually typed space or a space away from
     * the cursor can never be swallowed. Deliberately separate from
     * `pendingAcceptedWord` (metrics), which has different semantics
     * and lifecycle.
     */
    private var autoSpacedAcceptedWord: String? = null

    /**
     * The globe never reaches `handleKey` on Android (the service
     * switches input methods directly), but on iOS it consumes the
     * swallow flag like every other key — the service calls this to
     * keep that timing identical.
     */
    fun disarmAutoSpaceSwallow() {
        autoSpacedAcceptedWord = null
    }

    // MARK: - Local quality metrics

    /**
     * Whether the word being composed has had at least one predictive
     * candidate (beyond the quoted literal) on the bar. One opportunity
     * is counted per completed word, not per suggestion refresh; the
     * flag resets when the word completes or the composition is
     * abandoned (prefix back to empty).
     */
    private var wordHadPredictions = false

    /**
     * The word most recently accepted from the bar (with its trailing
     * space), pending until any other word completes. Going back into
     * it counts as a correction — event-based, no timeout.
     */
    private var pendingAcceptedWord: String? = null

    /**
     * Metrics for one manually completed word: every manual completion
     * counts, and predictive candidates shown during composition close
     * as one opportunity that was passed over. Completing a word also
     * settles any pending acceptance as final (the user moved on).
     */
    private fun recordManualCompletion() {
        learnedWords?.bumpMetric(LearnedWords.Metric.TYPED_MANUALLY)
        if (wordHadPredictions) {
            learnedWords?.bumpMetric(LearnedWords.Metric.OPPORTUNITIES)
            learnedWords?.bumpMetric(LearnedWords.Metric.IGNORED)
        }
        wordHadPredictions = false
        pendingAcceptedWord = null
    }

    /** Metrics line for the DEBUG startup log. */
    fun metricsLine(): String = learnedWords?.metricsSummary() ?: "no learned store"

    /**
     * Sending a message usually clears the field without the word ever
     * getting a trailing space, so it would never be learned. The clear
     * is visible: the host's edit arrives with a completely empty
     * document while the composed word is still non-empty — that
     * commits it. Deliberately narrow: cursor moves and edits that
     * leave text never trigger, and the keyboard's own edits clear
     * `composedWord` synchronously first. Rare false positives (a
     * search field's clear button) are absorbed by the visibility
     * threshold.
     */
    private fun learnWordCommittedByHostClear(editor: TextEditor) {
        if (composedWord.isEmpty() || editor.hasText() || isPrivateField) return
        recordManualCompletion()
        learnedWords?.learn(composedWord, lastCompletedWord, picked = false)
    }

    /**
     * Records the word before the cursor as completed, together with
     * the word preceding it in the same sentence. Called before the
     * terminator (space / return / punctuation) is inserted.
     */
    private fun learnCompletedWord(editor: TextEditor) {
        if (isPrivateField) return
        val word = wordPrefix(editor)
        if (word.isEmpty()) return
        recordManualCompletion()
        learnedWords?.learn(word, previousWord(editor), picked = false)
        lastCompletedWord = word
    }

    /**
     * The completed word right before the one being typed, within the
     * same sentence — bigrams never cross a boundary (`. ! ?` or a new
     * line cut the context first). Null when the host truncates the
     * context short.
     */
    fun previousWord(editor: TextEditor): String? {
        val full = editor.textBeforeCursor(256)?.toString() ?: return null
        if (full.isEmpty()) return null
        val cut = full.indexOfLast { it in ".!?\n" }
        val context = if (cut >= 0) full.substring(cut + 1) else full
        val tokens = context.split(*wordSeparators.toCharArray()).filter { it.isNotEmpty() }
        val endsWithSeparator = full.last() in wordSeparators
        return when {
            endsWithSeparator -> tokens.lastOrNull()
            tokens.size >= 2 -> tokens[tokens.size - 2]
            else -> null
        }
    }

    /**
     * The active word prefix from the host context: a trailing
     * separator means the last word is already completed — nothing is
     * being composed, and a suggestion tap must never replace it.
     */
    fun wordPrefix(editor: TextEditor): String {
        val context = editor.textBeforeCursor(64)?.toString() ?: return ""
        if (context.isEmpty() || context.last() in wordSeparators) return ""
        return context.takeLastWhile { it !in wordSeparators }
    }

    /**
     * The suggestion pipeline (ANDROID_PORT_CONTEXT.md §7). Learned
     * candidates join the merge ahead of dictionary ones in Stage 6;
     * the ordering-level merge is the only sanctioned interaction
     * between the sources (the base-score contract).
     */
    fun updateSuggestions(editor: TextEditor) {
        if (isPrivateField) {
            suggestions = emptyList()
            fallbackSuggestions = emptyList()
            learnedDisplayWords = emptySet()
            unrecognizedTyped = null
            return
        }
        // Master switch: with word suggestions off the bar shows
        // nothing (learning itself continues — a separate concern).
        if (!settings.wordSuggestions) {
            suggestions = emptyList()
            learnedDisplayWords = emptySet()
            fallbackSuggestions = emptyList()
            unrecognizedTyped = null
            return
        }
        val prefix = composedWord
        if (prefix.isEmpty()) {
            // With no active prefix, suggest likely next words from the
            // learned bigrams of the last completed word; the bar falls
            // back to the random trio when there are none.
            val nextWords = if (settings.nextWordSuggestions) {
                lastCompletedWord?.let { learnedWords?.nextWords(it) }.orEmpty()
            } else {
                emptyList()
            }
            val display = nextWords.map { displayForm(it, "", editor) }
            val isIdle = display.isEmpty()
            if (isIdle && !barWasIdle) {
                fallbackWords = wordSuggestions?.randomWords(3) ?: emptyList()
            }
            barWasIdle = isIdle
            // No active composition: whatever opportunity was open was
            // either consumed by a completion hook or abandoned with
            // the erased word — either way the flag must not leak into
            // the next word (metrics).
            wordHadPredictions = false
            fallbackSuggestions = fallbackWords.map { displayForm(it, "", editor) }
            unrecognizedTyped = null
            suggestions = display
            learnedDisplayWords = display.toSet()
            return
        }
        barWasIdle = false
        fallbackSuggestions = fallbackWords.map { displayForm(it, "", editor) }

        // Learned candidates come first; the dictionary fills the
        // remaining slots (the ordering-level merge is the only
        // sanctioned interaction between the sources).
        val learned = learnedWords?.suggestions(prefix, previousWord(editor)).orEmpty()
        val merged = learned.toMutableList()
        val seen = merged.map { it.lowercase() }.toMutableSet()
        for (word in wordSuggestions?.suggestions(prefix) ?: emptyList()) {
            if (seen.add(word.lowercase())) merged.add(word)
        }
        val display = mutableListOf<String>()
        val learnedSet = mutableSetOf<String>()
        merged.take(3).forEachIndexed { index, word ->
            val form = displayForm(word, prefix, editor)
            display.add(form)
            if (index < learned.size) learnedSet.add(form)
        }

        // Native-style literal candidate: a typed word unknown to BOTH
        // sources leads the bar exactly as typed; the view alone adds
        // «…». A below-threshold learned word still counts as unknown.
        if (wordSuggestions?.contains(prefix) != true &&
            learnedWords?.isRecognized(prefix) != true
        ) {
            unrecognizedTyped = prefix
            suggestions = listOf(prefix) + display.take(2)
        } else {
            unrecognizedTyped = null
            suggestions = display
        }
        learnedDisplayWords = learnedSet
        // Metrics: the composed word has seen at least one predictive
        // candidate (the quoted literal alone does not count) — one
        // opportunity per word, consumed by the completion hooks.
        if (display.isNotEmpty()) wordHadPredictions = true
    }

    /**
     * Shared display form for every suggestion: a typed prefix dictates
     * its own case (capitalized prefix → capitalized word, fully
     * uppercase prefix → uppercase word); with no prefix the sentence
     * context decides; Caps Lock always uppercases. The inserted text
     * always matches the displayed form; storage stays lowercase.
     */
    private fun displayForm(word: String, prefix: String, editor: TextEditor): String {
        if (isCapsLock) return word.uppercase()
        if (prefix.isEmpty()) {
            return if (autocapMode != AutocapMode.NONE && isCursorAtSentenceStart(editor)) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word
            }
        }
        if (!prefix.first().isUpperCase()) return word
        val allCaps = prefix.length >= 2 && prefix == prefix.uppercase()
        return if (allCaps) word.uppercase() else word.replaceFirstChar { it.uppercase() }
    }

    /**
     * Records a suggestion chosen from the bar — a stronger learning
     * signal than typing. `previous` must be captured before the prefix
     * is replaced. With the trailing space inserted the word is
     * completed and next-word suggestions chain from it; without it
     * (auto-space setting off) it stays the composed prefix.
     */
    fun recordPickedSuggestion(word: String, previous: String?, insertedSpace: Boolean) {
        if (!isPrivateField) learnedWords?.learn(word, previous, picked = true)
        // Metrics: tapping the quoted literal confirms the user's own
        // word — any predictions shown were passed over, so it counts
        // as a manual completion. Tapping a real prediction is an
        // acceptance; it stays pending until another word completes,
        // and going back into it counts as a correction.
        if (word == unrecognizedTyped) {
            recordManualCompletion()
        } else {
            learnedWords?.bumpMetric(LearnedWords.Metric.ACCEPTED)
            if (wordHadPredictions) {
                learnedWords?.bumpMetric(LearnedWords.Metric.OPPORTUNITIES)
            }
            wordHadPredictions = false
            if (insertedSpace) pendingAcceptedWord = word
        }
        if (insertedSpace) {
            composedWord = ""
            lastCompletedWord = word
            autoSpacedAcceptedWord = word
        } else {
            composedWord = word
        }
    }

    /**
     * Removes a learned word chosen from the bar; the bundled
     * dictionary keeps suggesting its own entries as usual.
     */
    fun deleteLearnedWord(display: String, editor: TextEditor) {
        learnedWords?.delete(display)
        updateSuggestions(editor)
    }

    /**
     * Wipes the whole learned store (words and pairs) and refreshes
     * the bar. Triggered from the settings panel.
     */
    fun resetLearnedWords(editor: TextEditor) {
        learnedWords?.reset()
        lastCompletedWord = null
        updateSuggestions(editor)
    }

    // MARK: - Settings panel data

    /**
     * Saved words for the settings dictionary page: genuinely
     * user-added vocabulary only — words past the learned visibility
     * threshold that are absent from the bundled dictionary (the same
     * exact lookup the bar's literal candidate uses). The learning
     * store also keeps records for dictionary words — frequency and
     * bigram signals — but those are internal and never listed. The
     * page counter derives from this same list, so the count and the
     * list cannot disagree. The limit matches the store's own row cap,
     * so the set is complete.
     */
    fun savedWords(limit: Int = 5000): List<String> =
        learnedWords?.topWords(limit).orEmpty()
            .filter { wordSuggestions?.contains(it) != true }

    /** Re-rolls the idle-bar words; called once per keyboard appearance. */
    fun refreshFallbackSuggestions(editor: TextEditor) {
        if (isPrivateField || !settings.wordSuggestions) {
            fallbackWords = emptyList()
            fallbackSuggestions = emptyList()
            return
        }
        fallbackWords = wordSuggestions?.randomWords(3) ?: emptyList()
        fallbackSuggestions = fallbackWords.map { displayForm(it, "", editor) }
        barWasIdle = true
    }

    // MARK: - Emoji

    /**
     * Most recently used emoji, newest first (limit 24, move-to-front
     * dedup). The service loads and persists under the iOS
     * `recentEmojis` preference key (D-012) — not in the learning
     * database.
     */
    var recentEmojis by mutableStateOf(listOf<String>())

    /**
     * Whether the focused field accepts sticker images through the
     * Commit Content API — decides whether the emoji page shows the
     * sticker section (D-031). Set by the service per field.
     */
    var stickersAvailable by mutableStateOf(false)

    fun recordRecentEmoji(emoji: String) {
        recentEmojis =
            (listOf(emoji) + recentEmojis.filter { it != emoji }).take(RECENT_EMOJIS_LIMIT)
    }

    private var lastSpaceTapNanos: Long? = null

    /**
     * Where «ъ» lives; set from the gear menu (and the panel in
     * Stage 7). The service persists changes — the model stays
     * storage-free.
     */
    var layoutVariant by mutableStateOf(LayoutVariant.CLASSIC)

    /** Punctuation that returns from the numbers/symbols pages to letters. */
    private val returnsToLetters = setOf(".", ",", "?", "!", "'")

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
            if (needsGlobe) add(KeyCap.Globe)
            add(KeyCap.Space)
            // «ъ» sits next to the space bar in the classic variant and
            // moves to the top letter row in the topRow variant
            if (layoutVariant == LayoutVariant.CLASSIC) add(KeyCap.Character("ъ"))
            add(KeyCap.Return)
        }
        KeyboardPage.NUMBERS, KeyboardPage.SYMBOLS -> buildList {
            add(KeyCap.Letters)
            if (needsGlobe) add(KeyCap.Globe)
            add(KeyCap.Space)
            add(KeyCap.Return)
        }
        KeyboardPage.EMOJI -> emptyList()
    }

    fun handleKey(cap: KeyCap, editor: TextEditor) {
        // First keystroke dismisses the keyboard name, like native
        showsKeyboardName = false

        // The auto-space swallow is armed for the very next key only:
        // consume the flag now so every path below (including this key
        // itself) leaves it cleared.
        val autoSpaced = autoSpacedAcceptedWord
        autoSpacedAcceptedWord = null

        when (cap) {
            is KeyCap.Character -> {
                if (cap.text in wordTerminators) learnCompletedWord(editor)
                // Sentence punctuation typed right after a bar tap lands
                // next to the word: the auto-inserted trailing space is
                // removed — but only when it provably is that space (flag
                // armed by the tap AND the context still ends with word +
                // space). This runs after learnCompletedWord, which
                // no-ops on the trailing space, so the accepted word is
                // never learned twice.
                if (cap.text in autoSpaceSwallowers && autoSpaced != null &&
                    editor.textBeforeCursor(autoSpaced.length + 1)?.toString() == "$autoSpaced "
                ) {
                    editor.deleteBackward()
                }
                val text =
                    if (isShifted) LezgiLayout.applyCase(cap.text, isCapsLock) else cap.text
                editor.insertText(text)
                if (cap.text.length == 1 && cap.text.first() in wordSeparators) {
                    composedWord = ""
                } else {
                    composedWord += text
                }
                if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
                // Sentence-ending punctuation cuts the next-word context
                if (cap.text in sentenceEnders) lastCompletedWord = null
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
            // capital next (user-disableable in the settings panel)
            KeyCap.Space -> {
                val now = System.nanoTime()
                val last = lastSpaceTapNanos
                val before = editor.textBeforeCursor(2)
                if (settings.doubleSpacePeriod &&
                    last != null && now - last < 350_000_000L &&
                    before != null && before.length >= 2 && before.last() == ' ' &&
                    before[before.length - 2].isLetterOrDigit()
                ) {
                    editor.deleteBackward()
                    editor.insertText(". ")
                    if (shiftState != ShiftState.CAPS_LOCK) shiftState = ShiftState.ONCE
                    lastSpaceTapNanos = null
                    lastCompletedWord = null  // ". " ends the sentence
                } else {
                    learnCompletedWord(editor)
                    editor.insertText(" ")
                    lastSpaceTapNanos = now
                }
                composedWord = ""
            }

            KeyCap.Return -> {
                learnCompletedWord(editor)
                editor.performReturn()
                composedWord = ""
                lastCompletedWord = null  // a new line starts a new sentence
                // A new paragraph starts a new sentence, like the native
                // keyboard; the same side effect runs for action fields
                // (D-016) — the field decides what return did
                if (shiftState != ShiftState.CAPS_LOCK && autocapMode != AutocapMode.NONE) {
                    shiftState = ShiftState.ONCE
                }
            }

            KeyCap.Backspace -> {
                // Deleting the trailing space of a completed word means
                // "going back to edit that word": composition resumes
                // with the whole word (extracted from the pre-delete
                // context — the host lags behind our own edit). Only
                // this exact transition resumes; every other deletion
                // removes one character from the prefix.
                var resumedWord: String? = null
                if (composedWord.isEmpty()) {
                    val context = editor.textBeforeCursor(64)?.toString()
                    if (!context.isNullOrEmpty() && context.last() == ' ') {
                        val beforeSpace = context.dropLast(1)
                        if (beforeSpace.isNotEmpty() && beforeSpace.last() !in wordSeparators) {
                            resumedWord = beforeSpace.takeLastWhile { it !in wordSeparators }
                        }
                    }
                }
                editor.deleteBackward()
                if (resumedWord != null) {
                    // Metrics: going back into the word just accepted
                    // from the bar means the suggestion did not survive
                    // contact with the user — a correction, however
                    // much time has passed.
                    if (resumedWord == pendingAcceptedWord) {
                        learnedWords?.bumpMetric(LearnedWords.Metric.CORRECTED)
                    }
                    pendingAcceptedWord = null
                    composedWord = resumedWord
                } else if (composedWord.isNotEmpty()) {
                    composedWord = composedWord.dropLast(1)
                }
            }

            KeyCap.Shift -> shiftState = when (shiftState) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> ShiftState.CAPS_LOCK
                ShiftState.CAPS_LOCK -> ShiftState.OFF
            }

            KeyCap.Numbers -> page = KeyboardPage.NUMBERS
            KeyCap.Symbols -> page = KeyboardPage.SYMBOLS
            KeyCap.Letters -> page = KeyboardPage.LETTERS

            // The gear opens the in-keyboard settings panel, the emoji
            // key the fullscreen emoji page; the globe never reaches
            // the model (the service switches input methods directly).
            KeyCap.Settings -> showsSettings = true
            KeyCap.Emoji -> page = KeyboardPage.EMOJI
            KeyCap.Globe -> Unit
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

    /** Punctuation that finishes the word before it, like space and return. */
    private val wordTerminators = setOf(".", ",", "?", "!", ";", ":")

    /**
     * Punctuation that, typed right after a bar tap, swallows the
     * auto-inserted trailing space and lands next to the word.
     */
    private val autoSpaceSwallowers = setOf(".", ",", "?", "!")

    /** Characters that finish a word — the iOS separator set. */
    private val wordSeparators =
        " \t\n.,!?;:\"'()[]{}—–-".toSet()

    private companion object {
        const val RECENT_EMOJIS_LIMIT = 24
    }
}
