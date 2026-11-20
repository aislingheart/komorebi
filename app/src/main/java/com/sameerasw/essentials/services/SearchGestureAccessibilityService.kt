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
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sameerasw.essentials.assistant.ScreenCaptureStore
import com.sameerasw.essentials.assistant.ScreenContextExtractor
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity
import com.sameerasw.essentials.utils.AppLaunchUtil
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SearchGestureAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "SearchGestureService"
        private const val DOUBLE_PRESS_WINDOW_MS = 550L
        private const val TRIGGER_DEBOUNCE_MS = 220L
        private const val SCREENSHOT_TIMEOUT_MS = 180L
        private const val BACKDROP_MAX_WIDTH = 720
        @Volatile internal var instance: SearchGestureAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var accessibilityButtonController: AccessibilityButtonController? = null
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastHardwareKeyPressAt = 0L
    private var lastTriggerAt = 0L
    private var consumeNextUp = false
    private val capturingScreenshot = AtomicBoolean(false)

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

    internal fun openSearch(toggleIfShowing: Boolean = true) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { openSearch(toggleIfShowing) }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) return
        lastTriggerAt = now

        if (SearchOverlayController.isShowing) {
            if (toggleIfShowing) {
                SearchOverlayController.hide()
            }
            return
        }

        if (toggleIfShowing && PixelSearchResultsActivity.dismissIfForeground()) {
            return
        }

        runCatching {
            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg != packageName) {
                ScreenContextExtractor.capture(this)
            }
        }

        if (!SettingsRepository(this).getPixelSearchOverlayEnabled()) {
            launchSearchActivity()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && capturingScreenshot.compareAndSet(false, true)) {
            val overlayOpened = AtomicBoolean(false)
            val watchdog = Runnable {
                if (overlayOpened.compareAndSet(false, true)) {
                    capturingScreenshot.set(false)
                    showOverlay(null)
                }
            }
            mainHandler.postDelayed(watchdog, SCREENSHOT_TIMEOUT_MS)

            val started = runCatching {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val full = screenshot.toSoftwareBitmap()
                            val backdrop = downscaleForBackdrop(full)
                            ScreenCaptureStore.store(full)
                            mainHandler.post {
                                mainHandler.removeCallbacks(watchdog)
                                capturingScreenshot.set(false)
                                if (overlayOpened.compareAndSet(false, true)) {
                                    showOverlay(backdrop)
                                } else if (SearchOverlayController.isShowing && backdrop != null) {
                                    SearchOverlayController.updateBackdrop(backdrop)
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.d(TAG, "takeScreenshot skipped (code=$errorCode); opening overlay immediately")
                            mainHandler.post {
                                mainHandler.removeCallbacks(watchdog)
                                capturingScreenshot.set(false)
                                if (overlayOpened.compareAndSet(false, true)) {
                                    showOverlay(null)
                                }
                            }
                        }
                    },
                )
            }.onFailure { error ->
                Log.w(TAG, "takeScreenshot threw; opening overlay immediately", error)
            }.isSuccess

            if (started) return
            mainHandler.removeCallbacks(watchdog)
            capturingScreenshot.set(false)
        }
        showOverlay(null)
    }

    private fun showOverlay(backdrop: Bitmap?) {
        val shown = runCatching { SearchOverlayController.show(this, backdrop) }
            .onFailure { error ->
                Log.e(TAG, "Unable to show search overlay", error)
            }
            .getOrDefault(false)
        if (shown) return
        launchSearchActivity()
    }

    private fun launchSearchActivity() {
        val intent = AppLaunchUtil.buildSearchResultsIntent(this)
        AppLaunchUtil.startActivityReliably(this, intent)
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
        mainHandler.removeCallbacksAndMessages(null)
        capturingScreenshot.set(false)
        SearchOverlayController.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buttonCallback != null) {
            accessibilityButtonController?.unregisterAccessibilityButtonCallback(buttonCallback!!)
        }
        buttonCallback = null
        accessibilityButtonController = null
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }
}
