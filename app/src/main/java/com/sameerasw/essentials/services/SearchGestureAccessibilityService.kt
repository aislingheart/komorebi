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
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sameerasw.essentials.assistant.AssistantAiMode
import com.sameerasw.essentials.assistant.AssistantAiSettings
import com.sameerasw.essentials.assistant.ScreenContextExtractor
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity

class SearchGestureAccessibilityService : AccessibilityService() {
    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 550L
        private const val BACKDROP_MAX_WIDTH = 720
        @Volatile internal var instance: SearchGestureAccessibilityService? = null
    }

    private var accessibilityButtonController: AccessibilityButtonController? = null
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastHardwareKeyPressAt = 0L
    private var consumeNextUp = false
    @Volatile private var capturingScreenshot = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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

    internal fun openSearch() {
        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
            return
        }
        ScreenContextExtractor.capture(this)
        if (!SettingsRepository(this).getPixelSearchOverlayEnabled() ||
            AssistantAiSettings.getMode(this) == AssistantAiMode.ON_DEVICE
        ) {
            launchSearchActivity()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !capturingScreenshot) {
            capturingScreenshot = true
            val started = runCatching {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            capturingScreenshot = false
                            val full = screenshot.toSoftwareBitmap()
                            com.sameerasw.essentials.assistant.ScreenCaptureStore.store(full)
                            showOverlay(downscaleForBackdrop(full))
                        }

                        override fun onFailure(errorCode: Int) {
                            capturingScreenshot = false
                            showOverlay(null)
                        }
                    },
                )
            }.isSuccess
            if (started) return
            capturingScreenshot = false
        }
        showOverlay(null)
    }

    private fun showOverlay(backdrop: Bitmap?) {
        val shown = runCatching { SearchOverlayController.show(this, backdrop) }
            .onFailure { error ->
                android.util.Log.e("SearchGestureService", "Unable to show search overlay", error)
            }
            .getOrDefault(false)
        if (shown) return
        launchSearchActivity()
    }

    private fun launchSearchActivity() {
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {
        val buffer = hardwareBuffer
        return try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
            hardware?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { buffer.close() }
        }
    }

    private fun downscaleForBackdrop(source: Bitmap?): Bitmap? {
        if (source == null) return null
        if (source.width <= BACKDROP_MAX_WIDTH) return source
        val height = (source.height * (BACKDROP_MAX_WIDTH.toFloat() / source.width))
            .toInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, BACKDROP_MAX_WIDTH, height, true)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        SearchOverlayController.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buttonCallback != null) {
            accessibilityButtonController?.unregisterAccessibilityButtonCallback(buttonCallback!!)
        }
        buttonCallback = null
        accessibilityButtonController = null
        super.onDestroy()
    }
}
