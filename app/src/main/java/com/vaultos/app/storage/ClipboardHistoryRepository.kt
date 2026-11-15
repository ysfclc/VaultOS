package com.vaultos.app.storage

import android.content.Context
import android.net.Uri
import com.vaultos.app.data.ClipboardHistoryEntry
import com.vaultos.app.data.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Clipboard events are a history stream, not merely unique files in the library.
 * Repeated copies create separate events while identical payloads may share one
 * archived VaultItem. The original payload remains in the private vault.
 */
class ClipboardHistoryRepository(private val context: Context) {
    private val dao = VaultDatabase.get(context).dao()
    private val vault = VaultRepository(context)

    fun observe(): Flow<List<ClipboardHistoryEntry>> = dao.observeClipboardHistory()
    fun search(query: String): Flow<List<ClipboardHistoryEntry>> = dao.searchClipboardHistory(query.trim())

    suspend fun captureText(
        text: String,
        foregroundPackage: String?,
        clipLabel: String?,
        mimeTypes: List<String>
    ): ClipboardHistoryEntry? = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext null
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = ClipboardPayloadIntegrity.sha256(bytes)
        val item = vault.saveText(text, "Clipboard capture")
        val entry = ClipboardHistoryEntry(
            id = UUID.randomUUID().toString(),
            itemId = item.id,
            contentType = "text",
            preview = text.take(PREVIEW_LIMIT),
            foregroundPackage = foregroundPackage?.take(180),
            clipLabel = clipLabel?.take(180),
            mimeTypes = mimeTypes.distinct().joinToString("|").take(500),
            contentHash = hash,
            characterCount = text.codePointCount(0, text.length),
            byteCount = bytes.size.toLong(),
            capturedAt = System.currentTimeMillis()
        )
        dao.insertClipboardHistory(entry)
        entry
    }

    suspend fun captureUri(
        uri: Uri,
        foregroundPackage: String?,
        clipLabel: String?,
        mimeTypes: List<String>
    ): ClipboardHistoryEntry = withContext(Dispatchers.IO) {
        val item = vault.importUri(uri, "Clipboard file capture")
        val entry = ClipboardHistoryEntry(
            id = UUID.randomUUID().toString(),
            itemId = item.id,
            contentType = "file",
            preview = item.name.take(PREVIEW_LIMIT),
            foregroundPackage = foregroundPackage?.take(180),
            clipLabel = clipLabel?.take(180),
            mimeTypes = mimeTypes.distinct().joinToString("|").take(500),
            contentHash = item.sha256,
            characterCount = 0,
            byteCount = item.sizeBytes,
            capturedAt = System.currentTimeMillis()
        )
        dao.insertClipboardHistory(entry)
        entry
    }

    suspend fun readText(entry: ClipboardHistoryEntry): String? = withContext(Dispatchers.IO) {
        if (entry.contentType != "text") return@withContext null
        val item = entry.itemId?.let { dao.getById(it) } ?: return@withContext null
        val text = vault.readFullText(item) ?: return@withContext null
        if (ClipboardPayloadIntegrity.matches(text, entry.byteCount, entry.contentHash)) text else null
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setClipboardPinned(id, pinned)
    suspend fun delete(id: String) = dao.deleteClipboardHistory(id)
    suspend fun clearUnpinned() = dao.clearUnpinnedClipboardHistory()

    companion object { private const val PREVIEW_LIMIT = 420 }
}
