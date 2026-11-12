package com.vaultos.app.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Locale

/**
 * Optional, on-device typing memory.
 *
 * Stores only individual words and adjacent-word counts. It never stores sentences,
 * clipboard content, field contents, or sends data off-device. Learning is opt-in.
 *
 * Parsed snapshots are cached for the lifetime of this instance. Preference changes
 * invalidate only the affected language snapshot, so suggestion rendering never
 * reparses the same JSON on every keystroke.
 */
class LocalTypingMemory(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val snapshotCache = mutableMapOf<String, Map<String, Int>>()
    private val wordListCache = mutableMapOf<String, List<String>>()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) invalidate(key)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun isLearningEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setLearningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun learn(language: String, word: String, previousWord: String = "") {
        if (!isLearningEnabled()) return
        val code = languageCode(language)
        val normalized = normalize(word, code) ?: return
        increment(wordKey(code), normalized, 1)
        val previous = normalize(previousWord, code)
        if (previous != null && previous != normalized) {
            increment(pairKey(code), "$previous$SEPARATOR$normalized", 1)
        }
        trim(wordKey(code), MAX_WORDS)
        trim(pairKey(code), MAX_PAIRS)
    }

    /** Explicitly save a word even when automatic learning is disabled. */
    fun addToDictionary(language: String, word: String) {
        val code = languageCode(language)
        val normalized = normalize(word, code) ?: return
        val values = read(wordKey(code)).toMutableMap()
        values[normalized] = maxOf(values[normalized] ?: 0, PINNED_WORD_SCORE)
        write(wordKey(code), values)
    }

    fun completions(language: String, prefix: String, limit: Int = 5): List<String> {
        val code = languageCode(language)
        val normalized = prefix.trim().lowercase(locale(code))
        if (normalized.length < 2 || limit <= 0) return emptyList()
        return read(wordKey(code)).entries.asSequence()
            .filter { it.key.startsWith(normalized) && it.key != normalized }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { preserveCase(prefix, it.key, code) }
            .toList()
    }

    fun dictionaryWords(language: String): List<String> {
        val code = languageCode(language)
        return wordListCache.getOrPut(code) { read(wordKey(code)).keys.toList() }
    }

    fun nextWords(language: String, previousWord: String, limit: Int = 3): List<String> {
        val code = languageCode(language)
        val previous = normalize(previousWord, code) ?: return emptyList()
        if (limit <= 0) return emptyList()
        val prefix = "$previous$SEPARATOR"
        return read(pairKey(code)).entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key.substringAfter(SEPARATOR) }
            .toList()
    }

    fun clear() {
        // Clearing learned data must not silently change the user's opt-in setting.
        val learningEnabled = isLearningEnabled()
        prefs.edit().clear().putBoolean(KEY_ENABLED, learningEnabled).apply()
        snapshotCache.clear()
        wordListCache.clear()
    }

    private fun increment(key: String, item: String, amount: Int) {
        val values = read(key).toMutableMap()
        values[item] = ((values[item] ?: 0) + amount).coerceAtMost(MAX_SCORE)
        write(key, values)
    }

    private fun trim(key: String, maximum: Int) {
        val values = read(key)
        if (values.size <= maximum) return
        write(key, values.entries.asSequence()
            .sortedByDescending { it.value }
            .take(maximum)
            .associate { it.key to it.value })
    }

    private fun read(key: String): Map<String, Int> =
        snapshotCache.getOrPut(key) {
            val json = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }
                .getOrElse { JSONObject() }
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val item = keys.next()
                    put(item, json.optInt(item, 0))
                }
            }
        }

    private fun write(key: String, values: Map<String, Int>) {
        val json = JSONObject()
        values.forEach { (item, score) -> json.put(item, score) }
        prefs.edit().putString(key, json.toString()).apply()
        // apply() notifies listeners synchronously for this process; seed the fresh
        // immutable snapshot after invalidation to avoid a second parse on next render.
        snapshotCache[key] = values.toMap()
        if (key.startsWith("words_")) {
            val code = key.removePrefix("words_")
            wordListCache[code] = values.keys.toList()
        }
    }

    private fun invalidate(key: String) {
        if (key.startsWith("words_") || key.startsWith("pairs_")) {
            snapshotCache.remove(key)
            if (key.startsWith("words_")) wordListCache.remove(key.removePrefix("words_"))
        }
    }

    private fun normalize(value: String, language: String): String? {
        val word = value.trim().lowercase(locale(language))
        if (word.length !in 2..32 || !word.any { it.isLetter() }) return null
        if (word.any { !it.isLetter() && it !in setOf('\'', '’', '-') }) return null
        return word
    }

    private fun preserveCase(prefix: String, word: String, language: String): String =
        if (prefix.firstOrNull()?.isUpperCase() == true) word.replaceFirstChar { it.titlecase(locale(language)) } else word

    private fun wordKey(code: String) = "words_$code"
    private fun pairKey(code: String) = "pairs_$code"

    private fun languageCode(value: String): String =
        value.lowercase(Locale.ROOT).takeIf { it in LANGUAGES } ?: "en"

    private fun locale(language: String): Locale = when (language) {
        "pl" -> Locale.forLanguageTag("pl-PL")
        "tr" -> Locale.forLanguageTag("tr-TR")
        else -> Locale.US
    }

    companion object {
        private const val PREFS = "vaultos_local_typing_memory"
        private const val KEY_ENABLED = "learning_enabled"
        private const val SEPARATOR = "\u001f"
        private const val MAX_WORDS = 500
        private const val MAX_PAIRS = 800
        private const val MAX_SCORE = 10_000
        private const val PINNED_WORD_SCORE = 1_000_000
        private val LANGUAGES = setOf("en", "pl", "tr")
    }
}
