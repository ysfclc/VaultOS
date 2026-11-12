package com.vaultos.app.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises LocalTypingMemory against Android's real SharedPreferences implementation.
 * The test app has its own application sandbox; cleanup prevents test-order coupling.
 */
@RunWith(AndroidJUnit4::class)
class LocalTypingMemoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("vaultos_local_typing_memory", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun learningIsOptInAndDisabledLearningDoesNotWriteWordsOrContexts() {
        val memory = LocalTypingMemory(context)

        assertFalse(memory.isLearningEnabled())
        memory.learn("en", "tomorrow", "want", "I")

        assertTrue(memory.dictionaryWords("en").isEmpty())
        assertTrue(memory.nextWords("en", "I", "want").isEmpty())
        assertFalse(preferences.contains("words_en"))
        assertFalse(preferences.contains("pairs_en"))
        assertFalse(preferences.contains("trigrams_en"))
    }

    @Test
    fun enabledLearningPersistsWordsBigramsAndTrigramsAcrossInstances() {
        val memory = LocalTypingMemory(context)
        memory.setLearningEnabled(true)
        memory.learn("en", "tomorrow", "want", "I")

        val reopened = LocalTypingMemory(context)
        assertTrue(reopened.isLearningEnabled())
        assertTrue(reopened.dictionaryWords("en").contains("tomorrow"))
        assertEquals(listOf("tomorrow"), reopened.nextWords("en", "want"))
        assertEquals(listOf("tomorrow"), reopened.nextWords("en", "I", "want"))
        assertTrue(preferences.contains("words_en"))
        assertTrue(preferences.contains("pairs_en"))
        assertTrue(preferences.contains("trigrams_en"))
    }

    @Test
    fun optingOutPausesNewLearningWithoutDeletingExistingData() {
        val memory = LocalTypingMemory(context)
        memory.setLearningEnabled(true)
        memory.learn("en", "tomorrow", "want", "I")

        memory.setLearningEnabled(false)
        memory.learn("en", "tonight", "leave", "we")

        assertFalse(memory.isLearningEnabled())
        assertTrue(memory.dictionaryWords("en").contains("tomorrow"))
        assertFalse(memory.dictionaryWords("en").contains("tonight"))
        assertEquals(listOf("tomorrow"), memory.nextWords("en", "I", "want"))
    }

    @Test
    fun clearRemovesAllPersonalModelsButPreservesOptInChoice() {
        val memory = LocalTypingMemory(context)
        memory.setLearningEnabled(true)
        memory.learn("en", "tomorrow", "want", "I")
        memory.learn("pl", "jutro", "chcę", "ja")
        memory.learn("tr", "yarın", "istiyorum", "ben")

        memory.clear()

        assertTrue(memory.isLearningEnabled())
        assertTrue(memory.dictionaryWords("en").isEmpty())
        assertTrue(memory.dictionaryWords("pl").isEmpty())
        assertTrue(memory.dictionaryWords("tr").isEmpty())
        assertTrue(memory.nextWords("en", "I", "want").isEmpty())
        assertFalse(preferences.contains("words_en"))
        assertFalse(preferences.contains("pairs_en"))
        assertFalse(preferences.contains("trigrams_en"))
        assertFalse(preferences.contains("words_pl"))
        assertFalse(preferences.contains("words_tr"))
    }

    @Test
    fun existingV1WordAndBigramJsonRemainsReadableWithoutTrigramData() {
        val oldWords = JSONObject().put("tomorrow", 7).put("today", 3)
        val oldPairs = JSONObject().put("want\u001ftomorrow", 9).put("want\u001ftoday", 2)
        preferences.edit()
            .putString("words_en", oldWords.toString())
            .putString("pairs_en", oldPairs.toString())
            .putBoolean("learning_enabled", false)
            .commit()

        val migrated = LocalTypingMemory(context)

        assertFalse(migrated.isLearningEnabled())
        assertEquals(listOf("tomorrow", "today"), migrated.nextWords("en", "want", 2))
        assertEquals(listOf("tomorrow", "today"), migrated.nextWords("en", "I", "want", 2))
        assertTrue(migrated.dictionaryWords("en").containsAll(listOf("tomorrow", "today")))
        assertFalse(preferences.contains("trigrams_en"))
    }

    @Test
    fun languageModelsRemainSeparated() {
        val memory = LocalTypingMemory(context)
        memory.setLearningEnabled(true)
        memory.learn("en", "tomorrow", "want", "I")
        memory.learn("tr", "yarın", "istiyorum", "ben")

        assertEquals(listOf("tomorrow"), memory.nextWords("en", "I", "want"))
        assertEquals(listOf("yarın"), memory.nextWords("tr", "ben", "istiyorum"))
        assertFalse(memory.dictionaryWords("en").contains("yarın"))
        assertFalse(memory.dictionaryWords("tr").contains("tomorrow"))
    }
}
