/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: SearchGestureAccessibilityService.kt
 * Description: Universal accessibility gesture that opens the Komorebi search surface from any app.
 */

package com.sameerasw.essentials.services

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity

class SearchGestureAccessibilityService : AccessibilityService() {
    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 550L
    }

    private var accessibilityButtonController: AccessibilityButtonController? = null
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastHardwareKeyPressAt = 0L
    private var consumeNextUp = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController = getAccessibilityButtonController()
            buttonCallback =
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        openSearch()
                    }

                    override fun onAvailabilityChanged(
                        controller: AccessibilityButtonController,
                        available: Boolean,
                    ) {
                    }
                }
            accessibilityButtonController?.registerAccessibilityButtonCallback(buttonCallback!!)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        SearchOverlayController.hide()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isHomeOrSearch =
            event.keyCode == KeyEvent.KEYCODE_HOME || event.keyCode == KeyEvent.KEYCODE_SEARCH
        if (!isHomeOrSearch) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = SystemClock.elapsedRealtime()
            val isDoublePress =
                lastHardwareKeyPressAt > 0L && now - lastHardwareKeyPressAt <= DOUBLE_PRESS_WINDOW_MS
            lastHardwareKeyPressAt = now
            if (isDoublePress) {
                consumeNextUp = true
                openSearch()
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP && consumeNextUp) {
            consumeNextUp = false
            return true
        }
        return false
    }

    private fun openSearch() {
        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
            return
        }
        if (SettingsRepository(this).getPixelSearchOverlayEnabled() &&
            SearchOverlayController.show(this)
        ) {
            return
        }
        val intent =
            Intent(this, PixelSearchResultsActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
        runCatching { startActivity(intent) }
    }

    override fun onDestroy() {
        SearchOverlayController.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buttonCallback != null) {
            accessibilityButtonController?.unregisterAccessibilityButtonCallback(buttonCallback!!)
        }
        buttonCallback = null
        accessibilityButtonController = null
        super.onDestroy()
    }
}
