package com.vaultos.app.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vaultos.app.storage.StorageScanWorker
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/** Exposes the private VaultOS archive through Android's native document picker. */
class VaultDocumentsProvider : DocumentsProvider() {
    private val appContext get() = requireNotNull(context)
    private val vaultRoot: File get() = File(appContext.filesDir, "vault").apply { mkdirs() }
    private val idPreferences get() = appContext.getSharedPreferences("vaultos_document_ids", android.content.Context.MODE_PRIVATE)

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: DEFAULT_ROOT_PROJECTION
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        fun put(column: String, value: Any?) { val index = cursor.getColumnIndex(column); if (index >= 0) row.add(index, value) }
        put(Root.COLUMN_ROOT_ID, ROOT_ID)
        put(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        put(Root.COLUMN_TITLE, "VaultOS")
        put(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_RECENTS or Root.FLAG_SUPPORTS_SEARCH)
        put(Root.COLUMN_MIME_TYPES, "*/*")
        put(Root.COLUMN_AVAILABLE_BYTES, vaultRoot.usableSpace)
        return cursor
    }

    override fun queryRecentDocuments(rootId: String, projection: Array<out String>?): Cursor {
        requireRoot(rootId)
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        vaultRoot.walkTopDown().filter { it.isFile && !isTransient(it) }.sortedByDescending { it.lastModified() }.take(100).forEach { addDocumentRow(cursor, it) }
        return cursor
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        requireRoot(rootId)
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        vaultRoot.walkTopDown().filter { it.isFile && !isTransient(it) && it.name.contains(query, true) }.take(200).forEach { addDocumentRow(cursor, it) }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileForId(documentId)
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addDocumentRow(cursor, file)
        return cursor
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory")
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        parent.listFiles()?.filterNot(::isTransient)?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })?.forEach {
            addDocumentRow(cursor, it)
        }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileForId(documentId)
        if (!file.isFile) throw FileNotFoundException("Not a file")
        val parsedMode = ParcelFileDescriptor.parseMode(mode)
        return if (mode.contains('w')) {
            ParcelFileDescriptor.open(file, parsedMode, Handler(Looper.getMainLooper())) {
                scheduleVaultSync()
            }
        } else ParcelFileDescriptor.open(file, parsedMode)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Parent is not a directory")
        val safe = displayName.replace(Regex("[/\\\\\\u0000-\\u001f]"), "_").take(180).ifBlank { "New file" }
        var target = File(parent, safe)
        var index = 1
        while (target.exists()) { target = File(parent, "$index-$safe"); index++ }
        val created = if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        if (!created) throw FileNotFoundException("Could not create $safe")
        scheduleVaultSync()
        return idForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForId(documentId)
        if (file == vaultRoot || !file.exists()) return
        // Remove ID mappings while the directory tree still exists.
        removeMappings(file)
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted) throw FileNotFoundException("Could not delete document")
        scheduleVaultSync()
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val source = fileForId(documentId)
        if (source == vaultRoot) throw FileNotFoundException("Cannot rename vault root")
        val safe = displayName.replace(Regex("[/\\\\\u0000-\u001f]"), "_").take(180).ifBlank {
            throw FileNotFoundException("Invalid name")
        }
        val target = File(source.parentFile, safe)
        if (target.exists() || !source.renameTo(target)) throw FileNotFoundException("Could not rename document")
        // SAF clients retain document URIs across renames, so preserve IDs for the
        // renamed node and every child if a directory was renamed.
        moveMappings(source, target, documentId)
        scheduleVaultSync()
        return documentId
    }

    override fun getDocumentType(documentId: String): String {
        val file = fileForId(documentId)
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForId(parentDocumentId).canonicalFile
        val child = fileForId(documentId).canonicalFile
        return child != parent && child.toPath().startsWith(parent.toPath())
    }

    private fun addDocumentRow(cursor: MatrixCursor, file: File) {
        val isDirectory = file.isDirectory
        val flags = if (isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        val row = cursor.newRow()
        fun put(column: String, value: Any?) { val index = cursor.getColumnIndex(column); if (index >= 0) row.add(index, value) }
        put(Document.COLUMN_DOCUMENT_ID, idForFile(file))
        put(Document.COLUMN_DISPLAY_NAME, if (file == vaultRoot) "VaultOS" else file.name)
        put(Document.COLUMN_MIME_TYPE, if (isDirectory) Document.MIME_TYPE_DIR else getDocumentType(idForFile(file)))
        put(Document.COLUMN_SIZE, if (isDirectory) 0 else file.length())
        put(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        put(Document.COLUMN_FLAGS, flags)
    }

    private fun scheduleVaultSync() {
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "vaultos-vault-sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<StorageScanWorker>()
                    .setInputData(workDataOf(StorageScanWorker.KEY_VAULT_ONLY to true))
                    .build()
            )
        }
    }

    private fun isTransient(file: File): Boolean = file.name.endsWith(".part", true) || file.name.endsWith(".restore", true)

    private fun requireRoot(id: String) { if (id != ROOT_ID) throw FileNotFoundException("Unknown root") }

    private fun fileForId(id: String): File {
        if (id == ROOT_ID) return vaultRoot
        val relative = idPreferences.getString(idKey(id), null)
            ?: throw FileNotFoundException("Unknown document id")
        val file = File(vaultRoot, relative).canonicalFile
        if (file != vaultRoot.canonicalFile && !file.toPath().startsWith(vaultRoot.canonicalFile.toPath())) {
            throw FileNotFoundException("Path outside vault")
        }
        if (!file.exists()) throw FileNotFoundException("Document not found")
        return file
    }

    private fun idForFile(file: File): String {
        if (file.canonicalFile == vaultRoot.canonicalFile) return ROOT_ID
        val relative = relativePath(file)
        val key = pathKey(relative)
        val existing = idPreferences.getString(key, null)
        if (existing != null) return existing
        val newId = java.util.UUID.randomUUID().toString()
        idPreferences.edit().putString(key, newId).putString(idKey(newId), relative).apply()
        return newId
    }

    private fun relativePath(file: File): String =
        vaultRoot.canonicalFile.toPath().relativize(file.canonicalFile.toPath()).toString()

    private fun pathKey(relative: String) = "path:$relative"
    private fun idKey(id: String) = "id:$id"

    private fun moveMappings(source: File, target: File, stableDocumentId: String) {
        val oldPrefix = relativePath(source)
        val newPrefix = relativePath(target)
        val separator = File.separator
        val mappingEntries = idPreferences.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("path:") || value !is String) return@mapNotNull null
            val oldPath = key.removePrefix("path:")
            if (oldPath == oldPrefix || oldPath.startsWith(oldPrefix + separator)) value to oldPath else null
        }
        val editor = idPreferences.edit()
        mappingEntries.forEach { (stableId, oldPath) ->
            val suffix = oldPath.removePrefix(oldPrefix)
            editor.remove(pathKey(oldPath)).putString(pathKey(newPrefix + suffix), stableId)
                .putString(idKey(stableId), newPrefix + suffix)
        }
        // The requested document ID is guaranteed to be the stable ID returned to the
        // caller, even if an older provider version left an incomplete reverse mapping.
        if (mappingEntries.none { it.second == oldPrefix }) {
            editor.putString(pathKey(newPrefix), stableDocumentId).putString(idKey(stableDocumentId), newPrefix)
        }
        editor.apply()
    }

    private fun removeMappings(file: File) {
        val root = vaultRoot.canonicalFile
        val editor = idPreferences.edit()
        val paths = if (file.isDirectory) file.walkTopDown().toList() else listOf(file)
        paths.forEach { child ->
            if (child.canonicalFile == root) return@forEach
            val relative = runCatching { relativePath(child) }.getOrNull() ?: return@forEach
            val key = pathKey(relative)
            idPreferences.getString(key, null)?.let { stableId -> editor.remove(idKey(stableId)) }
            editor.remove(key)
        }
        editor.apply()
    }

    companion object {
        private const val ROOT_ID = "vaultos-root"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_AVAILABLE_BYTES)
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
    }
}
