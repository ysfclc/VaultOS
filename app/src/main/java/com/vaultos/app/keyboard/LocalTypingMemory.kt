package com.vaultos.app.keyboard

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Optional, on-device typing memory.
 *
 * Stores only individual words and adjacent-word counts. It never stores sentences,
 * clipboard content, field contents, or sends data off-device. Learning is opt-in.
 */
class LocalTypingMemory(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        val values = read(wordKey(code))
        values.put(normalized, maxOf(values.optInt(normalized, 0), PINNED_WORD_SCORE))
        prefs.edit().putString(wordKey(code), values.toString()).apply()
    }

    fun completions(language: String, prefix: String, limit: Int = 5): List<String> {
        val code = languageCode(language)
        val normalized = prefix.trim().lowercase(locale(code))
        if (normalized.length < 2 || limit <= 0) return emptyList()
        val values = read(wordKey(code))
        return values.keys()
            .asSequence()
            .filter { it.startsWith(normalized) && it != normalized }
            .sortedWith(compareByDescending<String> { values.optInt(it, 0) }.thenBy { it })
            .take(limit)
            .map { preserveCase(prefix, it, code) }
            .toList()
    }

    fun dictionaryWords(language: String): List<String> {
        val code = languageCode(language)
        val values = read(wordKey(code))
        return values.keys().asSequence().toList()
    }

    fun nextWords(language: String, previousWord: String, limit: Int = 3): List<String> {
        val code = languageCode(language)
        val previous = normalize(previousWord, code) ?: return emptyList()
        if (limit <= 0) return emptyList()
        val pairs = read(pairKey(code))
        return pairs.keys().asSequence()
            .filter { it.startsWith("$previous$SEPARATOR") }
            .sortedWith(compareByDescending<String> { pairs.optInt(it, 0) }.thenBy { it })
            .take(limit)
            .map { it.substringAfter(SEPARATOR) }
            .toList()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun increment(key: String, item: String, amount: Int) {
        val values = read(key)
        values.put(item, (values.optInt(item, 0) + amount).coerceAtMost(MAX_SCORE))
        prefs.edit().putString(key, values.toString()).apply()
    }

    private fun trim(key: String, maximum: Int) {
        val values = read(key)
        if (values.length() <= maximum) return
        val keep = values.keys().asSequence()
            .sortedByDescending { values.optInt(it, 0) }
            .take(maximum)
            .toList()
        val compact = JSONObject()
        keep.forEach { compact.put(it, values.optInt(it, 0)) }
        prefs.edit().putString(key, compact.toString()).apply()
    }

    private fun read(key: String): JSONObject =
        runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }

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
