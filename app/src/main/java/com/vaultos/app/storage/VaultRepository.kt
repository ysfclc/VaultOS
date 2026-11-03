package com.vaultos.app.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vaultos.app.data.SearchChunk
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultItem
import com.vaultos.app.util.FileClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val dao = VaultDatabase.get(context).dao()
    val root: File get() = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

    suspend fun importUri(
        uri: Uri,
        source: String = "Shared",
        suppliedName: String? = null,
        suppliedText: String? = null
    ): VaultItem = withContext(Dispatchers.IO) {
        var name = suppliedName.orEmpty()
        val resolver = context.contentResolver
        var declaredSize = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (name.isBlank() && nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
            }
        }
        if (name.isBlank()) name = "Imported_${System.currentTimeMillis()}"
        name = FileClassifier.safeFileName(name)
        val mime = resolver.getType(uri) ?: if (suppliedText != null) "text/plain" else "application/octet-stream"
        val category = FileClassifier.classify(name, mime, suppliedText != null)
        val targetDir = File(root, folderFor(category)).apply { mkdirs() }
        val temp = File(targetDir, ".${UUID.randomUUID()}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        var preview = suppliedText?.take(PREVIEW_LIMIT)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    copied += read
                }
                output.fd.sync()
            }
        } ?: throw IllegalStateException("Android could not open the incoming content URI")
        val hash = digest.digest().toHex()
        val duplicate = dao.findHash(hash)
        if (duplicate?.isArchived == true) {
            temp.delete()
            if (duplicate.originalUri == uri.toString()) return@withContext duplicate
            val referenced = VaultItem(
                id = UUID.randomUUID().toString(), name = name, mimeType = mime, category = category,
                source = source, originalUri = uri.toString(), archivedPath = duplicate.archivedPath,
                sizeBytes = copied, sha256 = hash, createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(), isArchived = true, textContent = preview ?: duplicate.textContent
            )
            dao.upsert(referenced)
            if (category == "Text" || category == "Code") duplicate.archivedPath?.let { indexTextFile(referenced.id, File(it)) }
            return@withContext referenced
        }
        val finalFile = uniqueFile(targetDir, name)
        moveTemp(temp, finalFile)
        if (preview == null && (mime.startsWith("text/") || category == "Code")) {
            preview = readPreview(finalFile)
        }
        val id = UUID.randomUUID().toString()
        val item = VaultItem(
            id = id,
            name = name,
            mimeType = mime,
            category = category,
            source = source,
            originalUri = uri.toString(),
            archivedPath = finalFile.absolutePath,
            sizeBytes = copied,
            sha256 = hash,
            createdAt = duplicate?.createdAt ?: System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            isArchived = true,
            isPinned = duplicate?.isPinned ?: false,
            textContent = preview
        )
        dao.upsert(item)
        if (category == "Text" || category == "Code") indexTextFile(id, finalFile)
        item
    }

    suspend fun saveText(text: String, source: String = "Clipboard"): VaultItem = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        val duplicate = dao.findHash(hash)
        if (duplicate?.isArchived == true) return@withContext duplicate
        val id = UUID.randomUUID().toString()
        val dir = File(root, "text").apply { mkdirs() }
        val file = File(dir, "$id.txt")
        FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
        val title = text.lineSequence().firstOrNull()?.trim()?.take(90)?.ifBlank { "Copied text" } ?: "Copied text"
        val item = VaultItem(
            id, title, "text/plain", "Text", source, null, file.absolutePath,
            bytes.size.toLong(), hash, duplicate?.createdAt ?: System.currentTimeMillis(),
            System.currentTimeMillis(), true, duplicate?.isPinned ?: false,
            text.take(PREVIEW_LIMIT)
        )
        dao.upsert(item)
        dao.deleteChunks(id)
        indexTextFile(id, file)
        item
    }

    /** Indexes an existing file without moving or copying it. */
    suspend fun indexFile(file: File, source: String = "Device storage"): VaultItem? = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.canRead() || file.length() < 0L) return@withContext null
        val original = Uri.fromFile(file).toString()
        val previous = dao.findByOriginalUri(original)
        if (previous != null && previous.sizeBytes == file.length() && previous.modifiedAt == file.lastModified()) return@withContext previous
        val hash = sha256(file)
        val duplicate = dao.findHash(hash)
        val previousArchiveMatches = previous?.isArchived == true && previous.sha256 == hash &&
            previous.archivedPath?.let { File(it).isFile } == true
        val duplicateArchive = duplicate?.takeIf { it.isArchived && it.archivedPath?.let { path -> File(path).isFile } == true }
        val mime = mimeFor(file.name)
        val category = FileClassifier.classify(file.name, mime)
        val preview = if (category == "Text" || category == "Code") readPreview(file) else null
        val item = VaultItem(
            id = previous?.id ?: UUID.randomUUID().toString(),
            name = file.name,
            mimeType = mime,
            category = category,
            source = source,
            originalUri = original,
            // Identical payloads share the existing archived object, but keep one catalogue
            // record per original path so the smart library does not hide files. Never keep
            // an archive pointer when the original path has changed to different bytes.
            archivedPath = if (previousArchiveMatches) previous?.archivedPath else duplicateArchive?.archivedPath,
            sizeBytes = file.length(),
            sha256 = hash,
            createdAt = previous?.createdAt ?: file.lastModified(),
            modifiedAt = file.lastModified(),
            isArchived = previousArchiveMatches || duplicateArchive != null,
            isPinned = previous?.isPinned ?: false,
            textContent = preview
        )
        dao.upsert(item)
        if (category == "Text" || category == "Code") { dao.deleteChunks(item.id); indexTextFile(item.id, file) }
        item
    }

    /** Copies a newly detected shared-storage file into the private vault while leaving its source untouched. */
    suspend fun archiveIncomingFile(file: File, source: String = "Device storage", force: Boolean = false): VaultItem? = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.canRead()) return@withContext null
        val original = Uri.fromFile(file).toString()
        val previous = dao.findByOriginalUri(original)
        if (!force && previous?.isArchived == true && previous.sizeBytes == file.length() && previous.modifiedAt == file.lastModified()) return@withContext previous
        val targetDir = File(root, folderFor(FileClassifier.classify(file.name, mimeFor(file.name)))).apply { mkdirs() }
        val temp = File(targetDir, ".${UUID.randomUUID()}.part")
        val sourceLength = file.length()
        val sourceModified = file.lastModified()
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            FileInputStream(file).use { input -> FileOutputStream(temp).use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) { val read = input.read(buffer); if (read < 0) break; output.write(buffer, 0, read); digest.update(buffer, 0, read); copied += read }
                output.fd.sync()
            } }
        } catch (failure: Throwable) {
            temp.delete()
            throw failure
        }
        // A MediaStore notification can arrive while a browser is still writing a
        // download. Never archive a partial snapshot; a later completion/change scan
        // will retry once the source has stopped changing.
        if (!file.isFile || copied != sourceLength || file.length() != sourceLength || file.lastModified() != sourceModified) {
            temp.delete()
            return@withContext null
        }
        val hash = digest.digest().toHex()
        val duplicate = dao.findHash(hash)
        if (duplicate?.isArchived == true && duplicate.id == previous?.id) {
            temp.delete()
            return@withContext previous
        }
        if (duplicate?.isArchived == true && duplicate.id != previous?.id) {
            temp.delete()
            val referenced = VaultItem(
                id = previous?.id ?: UUID.randomUUID().toString(), name = file.name,
                mimeType = mimeFor(file.name), category = FileClassifier.classify(file.name, mimeFor(file.name)),
                source = source, originalUri = original, archivedPath = duplicate.archivedPath,
                sizeBytes = file.length(), sha256 = hash, createdAt = previous?.createdAt ?: file.lastModified(),
                modifiedAt = file.lastModified(), isArchived = true, isPinned = previous?.isPinned ?: false,
                textContent = duplicate.textContent
            )
            dao.upsert(referenced)
            if (referenced.category == "Text" || referenced.category == "Code") duplicate.archivedPath?.let { indexTextFile(referenced.id, File(it)) }
            return@withContext referenced
        }
        val id = previous?.id ?: UUID.randomUUID().toString()
        val name = FileClassifier.safeFileName(file.name)
        val finalFile = uniqueFile(targetDir, "${id.take(8)}_$name")
        moveTemp(temp, finalFile)
        val mime = mimeFor(file.name)
        val category = FileClassifier.classify(file.name, mime)
        val preview = if (category == "Text" || category == "Code") readPreview(finalFile) else null
        val item = VaultItem(
            id, file.name, mime, category, source, original, finalFile.absolutePath,
            copied, hash, previous?.createdAt ?: file.lastModified(), file.lastModified(),
            true, previous?.isPinned ?: duplicate?.isPinned ?: false, preview
        )
        dao.upsert(item)
        if (category == "Text" || category == "Code") { dao.deleteChunks(id); indexTextFile(id, finalFile) }
        item
    }

    /**
     * Reconciles files created, renamed or removed inside the private VaultOS archive
     * (including files written by Android's DocumentsProvider). Original device files
     * remain outside this routine and are never moved.
     */
    suspend fun syncVaultContents() = withContext(Dispatchers.IO) {
        val archiveRoot = root.canonicalFile
        val archivedItems = dao.allNow().filter { item ->
            if (item.isTrashed) return@filter false
            val path = item.archivedPath ?: return@filter false
            runCatching { File(path).canonicalFile.toPath().startsWith(archiveRoot.toPath()) }.getOrDefault(false)
        }
        val itemsByPath = archivedItems.groupBy { File(requireNotNull(it.archivedPath)).absolutePath }
        val missingByHash = archivedItems.filter { item ->
            item.archivedPath?.let { !File(it).exists() } == true
        }.filter { !it.sha256.isNullOrBlank() }.groupBy { it.sha256!! }
        val seenItemIds = mutableSetOf<String>()
        val claimedMissingIds = mutableSetOf<String>()
        val filesByHash = HashMap<String, File>()

        root.walkTopDown().onEnter { directory -> directory.name != ".trash" }.filter { file ->
            file.isFile && !file.name.endsWith(".part", true) && !file.name.endsWith(".restore", true)
        }.forEach { file ->
            val path = file.absolutePath
            val current = itemsByPath[path]
            if (!current.isNullOrEmpty()) {
                val metadataUnchanged = current.all { it.sizeBytes == file.length() && it.modifiedAt == file.lastModified() && !it.sha256.isNullOrBlank() }
                val hash = if (metadataUnchanged) current.first().sha256!! else sha256(file)
                filesByHash[hash] = file
                current.forEach { item ->
                    seenItemIds += item.id
                    if (!metadataUnchanged || item.sha256 != hash || item.name != file.name) {
                        val mime = mimeFor(file.name)
                        val category = FileClassifier.classify(file.name, mime)
                        val preview = if (category == "Text" || category == "Code") readPreview(file) else null
                        val updated = item.copy(
                            name = file.name, mimeType = mime, category = category,
                            sizeBytes = file.length(), sha256 = hash, modifiedAt = file.lastModified(),
                            isArchived = true, textContent = preview
                        )
                        dao.upsert(updated)
                        if (category == "Text" || category == "Code") indexTextFile(updated.id, file)
                        else dao.deleteChunks(updated.id)
                    }
                }
            } else {
                val hash = sha256(file)
                filesByHash[hash] = file
                val movedRecords = missingByHash[hash].orEmpty().filter { it.id !in claimedMissingIds }
                if (movedRecords.isNotEmpty()) {
                    movedRecords.forEach { old ->
                        claimedMissingIds += old.id
                        seenItemIds += old.id
                        val mime = mimeFor(file.name)
                        val category = FileClassifier.classify(file.name, mime)
                        val preview = if (category == "Text" || category == "Code") readPreview(file) else null
                        val moved = old.copy(
                            name = file.name, mimeType = mime, category = category,
                            archivedPath = path, sizeBytes = file.length(), sha256 = hash,
                            modifiedAt = file.lastModified(), isArchived = true, textContent = preview
                        )
                        dao.upsert(moved)
                        if (category == "Text" || category == "Code") indexTextFile(moved.id, file)
                        else dao.deleteChunks(moved.id)
                    }
                } else {
                    val mime = mimeFor(file.name)
                    val category = FileClassifier.classify(file.name, mime)
                    val item = VaultItem(
                        id = UUID.randomUUID().toString(), name = file.name, mimeType = mime, category = category,
                        source = "Created in VaultOS archive", originalUri = Uri.fromFile(file).toString(),
                        archivedPath = path, sizeBytes = file.length(), sha256 = hash,
                        createdAt = file.lastModified(), modifiedAt = file.lastModified(), isArchived = true,
                        textContent = if (category == "Text" || category == "Code") readPreview(file) else null
                    )
                    dao.upsert(item)
                    seenItemIds += item.id
                    if (category == "Text" || category == "Code") indexTextFile(item.id, file)
                }
            }
        }

        archivedItems.filter { it.id !in seenItemIds }.forEach { missing ->
            val replacement = missing.sha256?.let(filesByHash::get)
            if (replacement != null) {
                val mime = mimeFor(replacement.name)
                val category = FileClassifier.classify(replacement.name, mime)
                val preview = if (category == "Text" || category == "Code") readPreview(replacement) else null
                dao.upsert(missing.copy(
                    name = replacement.name, mimeType = mime, category = category,
                    archivedPath = replacement.absolutePath, sizeBytes = replacement.length(),
                    modifiedAt = replacement.lastModified(), isArchived = true, textContent = preview
                ))
            } else {
                dao.delete(missing.id)
                dao.deleteChunks(missing.id)
            }
        }
    }

    suspend fun setPinned(item: VaultItem, pinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setPinned(item.id, pinned)
    }

    suspend fun removeIndex(item: VaultItem) = withContext(Dispatchers.IO) {
        // This removes the catalog entry only. The original file is never deleted.
        dao.delete(item.id)
        dao.deleteChunks(item.id)
    }

    suspend fun trashArchivedFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val canonical = file.canonicalFile
        require(canonical.toPath().startsWith(root.canonicalFile.toPath())) { "Only managed VaultOS files can be moved to the recycle bin" }
        val records = dao.findByArchivedPath(canonical.absolutePath)
        if (records.isEmpty() || !canonical.exists()) return@withContext false
        val trashDir = File(root, ".trash").apply { mkdirs() }
        val destination = uniqueFile(trashDir, "${System.currentTimeMillis()}_${canonical.name}")
        require(canonical.renameTo(destination)) { "Could not move ${canonical.name} to the recycle bin" }
        dao.markPathTrashed(canonical.absolutePath, destination.absolutePath, System.currentTimeMillis())
        true
    }

    suspend fun restoreFromTrash(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        if (!item.isTrashed) return@withContext false
        val trashed = item.archivedPath?.let(::File) ?: return@withContext false
        if (!trashed.exists()) {
            dao.delete(item.id)
            dao.deleteChunks(item.id)
            return@withContext false
        }
        val originalPath = item.trashOriginalPath?.let(::File)
            ?: File(root, folderFor(item.category) + File.separator + item.name)
        originalPath.parentFile?.mkdirs()
        val destination = if (originalPath.exists()) uniqueFile(originalPath.parentFile ?: root, originalPath.name) else originalPath
        require(trashed.renameTo(destination)) { "Could not restore ${item.name}" }
        dao.restorePath(trashed.absolutePath, destination.absolutePath)
        true
    }

    suspend fun permanentlyDeleteTrashedItem(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        if (!item.isTrashed) return@withContext false
        val path = item.archivedPath ?: return@withContext false
        val related = dao.findByArchivedPath(path)
        val file = File(path)
        if (file.exists() && !file.deleteRecursively()) throw IllegalStateException("Could not permanently delete ${item.name}")
        related.forEach { record -> dao.delete(record.id); dao.deleteChunks(record.id) }
        true
    }

    suspend fun moveToTrash(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        val path = item.archivedPath?.let(::File) ?: return@withContext false
        if (!path.isFile || !path.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) return@withContext false
        trashArchivedFile(path)
    }

    suspend fun readFullText(item: VaultItem): String? = withContext(Dispatchers.IO) {
        if (item.category !in setOf("Text", "Code")) return@withContext item.textContent
        val file = item.archivedPath?.let(::File) ?: item.originalUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let(::File) }
        if (file?.isFile == true) file.readText(Charsets.UTF_8)
        else item.textContent?.takeIf { it.toByteArray(Charsets.UTF_8).size.toLong() >= item.sizeBytes }
    }

    suspend fun latestText(): String? = withContext(Dispatchers.IO) {
        val item = dao.latestTextItem() ?: return@withContext null
        val file = item.archivedPath?.let(::File) ?: item.originalUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let(::File) }
        if (file?.isFile == true) file.readText(Charsets.UTF_8)
        else item.textContent?.takeIf { it.toByteArray(Charsets.UTF_8).size.toLong() >= item.sizeBytes }
    }

    suspend fun reindexText(itemId: String, file: File) = indexTextFile(itemId, file)

    private suspend fun indexTextFile(itemId: String, file: File) = withContext(Dispatchers.IO) {
        dao.deleteChunks(itemId)
        if (!file.isFile || file.length() == 0L) return@withContext
        val chunks = ArrayList<SearchChunk>(128)
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            val carry = StringBuilder()
            val buffer = CharArray(8192)
            var chunkIndex = 0
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                carry.append(buffer, 0, count)
                while (carry.length >= SEARCH_CHUNK_SIZE) {
                    val value = carry.substring(0, SEARCH_CHUNK_SIZE)
                    chunks += SearchChunk("$itemId:$chunkIndex", itemId, chunkIndex++, value)
                    val keep = carry.substring(SEARCH_CHUNK_SIZE - SEARCH_OVERLAP)
                    carry.setLength(0); carry.append(keep)
                    if (chunks.size >= 128) { dao.upsertChunks(chunks.toList()); chunks.clear() }
                }
            }
            if (carry.isNotEmpty()) chunks += SearchChunk("$itemId:$chunkIndex", itemId, chunkIndex, carry.toString())
        }
        if (chunks.isNotEmpty()) dao.upsertChunks(chunks)
    }


    private fun readPreview(file: File): String? = runCatching {
        file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(PREVIEW_LIMIT)
            val count = reader.read(buffer)
            if (count <= 0) null else String(buffer, 0, count)
        }
    }.getOrNull()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().toHex()
    }

    private fun mimeFor(name: String): String = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    private fun folderFor(category: String) = category.lowercase().replace(' ', '_').replace('&', 'n')

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        var suffix = 1
        while (candidate.exists()) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0) File(directory, "${name.substring(0, dot)}_$suffix${name.substring(dot)}") else File(directory, "${name}_$suffix")
            suffix++
        }
        return candidate
    }

    private fun moveTemp(temp: File, target: File) {
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = false)
            if (!temp.delete()) throw IllegalStateException("Could not clean temporary import")
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val COPY_BUFFER = 128 * 1024
        private const val PREVIEW_LIMIT = 2_000
        private const val SEARCH_CHUNK_SIZE = 4_000
        private const val SEARCH_OVERLAP = 200
    }
}
