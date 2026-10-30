package com.vaultos.app.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class DownloadCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<StorageScanWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "vaultos-storage-scan-now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
