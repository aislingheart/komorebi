/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: SearchOverlayController.kt
 * Description: Accessibility overlay host for the Komorebi search surface.
 */

package com.sameerasw.essentials.services

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsScreen
import com.sameerasw.essentials.ui.theme.EssentialsTheme

object SearchOverlayController {
    private var host: SearchOverlayHost? = null
    private var windowView: View? = null
    private var windowManager: WindowManager? = null

    var isShowing: Boolean = false
        private set

    fun show(context: Context): Boolean {
        if (isShowing) return true
        val serviceContext = context
        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val overlayHost = SearchOverlayHost()
        overlayHost.onCreate()
        val content = ComposeView(serviceContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(overlayHost)
            setViewTreeViewModelStoreOwner(overlayHost)
            setViewTreeSavedStateRegistryOwner(overlayHost)
            setContent {
                val repository = remember { SettingsRepository(serviceContext) }
                val isPitchBlack by repository.isPitchBlackThemeEnabled.collectAsState(initial = false)
                CompositionLocalProvider(
                    LocalContext provides OverlayContext(serviceContext),
                    LocalOnBackPressedDispatcherOwner provides overlayHost,
                ) {
                    EssentialsTheme(pitchBlackTheme = isPitchBlack) {
                        PixelSearchResultsScreen(
                            initialQuery = "",
                            asOverlay = true,
                            onFinish = { hide() },
                        )
                    }
                }
            }
        }
        val view = object : FrameLayout(serviceContext) {
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val added = runCatching {
            wm.addView(view, params)
        }.isSuccess
        if (!added) {
            overlayHost.onDestroy()
            return false
        }
        host = overlayHost
        windowView = view
        windowManager = wm
        isShowing = true
        view.requestFocus()
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
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        overlayHost?.onDestroy()
    }

    private class OverlayContext(base: Context) : ContextWrapper(base) {
        private val overlayWindowManager =
            OverlayWindowManager(base.getSystemService(WindowManager::class.java))

        override fun getSystemService(name: String): Any? =
            if (name == Context.WINDOW_SERVICE) overlayWindowManager else super.getSystemService(name)

        override fun startActivity(intent: Intent) {
            super.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        override fun startActivity(intent: Intent, options: Bundle?) {
            super.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        }
    }

    private class OverlayWindowManager(
        private val delegate: WindowManager,
    ) : WindowManager by delegate {
        override fun addView(view: View, params: ViewGroup.LayoutParams) {
            if (params is WindowManager.LayoutParams && params.token == null) {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            delegate.addView(view, params)
        }

        override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
            if (params is WindowManager.LayoutParams && params.token == null) {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            delegate.updateViewLayout(view, params)
        }
    }

    private class SearchOverlayHost :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner,
        OnBackPressedDispatcherOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore = store
        override val onBackPressedDispatcher = OnBackPressedDispatcher()

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            val state = lifecycleRegistry.currentState
            if (state == Lifecycle.State.DESTROYED || state == Lifecycle.State.INITIALIZED) return
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
