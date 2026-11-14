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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.utils.ShizukuUtils
import eu.darken.porter.bridge.PorterShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.sentry.android.core.SentryAndroid
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

        PorterShizukuBridge.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        ShizukuUtils.initialize()
        com.sameerasw.essentials.utils.LogManager
            .init(this)

        com.sameerasw.essentials.services.CalendarSyncManager
            .init(this)
        com.sameerasw.essentials.utils.ServiceUtils
            .startRequiredServices(this)

        com.sameerasw.essentials.appfunctions.AppFunctionAvailabilityManager
            .updateAvailability(this)

        initSentry()
    }

    private fun initSentry() {
        val repository = SettingsRepository(this)
        val mode = repository.getString(SettingsRepository.KEY_SENTRY_REPORT_MODE, "auto")

        if (mode == "off") return

        SentryAndroid.init(this) { options ->
            options.dsn =
                "https://e105699467efe3a43a16bfbad3a63b33@o4510996760887296.ingest.de.sentry.io/4510996763312208"
            options.isEnabled = true

            options.setBeforeSend { event, hint ->
                try {
                    val throwable = event.throwable
                    val threadName = event.threads?.firstOrNull()?.name ?: Thread.currentThread().name
                    val message = event.message?.formatted ?: event.exceptions?.firstOrNull()?.value
                    com.sameerasw.essentials.utils.LogManager.saveCrashReport(
                        context = this@EssentialsApp,
                        threadName = threadName,
                        throwable = throwable,
                        customMessage = message,
                    )
                } catch (e: Exception) {
                    android.util.Log.e("EssentialsApp", "Failed to save crash report locally in Sentry callback", e)
                }

                Handler(Looper.getMainLooper()).post {
                    Toast
                        .makeText(
                            this@EssentialsApp,
                            R.string.sentry_crash_toast,
                            Toast.LENGTH_LONG,
                        ).show()
                }
                event
            }
        }
    }
}
