/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: EssentialsVoiceInteractionSessionService.kt
 * Description: Voice interaction session service routing assistant invocations to Komorebi search.
 */

package com.sameerasw.essentials.services

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity
import com.sameerasw.essentials.utils.AppLaunchUtil
import com.sameerasw.essentials.utils.SearchLaunchOrigin

class EssentialsVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return EssentialsVoiceInteractionSession(this)
    }
}

class EssentialsVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        // Never allocate or display the default empty VoiceInteractionSession window:
        // it steals focus, dismisses the IME on hide(), and pollutes Accessibility screenshots.
        runCatching { setUiEnabled(false) }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val query = AppLaunchUtil.extractSafeQuery(args)

        if (query.isEmpty() && SearchGestureAccessibilityService.isDuplicateLaunchBurst()) {
            Log.d("EssentialsAssistant", "Skipping duplicate assistant invocation burst")
            mainHandler.post { runCatching { hide() } }
            return
        }

        // If overlay mode is enabled and the accessibility service is connected (with no prefilled query),
        // route directly to the Accessibility overlay after releasing the voice session cleanly.
        val accessibilityService = SearchGestureAccessibilityService.instance
        val overlayEnabled = runCatching {
            SettingsRepository(context).getPixelSearchOverlayEnabled()
        }.getOrDefault(true)
        val assistantOrigin = SearchLaunchOrigin.powerButton()

        if (query.isEmpty() && accessibilityService != null && overlayEnabled) {
            runCatching { hide() }
            mainHandler.post {
                accessibilityService.openSearch(
                    toggleIfShowing = true,
                    openedViaGesture = true,
                    launchOrigin = assistantOrigin,
                )
            }
            return
        }

        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
            if (query.isEmpty()) {
                runCatching { hide() }
                return
            }
        }

        if (query.isEmpty() && PixelSearchResultsActivity.dismissIfForeground()) {
            runCatching { hide() }
            return
        }

        SearchGestureAccessibilityService.markGlobalLaunch()

        val intent = AppLaunchUtil.buildSearchResultsIntent(
            context = context,
            query = query,
            openedViaGesture = true,
            launchOrigin = assistantOrigin,
        )
        val launchedViaAssistant =
            runCatching {
                startAssistantActivity(intent)
                true
            }.onFailure { error ->
                Log.w("EssentialsAssistant", "startAssistantActivity failed; falling back to AppLaunchUtil", error)
            }.getOrDefault(false)

        if (!launchedViaAssistant) {
            AppLaunchUtil.startActivityReliably(context, intent)
        }

        // Defer hiding the session slightly so ActivityTaskManager completes the launch handoff cleanly.
        mainHandler.postDelayed({ runCatching { hide() } }, 120L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
