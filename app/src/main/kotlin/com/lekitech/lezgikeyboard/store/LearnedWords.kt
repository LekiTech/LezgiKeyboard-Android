package com.lekitech.lezgikeyboard.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.lekitech.lezgikeyboard.layout.LezgiLayout
import java.io.File
import java.util.Locale

/**
 * On-device learned-word store (`learned.sqlite`) — the Android
 * counterpart of the iOS `LearnedWords`, schema and every formula
 * byte-compatible (D-006) so a future account sync serves both
 * platforms from one personal model. Lives in the keyboard's own
 * sandbox (`noBackupFilesDir`, D-008); only individual words and word
 * pairs are stored, never sentences, and the rows stay event-shaped
 * for the Stage 8 cloud replay.
 */
class LearnedWords private constructor(private val db: SQLiteDatabase) {

    /**
     * A word must be confirmed this many times (typed or picked) before
     * it is suggested, so a typo made once or twice never surfaces.
     * User-adjustable from Stage 7 (fast 1 / normal 3 / conservative 5);
     * only the visibility threshold changes, never the learning.
     */
    var minVisibleUses = 3

    // MARK: - Learnability

    /**
     * Words worth learning: no digits, no email/URL fragments, and at
     * least two Lezgi letters — a digraph tail (ӏ, ь, ъ — derived from
     * the layout's callout table) extends the previous letter instead
     * of counting as its own, so a lone «цӏ» is rejected.
     */
    private fun isLearnable(word: String): Boolean {
        if (word.length > MAX_WORD_LENGTH) return false
        if (lezgiLetterCount(word) < 2) return false
        if (word.any { it.isDigit() }) return false
        if (word.any { it == '@' || it == '/' || it == '.' }) return false
        return true
    }

    // A low surrogate never counts: an astral character (an emoji in a
    // composed word) is one letter, mirroring iOS's grapheme-based count.
    private fun lezgiLetterCount(word: String): Int =
        word.count { it !in digraphTails && !it.isLowSurrogate() }

    // MARK: - Learning

    /**
     * Records a completed word and, when the preceding word of the same
     * sentence is known, the (previous, word) pair. `picked` marks a
     * word chosen from the suggestion bar — a stronger signal than
     * plain typing.
     */
    fun learn(word: String, previous: String?, picked: Boolean) {
        val w = word.lowercase()
        if (!isLearnable(w)) return
        val now = System.currentTimeMillis() / 1000
        db.execSQL(
            """
            INSERT INTO user_word(word, count, picked, last_used) VALUES(?1, ?2, ?3, ?4)
            ON CONFLICT(word) DO UPDATE SET
                count = count + ?2, picked = picked + ?3, last_used = ?4
            """,
            arrayOf(w, if (picked) 0 else 1, if (picked) 1 else 0, now),
        )
        val prev = previous?.lowercase()
        if (prev != null && isLearnable(prev)) {
            db.execSQL(
                """
                INSERT INTO user_bigram(prev, word, count, last_used) VALUES(?1, ?2, 1, ?3)
                ON CONFLICT(prev, word) DO UPDATE SET
                    count = count + 1, last_used = ?3
                """,
                arrayOf(prev, w, now),
            )
        }
        maintain()
    }

    /**
     * Counts learn events and runs the periodic cleanup: every 2000
     * events all counters halve (integer division — one-off words
     * vanish, stale habits fade) and the file is compacted; every 200
     * events the row caps are enforced.
     */
    private fun maintain() {
        db.execSQL("UPDATE meta SET value = value + 1 WHERE key = 'total_events'")
        val events = intValue("SELECT value FROM meta WHERE key = 'total_events'")
        if (events >= DECAY_AFTER_EVENTS) {
            db.execSQL("UPDATE user_word SET count = count / 2, picked = picked / 2")
            db.execSQL("DELETE FROM user_word WHERE count + picked = 0")
            db.execSQL("UPDATE user_bigram SET count = count / 2")
            db.execSQL("DELETE FROM user_bigram WHERE count = 0")
            db.execSQL("UPDATE meta SET value = 0 WHERE key = 'total_events'")
            prune()
            db.execSQL("VACUUM")
        } else if (events % PRUNE_CHECK_EVERY == 0L) {
            prune()
        }
    }

    /** Deletes the lowest-ranked rows once the hard caps are exceeded. */
    private fun prune() {
        val excessWords = intValue("SELECT COUNT(*) FROM user_word") - MAX_WORDS
        if (excessWords > 0) {
            db.execSQL(
                """
                DELETE FROM user_word WHERE word IN (
                    SELECT word FROM user_word
                    ORDER BY count + 3 * picked ASC, last_used ASC
                    LIMIT $excessWords
                )
                """,
            )
        }
        val excessPairs = intValue("SELECT COUNT(*) FROM user_bigram") - MAX_BIGRAMS
        if (excessPairs > 0) {
            db.execSQL(
                """
                DELETE FROM user_bigram WHERE rowid IN (
                    SELECT rowid FROM user_bigram
                    ORDER BY count ASC, last_used ASC
                    LIMIT $excessPairs
                )
                """,
            )
        }
    }

    // MARK: - Queries

    /**
     * Learned words matching the prefix, best first: picked words weigh
     * more than merely typed ones, recently used words get a boost, and
     * words that have followed `previous` before are boosted by the
     * pair counter — bigrams only affect ranking here, never the
     * candidate set.
     */
    fun suggestions(prefix: String, previous: String?, limit: Int = 3): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val pattern = p
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") + "%"
        val results = mutableListOf<String>()
        // Numeric values are interpolated: rawQuery binds args as TEXT,
        // and SQLite does not coerce text in numeric comparisons.
        db.rawQuery(
            """
            SELECT w.word FROM user_word w
            LEFT JOIN user_bigram b ON b.prev = ?2 AND b.word = w.word
            WHERE w.word LIKE ?1 ESCAPE '\' AND w.count + w.picked >= $minVisibleUses
                  AND LENGTH(w.word) >= 2
            ORDER BY (w.count + 3 * w.picked)
                     * (CASE WHEN w.last_used >= ${recencyCutoff()} THEN 2 ELSE 1 END)
                     * (1 + MIN(IFNULL(b.count, 0), 4)) DESC,
                     w.last_used DESC
            LIMIT $limit
            """,
            arrayOf(pattern, previous?.lowercase() ?: ""),
        ).use { cursor ->
            while (cursor.moveToNext()) results.add(cursor.getString(0))
        }
        return results
    }

    /**
     * Whether the word is learned AND visible — exactly the recognition
     * rule of `suggestions`, so a below-threshold word keeps counting
     * as unknown (and quoted) until it would actually surface.
     */
    fun isRecognized(word: String): Boolean {
        db.rawQuery(
            """
            SELECT 1 FROM user_word
            WHERE word = ?1 AND count + picked >= $minVisibleUses AND LENGTH(word) >= 2
            LIMIT 1
            """,
            arrayOf(word.lowercase()),
        ).use { cursor -> return cursor.moveToFirst() }
    }

    /**
     * Most likely follow-ups to `previous` from the bigram table, best
     * first — a pair must be seen at least twice to ever surface.
     */
    fun nextWords(previous: String, limit: Int = 3): List<String> {
        val prev = previous.lowercase()
        if (prev.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        db.rawQuery(
            """
            SELECT word FROM user_bigram
            WHERE prev = ?1 AND count >= $MIN_PAIR_USES AND LENGTH(word) >= 2
            ORDER BY count * (CASE WHEN last_used >= ${recencyCutoff()} THEN 2 ELSE 1 END) DESC,
                     last_used DESC
            LIMIT $limit
            """,
            arrayOf(prev),
        ).use { cursor ->
            while (cursor.moveToNext()) results.add(cursor.getString(0))
        }
        return results
    }

    // MARK: - Local quality metrics
    //
    // Plain counters in the meta table: never leave the device, never
    // affect ranking or learning, and survive a learned-data reset —
    // they describe the bar's quality history, not the dictionary.
    // Baseline first, ranking changes later, one at a time.

    enum class Metric(val key: String) {
        /**
         * Completed words that had at least one predictive candidate
         * (beyond the quoted literal) visible while being composed.
         * Counted once per completed word, not per suggestion refresh.
         */
        OPPORTUNITIES("m_opportunities"),

        /** Predictive suggestions accepted from the bar. */
        ACCEPTED("m_accepted"),

        /**
         * Words completed manually — terminator typed, host
         * send-clear, or the quoted literal tapped.
         */
        TYPED_MANUALLY("m_typed_manually"),

        /** Manual completions that had predictive candidates available. */
        IGNORED("m_ignored"),

        /**
         * Accepted suggestions the user went back into before
         * completing any other word (event-based, no timeout).
         */
        CORRECTED("m_corrected"),
    }

    fun bumpMetric(metric: Metric) {
        db.execSQL(
            """
            INSERT INTO meta(key, value) VALUES(?1, 1)
            ON CONFLICT(key) DO UPDATE SET value = value + 1
            """,
            arrayOf(metric.key),
        )
    }

    /** One line for the DEBUG startup log (logcat, tag "kb-metrics"). */
    fun metricsSummary(): String {
        fun value(metric: Metric): Long =
            intValue("SELECT value FROM meta WHERE key = '${metric.key}'")
        val accepted = value(Metric.ACCEPTED)
        val ignored = value(Metric.IGNORED)
        val rate = if (accepted + ignored > 0) {
            String.format(Locale.US, "%.1f%%", 100.0 * accepted / (accepted + ignored))
        } else {
            "n/a"
        }
        return "opportunities=${value(Metric.OPPORTUNITIES)} accepted=$accepted " +
            "typedManually=${value(Metric.TYPED_MANUALLY)} ignored=$ignored " +
            "corrected=${value(Metric.CORRECTED)} acceptance=$rate"
    }

    /**
     * Learned words for the settings dictionary list, ranked the same
     * way as suggestions (picked > typed, then recency) and filtered
     * by the same visibility rule (`count + picked >= minVisibleUses`,
     * ≥ 2 characters): below-threshold rows are internal frequency
     * signals, not yet user-facing vocabulary. Bundled-dictionary
     * membership is filtered by the caller, which owns that database
     * handle.
     */
    fun topWords(limit: Int): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            """
            SELECT word FROM user_word
            WHERE count + picked >= $minVisibleUses AND LENGTH(word) >= 2
            ORDER BY count + 3 * picked DESC, last_used DESC
            LIMIT $limit
            """,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) results.add(cursor.getString(0))
        }
        return results
    }

    /**
     * Wipes everything learned — words, pairs, and the event counter.
     * The bundled dictionary is untouched.
     */
    fun reset() {
        db.execSQL("DELETE FROM user_word")
        db.execSQL("DELETE FROM user_bigram")
        db.execSQL("UPDATE meta SET value = 0 WHERE key = 'total_events'")
        db.execSQL("VACUUM")
    }

    /**
     * Removes a single learned word and every pair that references it;
     * the bundled dictionary is never touched.
     */
    fun delete(word: String) {
        val w = word.lowercase()
        db.execSQL("DELETE FROM user_word WHERE word = ?1", arrayOf(w))
        db.execSQL("DELETE FROM user_bigram WHERE word = ?1 OR prev = ?1", arrayOf(w))
    }

    fun close() = db.close()

    // MARK: - Internals

    private fun recencyCutoff(): Long =
        System.currentTimeMillis() / 1000 - RECENCY_WINDOW_SECONDS

    private fun intValue(sql: String): Long {
        db.rawQuery(sql, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0
        }
    }

    /**
     * Deletes stored words that no longer pass `isLearnable` — SQL
     * cannot count Lezgi letters, so the check runs in Kotlin. Runs
     * once per `FILTERS_VERSION` bump.
     */
    private fun purgeUnlearnableWords() {
        val stale = mutableListOf<String>()
        db.rawQuery("SELECT word FROM user_word", null).use { cursor ->
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                if (!isLearnable(word)) stale.add(word)
            }
        }
        for (word in stale) delete(word)
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val FILTERS_VERSION = 1
        private const val MAX_WORD_LENGTH = 64
        private const val MIN_PAIR_USES = 2
        private const val RECENCY_WINDOW_SECONDS = 14L * 24 * 3600
        private const val MAX_WORDS = 5000
        private const val MAX_BIGRAMS = 10000
        private const val DECAY_AFTER_EVENTS = 2000L
        private const val PRUNE_CHECK_EVERY = 200L

        /**
         * Digraph tails from the layout's long-press alternates (ӏ, ь,
         * ъ): a tail extends the previous letter instead of counting as
         * its own.
         */
        private val digraphTails: Set<Char> =
            LezgiLayout.callouts.values.flatten()
                .filter { it.length == 2 }
                .map { it.last() }
                .toSet()

        fun open(context: Context): LearnedWords? = try {
            val file = File(context.noBackupFilesDir, "learned.sqlite")
            val db = SQLiteDatabase.openDatabase(
                file.path, null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            )
            db.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS meta(
                    key TEXT PRIMARY KEY,
                    value INTEGER NOT NULL
                )
                """,
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_word(
                    word TEXT PRIMARY KEY,
                    count INTEGER NOT NULL DEFAULT 0,
                    picked INTEGER NOT NULL DEFAULT 0,
                    last_used INTEGER NOT NULL
                )
                """,
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_bigram(
                    prev TEXT NOT NULL,
                    word TEXT NOT NULL,
                    count INTEGER NOT NULL DEFAULT 0,
                    last_used INTEGER NOT NULL,
                    PRIMARY KEY(prev, word)
                )
                """,
            )
            db.execSQL(
                "INSERT OR IGNORE INTO meta(key, value) VALUES('schema_version', $SCHEMA_VERSION)",
            )
            db.execSQL("INSERT OR IGNORE INTO meta(key, value) VALUES('total_events', 0)")
            val store = LearnedWords(db)
            // Records learned before the current filters existed are
            // purged once per filter-version bump; the bundled
            // dictionary is a separate read-only database.
            if (store.intValue("SELECT value FROM meta WHERE key = 'filters_version'") < FILTERS_VERSION) {
                store.purgeUnlearnableWords()
                db.execSQL(
                    "INSERT OR REPLACE INTO meta(key, value) VALUES('filters_version', $FILTERS_VERSION)",
                )
            }
            store
        } catch (_: Exception) {
            null
        }
    }
}
