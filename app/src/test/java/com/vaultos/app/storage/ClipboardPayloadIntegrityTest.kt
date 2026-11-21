package com.vaultos.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ClipboardPayloadIntegrityTest {
    private val payload = "Hello Yusuf 👋\nTürkçe: ğüşiöç · Polski: ąćęłńóśźż · 日本語"

    @Test
    fun acceptsExactUtf8PayloadIncludingMultibyteAndSupplementaryCharacters() {
        val bytes = payload.toByteArray(Charsets.UTF_8)

        assertTrue(
            ClipboardPayloadIntegrity.matches(
                text = payload,
                expectedHash = sha256(bytes),
                expectedByteCount = bytes.size.toLong(),
                expectedCharacterCount = payload.codePointCount(0, payload.length)
            )
        )
    }

    @Test
    fun rejectsChangedPayloadEvenWhenCharacterCountMatches() {
        val bytes = payload.toByteArray(Charsets.UTF_8)

        assertFalse(
            ClipboardPayloadIntegrity.matches(
                text = payload.replace("Hello", "Hallo"),
                expectedHash = sha256(bytes),
                expectedByteCount = bytes.size.toLong(),
                expectedCharacterCount = payload.codePointCount(0, payload.length)
            )
        )
    }

    @Test
    fun rejectsByteCountMismatch() {
        val bytes = payload.toByteArray(Charsets.UTF_8)

        assertFalse(
            ClipboardPayloadIntegrity.matches(
                payload, sha256(bytes), bytes.size.toLong() - 1,
                payload.codePointCount(0, payload.length)
            )
        )
    }

    @Test
    fun rejectsUnicodeCodePointCountMismatch() {
        val bytes = payload.toByteArray(Charsets.UTF_8)

        assertFalse(
            ClipboardPayloadIntegrity.matches(
                payload, sha256(bytes), bytes.size.toLong(),
                payload.length
            )
        )
    }

    @Test
    fun rejectsMissingOrMalformedHash() {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val characters = payload.codePointCount(0, payload.length)

        assertFalse(ClipboardPayloadIntegrity.matches(payload, null, bytes.size.toLong(), characters))
        assertFalse(ClipboardPayloadIntegrity.matches(payload, "", bytes.size.toLong(), characters))
        assertFalse(ClipboardPayloadIntegrity.matches(payload, "not-a-sha256", bytes.size.toLong(), characters))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
