package com.vaultos.app.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object VaultBackupManager {
    data class ResultSummary(val files: Int, val bytes: Long, val records: Int)

    fun backup(context: Context, treeUri: Uri): ResultSummary {
        require(treeUri.authority != "${context.packageName}.documents") { "Choose a backup destination outside the VaultOS archive" }
        val root = File(context.filesDir, "vault")
        val destinationRoot = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("The selected backup folder is no longer available")
        val backupFolder = destinationRoot.findFile("VaultOS Backup")
            ?: destinationRoot.createDirectory("VaultOS Backup")
            ?: throw IllegalStateException("Could not create VaultOS Backup folder")
        var count = 0
        var bytes = 0L

        fun copyDirectory(source: File, destination: DocumentFile) {
            source.listFiles()?.sortedBy { it.name }?.forEach { child ->
                if (child.name.endsWith(".part") || child.name.endsWith(".restore") || child.name == "backup-index.json") return@forEach
                if (child.isDirectory) {
                    val target = destination.findFile(child.name) ?: destination.createDirectory(child.name)
                        ?: throw IllegalStateException("Could not create backup folder ${child.name}")
                    copyDirectory(child, target)
                } else if (child.isFile) {
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(child.extension.lowercase()) ?: "application/octet-stream"
                    destination.findFile(child.name)?.delete()
                    val target = destination.createFile(mime, child.name)
                        ?: throw IllegalStateException("Could not create backup file ${child.name}")
                    context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                        child.inputStream().use { input -> bytes += input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not write backup file ${child.name}")
                    count++
                }
            }
        }

        if (root.exists()) copyDirectory(root, backupFolder)
        val items = kotlinx.coroutines.runBlocking { VaultDatabase.get(context).dao().allNow() }
        val records = JSONArray()
        // Preserve the complete catalogue, including indexed-only device files. Their
        // original paths are metadata references; only archived payloads are copied.
        items.forEach { item ->
            val relative = item.archivedPath?.let { archivedPath ->
                runCatching {
                    val archived = File(archivedPath).canonicalFile
                    require(archived.toPath().startsWith(root.canonicalFile.toPath()))
                    root.canonicalFile.toPath().relativize(archived.toPath()).toString()
                }.getOrNull()
            }
            records.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("mimeType", item.mimeType); put("category", item.category)
                put("source", item.source); put("originalUri", item.originalUri)
                put("relativePath", relative?.let { it } ?: JSONObject.NULL)
                put("isArchived", item.isArchived && relative != null)
                put("sizeBytes", item.sizeBytes); put("sha256", item.sha256); put("createdAt", item.createdAt)
                put("modifiedAt", item.modifiedAt); put("isPinned", item.isPinned); put("textContent", item.textContent)
            })
        }
        val manifest = JSONObject().apply {
            put("format", "vaultos-backup-v1")
            put("createdAt", System.currentTimeMillis())
            put("records", records)
        }
        backupFolder.findFile("backup-index.json")?.delete()
        val manifestFile = backupFolder.createFile("application/json", "backup-index.json")
            ?: throw IllegalStateException("Could not write backup manifest")
        context.contentResolver.openOutputStream(manifestFile.uri, "w")?.bufferedWriter()?.use { it.write(manifest.toString(2)) }
            ?: throw IllegalStateException("Could not open backup manifest")
        return ResultSummary(count, bytes, records.length())
    }

    suspend fun restore(context: Context, treeUri: Uri): ResultSummary {
        require(treeUri.authority != "${context.packageName}.documents") { "Choose a backup source outside the VaultOS archive" }
        val selectedRoot = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("The selected backup folder is unavailable")
        val backupFolder = selectedRoot.findFile("VaultOS Backup")
            ?: throw IllegalStateException("No VaultOS Backup folder was found")
        val vault = File(context.filesDir, "vault").apply { mkdirs() }
        var count = 0
        var bytes = 0L
        val restoredPaths = mutableMapOf<String, String>()

        fun safeName(name: String) = name.replace(Regex("[/\\\\\\u0000-\\u001f]"), "_").take(180).ifBlank { "restored-file" }

        fun uniqueRestoredTarget(parent: File, originalName: String): File {
            val dot = originalName.lastIndexOf('.')
            val stem = if (dot > 0) originalName.substring(0, dot) else originalName
            val extension = if (dot > 0) originalName.substring(dot) else ""
            var suffix = 1
            var candidate: File
            do {
                candidate = File(parent, "${stem}_restored_$suffix$extension")
                suffix++
            } while (candidate.exists())
            return candidate
        }

        fun fileSha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun restoreDirectory(source: DocumentFile, destination: File, sourcePrefix: String = "") {
            destination.mkdirs()
            source.listFiles().forEach { child ->
                if (sourcePrefix.isEmpty() && child.name == "backup-index.json") return@forEach
                val name = safeName(child.name ?: "restored-file")
                if (name == "." || name == "..") return@forEach
                val sourceRelative = if (sourcePrefix.isEmpty()) name else "$sourcePrefix/$name"
                if (child.isDirectory) {
                    var target = File(destination, name).canonicalFile
                    if (!target.toPath().startsWith(vault.canonicalFile.toPath())) return@forEach
                    if (target.exists() && !target.isDirectory) target = uniqueRestoredTarget(destination, name).canonicalFile
                    restoredPaths[sourceRelative] = vault.toPath().relativize(target.toPath()).toString()
                    restoreDirectory(child, target, sourceRelative)
                } else {
                    val temp = File(destination, ".${java.util.UUID.randomUUID()}.restore")
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    var copiedForItem = 0L
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        temp.outputStream().buffered().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                copiedForItem += read
                            }
                        }
                    } ?: throw IllegalStateException("Could not read backup item ${child.name}")
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    var target = File(destination, name).canonicalFile
                    if (!target.toPath().startsWith(vault.canonicalFile.toPath())) {
                        temp.delete()
                        return@forEach
                    }
                    if (target.exists()) {
                        if (target.isFile && runCatching { fileSha256(target) }.getOrNull() == hash) {
                            temp.delete()
                        } else {
                            target = uniqueRestoredTarget(destination, name).canonicalFile
                            if (!temp.renameTo(target)) {
                                temp.copyTo(target, overwrite = false)
                                temp.delete()
                            }
                            bytes += copiedForItem
                            count++
                        }
                    } else {
                        if (!temp.renameTo(target)) {
                            temp.copyTo(target, overwrite = false)
                            temp.delete()
                        }
                        bytes += copiedForItem
                        count++
                    }
                    restoredPaths[sourceRelative] = vault.toPath().relativize(target.toPath()).toString()
                }
            }
        }
        restoreDirectory(backupFolder, vault)

        val manifestFile = backupFolder.findFile("backup-index.json")
        var records = 0
        if (manifestFile != null) {
            val json = context.contentResolver.openInputStream(manifestFile.uri)?.bufferedReader()?.use { it.readText() }
            if (!json.isNullOrBlank()) {
                val rootObject = JSONObject(json)
                require(rootObject.optString("format") == "vaultos-backup-v1") { "Unsupported VaultOS backup format" }
                val recordArray = rootObject.optJSONArray("records") ?: JSONArray()
                val dao = VaultDatabase.get(context).dao()
                val repository = VaultRepository(context)
                for (i in 0 until recordArray.length()) {
                    val row = recordArray.optJSONObject(i) ?: continue
                    val originalUri = row.optString("originalUri").takeIf { it.isNotBlank() && it != "null" }
                    val backupRelative = row.optString("relativePath").takeIf { it.isNotBlank() && it != "null" }
                    val preview = row.optString("textContent").takeIf { it.isNotBlank() && it != "null" }
                    val hash = row.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
                    if (backupRelative == null) {
                        // Indexed-only records are restored as references, not fake archive
                        // copies. The original path may be unavailable on a replacement phone.
                        val item = VaultItem(
                            id = row.optString("id"), name = row.optString("name"), mimeType = row.optString("mimeType", "application/octet-stream"),
                            category = row.optString("category", "Other"), source = "Restored index reference",
                            originalUri = originalUri, archivedPath = null, sizeBytes = row.optLong("sizeBytes"), sha256 = hash,
                            createdAt = row.optLong("createdAt", System.currentTimeMillis()), modifiedAt = row.optLong("modifiedAt"),
                            isArchived = false, isPinned = row.optBoolean("isPinned"), textContent = preview
                        )
                        dao.upsert(item)
                        val sourceFile = originalUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let(::File) }
                        if (sourceFile?.isFile == true && item.category in setOf("Text", "Code")) repository.reindexText(item.id, sourceFile)
                        records++
                        continue
                    }
                    val relative = restoredPaths[backupRelative] ?: backupRelative
                    val file = File(vault, relative).canonicalFile
                    if (!file.toPath().startsWith(vault.canonicalFile.toPath()) || !file.isFile) continue
                    if (hash != null && fileSha256(file) != hash) {
                        throw IllegalStateException("Backup integrity check failed for ${row.optString("name")}")
                    }
                    val item = VaultItem(
                        id = row.optString("id"), name = row.optString("name"), mimeType = row.optString("mimeType", "application/octet-stream"),
                        category = row.optString("category", "Other"), source = "Restored from VaultOS backup",
                        originalUri = originalUri, archivedPath = file.absolutePath, sizeBytes = file.length(), sha256 = hash,
                        createdAt = row.optLong("createdAt", System.currentTimeMillis()), modifiedAt = file.lastModified(),
                        isArchived = true, isPinned = row.optBoolean("isPinned"), textContent = preview
                    )
                    dao.upsert(item)
                    if (item.category == "Text" || item.category == "Code") repository.reindexText(item.id, file)
                    records++
                }
            }
        }
        return ResultSummary(count, bytes, records)
    }
}
