package com.vaultos.app.keyboard

import java.util.Locale

/**
 * Small, deterministic on-device candidate helper.
 *
 * Android's SpellCheckerSession remains responsible for spelling corrections. This
 * lexicon adds predictable prefix completions and short next-word suggestions when
 * the platform dictionary has no useful result. It does not send typed text anywhere.
 */
object KeyboardSuggestionEngine {
    private val words = mapOf(
        "en" to listOf(
            "the", "this", "that", "there", "their", "then", "they", "them", "these",
            "think", "thanks", "thank", "you", "your", "you're", "with", "what", "when",
            "where", "which", "who", "will", "would", "can", "could", "should", "have",
            "has", "had", "hello", "help", "helpful", "here", "how", "however", "good",
            "great", "get", "going", "go", "for", "from", "first", "find", "feel", "about",
            "after", "again", "also", "always", "because", "before", "been", "being",
            "but", "by", "day", "did", "do", "does", "done", "even", "every", "few",
            "give", "going", "i", "if", "in", "into", "is", "it", "it's", "just", "know",
            "like", "make", "me", "more", "much", "my", "need", "new", "no", "not", "now",
            "of", "on", "one", "only", "or", "our", "out", "please", "really", "right",
            "see", "so", "some", "something", "than", "time", "to", "today", "too", "up",
            "use", "usable", "very", "want", "was", "way", "we", "well", "were", "work", "yes",
            "actually", "better", "build", "elegant", "experience", "keyboard", "maybe",
            "recommendation", "recommendations", "should", "suggestion", "suggestions"
        ),
        "pl" to listOf(
            "nie", "tak", "to", "jest", "że", "się", "na", "do", "i", "w", "z", "za",
            "dla", "ale", "albo", "aby", "być", "bardzo", "będzie", "bo", "było", "był",
            "była", "chcę", "czy", "czemu", "często", "dobrze", "dlaczego", "dzięki",
            "dzień", "dzisiaj", "jeszcze", "już", "jak", "jaki", "jaka", "jakie", "jego",
            "jej", "kiedy", "który", "która", "które", "kto", "mam", "ma", "może", "mnie",
            "mój", "moja", "moje", "my", "nad", "nas", "nasz", "nasza", "nasze", "nic",
            "od", "oraz", "po", "pod", "proszę", "przez", "przy", "sam", "są", "ten",
            "ta", "te", "tego", "tej", "teraz", "też", "tylko", "trzeba", "tutaj",
            "wszystko", "wy", "zawsze", "że", "żeby"
        ),
        "tr" to listOf(
            "bir", "bu", "ben", "sen", "ve", "çok", "daha", "değil", "için", "ile",
            "ama", "artık", "aslında", "acaba", "bana", "benim", "biraz", "bugün",
            "bunu", "böyle", "çünkü", "diye", "gibi", "güzel", "gün", "hangi", "her",
            "iyi", "nasıl", "ne", "neden", "nerede", "olabilir", "oldu", "olmak",
            "olarak", "onun", "sonra", "şey", "şimdi", "tamam", "teşekkür", "teşekkürler",
            "tüm", "var", "veya", "ya", "yani", "yap", "yapmak", "yeni", "yine", "yok",
            "zaman", "zaten", "merhaba", "merak", "müsait", "lütfen", "görüşürüz"
        )
    )

    private val nextWords = mapOf(
        "en" to mapOf(
            "i" to listOf("am", "have", "think"),
            "you" to listOf("are", "can", "have"),
            "we" to listOf("can", "are", "need"),
            "they" to listOf("are", "have", "will"),
            "it" to listOf("is", "was", "will"),
            "this" to listOf("is", "was", "one"),
            "that" to listOf("is", "was", "you"),
            "what" to listOf("is", "do", "about"),
            "how" to listOf("are", "do", "is"),
            "good" to listOf("morning", "idea", "to"),
            "thanks" to listOf("for", "you", "a"),
            "thank" to listOf("you"),
            "looking" to listOf("forward", "for", "good"),
            "for" to listOf("the", "you", "me"),
            "have" to listOf("a", "to", "been"),
            "can" to listOf("you", "i", "we"),
            "will" to listOf("be", "do", "have"),
            "not" to listOf("sure", "a", "the"),
            "see" to listOf("you", "it", "what"),
            "hello" to listOf("there", "how", "again")
        ),
        "pl" to mapOf(
            "jak" to listOf("się", "jest", "to"),
            "nie" to listOf("wiem", "ma", "mam"),
            "to" to listOf("jest", "było", "będzie"),
            "czy" to listOf("możesz", "jest", "to"),
            "bardzo" to listOf("dobrze", "mi", "dziękuję"),
            "dzień" to listOf("dobry", "dzisiaj"),
            "dobry" to listOf("pomysł", "dzień", "wieczór"),
            "dzięki" to listOf("za", "wielkie", "bardzo"),
            "mam" to listOf("pytanie", "nadzieję", "to"),
            "w" to listOf("tym", "pracy", "domu"),
            "na" to listOf("pewno", "razie", "przykład"),
            "co" to listOf("jest", "robisz", "myślisz")
        ),
        "tr" to mapOf(
            "ben" to listOf("de", "bugün", "sana"),
            "sen" to listOf("nasılsın", "de", "ne"),
            "bu" to listOf("çok", "bir", "da"),
            "çok" to listOf("iyi", "güzel", "teşekkürler"),
            "nasıl" to listOf("bir", "olur", "yani"),
            "ne" to listOf("zaman", "kadar", "demek"),
            "merhaba" to listOf("nasılsın", "nasıl", "iyi"),
            "iyi" to listOf("günler", "olur", "mi"),
            "teşekkürler" to listOf("çok", "sana", "ederim"),
            "için" to listOf("teşekkürler", "çok", "önemli"),
            "tamam" to listOf("olur", "anlaştık", "teşekkürler")
        )
    )

    fun completePrefix(language: String, prefix: String, limit: Int = 3): List<String> {
        val normalized = prefix.trim().lowercase(locale(language))
        if (normalized.length < 2 || limit <= 0) return emptyList()
        return words[languageCode(language)].orEmpty()
            .asSequence()
            .filter { it.startsWith(normalized) && !it.equals(normalized, ignoreCase = true) }
            .distinct()
            .take(limit)
            .toList()
    }

    fun nextWordSuggestions(language: String, previousWord: String, limit: Int = 3): List<String> {
        if (limit <= 0) return emptyList()
        val normalized = previousWord.trim().lowercase(locale(language))
        if (normalized.isBlank()) return emptyList()
        return nextWords[languageCode(language)]?.get(normalized).orEmpty()
            .distinct()
            .take(limit)
    }

    private fun languageCode(value: String): String = value.lowercase(Locale.ROOT).takeIf { it in words } ?: "en"

    private fun locale(language: String): Locale = when (languageCode(language)) {
        "pl" -> Locale.forLanguageTag("pl-PL")
        "tr" -> Locale.forLanguageTag("tr-TR")
        else -> Locale.US
    }
}
