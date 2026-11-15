package com.vaultos.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPayloadIntegrityTest {
    @Test
    fun acceptsExactUtf8PayloadIncludingMultilingualAndEmojiText() {
        val text = "Zażółć gęślą jaźń — Türkçe: ığüşöç — 👋🌍"
        val bytes = text.toByteArray(Charsets.UTF_8)

        assertTrue(ClipboardPayloadIntegrity.matches(text, bytes.size.toLong(), ClipboardPayloadIntegrity.sha256(bytes)))
    }

    @Test
    fun rejectsPayloadWithSameByteCountButDifferentContent() {
        val original = "café"
        val changed = "cafè"
        val originalBytes = original.toByteArray(Charsets.UTF_8)
        val changedBytes = changed.toByteArray(Charsets.UTF_8)

        assertTrue(originalBytes.size == changedBytes.size)
        assertFalse(ClipboardPayloadIntegrity.matches(
            changed,
            originalBytes.size.toLong(),
            ClipboardPayloadIntegrity.sha256(originalBytes)
        ))
    }

    @Test
    fun rejectsTruncatedOrExtendedPayloadByUtf8ByteCount() {
        val text = "A🙂B"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = ClipboardPayloadIntegrity.sha256(bytes)

        assertFalse(ClipboardPayloadIntegrity.matches(text, bytes.size.toLong() - 1, hash))
        assertFalse(ClipboardPayloadIntegrity.matches(text, bytes.size.toLong() + 1, hash))
    }

    @Test
    fun permitsLegacyEntriesWithoutHashOnlyWhenByteCountMatches() {
        val text = "legacy clipboard text"
        val byteCount = text.toByteArray(Charsets.UTF_8).size.toLong()

        assertTrue(ClipboardPayloadIntegrity.matches(text, byteCount, null))
        assertFalse(ClipboardPayloadIntegrity.matches(text, byteCount - 1, null))
    }

    @Test
    fun acceptsEmptyPayloadOnlyWhenRecordedByteCountIsZero() {
        assertTrue(ClipboardPayloadIntegrity.matches("", 0L, ClipboardPayloadIntegrity.sha256(byteArrayOf())))
        assertFalse(ClipboardPayloadIntegrity.matches("", 1L, null))
    }
}
