/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchActivity.kt
 * Description: Invisible trampoline that opens the search surface.
 */

package com.sameerasw.essentials

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.services.SearchGestureAccessibilityService
import com.sameerasw.essentials.services.SearchOverlayController
import com.sameerasw.essentials.utils.AppLaunchUtil

class PixelSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchSearch(intent)
    }

    private fun launchSearch(incoming: Intent?) {
        val showUpdateSheet =
            incoming?.getBooleanExtra(
                com.sameerasw.essentials.utils.UpdateNotificationHelper.EXTRA_SHOW_UPDATE_SHEET,
                false,
            ) == true
        if (showUpdateSheet) {
            runCatching {
                startActivity(
                    AppLaunchUtil.buildSearchResultsIntent(
                        context = this,
                        showUpdateSheet = true,
                        launchOrigin = com.sameerasw.essentials.utils.SearchLaunchOrigin.fromIntent(this, incoming),
                    ),
                )
            }
            finishSilently()
            return
        }

        val query = incoming?.getStringExtra(SearchManager.QUERY)
            ?: incoming?.getStringExtra("query")
            ?: ""

        val action = incoming?.action
        val isAssistantAction =
            action == Intent.ACTION_ASSIST ||
                action == Intent.ACTION_SEARCH_LONG_PRESS ||
                action == Intent.ACTION_VOICE_COMMAND

        val resolvedOrigin = if (isAssistantAction) {
            com.sameerasw.essentials.utils.SearchLaunchOrigin.powerButton()
        } else {
            com.sameerasw.essentials.utils.SearchLaunchOrigin.fromIntent(this, incoming)
        }

        // Suppress duplicate ACTION_ASSIST intents fired simultaneously with VoiceInteractionSession.
        if (isAssistantAction && query.isEmpty() && SearchGestureAccessibilityService.isDuplicateLaunchBurst()) {
            Log.d("PixelSearchActivity", "Suppressing duplicate assistant trampoline launch")
            finishSilently()
            return
        }

        val accessibilityService = SearchGestureAccessibilityService.instance
        if (
            isAssistantAction &&
            query.isEmpty() &&
            accessibilityService != null &&
            runCatching { SettingsRepository(this).getPixelSearchOverlayEnabled() }.getOrDefault(true)
        ) {
            finishSilently()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                {
                    SearchGestureAccessibilityService.instance?.openSearch(
                        toggleIfShowing = true,
                        openedViaGesture = true,
                        launchOrigin = resolvedOrigin,
                    )
                },
                32L,
            )
            return
        }

        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }

        SearchGestureAccessibilityService.markGlobalLaunch()

        try {
            val intent = AppLaunchUtil.buildSearchResultsIntent(
                context = this,
                query = query,
                openedViaGesture = true,
                launchOrigin = resolvedOrigin,
            )
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e("PixelSearchActivity", "Failed to open search results", t)
            runCatching {
                startActivity(
                    Intent(this, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }

        finishSilently()
    }

    private fun finishSilently() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { overridePendingTransition(0, 0) }
        }
    }
}
