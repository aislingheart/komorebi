/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: DownloadUpdateReceiver.kt
 * Description: BroadcastReceiver handling notification Download action by enqueuing AppUpdateDownloadWorker.
 */

package com.sameerasw.essentials.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sameerasw.essentials.services.AppUpdateDownloadWorker
import com.sameerasw.essentials.utils.UpdateNotificationHelper

class DownloadUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != UpdateNotificationHelper.ACTION_DOWNLOAD_UPDATE) return

        val downloadUrl = intent.getStringExtra(UpdateNotificationHelper.EXTRA_DOWNLOAD_URL).orEmpty()
        val version = intent.getStringExtra(UpdateNotificationHelper.EXTRA_VERSION).orEmpty()
        if (downloadUrl.isBlank()) return

        val workData =
            workDataOf(
                AppUpdateDownloadWorker.KEY_DOWNLOAD_URL to downloadUrl,
                AppUpdateDownloadWorker.KEY_VERSION to version,
            )

        val request =
            OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                .setInputData(workData)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AppUpdateDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
