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
    private const val MAX_CORRECTION_CANDIDATES = 120

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
            "recommendation", "recommendations", "should", "suggestion", "suggestions",
            "application", "apps", "available", "back", "between", "browser", "business",
            "call", "change", "check", "choose", "clear", "come", "conversation", "copy",
            "could", "currently", "details", "different", "download", "easy", "edit", "email",
            "everything", "exactly", "file", "files", "final", "folder", "following", "free",
            "full", "happy", "important", "information", "install", "instead", "job", "keep",
            "learn", "learning", "library", "link", "list", "long", "looking", "love", "meeting",
            "message", "minute", "mobile", "money", "move", "music", "myself", "near", "number",
            "once", "option", "people", "phone", "photo", "possible", "probably", "problem",
            "product", "project", "quick", "ready", "reason", "record", "save", "saved", "search",
            "send", "settings", "share", "simple", "start", "storage", "take", "thank", "thing",
            "together", "try", "understand", "update", "version", "watch", "week", "welcome",
            "whatever", "without", "write", "wrong"
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
            "wszystko", "wy", "zawsze", "że", "żeby",
            "adres", "aplikacja", "aplikacje", "będę", "będziemy", "bez", "chciałbym",
            "chciałabym", "chcę", "chyba", "dalej", "dlatego", "dostęp", "dostępny", "dużo",
            "działa", "działać", "firmie", "folder", "gotowe", "informacje", "instalacja",
            "jutro", "karta", "każdy", "klawiatura", "kolejny", "komputer", "koniec", "konto",
            "korzystać", "krótko", "możesz", "możliwe", "muszę", "myślę", "najlepiej",
            "naprawdę", "następnie", "nawet", "niestety", "nowy", "obecnie", "oczywiście",
            "odpowiedź", "ogólnie", "otworzyć", "pamiętać", "pewnie", "pierwszy", "plik",
            "pliki", "podoba", "podczas", "pomoc", "pomocy", "ponieważ", "potrzebuję",
            "problem", "przede", "przyszłość", "razem", "robić", "rozumiem", "rzeczy",
            "sprawdzić", "strona", "super", "szczególnie", "także", "twój", "twoja", "twoje",
            "udało", "używać", "ważne", "więcej", "właśnie", "wygląda", "wtedy", "zrobić",
            "zobaczyć", "znowu", "został"
        ),
        "tr" to listOf(
            "bir", "bu", "ben", "sen", "ve", "çok", "daha", "değil", "için", "ile",
            "ama", "artık", "aslında", "acaba", "bana", "benim", "biraz", "bugün",
            "bunu", "böyle", "çünkü", "diye", "gibi", "güzel", "gün", "hangi", "her",
            "iyi", "nasıl", "ne", "neden", "nerede", "olabilir", "oldu", "olmak",
            "olarak", "onun", "sonra", "şey", "şimdi", "tamam", "teşekkür", "teşekkürler",
            "tüm", "var", "veya", "ya", "yani", "yap", "yapmak", "yeni", "yine", "yok",
            "zaman", "zaten", "merhaba", "merak", "müsait", "lütfen", "görüşürüz",
            "acil", "açık", "adres", "arkadaş", "başka", "başladı", "bekliyorum", "belki",
            "bence", "beraber", "birlikte", "bulmak", "burada", "büyük", "çalışıyor",
            "çalışmak", "doğru", "düşünüyorum", "elbette", "farklı", "fakat", "fiyat",
            "gerekli", "gerçekten", "gönder", "hafta", "hala", "hemen", "herhangi", "herkes",
            "hiç", "ihtiyaç", "iletişim", "iş", "işte", "kadar", "kaldı", "kendi",
            "kesinlikle", "kolay", "konu", "kullanım", "kullanmak", "lazım", "mesaj",
            "mümkün", "mutlu", "nasılsın", "olacak", "olur", "önemli", "özellikle",
            "paylaş", "program", "proje", "şimdilik", "şöyle", "tamamen", "tekrar",
            "uygun", "yardım", "yapabilir", "yeterli"
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
            "hello" to listOf("there", "how", "again"),
            "i'm" to listOf("not", "sure", "going"),
            "are" to listOf("you", "we", "they"),
            "could" to listOf("you", "we", "be"),
            "do" to listOf("you", "we", "that"),
            "does" to listOf("it", "that", "this"),
            "if" to listOf("you", "we", "it"),
            "just" to listOf("want", "need", "think"),
            "know" to listOf("what", "that", "you"),
            "like" to listOf("to", "it", "this"),
            "make" to listOf("it", "sure", "sense"),
            "need" to listOf("to", "a", "some"),
            "please" to listOf("let", "send", "check"),
            "should" to listOf("be", "we", "i"),
            "there" to listOf("is", "are", "was"),
            "want" to listOf("to", "you", "me"),
            "when" to listOf("you", "we", "is"),
            "where" to listOf("is", "are", "you"),
            "with" to listOf("the", "you", "me")
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
            "co" to listOf("jest", "robisz", "myślisz"),
            "będę" to listOf("w", "miał", "mogła"),
            "chcę" to listOf("się", "to", "zrobić"),
            "dzisiaj" to listOf("mam", "jest", "będzie"),
            "jest" to listOf("to", "bardzo", "dobrze"),
            "jestem" to listOf("w", "tu", "pewny"),
            "możesz" to listOf("mi", "to", "sprawdzić"),
            "muszę" to listOf("to", "już", "iść"),
            "potrzebuję" to listOf("pomocy", "tego", "więcej"),
            "proszę" to listOf("o", "sprawdzić", "daj"),
            "właśnie" to listOf("tak", "teraz", "to"),
            "zrobić" to listOf("to", "jeszcze", "tak")
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
            "tamam" to listOf("olur", "anlaştık", "teşekkürler"),
            "bana" to listOf("yardım", "göre", "bir"),
            "bir" to listOf("şey", "daha", "süre"),
            "bugün" to listOf("çok", "müsait", "ne"),
            "daha" to listOf("iyi", "fazla", "sonra"),
            "gerçekten" to listOf("çok", "iyi", "değil"),
            "istiyorum" to listOf("ama", "ve", "sadece"),
            "lütfen" to listOf("bana", "bir", "yardım"),
            "şimdi" to listOf("ne", "çok", "tamam"),
            "sonra" to listOf("görüşürüz", "konuşuruz", "yaparız"),
            "yardım" to listOf("eder", "lazım", "istiyorum")
        )
    )

    private val contextNextWords = mapOf(
        "en" to mapOf(
            "i\u001fam" to listOf("not", "going", "sure"),
            "thank\u001fyou" to listOf("for", "so", "very"),
            "i\u001fthink" to listOf("that", "it", "we"),
            "looking\u001fforward" to listOf("to"),
            "i\u001fneed" to listOf("to", "help", "a"),
            "i\u001fwant" to listOf("to", "you", "a"),
            "i\u001fwill" to listOf("be", "let", "try"),
            "we\u001fneed" to listOf("to", "a", "more"),
            "you\u001fcan" to listOf("also", "use", "do"),
            "can\u001fyou" to listOf("please", "help", "check"),
            "do\u001fyou" to listOf("think", "have", "know"),
            "how\u001fare" to listOf("you", "things", "we"),
            "let\u001fme" to listOf("know", "check", "see"),
            "there\u001fis" to listOf("a", "no", "one"),
            "going\u001fto" to listOf("be", "do", "the")
        ),
        "pl" to mapOf(
            "nie\u001fwiem" to listOf("czy", "jak", "co"),
            "w\u001ftym" to listOf("momencie", "czasie", "przypadku"),
            "dzień\u001fdobry" to listOf("wszystkim", "bardzo"),
            "to\u001fjest" to listOf("bardzo", "ważne", "dobrze"),
            "czy\u001fmożesz" to listOf("mi", "to", "sprawdzić"),
            "jak\u001fsię" to listOf("masz", "czujesz", "udało"),
            "mam\u001fpytanie" to listOf("dotyczące", "o", "w"),
            "na\u001fpewno" to listOf("tak", "nie", "będę"),
            "dzięki\u001fza" to listOf("pomoc", "informacje", "odpowiedź"),
            "w\u001ftej" to listOf("sprawie", "chwili", "kwestii"),
            "co\u001fsię" to listOf("stało", "dzieje", "zmieniło")
        ),
        "tr" to mapOf(
            "ben\u001fde" to listOf("öyle", "bugün", "istiyorum"),
            "çok\u001fteşekkür" to listOf("ederim", "ler"),
            "bir\u001fşey" to listOf("söylemek", "var", "diyeceğim"),
            "ben\u001fistiyorum" to listOf("ama", "ve", "sadece"),
            "çok\u001fiyi" to listOf("oldu", "görünüyor", "hissettiriyor"),
            "ne\u001fzaman" to listOf("geliyorsun", "başlıyor", "uygun"),
            "şu\u001fanda" to listOf("müsaitim", "meşgulüm", "buradayım"),
            "lütfen\u001fbana" to listOf("yardım", "haber", "söyle"),
            "yardım\u001feder" to listOf("misin", "mi", "diye"),
            "teşekkür\u001federim" to listOf("çok", "yardımın", "desteğin"),
            "daha\u001fsonra" to listOf("görüşürüz", "konuşuruz", "yazarım")
        )
    )

    fun nextWordSuggestions(
        language: String,
        previousPreviousWord: String,
        previousWord: String,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()
        val code = languageCode(language)
        val wordLocale = locale(code)
        val previousPrevious = previousPreviousWord.trim().lowercase(wordLocale)
        val previous = previousWord.trim().lowercase(wordLocale)
        if (previousPrevious.isNotBlank() && previous.isNotBlank()) {
            val contextual = contextNextWords[code]?.get("$previousPrevious\u001f$previous").orEmpty()
            if (contextual.isNotEmpty()) return contextual.distinct().take(limit)
        }
        return nextWordSuggestions(code, previous, limit)
    }

    fun completePrefix(language: String, prefix: String, limit: Int = 3, additionalWords: List<String> = emptyList()): List<String> {
        val normalized = prefix.trim().lowercase(locale(language))
        if (normalized.length < 2 || limit <= 0) return emptyList()
        return (additionalWords + words[languageCode(language)].orEmpty())
            .asSequence()
            .filter { it.startsWith(normalized) && !it.equals(normalized, ignoreCase = true) }
            .distinct()
            .take(limit)
            .toList()
    }

    /**
     * Combines candidate sources without allowing the currently typed token to crowd
     * every alternative out of the strip. Source order is intentional: platform
     * typo candidates, frequently used personal words, local typo matches, then
     * static offline prefix completions.
     */
    fun rankTypedCandidates(
        language: String,
        typed: String,
        spellCheckerCandidates: List<String> = emptyList(),
        personalCompletions: List<String> = emptyList(),
        localCorrections: List<String> = emptyList(),
        builtInCompletions: List<String> = emptyList(),
        limit: Int = 2
    ): List<String> {
        if (limit <= 0) return emptyList()
        val wordLocale = locale(language)
        val normalized = typed.trim().lowercase(wordLocale)
        return sequenceOf(
            spellCheckerCandidates,
            personalCompletions,
            localCorrections,
            builtInCompletions
        ).flatten()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.lowercase(wordLocale).equals(normalized, ignoreCase = false) }
            .distinctBy { it.lowercase(wordLocale) }
            .take(limit)
            .toList()
    }

    fun nearbyCorrections(language: String, input: String, limit: Int = 2, additionalWords: List<String> = emptyList()): List<String> {
        val code = languageCode(language)
        val wordLocale = locale(code)
        val normalized = input.trim().lowercase(wordLocale)
        if (normalized.length < 3 || limit <= 0) return emptyList()

        val maxDistance = if (normalized.length <= 6) 1 else 2
        val lengthCompatible = (additionalWords + words[code].orEmpty()).asSequence()
            .distinct()
            .filter { candidate ->
                candidate.length in (normalized.length - maxDistance)..(normalized.length + maxDistance)
            }
            .toList()
        // Exact known words must never enter the correction path, even if they
        // fall outside the bounded candidate pool used for typo ranking.
        if (lengthCompatible.any { it.equals(normalized, ignoreCase = true) }) return emptyList()

        // Evaluate likely same-initial candidates first, but retain a bounded
        // fallback pool for genuine first-letter mistakes.
        val vocabulary = lengthCompatible
            .sortedBy { if (it.firstOrNull()?.lowercaseChar() == normalized.firstOrNull()) 0 else 1 }
            .take(MAX_CORRECTION_CANDIDATES)

        return vocabulary.asSequence()
            .map { candidate -> candidate to boundedDamerauLevenshtein(normalized, candidate.lowercase(wordLocale), maxDistance) }
            .filter { (_, distance) -> distance in 1..maxDistance }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** O(min(n,m)) memory and early exit when a row cannot meet the distance bound. */
    private fun boundedDamerauLevenshtein(left: String, right: String, maximum: Int): Int {
        if (kotlin.math.abs(left.length - right.length) > maximum) return maximum + 1
        var previousPrevious = IntArray(right.length + 1) { it }
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in 1..left.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                var value = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost
                )
                if (i > 1 && j > 1 &&
                    left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]
                ) {
                    value = minOf(value, previousPrevious[j - 2] + 1)
                }
                current[j] = value
                rowMinimum = minOf(rowMinimum, value)
            }
            if (rowMinimum > maximum) return maximum + 1
            previousPrevious = previous
            previous = current
            current = IntArray(right.length + 1)
        }
        return previous[right.length].takeIf { it <= maximum } ?: maximum + 1
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
