package com.vaultos.app.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.Cursor
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StorageScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = PROCESS_SCAN_MUTEX.withLock {
        if (inputData.getBoolean(KEY_VAULT_ONLY, false)) {
            runCatching { VaultRepository(applicationContext).syncVaultContents() }
                .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        } else scanStorage()
    }

    private suspend fun scanStorage(): Result {
        val repository = VaultRepository(applicationContext)
        runCatching { repository.syncVaultContents() }
        val canScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            applicationContext.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!canScan) return Result.success(workDataOf("skipped" to "storage permission not granted"))

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initialScanDone = prefs.getBoolean(KEY_INITIAL_SCAN_DONE, false)
        if (initialScanDone) return scanRecentMediaStoreFiles(repository, prefs)

        val scanStartedAt = prefs.getLong(KEY_INITIAL_SCAN_STARTED_AT, 0L).takeIf { it > 0L }
            ?: (System.currentTimeMillis() / 1000L).also { started ->
                val edit = prefs.edit().putLong(KEY_INITIAL_SCAN_STARTED_AT, started)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    edit.putLong(KEY_INITIAL_GENERATION, MediaStore.getGeneration(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY))
                        .putString(KEY_INITIAL_MEDIASTORE_VERSION, MediaStore.getVersion(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY))
                }
                edit.apply()
            }
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists() || !root.isDirectory) return Result.retry()

        // Persist the directory frontier so very large phone storage can be scanned over
        // multiple bounded WorkManager executions instead of restarting at the root.
        val savedPaths = prefs.getStringSet(KEY_PENDING_DIRS, null)
        val queue = ArrayDeque<File>()
        if (!savedPaths.isNullOrEmpty()) savedPaths.forEach { queue.addLast(File(it)) }
        else queue.add(root)

        var processed = 0
        var visitedDirs = 0
        val startedAt = System.currentTimeMillis()
        fun checkpoint() {
            prefs.edit().putStringSet(KEY_PENDING_DIRS, queue.map { it.absolutePath }.toSet()).apply()
        }

        while (queue.isNotEmpty()) {
            if (isStopped) { checkpoint(); return Result.retry() }
            if (System.currentTimeMillis() - startedAt > MAX_RUN_MILLIS) {
                checkpoint()
                return Result.retry()
            }
            val directory = queue.removeFirst()
            if (!directory.exists() || !directory.isDirectory || isExcludedDirectory(directory)) continue
            val children = runCatching { directory.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            for (child in children) {
                if (child.isDirectory) {
                    if (!isExcludedDirectory(child)) queue.addLast(child)
                } else if (child.isFile && child.canRead() && !child.name.endsWith(".part", true)) {
                    runCatching {
                        if (!initialScanDone) repository.indexFile(child, "Existing device storage")
                        else repository.archiveIncomingFile(child, "Automatically captured from device storage")
                    }
                    processed++
                }
            }
            visitedDirs++
            if (visitedDirs % CHECKPOINT_EVERY_DIRS == 0) checkpoint()
            if (processed % 200 == 0 && processed > 0) setProgress(workDataOf("processed" to processed, "queuedDirectories" to queue.size))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val startingVersion = prefs.getString(KEY_INITIAL_MEDIASTORE_VERSION, null)
            val currentVersion = MediaStore.getVersion(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
            if (startingVersion != null && startingVersion != currentVersion) {
                val currentGeneration = MediaStore.getGeneration(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
                prefs.edit().remove(KEY_PENDING_DIRS).putBoolean(KEY_INITIAL_SCAN_DONE, false)
                    .putLong(KEY_INITIAL_SCAN_STARTED_AT, System.currentTimeMillis() / 1000L)
                    .putLong(KEY_INITIAL_GENERATION, currentGeneration)
                    .putString(KEY_INITIAL_MEDIASTORE_VERSION, currentVersion).apply()
                return Result.retry()
            }
        }
        val completionEdit = prefs.edit().remove(KEY_PENDING_DIRS)
            .putBoolean(KEY_INITIAL_SCAN_DONE, true)
            .putLong(KEY_FIRST_SCAN_AT, prefs.getLong(KEY_FIRST_SCAN_AT, System.currentTimeMillis()))
            // Start the incremental window at the beginning of the baseline scan. This
            // catches downloads created while a long first scan was running without
            // copying old files that pre-date the first scan.
            .putLong(KEY_LAST_INCREMENTAL_SCAN_AT, scanStartedAt)
            .remove(KEY_INITIAL_SCAN_STARTED_AT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            completionEdit.putLong(KEY_LAST_GENERATION, prefs.getLong(KEY_INITIAL_GENERATION, 0L))
                .putString(KEY_MEDIASTORE_VERSION, prefs.getString(KEY_INITIAL_MEDIASTORE_VERSION, "").orEmpty())
                .remove(KEY_INITIAL_GENERATION)
                .remove(KEY_INITIAL_MEDIASTORE_VERSION)
        }
        completionEdit.apply()
        return Result.success(workDataOf("processed" to processed, "initialScan" to true))
    }

    /** Uses MediaStore generations (Android 11+) or modification timestamps for routine catch-up. */
    private suspend fun scanRecentMediaStoreFiles(repository: VaultRepository, prefs: android.content.SharedPreferences): Result {
        val modernMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val currentVersion = if (modernMediaStore) MediaStore.getVersion(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY) else null
        val storedVersion = prefs.getString(KEY_MEDIASTORE_VERSION, null)
        if (modernMediaStore && !storedVersion.isNullOrBlank() && storedVersion != currentVersion) {
            // MediaStore generations are valid only within one version epoch. Rebuild the
            // baseline, then catch up from the generation captured at the rebuild start.
            val generation = MediaStore.getGeneration(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
            prefs.edit().putBoolean(KEY_INITIAL_SCAN_DONE, false).remove(KEY_PENDING_DIRS)
                .putLong(KEY_INITIAL_SCAN_STARTED_AT, System.currentTimeMillis() / 1000L)
                .putLong(KEY_INITIAL_GENERATION, generation)
                .putString(KEY_INITIAL_MEDIASTORE_VERSION, currentVersion).apply()
            return Result.retry()
        }

        val upperBound = if (modernMediaStore) MediaStore.getGeneration(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else System.currentTimeMillis() / 1000L
        val lowerBound = if (modernMediaStore) prefs.getLong(KEY_LAST_GENERATION, upperBound)
            else prefs.getLong(KEY_LAST_INCREMENTAL_SCAN_AT, prefs.getLong(KEY_FIRST_SCAN_AT, 0L) / 1000L)
        if (modernMediaStore && upperBound <= lowerBound) return Result.success(workDataOf("processed" to 0, "initialScan" to false))

        val changeColumn = if (modernMediaStore) MediaStore.MediaColumns.GENERATION_MODIFIED else MediaStore.MediaColumns.DATE_MODIFIED
        val selection = if (modernMediaStore) "$changeColumn > ? AND $changeColumn <= ?" else "$changeColumn >= ? AND $changeColumn <= ?"
        val since = if (modernMediaStore) lowerBound else (lowerBound - MEDIASTORE_OVERLAP_SECONDS).coerceAtLeast(0L)
        val projection = arrayOf(MediaStore.MediaColumns.DATA, changeColumn)
        var processed = 0
        val mediaVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        val uri = MediaStore.Files.getContentUri(mediaVolume)
        val cursor: Cursor = applicationContext.contentResolver.query(
            uri, projection, selection, arrayOf(since.toString(), upperBound.toString()), "$changeColumn ASC"
        ) ?: return Result.retry()

        try {
            val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (pathColumn < 0) return Result.failure(workDataOf("error" to "MediaStore path column unavailable"))
            while (cursor.moveToNext()) {
                if (isStopped) return Result.retry()
                val path = cursor.getString(pathColumn)?.takeIf { it.isNotBlank() } ?: continue
                val file = File(path)
                if (!file.isFile || !file.canRead() || isExcludedFile(file)) continue
                runCatching { repository.archiveIncomingFile(file, "Automatically captured from device storage") }
                processed++
                if (processed % 250 == 0) setProgress(workDataOf("processed" to processed, "mode" to "incremental"))
            }
        } finally {
            cursor.close()
        }
        val edit = prefs.edit().putLong(KEY_LAST_INCREMENTAL_SCAN_AT, upperBound)
        if (modernMediaStore) edit.putLong(KEY_LAST_GENERATION, upperBound).putString(KEY_MEDIASTORE_VERSION, currentVersion)
        edit.apply()
        return Result.success(workDataOf("processed" to processed, "initialScan" to false, "changeTracking" to if (modernMediaStore) "generation" else "modified-time"))
    }

    private fun isExcludedFile(file: File): Boolean {
        val path = file.absolutePath.replace('\\', '/').lowercase()
        return path.contains("/android/data/") || path.contains("/android/obb/") ||
            path.contains("/android/sandbox/") || file.name.startsWith(".") ||
            file.name.endsWith(".part", true) || file.name.endsWith(".tmp", true)
    }

    private fun isExcludedDirectory(directory: File): Boolean {
        val name = directory.name.lowercase()
        val path = directory.absolutePath.replace('\\', '/').lowercase()
        return name in setOf(".thumbnails", ".trash", "cache") ||
            path.contains("/android/data/") || path.endsWith("/android/data") ||
            path.contains("/android/obb/") || path.endsWith("/android/obb") ||
            path.contains("/android/sandbox/")
    }

    companion object {
        const val PREFS = "vaultos_preferences"
        const val KEY_INITIAL_SCAN_DONE = "initial_storage_scan_complete"
        const val KEY_FIRST_SCAN_AT = "first_storage_scan_at"
        const val KEY_VAULT_ONLY = "vault_only"
        private const val KEY_PENDING_DIRS = "storage_scan_pending_directories"
        private const val KEY_INITIAL_SCAN_STARTED_AT = "initial_storage_scan_started_at"
        private const val KEY_LAST_INCREMENTAL_SCAN_AT = "last_incremental_storage_scan_at"
        private const val KEY_INITIAL_GENERATION = "initial_media_store_generation"
        private const val KEY_INITIAL_MEDIASTORE_VERSION = "initial_media_store_version"
        private const val KEY_LAST_GENERATION = "last_media_store_generation"
        private const val KEY_MEDIASTORE_VERSION = "media_store_version"
        private const val MEDIASTORE_OVERLAP_SECONDS = 3L
        private const val MAX_RUN_MILLIS = 7 * 60 * 1000L
        private const val CHECKPOINT_EVERY_DIRS = 25
        private val PROCESS_SCAN_MUTEX = Mutex()
    }
}
