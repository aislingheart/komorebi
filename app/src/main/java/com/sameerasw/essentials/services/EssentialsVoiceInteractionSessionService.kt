/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: EssentialsVoiceInteractionSessionService.kt
 * Description: Voice interaction session service routing assistant invocations to search results.
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
import com.sameerasw.essentials.utils.AppLaunchUtil

class EssentialsVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return EssentialsVoiceInteractionSession(this)
    }
}

class EssentialsVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val query = AppLaunchUtil.extractSafeQuery(args)

        // If overlay mode is enabled and the accessibility service is connected (with no prefilled query),
        // open the exact same zero-flicker overlay surface used by the accessibility gesture.
        val accessibilityService = SearchGestureAccessibilityService.instance
        if (
            query.isEmpty() &&
            accessibilityService != null &&
            runCatching { SettingsRepository(context).getPixelSearchOverlayEnabled() }.getOrDefault(true)
        ) {
            accessibilityService.openSearch(toggleIfShowing = true, openedViaGesture = true)
            mainHandler.post { runCatching { hide() } }
            return
        }

        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }

        val intent = AppLaunchUtil.buildSearchResultsIntent(context, query, openedViaGesture = true)
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
        mainHandler.postDelayed({ runCatching { hide() } }, 140L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
