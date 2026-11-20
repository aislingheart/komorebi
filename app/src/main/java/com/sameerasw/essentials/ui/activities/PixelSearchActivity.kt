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
        if (incoming?.getBooleanExtra(
                com.sameerasw.essentials.utils.UpdateNotificationHelper.EXTRA_SHOW_UPDATE_SHEET,
                false,
            ) == true
        ) {
            runCatching {
                startActivity(
                    Intent(this, SettingsActivity::class.java).apply {
                        putExtra(
                            com.sameerasw.essentials.utils.UpdateNotificationHelper.EXTRA_SHOW_UPDATE_SHEET,
                            true,
                        )
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )
                    },
                )
            }
            finish()
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

        val accessibilityService = SearchGestureAccessibilityService.instance
        if (
            isAssistantAction &&
            query.isEmpty() &&
            accessibilityService != null &&
            runCatching { SettingsRepository(this).getPixelSearchOverlayEnabled() }.getOrDefault(true)
        ) {
            accessibilityService.openSearch(toggleIfShowing = true, openedViaGesture = true)
            finish()
            return
        }

        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }

        try {
            val intent = AppLaunchUtil.buildSearchResultsIntent(this, query, openedViaGesture = isAssistantAction)
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

        finish()
    }
}
