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
    fun unknownContextDoesNotInventSuggestions() {
        assertTrue(KeyboardSuggestionEngine.nextWordSuggestions("en", "qzxv").isEmpty())
    }

    @Test
    fun shortPrefixesAreNotNoisy() {
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "a").isEmpty())
        assertTrue(KeyboardSuggestionEngine.completePrefix("en", "hello").isEmpty())
    }
}
