/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: AppUpdateDownloadWorker.kt
 * Description: Background worker downloading and launching installation for Komorebi APK updates.
 */

package com.sameerasw.essentials.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sameerasw.essentials.utils.AutoUpdateManagerHelper
import com.sameerasw.essentials.utils.UpdateNotificationHelper

class AppUpdateDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL).orEmpty()
        val version = inputData.getString(KEY_VERSION).orEmpty()
        if (downloadUrl.isBlank()) {
            return Result.failure()
        }

        val context = applicationContext
        val helper = AutoUpdateManagerHelper(context)
        val cleanVersion = version.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "latest" }

        UpdateNotificationHelper.showDownloadProgressNotification(context, version, 0)

        val result =
            helper.downloadAndInstallApk(
                apkUrl = downloadUrl,
                apkName = "Komorebi_$cleanVersion",
                autoInstall = false,
                onProgressUpdate = { progress ->
                    if (progress < 100) {
                        UpdateNotificationHelper.showDownloadProgressNotification(
                            context,
                            version,
                            progress,
                        )
                    }
                },
            )

        return when (result) {
            is AutoUpdateManagerHelper.DownloadResult.Success -> {
                val installIntent = helper.buildInstallIntent(context, result.apkFile)
                UpdateNotificationHelper.showReadyToInstallNotification(
                    context = context,
                    version = version,
                    installIntent = installIntent,
                )
                helper.installApk(context, result.apkFile)
                Result.success()
            }

            is AutoUpdateManagerHelper.DownloadResult.Failed -> {
                UpdateNotificationHelper.showDownloadFailedNotification(context)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_VERSION = "version"
        const val WORK_NAME = "app_update_download_work"
    }
}
