package com.lekitech.lezgikeyboard.model

import com.lekitech.lezgikeyboard.layout.KeyCap

/**
 * Stage 4 scaffolding: a scripted candidate source so the suggestion
 * bar's layout and animations are exercisable before the real engine
 * exists. Tracks a local prefix, offers the quoted literal plus two
 * derived candidates (the middle one posing as "learned" so long-press
 * deletion is testable), and a static idle trio. Replaced wholesale by
 * the prediction engine in Stage 5 — nothing here is behavior.
 */
class FakeSuggestionSource {

    data class BarContent(
        val suggestions: List<String>,
        val literal: String?,
        val learned: Set<String>,
        val fallback: List<String>,
    )

    private var prefix = ""
    private val deletedLearned = mutableSetOf<String>()
    private val idleWords = listOf("зун", "вун", "чна")

    fun onKey(cap: KeyCap) {
        when (cap) {
            is KeyCap.Character -> prefix += cap.text
            KeyCap.Backspace -> prefix = prefix.dropLast(1)
            KeyCap.Space, KeyCap.Return -> prefix = ""
            else -> Unit
        }
    }

    fun prefixLength(): Int = prefix.length

    fun accept() {
        prefix = ""
    }

    fun delete(word: String) {
        deletedLearned += word
    }

    fun reset() {
        prefix = ""
    }

    fun content(): BarContent {
        if (prefix.isEmpty()) {
            return BarContent(emptyList(), null, emptySet(), idleWords)
        }
        val learnedCandidate = prefix + "ди"
        val dictionaryCandidate = prefix + "ар"
        return BarContent(
            suggestions = listOf(prefix, learnedCandidate, dictionaryCandidate),
            literal = prefix,
            learned = if (learnedCandidate in deletedLearned) emptySet() else setOf(learnedCandidate),
            fallback = idleWords,
        )
    }
}
