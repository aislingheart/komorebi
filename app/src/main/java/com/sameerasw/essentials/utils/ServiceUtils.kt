/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities - General
 * File: ServiceUtils.kt
 * Description: Utility helper for ServiceUtils.kt.
 */

package com.sameerasw.essentials.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.services.AppUpdateWorker
import java.util.concurrent.TimeUnit

object ServiceUtils {
    private const val APP_UPDATE_WORK_NAME = "app_update_check_work"

    /**
     * Executes the start required services operation.
     *
     * @param context [Context] Target context.
     */
    fun startRequiredServices(context: Context) {
        val settingsRepository = SettingsRepository(context)
        if (settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_UPDATE_ENABLED, true)) {
            schedulePeriodicAppUpdateCheck(context)
        } else {
            cancelPeriodicAppUpdateCheck(context)
        }
    }

    fun schedulePeriodicAppUpdateCheck(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val updateCheckWork =
            PeriodicWorkRequestBuilder<AppUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            APP_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateCheckWork,
        )
    }

    fun cancelPeriodicAppUpdateCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(APP_UPDATE_WORK_NAME)
    }
}
