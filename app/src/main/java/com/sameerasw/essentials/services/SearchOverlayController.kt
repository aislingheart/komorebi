/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: SearchOverlayController.kt
 * Description: Accessibility overlay host for the Komorebi search surface, built on UnifiedOverlayWindowHost.
 */

package com.sameerasw.essentials.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.assistant.ScreenCaptureStore
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsScreen
import com.sameerasw.essentials.ui.modifiers.GestureRippleWaveOverlay
import com.sameerasw.essentials.ui.modifiers.LocalGestureRippleController
import com.sameerasw.essentials.ui.modifiers.gestureRippleRefraction
import com.sameerasw.essentials.ui.modifiers.interactiveGestureRipple
import com.sameerasw.essentials.ui.modifiers.isGestureRippleEnabled
import com.sameerasw.essentials.ui.modifiers.originExpansionReveal
import com.sameerasw.essentials.ui.modifiers.rememberGestureRippleController
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.utils.SearchLaunchOrigin
import com.sameerasw.essentials.utils.overlay.OverlayVisibilityCoordinator
import com.sameerasw.essentials.utils.overlay.UnifiedOverlayWindowHost
import java.util.concurrent.atomic.AtomicLong

object SearchOverlayController {
    private const val TAG = "SearchOverlayController"
    private const val OVERLAY_ID = "komorebi_search_overlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: UnifiedOverlayWindowHost? = null
    private var windowView: View? = null
    private var windowManager: WindowManager? = null
    private val backdropState = mutableStateOf<Bitmap?>(null)
    private val gestureRippleTriggerState = mutableStateOf(0)
    private val launchRevealTriggerState = mutableStateOf(0)
    private val launchOriginState = mutableStateOf(SearchLaunchOrigin.accessibilityBottom())
    private var pendingHideRunnable: Runnable? = null
    private val sessionGeneration = AtomicLong(0L)

    @Volatile
    var isShowing: Boolean = false
        private set

    fun isActivelyVisible(): Boolean {
        if (!isShowing || pendingHideRunnable != null) return false
        val view = windowView ?: return false
        return view.isAttachedToWindow
    }

    fun currentGeneration(): Long = sessionGeneration.get()

    fun show(
        context: Context,
        backdrop: Bitmap? = null,
        openedViaGesture: Boolean = false,
        launchOrigin: SearchLaunchOrigin = SearchLaunchOrigin.accessibilityBottom(),
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "show() called off main thread; posting to main")
            mainHandler.post { show(context, backdrop, openedViaGesture, launchOrigin) }
            return true
        }

        // If a deferred hide was queued, flush it cleanly so we re-open without racing teardown.
        if (pendingHideRunnable != null) {
            hideInternal(clearCapture = false)
        }

        // Guard against a stale window that Android detached externally without calling hide().
        if (isShowing && windowView?.isAttachedToWindow != true) {
            Log.w(TAG, "Detected stale detached search overlay; resetting before show")
            hideInternal(clearCapture = false)
        }

        if (isShowing) {
            if (backdrop != null) backdropState.value = backdrop
            launchOriginState.value = launchOrigin
            launchRevealTriggerState.value += 1
            if (openedViaGesture) {
                gestureRippleTriggerState.value += 1
            }
            runCatching { windowView?.requestFocus() }
            return true
        }

        sessionGeneration.incrementAndGet()
        backdropState.value = backdrop
        launchOriginState.value = launchOrigin
        launchRevealTriggerState.value += 1
        gestureRippleTriggerState.value =
            if (openedViaGesture) gestureRippleTriggerState.value + 1 else 0

        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val canDrawOverlays = Settings.canDrawOverlays(context)
        val isAccessibilityService = context is AccessibilityService
        val primaryWindowType = when {
            isAccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            canDrawOverlays -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> {
                Log.w(TAG, "Search overlay permission is not granted; falling back to activity")
                return false
            }
        }
        return showWithWindowType(context, wm, primaryWindowType) ||
            (isAccessibilityService &&
                canDrawOverlays &&
                showWithWindowType(context, wm, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY))
    }

    fun updateBackdrop(
        backdrop: Bitmap?,
        expectedGeneration: Long? = null,
    ) {
        if (backdrop == null) return
        mainHandler.post {
            if (!isActivelyVisible()) return@post
            if (expectedGeneration != null && sessionGeneration.get() != expectedGeneration) return@post
            backdropState.value = backdrop
        }
    }

    private fun showWithWindowType(
        context: Context,
        wm: WindowManager,
        windowType: Int,
    ): Boolean {
        val appContext = context.applicationContext ?: context
        val overlayContext = UnifiedOverlayWindowHost.createThemedOverlayContext(context, windowType)
        val overlayHost = UnifiedOverlayWindowHost(appContext).apply { onCreateAndResume() }

        val content = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            isFocusable = true
            isFocusableInTouchMode = true
            overlayHost.attachToView(this)
            setContent {
                val repository = remember { SettingsRepository(appContext) }
                val isPitchBlack by repository.isPitchBlackThemeEnabled.collectAsState(initial = false)
                var ripplePrefEnabled by remember { mutableStateOf(isGestureRippleEnabled(appContext)) }
                var blurPrefEnabled by remember {
                    mutableStateOf(
                        repository.getBoolean(
                            SettingsRepository.KEY_USE_BLUR,
                            !com.sameerasw.essentials.utils.DeviceUtils.isBlurProblematicDevice(),
                        ),
                    )
                }

                LaunchedEffect(repository) {
                    repository.observeKeyChanges().collect { key ->
                        when (key) {
                            SettingsRepository.KEY_USE_RIPPLE, null -> {
                                ripplePrefEnabled = isGestureRippleEnabled(appContext)
                            }
                            SettingsRepository.KEY_USE_BLUR -> {
                                blurPrefEnabled = repository.getBoolean(
                                    SettingsRepository.KEY_USE_BLUR,
                                    !com.sameerasw.essentials.utils.DeviceUtils.isBlurProblematicDevice(),
                                )
                            }
                        }
                    }
                }

                val currentBackdrop by backdropState
                val rippleTrigger by gestureRippleTriggerState
                val revealTrigger by launchRevealTriggerState
                val currentLaunchOrigin by launchOriginState
                val resolvedOriginOffset = remember(currentLaunchOrigin, revealTrigger) {
                    currentLaunchOrigin.resolveNormalizedOffset(overlayContext)
                }
                val rippleController = rememberGestureRippleController()

                LaunchedEffect(rippleTrigger, ripplePrefEnabled, resolvedOriginOffset) {
                    if (rippleTrigger > 0 && ripplePrefEnabled) {
                        rippleController.spawnLaunch(resolvedOriginOffset)
                    }
                }

                val isScreenReaderActive = remember(overlayContext) {
                    val am = overlayContext.getSystemService(Context.ACCESSIBILITY_SERVICE)
                        as? android.view.accessibility.AccessibilityManager
                    am?.isTouchExplorationEnabled == true
                }

                CompositionLocalProvider(
                    LocalContext provides overlayContext,
                    LocalOnBackPressedDispatcherOwner provides overlayHost,
                    LocalActivityResultRegistryOwner provides overlayHost,
                    LocalGestureRippleController provides rippleController,
                ) {
                    EssentialsTheme(pitchBlackTheme = isPitchBlack) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!isScreenReaderActive) {
                                        // Komorebi's own SearchGestureAccessibilityService sets AccessibilityManager.isEnabled=true,
                                        // causing Compose's AndroidComposeViewAccessibilityDelegateCompat to run O(N^2) geometry
                                        // sorting on every keystroke/ripple frame even when no screen reader is active.
                                        Modifier.clearAndSetSemantics {}
                                    } else {
                                        Modifier
                                    },
                                )
                                .originExpansionReveal(
                                    triggerKey = revealTrigger,
                                    originNormalized = resolvedOriginOffset,
                                    enabled = true,
                                )
                                .interactiveGestureRipple(
                                    controller = rippleController,
                                    enabled = ripplePrefEnabled,
                                ),
                        ) {
                            AnimatedVisibility(
                                visible = currentBackdrop != null,
                                enter = fadeIn(),
                            ) {
                                currentBackdrop?.let { image ->
                                    val imageBitmap = remember(image, blurPrefEnabled) {
                                        val source = if (blurPrefEnabled && image.width > 360 && image.height > 360) {
                                            val targetW = (image.width / 4).coerceAtLeast(1)
                                            val targetH = (image.height / 4).coerceAtLeast(1)
                                            runCatching {
                                                Bitmap.createScaledBitmap(image, targetW, targetH, true)
                                            }.getOrDefault(image)
                                        } else {
                                            image
                                        }
                                        source.asImageBitmap()
                                    }
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (blurPrefEnabled) {
                                                    Modifier.blur(10.dp)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .gestureRippleRefraction(
                                                enabled = ripplePrefEnabled,
                                                controller = rippleController,
                                            ),
                                    )
                                }
                            }
                            PixelSearchResultsScreen(
                                initialQuery = "",
                                asOverlay = true,
                                onFinish = { hideDeferred() },
                            )
                            GestureRippleWaveOverlay(
                                enabled = ripplePrefEnabled,
                                controller = rippleController,
                            )
                        }
                    }
                }
            }
        }

        val view = object : FrameLayout(overlayContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (overlayHost.onBackPressedDispatcher.hasEnabledCallbacks()) {
                            overlayHost.onBackPressedDispatcher.onBackPressed()
                        } else {
                            hide()
                        }
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            overlayHost.attachToView(this)
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        content.post {
                            runCatching { content.requestFocus() }
                        }
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        // Clean up if Android detached the window externally (service restart, display change).
                        if (windowView === v) {
                            Log.d(TAG, "Search overlay detached externally; cleaning up state")
                            cancelPendingHide()
                            windowView = null
                            windowManager = null
                            val staleHost = host
                            host = null
                            isShowing = false
                            backdropState.value = null
                            ScreenCaptureStore.clear()
                            sessionGeneration.incrementAndGet()
                            OverlayVisibilityCoordinator.markHidden(OVERLAY_ID)
                            runCatching { content.disposeComposition() }
                            runCatching { staleHost?.onDestroy() }
                        }
                    }
                },
            )
        }

        val params = UnifiedOverlayWindowHost.buildFullScreenParams(
            windowType = windowType,
            focusable = true,
            enableIme = true,
        )
        val added = runCatching {
            wm.addView(view, params)
        }.onFailure { error ->
            Log.e(TAG, "Failed to add search overlay window (type=$windowType)", error)
        }.isSuccess
        if (!added) {
            overlayHost.onDestroy()
            return false
        }
        host = overlayHost
        windowView = view
        windowManager = wm
        isShowing = true
        OverlayVisibilityCoordinator.markVisible(OVERLAY_ID)
        runCatching { content.requestFocus() }
            .onFailure { error -> Log.w(TAG, "Search overlay focus request failed", error) }
        return true
    }

    /**
     * Dismisses the overlay on the next UI frame after hiding the IME so any
     * Activity/LauncherApps launch dispatched from inside the overlay completes
     * cleanly before window teardown.
     */
    fun hideDeferred(delayMs: Long = 48L) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideDeferred(delayMs) }
            return
        }
        val view = windowView
        if (view != null) {
            runCatching {
                val imm = view.context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        cancelPendingHide()
        val runnable = Runnable {
            pendingHideRunnable = null
            hideInternal()
        }
        pendingHideRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideInternal() }
            return
        }
        hideInternal()
    }

    private fun hideInternal(clearCapture: Boolean = true) {
        cancelPendingHide()
        sessionGeneration.incrementAndGet()
        val view = windowView
        val wm = windowManager
        val overlayHost = host
        windowView = null
        windowManager = null
        host = null
        isShowing = false
        backdropState.value = null
        if (clearCapture) {
            ScreenCaptureStore.clear()
        }
        OverlayVisibilityCoordinator.markHidden(OVERLAY_ID)
        if (view != null) {
            runCatching {
                val imm = view.context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
            runCatching {
                ((view as? android.view.ViewGroup)?.getChildAt(0) as? ComposeView)?.disposeComposition()
            }
        }
        if (view != null && wm != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    wm.removeViewImmediate(view)
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to remove search overlay window", error)
            }
        }
        runCatching { overlayHost?.onDestroy() }
    }

    private fun cancelPendingHide() {
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingHideRunnable = null
    }
}
