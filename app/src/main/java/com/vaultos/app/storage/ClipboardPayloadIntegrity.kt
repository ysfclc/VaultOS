package com.vaultos.app.storage

import java.security.MessageDigest
import java.util.Locale

/**
 * Validates a clipboard event against the exact UTF-8 payload captured for it.
 *
 * VaultItem integrity protects the shared archived payload. These event-level
 * checks additionally detect a stale or mismatched clipboard-history reference.
 */
internal object ClipboardPayloadIntegrity {
    fun matches(
        text: String,
        expectedHash: String?,
        expectedByteCount: Long,
        expectedCharacterCount: Int
    ): Boolean {
        if (expectedHash.isNullOrBlank() || expectedHash.length != SHA256_HEX_LENGTH) return false

        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() != expectedByteCount) return false
        if (text.codePointCount(0, text.length) != expectedCharacterCount) return false

        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return actualHash == expectedHash.lowercase(Locale.ROOT)
    }

    private const val SHA256_HEX_LENGTH = 64
}
