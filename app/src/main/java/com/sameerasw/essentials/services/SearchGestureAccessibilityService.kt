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
import android.content.Intent
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
import java.util.concurrent.atomic.AtomicLong

class SearchGestureAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "SearchGestureService"
        private const val DOUBLE_PRESS_WINDOW_MS = 550L
        private const val TRIGGER_DEBOUNCE_MS = 280L
        private const val CROSS_PATH_DEDUPE_MS = 400L
        private const val SCREENSHOT_COOLDOWN_MS = 1050L
        private const val SCREENSHOT_TIMEOUT_MS = 240L
        private const val BACKDROP_MAX_WIDTH = 720

        @Volatile
        internal var instance: SearchGestureAccessibilityService? = null

        private val lastGlobalLaunchAt = AtomicLong(0L)

        /**
         * Returns true if a launch was initiated within [CROSS_PATH_DEDUPE_MS] across any entry point
         * (VoiceInteractionSession, ACTION_ASSIST trampoline, Accessibility button, hardware key).
         */
        internal fun isDuplicateLaunchBurst(): Boolean {
            val prev = lastGlobalLaunchAt.get()
            if (prev <= 0L) return false
            return SystemClock.elapsedRealtime() - prev < CROSS_PATH_DEDUPE_MS
        }

        internal fun markGlobalLaunch() {
            lastGlobalLaunchAt.set(SystemClock.elapsedRealtime())
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var accessibilityButtonController: AccessibilityButtonController? = null
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastHardwareKeyPressAt = 0L
    private var lastTriggerAt = 0L
    private var lastScreenshotAttemptAt = 0L
    private var consumeNextUp = false
    private val capturingScreenshot = AtomicBoolean(false)
    private val launchGeneration = AtomicLong(0L)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Unregister any previously registered callback to prevent double-firing on service reconnect.
            buttonCallback?.let { prev ->
                runCatching { accessibilityButtonController?.unregisterAccessibilityButtonCallback(prev) }
            }
            val controller = getAccessibilityButtonController()
            accessibilityButtonController = controller
            val callback =
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        openSearch(toggleIfShowing = true, openedViaGesture = true)
                    }

                    override fun onAvailabilityChanged(
                        controller: AccessibilityButtonController,
                        available: Boolean,
                    ) {
                    }
                }
            buttonCallback = callback
            runCatching { controller.registerAccessibilityButtonCallback(callback) }
                .onFailure { error -> Log.w(TAG, "Failed to register accessibility button callback", error) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        launchGeneration.incrementAndGet()
        capturingScreenshot.set(false)
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
                openSearch(toggleIfShowing = true, openedViaGesture = true)
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP && consumeNextUp) {
            consumeNextUp = false
            return true
        }
        return false
    }

    internal fun openSearch(
        toggleIfShowing: Boolean = true,
        openedViaGesture: Boolean = true,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { openSearch(toggleIfShowing, openedViaGesture) }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring debounced search trigger (${now - lastTriggerAt}ms)")
            return
        }
        lastTriggerAt = now

        if (SearchOverlayController.isActivelyVisible()) {
            if (toggleIfShowing) {
                launchGeneration.incrementAndGet()
                capturingScreenshot.set(false)
                SearchOverlayController.hide()
            }
            return
        }

        if (toggleIfShowing && PixelSearchResultsActivity.dismissIfForeground()) {
            launchGeneration.incrementAndGet()
            capturingScreenshot.set(false)
            return
        }

        // Prevent a stale/half-detached overlay from colliding with a fresh launch.
        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }

        markGlobalLaunch()
        val generation = launchGeneration.incrementAndGet()

        runCatching {
            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg != packageName) {
                ScreenContextExtractor.capture(this)
            }
        }

        val overlayEnabled = runCatching {
            SettingsRepository(this).getPixelSearchOverlayEnabled()
        }.getOrDefault(true)
        if (!overlayEnabled) {
            launchSearchActivity(openedViaGesture)
            return
        }

        val canAttemptScreenshot =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !screenshotExecutor.isShutdown &&
                (now - lastScreenshotAttemptAt >= SCREENSHOT_COOLDOWN_MS) &&
                capturingScreenshot.compareAndSet(false, true)

        if (canAttemptScreenshot) {
            lastScreenshotAttemptAt = now
            val overlayOpened = AtomicBoolean(false)
            val watchdog = Runnable {
                if (launchGeneration.get() != generation) {
                    capturingScreenshot.set(false)
                    return@Runnable
                }
                if (overlayOpened.compareAndSet(false, true)) {
                    capturingScreenshot.set(false)
                    showOverlay(null, openedViaGesture)
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
                                if (launchGeneration.get() != generation) {
                                    // Trigger was superseded or dismissed before screenshot returned.
                                    return@post
                                }
                                if (overlayOpened.compareAndSet(false, true)) {
                                    showOverlay(backdrop, openedViaGesture)
                                } else if (SearchOverlayController.isActivelyVisible() && backdrop != null) {
                                    SearchOverlayController.updateBackdrop(backdrop)
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.d(TAG, "takeScreenshot skipped (code=$errorCode); opening overlay immediately")
                            mainHandler.post {
                                mainHandler.removeCallbacks(watchdog)
                                capturingScreenshot.set(false)
                                if (launchGeneration.get() != generation) return@post
                                if (overlayOpened.compareAndSet(false, true)) {
                                    showOverlay(null, openedViaGesture)
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

        showOverlay(null, openedViaGesture)
    }

    private fun showOverlay(
        backdrop: Bitmap?,
        openedViaGesture: Boolean,
    ) {
        val shown = runCatching { SearchOverlayController.show(this, backdrop, openedViaGesture) }
            .onFailure { error ->
                Log.e(TAG, "Unable to show search overlay", error)
            }
            .getOrDefault(false)
        if (shown) return
        launchSearchActivity(openedViaGesture)
    }

    private fun launchSearchActivity(openedViaGesture: Boolean) {
        val intent = AppLaunchUtil.buildSearchResultsIntent(this, openedViaGesture = openedViaGesture)
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

    private fun cleanupServiceResources() {
        if (instance === this) instance = null
        launchGeneration.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        capturingScreenshot.set(false)
        SearchOverlayController.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buttonCallback != null) {
            runCatching {
                accessibilityButtonController?.unregisterAccessibilityButtonCallback(buttonCallback!!)
            }
        }
        buttonCallback = null
        accessibilityButtonController = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupServiceResources()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupServiceResources()
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }
}
