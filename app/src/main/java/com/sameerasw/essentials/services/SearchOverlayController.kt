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
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsScreen
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.utils.overlay.OverlayVisibilityCoordinator
import com.sameerasw.essentials.utils.overlay.UnifiedOverlayWindowHost

object SearchOverlayController {
    private const val TAG = "SearchOverlayController"
    private const val OVERLAY_ID = "komorebi_search_overlay"
    private var host: UnifiedOverlayWindowHost? = null
    private var windowView: View? = null
    private var windowManager: WindowManager? = null

    var isShowing: Boolean = false
        private set

    fun show(context: Context, backdrop: Bitmap? = null): Boolean {
        if (isShowing) return true
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
        return showWithWindowType(context, wm, primaryWindowType, backdrop) ||
            (isAccessibilityService &&
                canDrawOverlays &&
                showWithWindowType(context, wm, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, backdrop))
    }

    private fun showWithWindowType(
        context: Context,
        wm: WindowManager,
        windowType: Int,
        backdrop: Bitmap?,
    ): Boolean {
        val appContext = context.applicationContext ?: context
        val overlayContext = UnifiedOverlayWindowHost.createThemedOverlayContext(context, windowType)
        val overlayHost = UnifiedOverlayWindowHost(appContext).apply { onCreateAndResume() }

        val content = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            overlayHost.attachToView(this)
            setContent {
                val repository = remember { SettingsRepository(appContext) }
                val isPitchBlack by repository.isPitchBlackThemeEnabled.collectAsState(initial = false)
                CompositionLocalProvider(
                    LocalContext provides overlayContext,
                    LocalOnBackPressedDispatcherOwner provides overlayHost,
                    LocalActivityResultRegistryOwner provides overlayHost,
                ) {
                    EssentialsTheme(pitchBlackTheme = isPitchBlack) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            backdrop?.let { image ->
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(24.dp),
                                )
                            }
                            PixelSearchResultsScreen(
                                initialQuery = "",
                                asOverlay = true,
                                onFinish = { hide() },
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
            isFocusable = true
            isFocusableInTouchMode = true
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
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
        runCatching { view.requestFocus() }
            .onFailure { error -> Log.w(TAG, "Search overlay focus request failed", error) }
        return true
    }

    fun hide() {
        val view = windowView
        val wm = windowManager
        val overlayHost = host
        windowView = null
        windowManager = null
        host = null
        isShowing = false
        OverlayVisibilityCoordinator.markHidden(OVERLAY_ID)
        if (view != null) {
            runCatching {
                val imm = view.context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { error -> Log.w(TAG, "Failed to remove search overlay window", error) }
        }
        runCatching { overlayHost?.onDestroy() }
            .onFailure { error -> Log.w(TAG, "Failed to destroy search overlay host", error) }
    }
}
