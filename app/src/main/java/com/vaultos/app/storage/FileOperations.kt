package com.vaultos.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FileOperationProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val currentFile: String,
    val completedFiles: Int,
    val totalFiles: Int
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (completedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

object FileOperations {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 20_000
    private const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024

    suspend fun copy(
        source: File,
        destination: File,
        onProgress: (FileOperationProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(source.exists() && source.canRead()) { "Source is not readable: ${source.name}" }
        require(!destination.exists()) { "Destination already exists: ${destination.name}" }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination are the same" }
        if (source.isDirectory) {
            require(!destination.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
                "Cannot copy a folder into itself"
            }
        }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create destination folder" } }
        val temp = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.partial")
        val stats = stats(source)
        var copied = 0L
        var completedFiles = 0
        try {
            suspend fun copyNode(from: File, to: File) {
                currentCoroutineContext().ensureActive()
                require(!Files.isSymbolicLink(from.toPath())) { "Refusing to copy through a symbolic link: " + from.name }
                if (Files.isDirectory(from.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    require(to.mkdirs() || to.isDirectory) { "Cannot create folder ${to.name}" }
                    from.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
                        currentCoroutineContext().ensureActive()
                        copyNode(child, File(to, child.name))
                    } ?: throw IllegalStateException("Cannot read folder ${from.name}")
                } else {
                    require(Files.isRegularFile(from.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Unsupported filesystem entry: " + from.name }
                    to.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create folder" } }
                    FileInputStream(from).use { input ->
                        FileOutputStream(to).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                onProgress(FileOperationProgress(copied, stats.first, from.name, completedFiles, stats.second))
                            }
                            output.fd.sync()
                        }
                    }
                    completedFiles++
                    onProgress(FileOperationProgress(copied, stats.first, from.name, completedFiles, stats.second))
                }
            }
            copyNode(source, temp)
            currentCoroutineContext().ensureActive()
            require(temp.renameTo(destination)) { "Could not commit copied file" }
        } catch (failure: Throwable) {
            temp.deleteRecursively()
            throw failure
        }
    }

    /**
     * Move uses a same-filesystem rename when possible. Cross-volume moves first
     * commit a complete copy, then remove the source in a non-cancellable section:
     * cancelling a move can leave a duplicate, but never a half-deleted original.
     */
    suspend fun move(
        source: File,
        destination: File,
        onProgress: (FileOperationProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(source.exists() && source.canRead()) { "Source is not readable: " + source.name }
        require(!destination.exists()) { "Destination already exists: " + destination.name }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination are the same" }
        if (source.isDirectory) {
            require(!destination.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
                "Cannot move a folder into itself"
            }
        }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create destination folder" } }
        val totals = stats(source)
        if (source.renameTo(destination)) {
            onProgress(FileOperationProgress(totals.first, totals.first, source.name, totals.second, totals.second))
        } else {
            copy(source, destination, onProgress)
            currentCoroutineContext().ensureActive()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                delete(source, onProgress)
            }
        }
    }

    suspend fun createZip(
        sources: List<File>,
        destination: File,
        onProgress: (FileOperationProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "Select at least one file" }
        require(!destination.exists()) { "Archive already exists" }
        require(sources.none { Files.isSymbolicLink(it.toPath()) }) {
            "Symbolic links cannot be selected for archive creation"
        }
        val canonicalSources = sources.map { it.canonicalFile }
        require(canonicalSources.all { it.exists() && it.canRead() }) { "A selected item is no longer readable" }
        require(canonicalSources.none { it == destination.canonicalFile || (it.isDirectory && destination.canonicalPath.startsWith(it.canonicalPath + File.separator)) }) {
            "The archive cannot be created inside a selected folder"
        }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create archive folder" } }
        val temp = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.partial")
        val sourceStats = canonicalSources.map(::stats)
        val total = sourceStats.sumOf { it.first }
        val totalFiles = sourceStats.sumOf { it.second }
        var written = 0L
        var done = 0
        val usedNames = mutableSetOf<String>()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                suspend fun addNode(source: File, entryName: String) {
                    currentCoroutineContext().ensureActive()
                    require(!Files.isSymbolicLink(source.toPath())) { "Refusing to archive through a symbolic link: " + source.name }
                    if (Files.isDirectory(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        val dirName = entryName.trimEnd('/') + "/"
                        if (usedNames.add(dirName)) {
                            zip.putNextEntry(ZipEntry(dirName))
                            zip.closeEntry()
                        }
                        source.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
                            addNode(child, "$dirName${child.name}")
                        } ?: throw IllegalStateException("Cannot read folder ${source.name}")
                    } else {
                        require(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Unsupported filesystem entry: " + source.name }
                        val safeName = uniqueEntry(entryName, usedNames)
                        zip.putNextEntry(ZipEntry(safeName).apply { time = source.lastModified() })
                        FileInputStream(source).use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                zip.write(buffer, 0, read)
                                written += read
                                onProgress(FileOperationProgress(written, total, source.name, done, totalFiles))
                            }
                        }
                        zip.closeEntry()
                        done++
                        onProgress(FileOperationProgress(written, total, source.name, done, totalFiles))
                    }
                }
                canonicalSources.forEach { source ->
                    val rootName = if (source.isDirectory) uniqueEntry(source.name, usedNames) else source.name
                    addNode(source, rootName)
                }
            }
            currentCoroutineContext().ensureActive()
            require(temp.renameTo(destination)) { "Could not save archive" }
        } catch (failure: Throwable) {
            temp.delete()
            throw failure
        }
    }

    suspend fun extractZip(
        archive: File,
        destinationDirectory: File,
        onProgress: (FileOperationProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(archive.isFile && archive.canRead()) { "Archive is not readable" }
        require(!destinationDirectory.exists()) { "Extraction folder already exists" }
        destinationDirectory.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create extraction parent" } }
        val temp = File(destinationDirectory.parentFile, ".${destinationDirectory.name}.${UUID.randomUUID()}.partial")
        val entries = ZipFile(archive).use { zip ->
            val list = zip.entries().asSequence().toList()
            require(list.size <= MAX_ARCHIVE_ENTRIES) { "Archive contains too many entries" }
            val totalBytes = list.sumOf { if (it.isDirectory || it.size < 0L) 0L else it.size }
            require(totalBytes <= MAX_EXPANDED_BYTES) { "Archive expands beyond the 2 GB safety limit" }
            list
        }
        val total = entries.sumOf { if (it.isDirectory || it.size < 0L) 0L else it.size }
        var extracted = 0L
        var done = 0
        try {
            require(temp.mkdirs()) { "Cannot create temporary extraction folder" }
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = input.nextEntry ?: break
                    val target = File(temp, entry.name).canonicalFile
                    val rootPath = temp.canonicalPath + File.separator
                    require(target.canonicalPath.startsWith(rootPath) || target.canonicalPath == temp.canonicalPath) {
                        "Archive contains an unsafe path"
                    }
                    if (entry.isDirectory) {
                        require(target.mkdirs() || target.isDirectory) { "Cannot create extracted folder" }
                    } else {
                        target.parentFile?.let { require(it.mkdirs() || it.isDirectory) { "Cannot create extracted folder" } }
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                extracted += read
                                require(extracted <= MAX_EXPANDED_BYTES) { "Archive expands beyond the 2 GB safety limit" }
                                output.write(buffer, 0, read)
                                onProgress(FileOperationProgress(extracted, total, target.name, done, entries.size))
                            }
                            output.fd.sync()
                        }
                    }
                    done++
                    onProgress(FileOperationProgress(extracted, total, target.name, done, entries.size))
                    input.closeEntry()
                }
            }
            currentCoroutineContext().ensureActive()
            require(temp.renameTo(destinationDirectory)) { "Could not commit extracted files" }
        } catch (failure: Throwable) {
            temp.deleteRecursively()
            throw failure
        }
    }

    suspend fun delete(
        target: File,
        onProgress: (FileOperationProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(target.exists()) { "File no longer exists" }
        val stats = stats(target)
        var removedFiles = 0
        var removedBytes = 0L
        suspend fun deleteNode(file: File) {
            currentCoroutineContext().ensureActive()
            require(!Files.isSymbolicLink(file.toPath())) { "Refusing to delete through symbolic link: " + file.name }
            val isDirectory = Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)
            if (isDirectory) {
                file.listFiles()?.forEach { child -> deleteNode(child) }
            }
            val fileBytes = if (!isDirectory && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) file.length() else 0L
            require(file.delete()) { "Could not delete " + file.name }
            if (!isDirectory) {
                removedFiles++
                removedBytes += fileBytes
            }
            onProgress(FileOperationProgress(removedBytes, stats.first, file.name, removedFiles, stats.second))
        }
        deleteNode(target)
    }

    private fun uniqueEntry(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val file = File(name)
        val base = file.nameWithoutExtension
        val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 2
        while (true) {
            val candidate = file.parent?.let { "$it/${base}_$index$ext" } ?: "${base}_$index$ext"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    private fun stats(file: File): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        fun visit(node: File) {
            require(!Files.isSymbolicLink(node.toPath())) {
                "Symbolic links are not supported for file operations: " + node.name
            }
            if (Files.isDirectory(node.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val children = node.listFiles()
                    ?: throw IllegalStateException("Cannot read folder " + node.name)
                children.forEach(::visit)
            } else {
                require(Files.isRegularFile(node.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "Unsupported filesystem entry: " + node.name
                }
                bytes += node.length()
                files++
            }
        }
        visit(file)
        return bytes to files
    }
}
