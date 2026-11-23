package com.vaultos.app.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultItem
import com.vaultos.app.data.UserTag
import com.vaultos.app.data.ItemTagCrossRef
import com.vaultos.app.data.UserCollection
import com.vaultos.app.data.CollectionItemCrossRef
import com.vaultos.app.data.SavedSearch
import com.vaultos.app.data.ItemMetadata
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
        val dao = VaultDatabase.get(context).dao()
        val items = kotlinx.coroutines.runBlocking { dao.allNow() }
        val userTags = kotlinx.coroutines.runBlocking { dao.allTagsNow() }
        val itemTags = kotlinx.coroutines.runBlocking { dao.allItemTagsNow() }
        val collections = kotlinx.coroutines.runBlocking { dao.allCollectionsNow() }
        val collectionItems = kotlinx.coroutines.runBlocking { dao.allCollectionItemsNow() }
        val savedSearches = kotlinx.coroutines.runBlocking { dao.allSavedSearchesNow() }
        val metadata = kotlinx.coroutines.runBlocking { dao.allMetadataNow() }
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
            val trashOriginalRelative = item.trashOriginalPath?.let { originalPath ->
                runCatching {
                    val original = File(originalPath).canonicalFile
                    require(original.toPath().startsWith(root.canonicalFile.toPath()))
                    root.canonicalFile.toPath().relativize(original.toPath()).toString()
                }.getOrNull()
            }
            records.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("mimeType", item.mimeType); put("category", item.category)
                put("source", item.source); put("originalUri", item.originalUri)
                put("relativePath", relative?.let { it } ?: JSONObject.NULL)
                put("isArchived", item.isArchived && relative != null)
                put("sizeBytes", item.sizeBytes); put("sha256", item.sha256); put("createdAt", item.createdAt)
                put("modifiedAt", item.modifiedAt); put("isPinned", item.isPinned); put("textContent", item.textContent)
                put("isTrashed", item.isTrashed); put("trashedAt", item.trashedAt ?: JSONObject.NULL)
                put("trashOriginalRelativePath", trashOriginalRelative?.let { it } ?: JSONObject.NULL)
            })
        }
        val tagRecords = JSONArray().apply { userTags.forEach { tag -> put(JSONObject().apply {
            put("id", tag.id); put("name", tag.name); put("normalizedName", tag.normalizedName); put("createdAt", tag.createdAt)
        }) } }
        val itemTagRecords = JSONArray().apply { itemTags.forEach { ref -> put(JSONObject().apply {
            put("itemId", ref.itemId); put("tagId", ref.tagId); put("addedAt", ref.addedAt)
        }) } }
        val collectionRecords = JSONArray().apply { collections.forEach { collection -> put(JSONObject().apply {
            put("id", collection.id); put("name", collection.name); put("normalizedName", collection.normalizedName)
            put("description", collection.description); put("createdAt", collection.createdAt); put("updatedAt", collection.updatedAt)
        }) } }
        val collectionItemRecords = JSONArray().apply { collectionItems.forEach { ref -> put(JSONObject().apply {
            put("collectionId", ref.collectionId); put("itemId", ref.itemId); put("addedAt", ref.addedAt)
        }) } }
        val savedSearchRecords = JSONArray().apply { savedSearches.forEach { saved -> put(JSONObject().apply {
            put("id", saved.id); put("name", saved.name); put("normalizedName", saved.normalizedName)
            put("query", saved.query); put("createdAt", saved.createdAt)
        }) } }
        val metadataRecords = JSONArray().apply { metadata.forEach { meta -> put(JSONObject().apply {
            put("itemId", meta.itemId); put("title", meta.title ?: JSONObject.NULL); put("author", meta.author ?: JSONObject.NULL)
            put("subject", meta.subject ?: JSONObject.NULL); put("pageCount", meta.pageCount ?: JSONObject.NULL)
            put("widthPx", meta.widthPx ?: JSONObject.NULL); put("heightPx", meta.heightPx ?: JSONObject.NULL)
            put("durationMs", meta.durationMs ?: JSONObject.NULL); put("capturedAt", meta.capturedAt ?: JSONObject.NULL)
            put("cameraModel", meta.cameraModel ?: JSONObject.NULL); put("packageName", meta.packageName ?: JSONObject.NULL)
            put("packageVersionName", meta.packageVersionName ?: JSONObject.NULL); put("packageVersionCode", meta.packageVersionCode ?: JSONObject.NULL)
            put("extractedAt", meta.extractedAt)
        }) } }
        val manifest = JSONObject().apply {
            put("format", "vaultos-backup-v2")
            put("createdAt", System.currentTimeMillis())
            put("records", records)
            put("userTags", tagRecords)
            put("itemTags", itemTagRecords)
            put("userCollections", collectionRecords)
            put("collectionItems", collectionItemRecords)
            put("savedSearches", savedSearchRecords)
            put("itemMetadata", metadataRecords)
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

        // Parse and reject an unsupported/corrupt manifest before copying any payload
        // into the private vault. This prevents malformed backups leaving copied files.
        val manifestFile = backupFolder.findFile("backup-index.json")
        val manifestJson = manifestFile?.let { file ->
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Could not read backup manifest")
        }
        val manifestRoot = manifestJson?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        val manifestFormat = manifestRoot?.optString("format").orEmpty()
        if (manifestRoot != null) {
            require(isSupportedBackupFormat(manifestFormat)) { "Unsupported VaultOS backup format" }
        }

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

        var records = 0
        if (manifestRoot != null) {
                val rootObject = manifestRoot
                val format = manifestFormat
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
                            isArchived = false, isPinned = row.optBoolean("isPinned"), textContent = preview,
                            isTrashed = false
                        )
                        dao.upsert(item)
                        val sourceFile = originalUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let(::File) }
                        if (sourceFile?.isFile == true && item.category in setOf("Text", "Code")) repository.reindexText(item.id, sourceFile)
                        records++
                        continue
                    }
                    val relative = restoredPaths[backupRelative] ?: backupRelative
                    val file = resolveVaultRelativePath(vault, relative)?.takeIf { it.isFile } ?: continue
                    if (hash != null && fileSha256(file) != hash) {
                        throw IllegalStateException("Backup integrity check failed for ${row.optString("name")}")
                    }
                    val trashOriginalRelativePath = row.optString("trashOriginalRelativePath")
                        .takeIf { it.isNotBlank() && it != "null" }
                    val trashOriginalPath = trashOriginalRelativePath?.let { relativePath ->
                        resolveVaultRelativePath(vault, relativePath)?.absolutePath
                            ?: throw IllegalStateException("Backup contains an unsafe recycle-bin restore path")
                    }
                    val item = VaultItem(
                        id = row.optString("id"), name = row.optString("name"), mimeType = row.optString("mimeType", "application/octet-stream"),
                        category = row.optString("category", "Other"), source = "Restored from VaultOS backup",
                        originalUri = originalUri, archivedPath = file.absolutePath, sizeBytes = file.length(), sha256 = hash,
                        createdAt = row.optLong("createdAt", System.currentTimeMillis()), modifiedAt = file.lastModified(),
                        isArchived = true, isPinned = row.optBoolean("isPinned"), textContent = preview,
                        isTrashed = row.optBoolean("isTrashed"),
                        trashedAt = if (row.isNull("trashedAt")) null else row.optLong("trashedAt"),
                        trashOriginalPath = trashOriginalPath
                    )
                    dao.upsert(item)
                    if (item.category == "Text" || item.category == "Code") repository.reindexText(item.id, file)
                    records++
                }

                // v2 restores the user-defined organization layer after item records exist.
                if (format == "vaultos-backup-v2") {
                    val tagIds = mutableMapOf<String, String>()
                    val tagArray = rootObject.optJSONArray("userTags") ?: JSONArray()
                    for (i in 0 until tagArray.length()) {
                        val row = tagArray.optJSONObject(i) ?: continue
                        val incoming = UserTag(row.optString("id"), row.optString("name"), row.optString("normalizedName"), row.optLong("createdAt"))
                        dao.insertTag(incoming)
                        tagIds[incoming.id] = dao.findTag(incoming.normalizedName)?.id ?: incoming.id
                    }
                    val collectionIds = mutableMapOf<String, String>()
                    val collectionArray = rootObject.optJSONArray("userCollections") ?: JSONArray()
                    for (i in 0 until collectionArray.length()) {
                        val row = collectionArray.optJSONObject(i) ?: continue
                        val incoming = UserCollection(
                            row.optString("id"), row.optString("name"), row.optString("normalizedName"),
                            row.optString("description"), row.optLong("createdAt"), row.optLong("updatedAt")
                        )
                        dao.insertCollection(incoming)
                        collectionIds[incoming.id] = dao.findCollection(incoming.normalizedName)?.id ?: incoming.id
                    }
                    val itemTagArray = rootObject.optJSONArray("itemTags") ?: JSONArray()
                    for (i in 0 until itemTagArray.length()) {
                        val row = itemTagArray.optJSONObject(i) ?: continue
                        val itemId = row.optString("itemId")
                        val tagId = tagIds[row.optString("tagId")] ?: row.optString("tagId")
                        if (dao.getById(itemId) != null && tagIds.containsValue(tagId)) {
                            dao.addItemTag(ItemTagCrossRef(itemId, tagId, row.optLong("addedAt")))
                        }
                    }
                    val collectionItemArray = rootObject.optJSONArray("collectionItems") ?: JSONArray()
                    for (i in 0 until collectionItemArray.length()) {
                        val row = collectionItemArray.optJSONObject(i) ?: continue
                        val collectionId = collectionIds[row.optString("collectionId")] ?: row.optString("collectionId")
                        val itemId = row.optString("itemId")
                        if (dao.getById(itemId) != null && collectionIds.containsValue(collectionId)) {
                            dao.addCollectionItem(CollectionItemCrossRef(collectionId, itemId, row.optLong("addedAt")))
                        }
                    }
                    val savedArray = rootObject.optJSONArray("savedSearches") ?: JSONArray()
                    for (i in 0 until savedArray.length()) {
                        val row = savedArray.optJSONObject(i) ?: continue
                        dao.insertSavedSearch(SavedSearch(row.optString("id"), row.optString("name"), row.optString("normalizedName"), row.optString("query"), row.optLong("createdAt")))
                    }
                    val metadataArray = rootObject.optJSONArray("itemMetadata") ?: JSONArray()
                    for (i in 0 until metadataArray.length()) {
                        val row = metadataArray.optJSONObject(i) ?: continue
                        val itemId = row.optString("itemId")
                        if (dao.getById(itemId) == null) continue
                        dao.upsertMetadata(ItemMetadata(
                            itemId = itemId,
                            title = row.optString("title").takeIf { it.isNotBlank() && it != "null" },
                            author = row.optString("author").takeIf { it.isNotBlank() && it != "null" },
                            subject = row.optString("subject").takeIf { it.isNotBlank() && it != "null" },
                            pageCount = if (row.isNull("pageCount")) null else row.optInt("pageCount"),
                            widthPx = if (row.isNull("widthPx")) null else row.optInt("widthPx"),
                            heightPx = if (row.isNull("heightPx")) null else row.optInt("heightPx"),
                            durationMs = if (row.isNull("durationMs")) null else row.optLong("durationMs"),
                            capturedAt = row.optString("capturedAt").takeIf { it.isNotBlank() && it != "null" },
                            cameraModel = row.optString("cameraModel").takeIf { it.isNotBlank() && it != "null" },
                            packageName = row.optString("packageName").takeIf { it.isNotBlank() && it != "null" },
                            packageVersionName = row.optString("packageVersionName").takeIf { it.isNotBlank() && it != "null" },
                            packageVersionCode = if (row.isNull("packageVersionCode")) null else row.optLong("packageVersionCode"),
                            extractedAt = row.optLong("extractedAt", System.currentTimeMillis())
                        ))
                    }
                }
        }
        return ResultSummary(count, bytes, records)
    }
}
