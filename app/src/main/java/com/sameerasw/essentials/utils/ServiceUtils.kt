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
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ServiceUtils {
    /**
     * Executes the start required services operation.
     *
     * @param context [Context] Target context.
     */
    fun startRequiredServices(context: Context) {
        val settingsRepository = SettingsRepository(context)
        applyPostRebootSettings(context, settingsRepository)
    }

    fun applyPostRebootSettings(
        context: Context,
        settingsRepository: SettingsRepository,
    ) {
        if (!ShizukuUtils.isShizukuAvailable() || !ShizukuUtils.hasPermission()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            StatusBarManager.update(context)

            if (settingsRepository.isSimNamesApplyOnBootEnabled()) {
                SimCarrierUtil.applySavedCarrierNames(context)
            }
        }
    }
}
