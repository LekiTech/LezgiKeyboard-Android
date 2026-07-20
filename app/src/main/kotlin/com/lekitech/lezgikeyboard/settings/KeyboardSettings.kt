package com.lekitech.lezgikeyboard.settings

import android.content.SharedPreferences

/**
 * User-adjustable keyboard behavior (settings panel, Stage 7) — the
 * Android counterpart of the iOS `KeyboardSettings`, preference keys
 * and enum raw values identical (D-012) so a future cloud sync treats
 * settings uniformly across platforms. Every default reproduces the
 * pre-settings behavior, so a missing key changes nothing. Values
 * persist in the keyboard's own SharedPreferences; the model stays
 * storage-free — the service loads and saves.
 */
data class KeyboardSettings(
    // Suggestions
    val wordSuggestions: Boolean = true,
    val nextWordSuggestions: Boolean = true,
    val autoSpaceAfterSuggestion: Boolean = true,
    // Keys
    val doubleSpacePeriod: Boolean = true,
    val spaceCursor: Boolean = true,
    val spaceLabel: Boolean = true,
    // Learning / long press
    val learnSpeed: LearnSpeed = LearnSpeed.NORMAL,
    val calloutDelay: CalloutDelay = CalloutDelay.NORMAL,
    // Theme
    val theme: Theme = Theme.SYSTEM,
) {

    /**
     * Learning speed — only the visibility threshold for learned
     * suggestions changes, never the learning algorithm itself.
     */
    enum class LearnSpeed(val rawValue: String, val minUses: Int) {
        FAST("fast", 1),
        NORMAL("normal", 3),
        CONSERVATIVE("conservative", 5),
    }

    /**
     * Delay before the long-press callout with alternative characters
     * (ӏ, кь, къ…) appears.
     */
    enum class CalloutDelay(val rawValue: String, val millis: Long) {
        SHORT("short", 200L),
        NORMAL("normal", 300L),
        LONG("long", 450L),
    }

    /**
     * Keyboard color theme; SYSTEM follows the host's appearance.
     * Deliberately named Theme, not Appearance: if animation/size/
     * contrast settings ever appear, they get an Appearance section of
     * their own with Theme as one item inside it.
     */
    enum class Theme(val rawValue: String) {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark"),
    }

    fun save(preferences: SharedPreferences) {
        preferences.edit()
            .putBoolean(KEY_WORD_SUGGESTIONS, wordSuggestions)
            .putBoolean(KEY_NEXT_WORD_SUGGESTIONS, nextWordSuggestions)
            .putBoolean(KEY_AUTO_SPACE, autoSpaceAfterSuggestion)
            .putBoolean(KEY_DOUBLE_SPACE_PERIOD, doubleSpacePeriod)
            .putBoolean(KEY_SPACE_CURSOR, spaceCursor)
            .putBoolean(KEY_SPACE_LABEL, spaceLabel)
            .putString(KEY_LEARN_SPEED, learnSpeed.rawValue)
            .putString(KEY_CALLOUT_DELAY, calloutDelay.rawValue)
            .putString(KEY_THEME, theme.rawValue)
            .apply()
    }

    companion object {
        // iOS-parity preference keys (D-012)
        private const val KEY_WORD_SUGGESTIONS = "set_wordSuggestions"
        private const val KEY_NEXT_WORD_SUGGESTIONS = "set_nextWordSuggestions"
        private const val KEY_AUTO_SPACE = "set_autoSpaceAfterSuggestion"
        private const val KEY_DOUBLE_SPACE_PERIOD = "set_doubleSpacePeriod"
        private const val KEY_SPACE_CURSOR = "set_spaceCursor"
        private const val KEY_SPACE_LABEL = "set_spaceLabel"
        private const val KEY_LEARN_SPEED = "set_learnSpeed"
        private const val KEY_CALLOUT_DELAY = "set_calloutDelay"
        private const val KEY_THEME = "set_theme"

        fun load(preferences: SharedPreferences): KeyboardSettings {
            val defaults = KeyboardSettings()
            fun bool(key: String, fallback: Boolean) = preferences.getBoolean(key, fallback)
            return KeyboardSettings(
                wordSuggestions = bool(KEY_WORD_SUGGESTIONS, defaults.wordSuggestions),
                nextWordSuggestions =
                    bool(KEY_NEXT_WORD_SUGGESTIONS, defaults.nextWordSuggestions),
                autoSpaceAfterSuggestion =
                    bool(KEY_AUTO_SPACE, defaults.autoSpaceAfterSuggestion),
                doubleSpacePeriod = bool(KEY_DOUBLE_SPACE_PERIOD, defaults.doubleSpacePeriod),
                spaceCursor = bool(KEY_SPACE_CURSOR, defaults.spaceCursor),
                spaceLabel = bool(KEY_SPACE_LABEL, defaults.spaceLabel),
                learnSpeed = LearnSpeed.entries.firstOrNull {
                    it.rawValue == preferences.getString(KEY_LEARN_SPEED, null)
                } ?: defaults.learnSpeed,
                calloutDelay = CalloutDelay.entries.firstOrNull {
                    it.rawValue == preferences.getString(KEY_CALLOUT_DELAY, null)
                } ?: defaults.calloutDelay,
                theme = Theme.entries.firstOrNull {
                    it.rawValue == preferences.getString(KEY_THEME, null)
                } ?: defaults.theme,
            )
        }
    }
}
