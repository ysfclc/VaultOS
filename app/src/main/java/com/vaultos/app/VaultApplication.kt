package com.vaultos.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultos.app.storage.StorageScanWorker

class VaultApplication : Application() {
    private var mediaObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        val handler = Handler(Looper.getMainLooper())
        mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = scheduleScan()
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) = scheduleScan()
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"), true, mediaObserver!!
            )
        }
    }

    private fun scheduleScan() {
        runCatching {
            WorkManager.getInstance(this).enqueueUniqueWork(
                "vaultos-storage-scan-now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StorageScanWorker>().build()
            )
        }
    }

    override fun onTerminate() {
        mediaObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onTerminate()
    }
}
