package com.vaultos.app.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

data class UsbOtgImportResult(
    val imported: Int,
    val failed: Int,
    val truncated: Boolean
)

/**
 * Copies files from a user-selected SAF tree into VaultOS.
 * The source tree is only queried/read; no delete, rename, move or write operation is issued.
 */
object UsbOtgImporter {
    private const val MAX_FILES = 2000
    private const val MAX_DEPTH = 16

    suspend fun importTree(context: Context, repository: VaultRepository, treeUri: Uri): UsbOtgImportResult =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalArgumentException("Android could not open the selected folder")
            if (!root.isDirectory) throw IllegalArgumentException("The selected USB location is not a folder")

            data class PendingFolder(val folder: DocumentFile, val relativePath: String, val depth: Int)
            val pending = ArrayDeque<PendingFolder>()
            pending.add(PendingFolder(root, "", 0))
            var imported = 0
            var failed = 0
            var visitedFiles = 0
            var truncated = false

            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                val children = runCatching { current.folder.listFiles().toList() }.getOrElse {
                    failed++
                    emptyList()
                }
                for (child in children) {
                    val name = child.name?.takeIf { it.isNotBlank() } ?: "Unnamed item"
                    val relative = if (current.relativePath.isBlank()) name else "${current.relativePath}/$name"
                    when {
                        child.isDirectory -> {
                            if (current.depth < MAX_DEPTH) pending.add(PendingFolder(child, relative, current.depth + 1))
                            else failed++
                        }
                        child.isFile -> {
                            if (visitedFiles >= MAX_FILES) {
                                truncated = true
                                break
                            }
                            visitedFiles++
                            val result = runCatching {
                                repository.importUri(child.uri, "USB drive · $relative", suppliedName = name)
                            }
                            if (result.isSuccess) imported++ else failed++
                        }
                    }
                }
                if (truncated) break
            }
            UsbOtgImportResult(imported, failed, truncated)
        }
}
