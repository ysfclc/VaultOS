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
    private val scanHandler = Handler(Looper.getMainLooper())
    private val enqueueScan = Runnable {
        runCatching {
            WorkManager.getInstance(this).enqueueUniqueWork(
                "vaultos-storage-scan-now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StorageScanWorker>().build()
            )
        }
    }
    private var mediaObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        mediaObserver = object : ContentObserver(scanHandler) {
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
        scanHandler.removeCallbacks(enqueueScan)
        scanHandler.postDelayed(enqueueScan, SCAN_DEBOUNCE_MILLIS)
    }

    override fun onTerminate() {
        scanHandler.removeCallbacks(enqueueScan)
        mediaObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onTerminate()
    }

    private companion object {
        const val SCAN_DEBOUNCE_MILLIS = 30_000L
    }
}
