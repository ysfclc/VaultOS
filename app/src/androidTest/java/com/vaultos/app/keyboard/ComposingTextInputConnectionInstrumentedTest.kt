package com.vaultos.app.keyboard

import android.content.Context
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the composing-text contract the VaultOS IME relies on.
 * These tests use Android's real BaseInputConnection and an editable text target.
 */
@RunWith(AndroidJUnit4::class)
class ComposingTextInputConnectionInstrumentedTest {

    @Test
    fun successiveComposingUpdatesReplaceTheActiveWord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val editor = EditText(context)
            val connection = BaseInputConnection(editor, true)

            assertTrue(connection.setComposingText("h", 1))
            assertTrue(connection.setComposingText("he", 1))
            assertTrue(connection.setComposingText("hello", 1))

            assertEquals("hello", editor.text.toString())
            assertEquals("hello", connection.getTextBeforeCursor(20, 0).toString())
            connection.finishComposingText()
            connection.commitText(" ", 1)
            assertEquals("hello ", editor.text.toString())
        }
    }

    @Test
    fun emptyComposingUpdateRemovesTheFinalCharacter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val editor = EditText(context)
            val connection = BaseInputConnection(editor, true)

            assertTrue(connection.setComposingText("a", 1))
            assertTrue(connection.setComposingText("", 1))
            connection.finishComposingText()

            assertEquals("", editor.text.toString())
        }
    }

    @Test
    fun finishingCompositionKeepsTypedWordBeforePunctuation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val editor = EditText(context)
            val connection = BaseInputConnection(editor, true)

            assertTrue(connection.setComposingText("VaultOS", 1))
            connection.finishComposingText()
            connection.commitText(",", 1)

            assertEquals("VaultOS,", editor.text.toString())
        }
    }
}
