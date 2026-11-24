/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Overlay Window Hosting
 * File: UnifiedOverlayWindowHost.kt
 * Description: Standardized Compose overlay window host providing lifecycle pause/resume/destroy,
 *              display cutout + IME layout params, sub-window WindowManager token rewriting,
 *              and active overlay visibility coordination.
 */

package com.sameerasw.essentials.utils.overlay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
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
import com.sameerasw.essentials.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks active interactive full-screen overlays so ambient overlays (e.g. Dynamic Island,
 * Ambient Glance) can yield gracefully without fighting for focus or z-order.
 */
object OverlayVisibilityCoordinator {
    private val _activeOverlays = MutableStateFlow<Set<String>>(emptySet())
    val activeOverlays: StateFlow<Set<String>> = _activeOverlays.asStateFlow()

    fun markVisible(id: String) {
        _activeOverlays.value = _activeOverlays.value + id
    }

    fun markHidden(id: String) {
        _activeOverlays.value = _activeOverlays.value - id
    }

    fun isAnyFullScreenOverlayVisible(): Boolean = _activeOverlays.value.isNotEmpty()
}

/**
 * Unified lifecycle + saved-state + back-press + activity-result owner for Jetpack Compose
 * overlay windows hosted inside services (Accessibility / Overlay).
 */
class UnifiedOverlayWindowHost(
    private val appContext: Context,
) : LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    OnBackPressedDispatcherOwner,
    ActivityResultRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = store
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
    override val activityResultRegistry: ActivityResultRegistry =
        OverlayActivityResultRegistry(appContext)

    fun attachToView(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun onCreateAndResume() {
        if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) return
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    fun onResume() {
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun onDestroy() {
        val state = lifecycleRegistry.currentState
        if (state == Lifecycle.State.DESTROYED || state == Lifecycle.State.INITIALIZED) return
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    companion object {
        fun createThemedOverlayContext(base: Context, windowType: Int): Context {
            val isAccessibilityOverlay =
                windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            val windowContext = if (!isAccessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { base.createWindowContext(windowType, null) }.getOrNull()
            } else {
                null
            }
            val themed = ContextThemeWrapper(windowContext ?: base, R.style.Theme_Komorebi)
            val hostWindowManager = base.getSystemService(WindowManager::class.java)
            return OverlayContext(themed, windowType, hostWindowManager)
        }

        fun buildFullScreenParams(
            windowType: Int,
            focusable: Boolean = true,
            enableIme: Boolean = true,
        ): WindowManager.LayoutParams {
            var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            }
            if (!focusable) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (enableIme && focusable) {
                    @Suppress("DEPRECATION")
                    softInputMode =
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                }
            }
        }
    }

    private class OverlayContext(
        base: Context,
        private val windowType: Int,
        hostWindowManager: WindowManager? = null,
    ) : ContextWrapper(base) {
        private val overlayWindowManager =
            OverlayWindowManager(
                hostWindowManager ?: base.getSystemService(WindowManager::class.java),
                windowType,
            )

        override fun getSystemService(name: String): Any? =
            if (name == Context.WINDOW_SERVICE) overlayWindowManager else super.getSystemService(name)

        override fun startActivity(intent: Intent) {
            startActivity(intent, null)
        }

        override fun startActivity(intent: Intent, options: Bundle?) {
            val prepared = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val effectiveOptions = options ?: com.sameerasw.essentials.utils.AppLaunchUtil.defaultLaunchOptions()
            val accessibilityHost = com.sameerasw.essentials.services.SearchGestureAccessibilityService.instance
            if (accessibilityHost != null) {
                val started =
                    runCatching {
                        if (effectiveOptions != null) {
                            accessibilityHost.startActivity(prepared, effectiveOptions)
                        } else {
                            accessibilityHost.startActivity(prepared)
                        }
                        true
                    }.getOrDefault(false)
                if (started) return
            }
            if (effectiveOptions != null) {
                super.startActivity(prepared, effectiveOptions)
            } else {
                super.startActivity(prepared)
            }
        }
    }

    private class OverlayWindowManager(
        private val delegate: WindowManager,
        private val windowType: Int,
    ) : WindowManager by delegate {
        override fun addView(view: View, params: ViewGroup.LayoutParams) {
            if (params is WindowManager.LayoutParams && params.token == null) {
                params.type = windowType
            }
            delegate.addView(view, params)
        }

        override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
            if (params is WindowManager.LayoutParams && params.token == null) {
                params.type = windowType
            }
            delegate.updateViewLayout(view, params)
        }
    }

    private class OverlayActivityResultRegistry(
        private val appContext: Context,
    ) : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            when (contract) {
                is ActivityResultContracts.RequestPermission -> {
                    if (input is String &&
                        ContextCompat.checkSelfPermission(appContext, input) == PackageManager.PERMISSION_GRANTED
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        dispatchResult(requestCode, true as O)
                        return
                    }
                    runCatching {
                        appContext.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + appContext.packageName),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, false as O)
                }
                is ActivityResultContracts.RequestMultiplePermissions -> {
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, emptyMap<String, Boolean>() as O)
                }
                is ActivityResultContracts.StartActivityForResult -> {
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, ActivityResult(Activity.RESULT_CANCELED, null) as O)
                }
                else -> {
                    // Overlay host cannot deliver activity results for arbitrary contracts; skip safely.
                }
            }
        }
    }
}
