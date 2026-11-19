/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: SearchOverlayController.kt
 * Description: Accessibility overlay host for the Komorebi search surface.
 */

package com.sameerasw.essentials.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.ui.activities.PixelSearchResultsScreen
import com.sameerasw.essentials.ui.theme.EssentialsTheme

object SearchOverlayController {
    private const val TAG = "SearchOverlayController"
    private var host: SearchOverlayHost? = null
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
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.createWindowContext(windowType, null)
            }.getOrNull()
        } else {
            null
        }
        val themedContext = ContextThemeWrapper(windowContext ?: context, R.style.Theme_Komorebi)
        val overlayContext = OverlayContext(themedContext, windowType)
        val overlayHost = SearchOverlayHost(appContext)
        overlayHost.onCreate()
        val content = ComposeView(overlayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(overlayHost)
            setViewTreeViewModelStoreOwner(overlayHost)
            setViewTreeSavedStateRegistryOwner(overlayHost)
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
            setViewTreeLifecycleOwner(overlayHost)
            setViewTreeViewModelStoreOwner(overlayHost)
            setViewTreeSavedStateRegistryOwner(overlayHost)
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
        val baseFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            baseFlags,
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }
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
        if (view != null) {
            runCatching {
                val imm = view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
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

    private class OverlayContext(
        base: Context,
        private val windowType: Int,
    ) : ContextWrapper(base) {
        private val overlayWindowManager =
            OverlayWindowManager(base.getSystemService(WindowManager::class.java), windowType)

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

    private class SearchOverlayHost(private val appContext: Context) :
        LifecycleOwner,
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

    /**
     * ActivityResultRegistry for the accessibility overlay window. The overlay has no Activity,
     * so runtime permission dialogs cannot be shown; requests resolve against the current state
     * and otherwise route the user to the app's permission settings. This keeps Compose calls
     * like rememberLauncherForActivityResult from crashing overlay composition.
     */
    private class OverlayActivityResultRegistry(
        private val appContext: Context,
    ) : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: androidx.core.app.ActivityOptionsCompat?,
        ) {
            if (contract is ActivityResultContracts.RequestPermission &&
                input is String &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    appContext,
                    input,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, true as O)
                return
            }
            if (contract is ActivityResultContracts.RequestPermission) {
                runCatching {
                    appContext.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + appContext.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED as O)
        }

    }
}
