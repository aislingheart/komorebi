/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Core Application Entrypoint
 * File: EssentialsApp.kt
 * Description: Main application entrypoint for EssentialsApp.kt.
 */

package com.sameerasw.essentials

import android.app.Application
import android.content.Context
import com.sameerasw.essentials.utils.ShizukuUtils
import eu.darken.porter.bridge.PorterShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.lsposed.hiddenapibypass.HiddenApiBypass

class EssentialsApp : Application() {
    companion object {
        lateinit var context: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        com.sameerasw.essentials.utils.HapticUtil.initialize(this)

        try {
            resources?.configuration
        } catch (e: Exception) {
        }

        try {
            HiddenApiBypass.setHiddenApiExemptions("")
        } catch (_: Throwable) {
        }

        runCatching {
            PorterShizukuBridge.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }.onFailure {
            android.util.Log.e("EssentialsApp", "Porter bridge failed to start", it)
        }

        runCatching { ShizukuUtils.initialize() }
            .onFailure { android.util.Log.e("EssentialsApp", "Shizuku init failed", it) }

        runCatching {
            com.sameerasw.essentials.utils.LogManager
                .init(this)
        }.onFailure { android.util.Log.e("EssentialsApp", "LogManager init failed", it) }

        runCatching {
            com.sameerasw.essentials.services.CalendarSyncManager
                .init(this)
        }.onFailure { android.util.Log.e("EssentialsApp", "Calendar sync init failed", it) }

        runCatching {
            com.sameerasw.essentials.utils.ServiceUtils
                .startRequiredServices(this)
        }.onFailure { android.util.Log.e("EssentialsApp", "Service startup failed", it) }

        runCatching {
            com.sameerasw.essentials.appfunctions.AppFunctionAvailabilityManager
                .updateAvailability(this)
        }.onFailure { android.util.Log.e("EssentialsApp", "App function init failed", it) }

        runCatching {
            com.sameerasw.essentials.launcher.ClipboardHistoryManager
                .startObserving(this)
        }.onFailure { android.util.Log.e("EssentialsApp", "Clipboard observer init failed", it) }
    }
}
