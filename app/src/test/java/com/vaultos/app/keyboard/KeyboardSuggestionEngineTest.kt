package com.vaultos.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSuggestionEngineTest {
    @Test
    fun englishPrefixReturnsUsefulCompletions() {
        assertEquals(listOf("hello", "help", "helpful"), KeyboardSuggestionEngine.completePrefix("en", "hel"))
    }

    @Test
    fun longerEnglishPrefixFindsRecommendationVocabulary() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "reco").contains("recommendations"))
    }

    @Test
    fun polishPrefixKeepsDiacritics() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("pl", "dzi").contains("dzień"))
        assertTrue(KeyboardSuggestionEngine.completePrefix("pl", "dzi").contains("dzisiaj"))
    }

    @Test
    fun turkishPrefixUsesTurkishWords() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("tr", "merh").contains("merhaba"))
    }

    @Test
    fun personalWordsJoinPrefixCompletions() {
        assertEquals(
            listOf("yusufword"),
            KeyboardSuggestionEngine.completePrefix("en", "yus", additionalWords = listOf("yusufword"))
        )
    }

    @Test
    fun personalWordsCanCorrectMistypes() {
        assertTrue(
            KeyboardSuggestionEngine.nearbyCorrections("en", "yusf", additionalWords = listOf("yusuf")).contains("yusuf")
        )
    }

    @Test
    fun localCorrectionsCatchCommonMistypes() {
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "helo").contains("hello"))
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "teh").contains("the"))
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "recommendatins").contains("recommendations"))
    }

    @Test
    fun knownWordsAreNotReplacedByNearbyCorrections() {
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "hello").isEmpty())
    }

    @Test
    fun contextSuggestionsAreLanguageSpecific() {
        assertEquals(listOf("am", "have", "think"), KeyboardSuggestionEngine.nextWordSuggestions("en", "I"))
        assertEquals(listOf("wiem", "ma", "mam"), KeyboardSuggestionEngine.nextWordSuggestions("pl", "NIE"))
        assertEquals(listOf("nasılsın", "nasıl", "iyi"), KeyboardSuggestionEngine.nextWordSuggestions("tr", "MERHABA"))
    }

    @Test
    fun expandedVocabularyCoversEverydayProductAndDeviceTerms() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "app").contains("application"))
        assertTrue(KeyboardSuggestionEngine.completePrefix("pl", "pot").contains("potrzebuję"))
        assertTrue(KeyboardSuggestionEngine.completePrefix("tr", "ger").contains("gerçekten"))
    }

    @Test
    fun expandedContextUsesTheCurrentPreviousWord() {
        assertEquals(listOf("not", "sure", "going"), KeyboardSuggestionEngine.nextWordSuggestions("en", "I'm"))
        assertEquals(listOf("pomocy", "tego", "więcej"), KeyboardSuggestionEngine.nextWordSuggestions("pl", "potrzebuję"))
        assertEquals(listOf("yardım", "göre", "bir"), KeyboardSuggestionEngine.nextWordSuggestions("tr", "BANA"))
    }

    @Test
    fun unknownContextDoesNotInventSuggestions() {
        assertTrue(KeyboardSuggestionEngine.nextWordSuggestions("en", "qzxv").isEmpty())
    }

    @Test
    fun shortPrefixesAreNotNoisy() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "a").isEmpty())
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "hello").isEmpty())
    }

    @Test
    fun twoWordContextPredictionsAreLanguageSpecific() {
        assertEquals(listOf("not", "going", "sure"), KeyboardSuggestionEngine.nextWordSuggestions("en", "I", "AM"))
        assertEquals(listOf("czy", "jak", "co"), KeyboardSuggestionEngine.nextWordSuggestions("pl", "nie", "wiem"))
        assertEquals(listOf("öyle", "bugün", "istiyorum"), KeyboardSuggestionEngine.nextWordSuggestions("tr", "BEN", "DE"))
    }

    @Test
    fun unknownTwoWordContextFallsBackToSingleWordPrediction() {
        assertEquals(listOf("am", "have", "think"), KeyboardSuggestionEngine.nextWordSuggestions("en", "unknown", "I"))
    }

    @Test
    fun correctionCandidatesRespectRequestedLimit() {
        assertEquals(1, KeyboardSuggestionEngine.nearbyCorrections("en", "helo", limit = 1).size)
    }

    @Test
    fun correctionEngineHandlesAdjacentTransposition() {
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "teh").contains("the"))
    }

    @Test
    fun unrelatedInputDoesNotProduceCorrectionNoise() {
        assertTrue(KeyboardSuggestionEngine.nearbyCorrections("en", "qzxvplmok").isEmpty())
    }

    @Test
    fun exactTwoWordContextLeadsAndFillsFromAdjacentWordEvidence() {
        assertEquals(
            listOf("tomorrow", "later", "today"),
            ContextualPredictionRanker.rank(
                trigramCandidates = listOf("tomorrow", "later", "tomorrow"),
                bigramCandidates = listOf("today", "soon")
            )
        )
    }

    @Test
    fun typedCandidatesCombineSourcesAndDeduplicateWithoutRepeatingTheTypedWord() {
        assertEquals(
            listOf("hello", "heloworld", "heloo", "help"),
            KeyboardSuggestionEngine.rankTypedCandidates(
                language = "en",
                typed = "helo",
                spellCheckerCandidates = listOf("hello"),
                personalCompletions = listOf("heloworld", "HELLO"),
                localCorrections = listOf("heloo", "hello"),
                builtInCompletions = listOf("help", "heloworld"),
                limit = 4
            )
        )
    }

    @Test
    fun typedCandidateRankingHonorsLimitAndRejectsBlankCandidates() {
        assertEquals(
            listOf("świat", "świetnie"),
            KeyboardSuggestionEngine.rankTypedCandidates(
                language = "pl",
                typed = "św",
                spellCheckerCandidates = listOf("", "świat", "śW"),
                personalCompletions = listOf("świetnie"),
                limit = 2
            )
        )
        assertTrue(KeyboardSuggestionEngine.rankTypedCandidates("en", "hello", limit = 0).isEmpty())
    }

    @Test
    fun expandedTwoWordContextsCoverCommonDailyPhrases() {
        assertEquals(listOf("to", "help", "a"), KeyboardSuggestionEngine.nextWordSuggestions("en", "I", "need"))
        assertEquals(listOf("mi", "to", "sprawdzić"), KeyboardSuggestionEngine.nextWordSuggestions("pl", "czy", "możesz"))
        assertEquals(listOf("ama", "ve", "sadece"), KeyboardSuggestionEngine.nextWordSuggestions("tr", "ben", "istiyorum"))
    }

    @Test
    fun unknownTwoWordContextFallsBackToV1BigramCandidates() {
        assertEquals(
            listOf("today", "soon"),
            ContextualPredictionRanker.rank(
                trigramCandidates = emptyList(),
                bigramCandidates = listOf("today", "soon")
            )
        )
    }

    @Test
    fun contextualPredictionHonorsCandidateLimit() {
        assertEquals(
            listOf("one", "two"),
            ContextualPredictionRanker.rank(listOf("one", "two", "three"), emptyList(), limit = 2)
        )
        assertTrue(ContextualPredictionRanker.rank(listOf("one"), emptyList(), limit = 0).isEmpty())
    }
}
