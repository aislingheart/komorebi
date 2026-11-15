/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: AppUpdateWorker.kt
 * Description: Periodic background worker checking GitHub releases for Komorebi updates.
 */

package com.sameerasw.essentials.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.data.repository.UpdateRepository
import com.sameerasw.essentials.utils.UpdateNotificationHelper
import com.sameerasw.essentials.viewmodels.MainViewModel

class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing periodic update check")
        val context = applicationContext
        val settingsRepository = SettingsRepository(context)
        val updateRepository = UpdateRepository()

        return try {
            val isAutoUpdateEnabled =
                settingsRepository.getBoolean(
                    SettingsRepository.KEY_AUTO_UPDATE_ENABLED,
                    true,
                )
            if (!isAutoUpdateEnabled) {
                return Result.success()
            }

            val currentVersion =
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    "0.0"
                } ?: "0.0"

            val isPreReleaseEnabled =
                settingsRepository.getBoolean(
                    SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED,
                    false,
                )

            val updateInfo =
                updateRepository.checkForUpdates(context, isPreReleaseEnabled, currentVersion)

            if (updateInfo != null) {
                MainViewModel.cachedIsUpdateAvailable = updateInfo.isUpdateAvailable
                MainViewModel.cachedUpdateInfo = updateInfo

                if (updateInfo.isUpdateAvailable && updateInfo.downloadUrl.isNotEmpty()) {
                    val isNotifEnabled =
                        settingsRepository.getBoolean(
                            SettingsRepository.KEY_UPDATE_NOTIFICATION_ENABLED,
                            true,
                        )
                    if (isNotifEnabled) {
                        UpdateNotificationHelper.showUpdateNotification(
                            context,
                            updateInfo.versionName,
                            updateInfo.downloadUrl,
                        )
                    }
                }

                settingsRepository.putLong(
                    SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME,
                    System.currentTimeMillis(),
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic update check", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
    }
}
