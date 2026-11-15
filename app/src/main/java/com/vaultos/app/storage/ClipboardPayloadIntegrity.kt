package com.vaultos.app.storage

import java.security.MessageDigest

/**
 * Verifies a clipboard history event against its own payload metadata.
 *
 * VaultRepository verifies the shared archived item. This second check ensures
 * the history event still points to the exact text captured for that event.
 */
internal object ClipboardPayloadIntegrity {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun matches(text: String, expectedByteCount: Long, expectedSha256: String?): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() != expectedByteCount) return false
        val expectedHash = expectedSha256?.takeIf { it.isNotBlank() } ?: return true
        return sha256(bytes).equals(expectedHash, ignoreCase = true)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
