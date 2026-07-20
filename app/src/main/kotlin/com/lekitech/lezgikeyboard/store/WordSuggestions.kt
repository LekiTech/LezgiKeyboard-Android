package com.lekitech.lezgikeyboard.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Read-only prefix lookup in the bundled `lezgi_words.sqlite`
 * dictionary — the Android counterpart of the iOS `WordSuggestions`,
 * with the SQL ported verbatim (D-006). The dictionary and the typed
 * text both use the Cyrillic palochka «ӏ» (U+04CF) and are fully
 * lowercase, so a lowercased prefix matches byte-for-byte — no
 * normalization needed.
 *
 * The query layer is shaped for the base-score contract: when the
 * dictionary gains an opaque `baseScore`, only the ORDER BY here
 * changes (`baseScore DESC, LENGTH(word)`) — the engine never mixes it
 * with personal counters.
 */
class WordSuggestions private constructor(private val db: SQLiteDatabase) {

    fun suggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT word FROM words WHERE word LIKE ? ORDER BY LENGTH(word) LIMIT ?",
            arrayOf(prefix.lowercase() + "%", limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) results.add(cursor.getString(0))
        }
        return results
    }

    /**
     * Exact-word membership — the same normalization rule as
     * `suggestions`: lowercase words with the Cyrillic palochka, so a
     * lowercased comparison is byte-exact.
     */
    fun contains(word: String): Boolean {
        if (word.isEmpty()) return false
        db.rawQuery(
            "SELECT 1 FROM words WHERE word = ?1 LIMIT 1",
            arrayOf(word.lowercase()),
        ).use { cursor -> return cursor.moveToFirst() }
    }

    /**
     * Random dictionary words for the idle suggestion bar. Queried on
     * keyboard appearance and idle transitions only, so the scan over
     * the small dictionary is imperceptible.
     */
    fun randomWords(count: Int): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT word FROM words ORDER BY RANDOM() LIMIT ?1",
            arrayOf(count.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) results.add(cursor.getString(0))
        }
        return results
    }

    fun close() = db.close()

    companion object {
        private const val FILE_NAME = "lezgi_words.sqlite"

        /**
         * Copies the bundled dictionary out of assets (re-copied when
         * the app version changes, so dictionary updates ship with the
         * app) and opens it read-only. Failures degrade silently to a
         * null store, like iOS — the keyboard types without
         * suggestions rather than crashing.
         */
        fun open(context: Context): WordSuggestions? = try {
            val file = File(context.noBackupFilesDir, FILE_NAME)
            val marker = File(context.noBackupFilesDir, "$FILE_NAME.version")
            @Suppress("DEPRECATION")
            val version = context.packageManager
                .getPackageInfo(context.packageName, 0).longVersionCode.toString()
            if (!file.exists() || !marker.exists() || marker.readText() != version) {
                context.assets.open(FILE_NAME).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                marker.writeText(version)
            }
            WordSuggestions(
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY),
            )
        } catch (_: Exception) {
            null
        }
    }
}
