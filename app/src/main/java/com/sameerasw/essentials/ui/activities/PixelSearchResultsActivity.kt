/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchResultsActivity.kt
 * Description: Activity displaying categorized search results from Pixel Launcher search bar.
 */

package com.sameerasw.essentials.ui.activities

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import android.Manifest
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.sameerasw.essentials.translation.TranslatableText
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sameerasw.essentials.FeatureSettingsActivity
import com.sameerasw.essentials.R
import com.sameerasw.essentials.SettingsActivity
import com.sameerasw.essentials.data.repository.AppUpdateState
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.services.SearchOverlayController
import com.sameerasw.essentials.search.InteractiveSearchSettingCard
import com.sameerasw.essentials.search.InternalSettingsCatalog
import com.sameerasw.essentials.search.triggerInteractiveSettingPrimaryAction
import com.sameerasw.essentials.ui.components.search.InSearchUpdateBannerCard
import com.sameerasw.essentials.ui.components.search.InSearchUpdateOverlay
import com.sameerasw.essentials.ui.modifiers.LocalGestureRippleController
import com.sameerasw.essentials.ui.modifiers.interactiveGestureRipple
import com.sameerasw.essentials.ui.modifiers.isGestureRippleEnabled
import com.sameerasw.essentials.ui.modifiers.originExpansionReveal
import com.sameerasw.essentials.ui.modifiers.rememberGestureRippleController
import com.sameerasw.essentials.utils.SearchLaunchOrigin
import com.sameerasw.essentials.utils.UpdateNotificationHelper
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.domain.registry.FeatureRegistry
import com.sameerasw.essentials.domain.registry.SearchRegistry
import com.sameerasw.essentials.domain.model.NotificationApp
import com.sameerasw.essentials.assistant.AssistantMemoryRepository
import com.sameerasw.essentials.assistant.AssistantTts
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import com.sameerasw.essentials.search.SearchTopTargetInput
import com.sameerasw.essentials.search.SearchTopTargetResolver
import com.sameerasw.essentials.search.TopSearchTarget
import com.sameerasw.essentials.launcher.ActiveNotificationStore
import com.sameerasw.essentials.launcher.BuiltInLauncherAction
import com.sameerasw.essentials.launcher.BuiltInLauncherTools
import com.sameerasw.essentials.launcher.CalcResult
import com.sameerasw.essentials.launcher.ClipboardHistoryManager
import com.sameerasw.essentials.launcher.CompatibilityReportSheet
import com.sameerasw.essentials.launcher.LauncherAssistantCommands
import com.sameerasw.essentials.launcher.LauncherCalculator
import com.sameerasw.essentials.launcher.LauncherToolModal
import com.sameerasw.essentials.launcher.LauncherToolModalOverlay
import com.sameerasw.essentials.launcher.Quicklink
import com.sameerasw.essentials.launcher.QuicklinksRepository
import com.sameerasw.essentials.launcher.SnippetsRepository
import com.sameerasw.essentials.launcher.TextSnippet
import com.sameerasw.essentials.raycast.AndroidCompatLevel
import com.sameerasw.essentials.raycast.CompatibilityReport
import com.sameerasw.essentials.raycast.InstalledRaycastExtension
import com.sameerasw.essentials.raycast.RaycastActionPanelSpec
import com.sameerasw.essentials.raycast.RaycastActionPanelSheet
import com.sameerasw.essentials.raycast.RaycastAlertRequest
import com.sameerasw.essentials.raycast.RaycastCommandManifest
import com.sameerasw.essentials.raycast.RaycastCompatibilityAnalyzer
import com.sameerasw.essentials.raycast.RaycastDropdownSpec
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.raycast.RaycastIconView
import com.sameerasw.essentials.raycast.RaycastOAuthManager
import com.sameerasw.essentials.raycast.RaycastRuntimeEngine
import com.sameerasw.essentials.raycast.RaycastScreenBody
import com.sameerasw.essentials.raycast.RaycastStoreClient
import com.sameerasw.essentials.raycast.RaycastViewScreen
import com.sameerasw.essentials.raycast.StoreExtensionListing
import com.sameerasw.essentials.raycast.executeRaycastAction
import com.sameerasw.essentials.raycast.rememberRaycastFormState
import com.sameerasw.essentials.raycast.rememberRaycastPrimaryPanel
import com.sameerasw.essentials.search.AppFunctionItem
import com.sameerasw.essentials.search.DiscoveredShortcut
import com.sameerasw.essentials.search.EntityRouteItem
import com.sameerasw.essentials.search.InternalCommandItem
import com.sameerasw.essentials.search.PrefixSuggestion
import com.sameerasw.essentials.search.SearchProvider
import com.sameerasw.essentials.search.SearchProviderDiscovery
import com.sameerasw.essentials.search.SearchProviderKind
import com.sameerasw.essentials.search.SearchIndex
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.ui.core.cards.FeatureCard
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.modifiers.BlurDirection
import com.sameerasw.essentials.ui.modifiers.progressiveBlur
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.ui.core.sheets.PixelSearchFileActionSheet
import com.sameerasw.essentials.utils.ContactsSearchUtil
import com.sameerasw.essentials.utils.ClipboardContent
import com.sameerasw.essentials.utils.ClipboardContentClassifier
import com.sameerasw.essentials.utils.ColorUtil
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.FuzzySearchUtil
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.PermissionUtils
import com.sameerasw.essentials.utils.SearchContextRanker
import com.sameerasw.essentials.utils.SearchUsageTracker
import com.sameerasw.essentials.utils.SystemSettingsSearchUtil
import com.sameerasw.essentials.utils.WindowingUtils
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PixelSearchResultsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TOOL = "open_tool"
        const val EXTRA_OPENED_VIA_GESTURE = "opened_via_gesture"

        @Volatile
        private var foregroundInstance: java.lang.ref.WeakReference<PixelSearchResultsActivity>? = null

        @Volatile
        var isForeground: Boolean = false
            private set

        fun dismissIfForeground(): Boolean {
            if (!isForeground) return false
            val activity = foregroundInstance?.get() ?: return false
            if (activity.isFinishing || activity.isDestroyed) return false
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
            return true
        }
    }

    private var incomingIntentQuery by mutableStateOf<Pair<String, Int>?>(null)
    private var intentNonce = 0
    private var incomingToolModal by mutableStateOf<LauncherToolModal?>(null)
    private var toolNonce = 0
    private var gestureRippleTrigger by mutableIntStateOf(0)
    private var launchRevealTrigger by mutableIntStateOf(1)
    private var launchOrigin by mutableStateOf(SearchLaunchOrigin())
    private var incomingShowUpdateSheetNonce by mutableIntStateOf(0)

    private fun extractQuery(intent: Intent?): String =
        intent?.getStringExtra(SearchManager.QUERY)
            ?: intent?.getStringExtra("query")
            ?: ""

    private fun extractTool(intent: Intent?): LauncherToolModal? {
        if (intent == null) return null
        intent.getStringExtra(EXTRA_OPEN_TOOL)?.let { name ->
            runCatching { LauncherToolModal.valueOf(name) }.getOrNull()?.let { return it }
        }
        val data = intent.data ?: return null
        val candidate = data.lastPathSegment ?: data.host ?: return null
        return runCatching { LauncherToolModal.valueOf(candidate.uppercase()) }.getOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dimAmount = if (isSystemDark) 0.2f else 0.08f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 60
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(dimAmount)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(if (isSystemDark) 0.35f else 0.15f)
        }

        val initialQuery = extractQuery(intent)
        val initialTool = extractTool(intent)
        val initialShowUpdateSheet =
            intent?.getBooleanExtra(UpdateNotificationHelper.EXTRA_SHOW_UPDATE_SHEET, false) == true
        launchOrigin = SearchLaunchOrigin.fromIntent(this, intent)
        launchRevealTrigger = 1
        if (intent?.getBooleanExtra(EXTRA_OPENED_VIA_GESTURE, false) == true || intent?.sourceBounds != null) {
            gestureRippleTrigger = 1
        }

        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }

        setContent {
            val repository = remember { SettingsRepository(this) }
            val isPitchBlack by repository.isPitchBlackThemeEnabled.collectAsState(initial = false)
            var ripplePrefEnabled by remember { mutableStateOf(isGestureRippleEnabled(this@PixelSearchResultsActivity)) }

            LaunchedEffect(repository) {
                repository.observeKeyChanges().collect { key ->
                    if (key == SettingsRepository.KEY_USE_RIPPLE || key == null) {
                        ripplePrefEnabled = isGestureRippleEnabled(this@PixelSearchResultsActivity)
                    }
                }
            }

            val rippleTrigger = gestureRippleTrigger
            val revealTrigger = launchRevealTrigger
            val currentOrigin = launchOrigin
            val resolvedOriginOffset = remember(currentOrigin, revealTrigger) {
                currentOrigin.resolveNormalizedOffset(this@PixelSearchResultsActivity)
            }
            val rippleController = rememberGestureRippleController()
            LaunchedEffect(rippleTrigger, ripplePrefEnabled, resolvedOriginOffset) {
                if (rippleTrigger > 0 && ripplePrefEnabled) {
                    rippleController.spawnLaunch(resolvedOriginOffset)
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                LocalGestureRippleController provides rippleController,
            ) {
                EssentialsTheme(pitchBlackTheme = isPitchBlack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                        PixelSearchResultsScreen(
                            initialQuery = initialQuery,
                            incomingIntentQuery = incomingIntentQuery,
                            initialToolModal = initialTool,
                            incomingToolModal = incomingToolModal,
                            incomingToolNonce = toolNonce,
                            initialShowUpdateSheet = initialShowUpdateSheet,
                            incomingShowUpdateSheetNonce = incomingShowUpdateSheetNonce,
                            onFinish = { finish() },
                        )
                        com.sameerasw.essentials.ui.modifiers.GestureRippleWaveOverlay(
                            enabled = ripplePrefEnabled,
                            controller = rippleController,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (SearchOverlayController.isShowing) {
            SearchOverlayController.hide()
        }
        foregroundInstance = java.lang.ref.WeakReference(this)
        isForeground = true
    }

    override fun onPause() {
        if (foregroundInstance?.get() === this) {
            isForeground = false
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (foregroundInstance?.get() === this) {
            foregroundInstance = null
            isForeground = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RaycastOAuthManager.handleRedirectUri(intent.data)
        intentNonce += 1
        incomingIntentQuery = extractQuery(intent) to intentNonce
        incomingToolModal = extractTool(intent)
        toolNonce += 1
        launchOrigin = SearchLaunchOrigin.fromIntent(this, intent)
        launchRevealTrigger += 1
        if (intent.getBooleanExtra(UpdateNotificationHelper.EXTRA_SHOW_UPDATE_SHEET, false)) {
            incomingShowUpdateSheetNonce += 1
        }
        if (intent.getBooleanExtra(EXTRA_OPENED_VIA_GESTURE, false) || intent.sourceBounds != null) {
            gestureRippleTrigger += 1
        }
    }
}

private sealed interface InlineOverlayMode {
    data object Search : InlineOverlayMode
    data class ExtensionsHub(val initialTab: Int = 0) : InlineOverlayMode
    data class ExtensionRunner(
        val extension: InstalledRaycastExtension,
        val command: RaycastCommandManifest,
        val returnToHub: Boolean = false,
    ) : InlineOverlayMode
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PixelSearchResultsScreen(
    initialQuery: String,
    incomingIntentQuery: Pair<String, Int>? = null,
    initialToolModal: LauncherToolModal? = null,
    incomingToolModal: LauncherToolModal? = null,
    incomingToolNonce: Int = 0,
    initialShowUpdateSheet: Boolean = false,
    incomingShowUpdateSheetNonce: Int = 0,
    asOverlay: Boolean = false,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    val queryState = rememberSaveable { mutableStateOf(initialQuery) }
    var settledQuery by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    var activeToolModal by remember { mutableStateOf(initialToolModal) }
    var showInlineUpdateSheet by remember { mutableStateOf(initialShowUpdateSheet) }
    var launcherDataVersion by remember { mutableIntStateOf(0) }
    var settingsRefreshNonce by remember { mutableIntStateOf(0) }
    val rippleController = LocalGestureRippleController.current

    val updateInfo by AppUpdateState.updateInfo.collectAsState()
    val isCheckingUpdate by AppUpdateState.isChecking.collectAsState()
    val updateCheckError by AppUpdateState.lastError.collectAsState()
    val updateCheckCompleted by AppUpdateState.checkCompleted.collectAsState()
    val dismissedUpdateVersion by AppUpdateState.dismissedVersion.collectAsState()

    val availableUpdate = updateInfo?.takeIf { it.isUpdateAvailable }
    val showUpdateBanner =
        availableUpdate != null && dismissedUpdateVersion != availableUpdate.versionName

    val openInlineUpdates: () -> Unit = remember(context) {
        {
            keyboardController?.hide()
            showInlineUpdateSheet = true
            if (AppUpdateState.updateInfo.value == null) {
                AppUpdateState.checkForUpdates(context, manual = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        AppUpdateState.checkForUpdates(context, manual = initialShowUpdateSheet)
    }

    LaunchedEffect(incomingShowUpdateSheetNonce) {
        if (incomingShowUpdateSheetNonce > 0) {
            keyboardController?.hide()
            showInlineUpdateSheet = true
            AppUpdateState.checkForUpdates(context, manual = AppUpdateState.updateInfo.value == null)
        }
    }

    LaunchedEffect(repository) {
        repository.observeKeyChanges().collect { key ->
            if (key == null ||
                key.startsWith("pixel_search_") ||
                key.startsWith("voice_") ||
                key == SettingsRepository.KEY_USE_BLUR ||
                key == SettingsRepository.KEY_USE_RIPPLE ||
                key == SettingsRepository.KEY_PITCH_BLACK_THEME_ENABLED ||
                key.startsWith("haptic_") ||
                key == SettingsRepository.KEY_ENABLE_UNSUPPORTED_FEATURES ||
                key == SettingsRepository.KEY_SHOW_LEGACY_FEATURES ||
                key == SettingsRepository.KEY_AUTO_UPDATE_ENABLED ||
                key == SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED ||
                key == SettingsRepository.KEY_DEVELOPER_MODE_ENABLED
            ) {
                settingsRefreshNonce++
            }
        }
    }

    LaunchedEffect(initialToolModal) {
        if (initialToolModal != null) {
            activeToolModal = initialToolModal
        }
    }

    LaunchedEffect(incomingToolNonce) {
        incomingToolModal?.let { activeToolModal = it }
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 180.dp.toPx() }

    var insetsAnimController by remember { mutableStateOf<WindowInsetsAnimationController?>(null) }

    val overscrollNestedScrollConnection = remember {
        object : NestedScrollConnection {

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    dragOffsetY = (dragOffsetY + available.y).coerceAtLeast(0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (insetsAnimController == null) {
                            val rootInsets = view.rootWindowInsets
                            if (rootInsets != null &&
                                rootInsets.isVisible(android.view.WindowInsets.Type.ime())
                            ) {
                                view.windowInsetsController?.controlWindowInsetsAnimation(
                                    android.view.WindowInsets.Type.ime(),
                                    -1L, null, null,
                                    object : WindowInsetsAnimationControlListener {
                                        override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                                            insetsAnimController = controller
                                        }
                                        override fun onFinished(controller: WindowInsetsAnimationController) {
                                            insetsAnimController = null
                                        }
                                        override fun onCancelled(controller: WindowInsetsAnimationController?) {
                                            insetsAnimController = null
                                        }
                                    }
                                )
                            }
                        }
                        val ctrl = insetsAnimController
                        if (ctrl != null) {
                            val shown = ctrl.shownStateInsets.bottom.toFloat()
                            if (shown > 0f) {
                                val target = (shown - dragOffsetY).coerceIn(0f, shown)
                                ctrl.setInsetsAndAlpha(
                                    android.graphics.Insets.of(0, 0, 0, target.toInt()),
                                    1f,
                                    target / shown,
                                )
                            }
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (available.y < 0f && dragOffsetY > 0f) {
                    val snap = minOf(-available.y, dragOffsetY)
                    dragOffsetY = (dragOffsetY - snap).coerceAtLeast(0f)
                    return Offset(0f, -snap)
                }

                if (available.y < 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (insetsAnimController == null) {
                        val rootInsets = view.rootWindowInsets
                        if (rootInsets != null &&
                            rootInsets.isVisible(android.view.WindowInsets.Type.ime())
                        ) {
                            view.windowInsetsController?.controlWindowInsetsAnimation(
                                android.view.WindowInsets.Type.ime(),
                                -1L, null, null,
                                object : WindowInsetsAnimationControlListener {
                                    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                                        insetsAnimController = controller
                                    }
                                    override fun onFinished(controller: WindowInsetsAnimationController) {
                                        insetsAnimController = null
                                    }
                                    override fun onCancelled(controller: WindowInsetsAnimationController?) {
                                        insetsAnimController = null
                                    }
                                }
                            )
                        }
                    }
                    val ctrl = insetsAnimController
                    if (ctrl != null) {
                        val shown = ctrl.shownStateInsets.bottom.toFloat()
                        if (shown > 0f) {
                            val current = ctrl.currentInsets.bottom.toFloat()
                            val target = (current + available.y).coerceIn(0f, shown)
                            ctrl.setInsetsAndAlpha(
                                android.graphics.Insets.of(0, 0, 0, target.toInt()),
                                1f,
                                target / shown,
                            )
                        }
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetY > dismissThresholdPx) {
                    onFinish()
                    return Velocity.Zero
                } else if (dragOffsetY > 0f) {
                    scope.launch {
                        animate(
                            initialValue = dragOffsetY,
                            targetValue = 0f,
                            animationSpec = spring(),
                        ) { v, _ -> dragOffsetY = v }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val ctrl = insetsAnimController
                    if (ctrl != null) {
                        insetsAnimController = null
                        val current = ctrl.currentInsets.bottom.toFloat()
                        val shown = ctrl.shownStateInsets.bottom.toFloat()
                        val shouldShow = !(shown > 0f && (available.y < -500f || current < shown * 0.5f))
                        val target = if (shouldShow) shown else 0f
                        animate(
                            initialValue = current,
                            targetValue = target,
                            initialVelocity = (-available.y).coerceIn(-shown * 15f, shown * 15f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        ) { value, _ ->
                            if (shown > 0f) {
                                ctrl.setInsetsAndAlpha(
                                    android.graphics.Insets.of(0, 0, 0, value.toInt()),
                                    1f,
                                    value / shown,
                                )
                            }
                        }
                        ctrl.finish(shouldShow)
                    }
                }

                return Velocity.Zero
            }
        }
    }

    val listState = rememberLazyListState()
    var userHasScrolled by remember { mutableStateOf(false) }

    if (listState.isScrollInProgress) {
        DisposableEffect(Unit) {
            userHasScrolled = true
            onDispose { }
        }
    }

    var appResults by remember { mutableStateOf<List<PixelSearchResultItem.AppItem>>(emptyList()) }
    var contactResults by remember { mutableStateOf<List<PixelSearchResultItem.ContactItem>>(emptyList()) }
    var mediaResults by remember { mutableStateOf<List<PixelSearchResultItem.FileItem>>(emptyList()) }
    var fileResults by remember { mutableStateOf<List<PixelSearchResultItem.FileItem>>(emptyList()) }
    var selectedFileForAction by remember { mutableStateOf<PixelSearchResultItem.FileItem?>(null) }
    var systemSettingResults by remember { mutableStateOf<List<PixelSearchResultItem.SystemSettingItem>>(emptyList()) }
    var settingResults by remember { mutableStateOf<List<PixelSearchResultItem.SettingItem>>(emptyList()) }
    var shortcutResults by remember { mutableStateOf<List<DiscoveredShortcut>>(emptyList()) }
    var entityRoutes by remember { mutableStateOf<List<EntityRouteItem>>(emptyList()) }
    var internalCommands by remember { mutableStateOf<List<InternalCommandItem>>(emptyList()) }
    var appFunctions by remember { mutableStateOf<List<AppFunctionItem>>(emptyList()) }
    var scopedPhotos by remember { mutableStateOf<List<PixelSearchResultItem.FileItem>>(emptyList()) }
    var discoveredProviders by remember { mutableStateOf<List<SearchProvider>>(emptyList()) }
    var activeScope by remember { mutableStateOf<SearchProvider?>(null) }
    var overlayMode by remember { mutableStateOf<InlineOverlayMode>(InlineOverlayMode.Search) }
    var hubTab by remember { mutableIntStateOf(0) }
    var hubQuery by remember { mutableStateOf("") }
    var storeSource by remember { mutableStateOf("raycast") }
    var installedExtensions by remember {
        mutableStateOf<List<InstalledRaycastExtension>>(
            RaycastExtensionRepository.listInstalledExtensions(context),
        )
    }
    var storeListings by remember { mutableStateOf<List<StoreExtensionListing>>(emptyList()) }
    var storeLoading by remember { mutableStateOf(false) }
    var storeError by remember { mutableStateOf<String?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun refreshInstalledExtensions() {
        installedExtensions = RaycastExtensionRepository.listInstalledExtensions(context)
    }

    var storeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun loadStore() {
        storeJob?.cancel()
        storeJob = scope.launch {
            storeLoading = true
            storeError = null
            val res = when (storeSource) {
                "vicinae" -> RaycastStoreClient.fetchVicinaeListings(query = hubQuery)
                "komorebi" -> RaycastStoreClient.fetchKomorebiListings(query = hubQuery)
                else -> RaycastStoreClient.fetchRaycastListings(query = hubQuery)
            }
            res.onSuccess { storeListings = it }
                .onFailure { storeError = it.message ?: "Failed to load store" }
            storeLoading = false
        }
    }

    LaunchedEffect(overlayMode, hubTab, storeSource) {
        if (overlayMode is InlineOverlayMode.ExtensionsHub) {
            refreshInstalledExtensions()
            if (hubTab == 1) {
                loadStore()
            }
        }
    }

    var assistantFeedback by remember { mutableStateOf<AssistantFeedback?>(null) }
    var pendingToolConfirmation by remember { mutableStateOf<PendingToolConfirmation?>(null) }

    var clipboardContent by remember { mutableStateOf<ClipboardContent?>(null) }
    var clipboardCardDismissed by remember { mutableStateOf(false) }
    var qrDialogText by remember { mutableStateOf<String?>(null) }

    val voiceSearchEnabled = remember(settingsRefreshNonce) { repository.isVoiceSearchEnabled() }
    val voiceTtsEnabled = remember(settingsRefreshNonce) { repository.isVoiceTtsEnabled() }
    val voiceTtsPreset = remember(settingsRefreshNonce) { repository.getVoiceTtsPreset() }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceQueryPending by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun createSpeechRecognizer(): SpeechRecognizer? {
        val onDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
        if (onDevice != null) return onDevice
        val externalComponent = runCatching {
            val services = context.packageManager.queryIntentServices(
                Intent(android.speech.RecognitionService.SERVICE_INTERFACE),
                0,
            )
            val external = services
                .mapNotNull { it.serviceInfo }
                .filter { it.packageName != context.packageName }
            val preferred = external.firstOrNull {
                it.packageName == "com.google.android.as" ||
                    it.packageName == "com.google.android.tts" ||
                    it.packageName == "com.google.android.googlequicksearchbox"
            } ?: external.firstOrNull()
            preferred?.let { android.content.ComponentName(it.packageName, it.name) }
        }.getOrNull()
        return if (externalComponent != null) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context, externalComponent) }.getOrNull()
        } else {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        }
    }

    fun startVoiceSearch() {
        if (!voiceSearchEnabled) return
        if (isVoiceListening) {
            runCatching { speechRecognizer?.cancel() }
            isVoiceListening = false
            return
        }
        AssistantTts.stop()
        voiceQueryPending = false
        val recognizer = speechRecognizer ?: createSpeechRecognizer()?.also { created ->
            created.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        isVoiceListening = false
                        assistantFeedback = AssistantFeedback(false, voiceErrorMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        isVoiceListening = false
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (text.isNullOrBlank()) {
                            assistantFeedback = AssistantFeedback(false, "I didn't catch that. Try again.")
                        } else {
                            queryState.value = text
                            voiceQueryPending = true
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            speechRecognizer = created
        }
        if (recognizer == null) {
            assistantFeedback = AssistantFeedback(false, "On-device speech recognition is unavailable.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        isVoiceListening = true
        runCatching { recognizer.startListening(intent) }.onFailure {
            isVoiceListening = false
            assistantFeedback = AssistantFeedback(false, "Could not start voice search: ${it.localizedMessage}")
        }
    }

    fun speakVoiceResponse(text: String) {
        if (!voiceQueryPending) return
        voiceQueryPending = false
        if (!voiceTtsEnabled || text.isBlank()) return
        AssistantTts.speak(context, text, voiceTtsPreset)
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceSearch()
        } else {
            assistantFeedback = AssistantFeedback(false, "Microphone permission is required for voice search.")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AssistantTts.stop()
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    LaunchedEffect(incomingIntentQuery) {
        incomingIntentQuery?.let { (newQuery, _) ->
            overlayMode = InlineOverlayMode.Search
            activeScope = null
            if (incomingToolModal == null) {
                activeToolModal = null
            }
            voiceQueryPending = false
            queryState.value = newQuery
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    val isAppsEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultAppsEnabled() }
    val isContactsEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultContactsEnabled() }
    val isMediaEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultMediaEnabled() }
    val isFilesEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultFilesEnabled() }
    val isSettingsEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultSettingsEnabled() }
    val isShortcutsEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultShortcutsEnabled() }
    val isWebEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultWebEnabled() }
    val visibleProviders = remember(discoveredProviders, isWebEnabled) {
        if (isWebEnabled) {
            discoveredProviders
        } else {
            discoveredProviders.filter { it.kind != SearchProviderKind.WEB }
        }
    }
    val prefixSuggestion = remember(settledQuery, activeScope, visibleProviders) {
        if (activeScope == null) {
            SearchProviderDiscovery.matchPrefixSuggestion(settledQuery, visibleProviders)
        } else {
            null
        }
    }
    val isBubblesWebEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchBubblesWebEnabled() }
    val searchEngine = remember(settingsRefreshNonce) { repository.getPixelSearchEngine() }
    val reachabilityLayout = remember(settingsRefreshNonce) { repository.isPixelSearchReachabilityEnabled() }
    val typoToleranceEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchTypoToleranceEnabled() }
    val maxResults = remember(settingsRefreshNonce) {
        repository.getPixelSearchMaxResults().let { if (it <= 0) Int.MAX_VALUE else it }
    }
    val hideInitialResults = remember(settingsRefreshNonce) { repository.isPixelSearchHideInitialResultsEnabled() }
    val notificationsSearchEnabled = remember(settingsRefreshNonce) { repository.isPixelSearchResultNotificationsEnabled() }
    LaunchedEffect(typoToleranceEnabled) {
        FuzzySearchUtil.setTypoToleranceEnabled(typoToleranceEnabled)
    }

    LaunchedEffect(Unit) {
        // Let the first frame draw and claim IME focus before kicking off heavy IO/PackageManager work.
        delay(180)
        runCatching { ClipboardHistoryManager.captureCurrent(context) }
        discoveredProviders = withContext(Dispatchers.IO) {
            SearchProviderDiscovery.discoverProviders(context)
        }
        scope.launch(Dispatchers.IO) {
            // Warm lightweight preference-backed repos first so they never block first keystrokes.
            runCatching { SnippetsRepository.search(context, "") }
            runCatching { QuicklinksRepository.search(context, "") }
            runCatching { LauncherCalculator.refreshRatesIfNeeded(context) }
            // Run heavy SearchIndex sequentially after provider discovery so PackageManager/ResourcesManager locks don't contend.
            runCatching {
                SearchIndex.get(
                    context,
                    repository.isEnableUnsupportedFeatures(),
                    repository.isShowLegacyFeatures(),
                )
            }
            runCatching {
                java.io.File(context.applicationContext.filesDir, "decision_models")
                    .takeIf { it.exists() }
                    ?.deleteRecursively()
            }
            if (installedExtensions.isNotEmpty()) {
                delay(1200)
                runCatching { RaycastRuntimeEngine.prewarm(context.applicationContext) }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Main) {
            val clipText = runCatching {
                ContextCompat.getSystemService(context, ClipboardManager::class.java)
                    ?.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
            }.getOrNull()
            clipboardContent = ClipboardContentClassifier.classify(clipText)
        }
    }

    val calcResultState = remember { mutableStateOf<CalcResult?>(null) }
    val calcResult: CalcResult? = calcResultState.value
    val builtInActions = remember { BuiltInLauncherTools.allActions() }
    LaunchedEffect(settledQuery) {
        assistantFeedback = null
    }
    LaunchedEffect(calcResult, voiceQueryPending) {
        val result = calcResult ?: return@LaunchedEffect
        if (voiceQueryPending) {
            speakVoiceResponse(result.formattedResult)
        }
    }
    LaunchedEffect(assistantFeedback, voiceQueryPending) {
        val feedback = assistantFeedback ?: return@LaunchedEffect
        if (voiceQueryPending) {
            speakVoiceResponse(feedback.message)
        }
    }
    var matchingToolActions by remember { mutableStateOf<List<BuiltInLauncherAction>>(emptyList()) }
    var matchingSnippets by remember { mutableStateOf<List<com.sameerasw.essentials.launcher.TextSnippet>>(emptyList()) }
    var matchingQuicklinks by remember { mutableStateOf<List<com.sameerasw.essentials.launcher.Quicklink>>(emptyList()) }
    var notificationResults by remember { mutableStateOf<List<com.sameerasw.essentials.launcher.ActiveNotificationItem>>(emptyList()) }

    LaunchedEffect(launcherDataVersion) {
        if (launcherDataVersion == 0) return@LaunchedEffect
        val q = settledQuery.trim()
        if (activeScope != null || q.isEmpty()) return@LaunchedEffect
        val refreshed = withContext(Dispatchers.IO) {
            val mem = assistantMemoryActions(q, context, scope) { success, message ->
                assistantFeedback = AssistantFeedback(success, message)
            }
            val reg = assistantRegistryActions(
                query = q,
                context = context,
                scope = scope,
                onRequestConfirmation = { title, description, proceed ->
                    pendingToolConfirmation = PendingToolConfirmation(title, description, proceed)
                },
            ) { success, message ->
                assistantFeedback = AssistantFeedback(success, message)
            }
            val tools = runCatching {
                (LauncherAssistantCommands.match(context, q) +
                    mem +
                    reg +
                    BuiltInLauncherTools.search(context, q))
                    .distinctBy { it.id }
                    .take(maxResults)
            }.getOrDefault(emptyList())
            val snips = runCatching { SnippetsRepository.search(context, q).take(maxResults) }.getOrDefault(emptyList())
            val links = runCatching { QuicklinksRepository.search(context, q).take(maxResults) }.getOrDefault(emptyList())
            Triple(tools, snips, links)
        }
        matchingToolActions = refreshed.first
        matchingSnippets = refreshed.second
        matchingQuicklinks = refreshed.third
    }
    val matchingProviders = remember(settledQuery, activeScope, visibleProviders) {
        val q = settledQuery.trim()
        if (activeScope != null || q.isEmpty()) {
            emptyList()
        } else {
            visibleProviders.mapNotNull { provider ->
                val score = FuzzySearchUtil.scoreFields(
                    query = q,
                    primary = provider.title,
                    secondary = provider.aliases,
                    tertiary = listOf(provider.subtitle, provider.category),
                )
                if (score > 0) provider to score else null
            }.sortedByDescending { it.second }
                .take(maxResults)
                .map { it.first }
        }
    }

    BackHandler(
        enabled = activeToolModal != null ||
            overlayMode is InlineOverlayMode.ExtensionsHub ||
            (overlayMode is InlineOverlayMode.Search && activeScope != null),
    ) {
        if (activeToolModal != null) {
            activeToolModal = null
            launcherDataVersion++
            return@BackHandler
        }
        when (overlayMode) {
            is InlineOverlayMode.ExtensionsHub -> {
                overlayMode = InlineOverlayMode.Search
                hubQuery = ""
            }
            is InlineOverlayMode.Search -> {
                activeScope = null
                voiceQueryPending = false
                queryState.value = ""
            }
            is InlineOverlayMode.ExtensionRunner -> Unit
        }
    }

    fun copyPlainText(label: String, value: String, toastMessage: String = "Copied $value") {
        val cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        ClipboardHistoryManager.recordCopiedText(context, value)
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    fun openExtensionsHub(initialTab: Int) {
        HapticUtil.performVirtualKeyHaptic(view)
        dragOffsetY = 0f
        hubTab = initialTab
        hubQuery = ""
        activeScope = null
        overlayMode = InlineOverlayMode.ExtensionsHub(initialTab = initialTab)
    }

    fun executeBuiltInAction(action: BuiltInLauncherAction) {
        HapticUtil.performVirtualKeyHaptic(view)
        SearchUsageTracker.record(context, SearchUsageTracker.actionKey(action.id))
        if (action.opensModal != null) {
            activeToolModal = action.opensModal
            return
        }
        if (action.id == "open_extensions") {
            openExtensionsHub(initialTab = 1)
            return
        }
        if (action.id == "show_qr_code") {
            val clipText = runCatching {
                ContextCompat.getSystemService(context, ClipboardManager::class.java)
                    ?.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
            }.getOrNull()?.trim()
            val text = clipText?.takeIf { it.isNotBlank() }
                ?: queryState.value.trim().takeIf { it.isNotBlank() }
            if (text == null) {
                assistantFeedback = AssistantFeedback(
                    false,
                    "Copy or type some text first, then run Show QR Code",
                )
            } else {
                qrDialogText = text
            }
            return
        }
        action.onExecute(context)
        if (action.closesSearch) {
            onFinish()
        }
    }

    fun openInlineExtension(
        ext: InstalledRaycastExtension,
        cmd: RaycastCommandManifest,
        returnToHub: Boolean = false,
    ) {
        HapticUtil.performVirtualKeyHaptic(view)
        dragOffsetY = 0f
        overlayMode = InlineOverlayMode.ExtensionRunner(
            extension = ext,
            command = cmd,
            returnToHub = returnToHub,
        )
    }

    fun enterScope(provider: SearchProvider) {
        HapticUtil.performVirtualKeyHaptic(view)
        if (provider.kind == SearchProviderKind.EXTENSION) {
            openExtensionsHub(initialTab = if (provider.id == "provider:store") 1 else 0)
            return
        }
        activeScope = provider
        voiceQueryPending = false
        queryState.value = ""
        runCatching { focusRequester.requestFocus() }
    }

    fun exitScope() {
        HapticUtil.performVirtualKeyHaptic(view)
        activeScope = null
        voiceQueryPending = false
        queryState.value = ""
        runCatching { focusRequester.requestFocus() }
    }

    fun rankApps(
        installed: List<NotificationApp>,
        queryText: String,
        limit: Int,
        rankContext: SearchContextRanker.Snapshot,
    ): List<PixelSearchResultItem.AppItem> =
        installed.mapNotNull { app ->
            val baseScore = if (queryText.isEmpty()) {
                1000
            } else {
                FuzzySearchUtil.scoreFields(
                    query = queryText,
                    primary = app.appName,
                )
            }
            if (baseScore <= 0) {
                null
            } else {
                app to (
                    baseScore +
                        SearchUsageTracker.boost(
                            context,
                            SearchUsageTracker.appKey(app.packageName),
                        ) +
                        SearchContextRanker.boostForApp(rankContext, app.packageName)
                    )
            }
        }.sortedWith(
            compareByDescending<Pair<NotificationApp, Int>> { it.second }
                .thenBy { it.first.appName.lowercase() },
        ).take(limit).map {
            val app = it.first
            PixelSearchResultItem.AppItem(
                appName = app.appName,
                packageName = app.packageName,
                icon = app.icon,
                isSystemApp = app.isSystemApp,
                isFrozen = false,
            )
        }

    fun rankSettings(
        items: List<com.sameerasw.essentials.domain.model.SearchableItem>,
        queryText: String,
        limit: Int,
    ): List<PixelSearchResultItem.SettingItem> =
        SearchRegistry.searchScored(context, items, queryText)
            .filter { it.first.featureKey != "Quick settings tiles" }
            .take(limit)
            .map { PixelSearchResultItem.SettingItem(it.first) }

    fun performSearch(q: String, currentScope: SearchProvider?) {
        searchJob?.cancel()
        val trimmed = q.trim()
        if (currentScope != null) {
            searchJob = scope.launch(Dispatchers.IO) {
                if (trimmed.isNotEmpty()) {
                    kotlinx.coroutines.delay(75L)
                }
                val spinnerJob = launch {
                    kotlinx.coroutines.delay(160L)
                    withContext(Dispatchers.Main) { isSearching = true }
                }
                try {
                when (currentScope.kind) {
                    SearchProviderKind.PHOTOS -> {
                        val photos = if (PermissionUtils.hasMediaPermissions(context)) {
                            SearchProviderDiscovery.searchPhotos(context, trimmed, limit = 36)
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            scopedPhotos = photos
                            appResults = emptyList()
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.APPS -> {
                        val index = SearchIndex.get(
                            context,
                            repository.isEnableUnsupportedFeatures(),
                            repository.isShowLegacyFeatures(),
                        )
                        val rankContext = SearchContextRanker.snapshot(context)
                        val filteredApps = rankApps(index.apps, trimmed, 24, rankContext)
                        withContext(Dispatchers.Main) {
                            appResults = filteredApps
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.CONTACTS -> {
                        val contacts = if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CONTACTS,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val index = SearchIndex.get(
                                context,
                                repository.isEnableUnsupportedFeatures(),
                                repository.isShowLegacyFeatures(),
                            )
                            ContactsSearchUtil.search(context, index.contacts, trimmed, limit = 20)
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            appResults = emptyList()
                            contactResults = contacts
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.FILES -> {
                        val docs = if (
                            PermissionUtils.hasManageExternalStoragePermission(context) ||
                                PermissionUtils.hasStoragePermission(context)
                        ) {
                            val index = SearchIndex.get(
                                context,
                                repository.isEnableUnsupportedFeatures(),
                                repository.isShowLegacyFeatures(),
                            )
                            FileSearchUtil.searchIndexedFiles(
                                context = context,
                                index = index.files,
                                query = trimmed,
                                limit = 20,
                                searchMedia = false,
                                searchDocs = true,
                            ).documentItems
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            appResults = emptyList()
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = docs
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.SETTINGS -> {
                        val cachedIndex = SearchIndex.peek()
                        val allSystemSettings = cachedIndex?.systemSettings
                            ?: SystemSettingsSearchUtil.all(context)
                        val allSettings = cachedIndex?.settings
                            ?: SearchRegistry.indexItems(
                                context,
                                repository.isEnableUnsupportedFeatures(),
                                repository.isShowLegacyFeatures(),
                            )
                        val sys = SystemSettingsSearchUtil.search(
                            context = context,
                            settings = allSystemSettings,
                            query = trimmed,
                            limit = 16,
                        )
                        val mapped = if (trimmed.isNotEmpty()) {
                            rankSettings(allSettings, trimmed, 16)
                        } else {
                            allSettings
                                .filter { it.featureKey != "Quick settings tiles" }
                                .sortedByDescending { it.control != null }
                                .take(16)
                                .map { PixelSearchResultItem.SettingItem(it) }
                        }
                        val cmds = SearchProviderDiscovery.matchInternalCommands(
                            context = context,
                            query = trimmed.ifEmpty { "settings" },
                            onOpenExtensions = { openExtensionsHub(initialTab = 0) },
                            onOpenStore = { openExtensionsHub(initialTab = 1) },
                            onRunExtension = { ext, cmd -> openInlineExtension(ext, cmd, returnToHub = false) },
                            onOpenUpdates = openInlineUpdates,
                        )
                        withContext(Dispatchers.Main) {
                            appResults = emptyList()
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = sys
                            settingResults = mapped
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = cmds
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.APP_INTENT -> {
                        val index = SearchIndex.get(
                            context,
                            repository.isEnableUnsupportedFeatures(),
                            repository.isShowLegacyFeatures(),
                        )
                        val pkgShortcuts = SearchProviderDiscovery.searchShortcutsIn(
                            context = context,
                            all = index.shortcuts,
                            query = trimmed,
                            packageFilter = currentScope.packageName,
                        )
                        withContext(Dispatchers.Main) {
                            appResults = emptyList()
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = pkgShortcuts
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.WEB,
                    SearchProviderKind.EXTENSION,
                    -> {
                        withContext(Dispatchers.Main) {
                            appResults = emptyList()
                            contactResults = emptyList()
                            mediaResults = emptyList()
                            fileResults = emptyList()
                            systemSettingResults = emptyList()
                            settingResults = emptyList()
                            shortcutResults = emptyList()
                            entityRoutes = emptyList()
                            internalCommands = emptyList()
                            appFunctions = emptyList()
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }
                }
                } finally {
                    spinnerJob.cancel()
                }
                withContext(Dispatchers.Main) {
                    settledQuery = q
                    calcResultState.value = null
                    matchingToolActions = emptyList()
                    matchingSnippets = emptyList()
                    matchingQuicklinks = emptyList()
                    notificationResults = emptyList()
                }
            }
            return
        }

        if (trimmed.isEmpty()) {
            val hadSettled = settledQuery.isNotEmpty()
            searchJob = scope.launch {
                if (hadSettled) {
                    kotlinx.coroutines.delay(45L)
                }
                settledQuery = ""
                calcResultState.value = null
                matchingToolActions = emptyList()
                matchingSnippets = emptyList()
                matchingQuicklinks = emptyList()
                notificationResults = emptyList()
                appResults = emptyList()
                contactResults = emptyList()
                mediaResults = emptyList()
                fileResults = emptyList()
                systemSettingResults = emptyList()
                settingResults = emptyList()
                shortcutResults = emptyList()
                entityRoutes = emptyList()
                internalCommands = emptyList()
                appFunctions = emptyList()
                scopedPhotos = emptyList()
                isSearching = false
            }
            return
        }

        searchJob = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(if (trimmed.length <= 1) 200L else 95L)
            val spinnerJob = launch {
                kotlinx.coroutines.delay(160L)
                withContext(Dispatchers.Main) { isSearching = true }
            }
            try {
                val cachedIndex = SearchIndex.peek()
                val matchedCommands = SearchProviderDiscovery.matchInternalCommands(
                    context = context,
                    query = trimmed,
                    onOpenExtensions = { openExtensionsHub(initialTab = 0) },
                    onOpenStore = { openExtensionsHub(initialTab = 1) },
                    onRunExtension = { ext, cmd -> openInlineExtension(ext, cmd, returnToHub = false) },
                    onOpenUpdates = openInlineUpdates,
                )
                val fastSystemSettings = if (isSettingsEnabled) {
                    val allSys = cachedIndex?.systemSettings ?: SystemSettingsSearchUtil.all(context)
                    SystemSettingsSearchUtil.search(context, allSys, trimmed)
                } else {
                    emptyList()
                }
                val fastMappedSettings = if (isSettingsEnabled) {
                    val allSettings = cachedIndex?.settings ?: SearchRegistry.indexItems(
                        context,
                        repository.isEnableUnsupportedFeatures(),
                        repository.isShowLegacyFeatures(),
                    )
                    rankSettings(allSettings, trimmed, 6)
                } else {
                    emptyList()
                }
                val evalCalc = runCatching { LauncherCalculator.evaluate(q, context) }.getOrNull()
                val fastTools = runCatching {
                    val mem = assistantMemoryActions(q, context, scope) { success, message ->
                        assistantFeedback = AssistantFeedback(success, message)
                    }
                    val reg = assistantRegistryActions(
                        query = q,
                        context = context,
                        scope = scope,
                        onRequestConfirmation = { title, description, proceed ->
                            pendingToolConfirmation = PendingToolConfirmation(title, description, proceed)
                        },
                    ) { success, message ->
                        assistantFeedback = AssistantFeedback(success, message)
                    }
                    (LauncherAssistantCommands.match(context, q) +
                        mem +
                        reg +
                        BuiltInLauncherTools.search(context, q))
                        .distinctBy { it.id }
                        .take(maxResults)
                }.getOrDefault(emptyList())
                val fastSnippets = runCatching { SnippetsRepository.search(context, q).take(maxResults) }.getOrDefault(emptyList())
                val fastQuicklinks = runCatching { QuicklinksRepository.search(context, q).take(maxResults) }.getOrDefault(emptyList())
                val fastNotifications = if (notificationsSearchEnabled) {
                    runCatching {
                        if (ActiveNotificationStore.hasAccess(context)) {
                            ActiveNotificationStore.search(context, q, maxResults)
                        } else {
                            emptyList()
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                if (cachedIndex == null) {
                    withContext(Dispatchers.Main) {
                        settledQuery = q
                        calcResultState.value = evalCalc
                        matchingToolActions = fastTools
                        matchingSnippets = fastSnippets
                        matchingQuicklinks = fastQuicklinks
                        notificationResults = fastNotifications
                        internalCommands = matchedCommands
                        systemSettingResults = fastSystemSettings
                        settingResults = fastMappedSettings
                    }
                }

                val index = cachedIndex ?: SearchIndex.get(
                    context,
                    repository.isEnableUnsupportedFeatures(),
                    repository.isShowLegacyFeatures(),
                )
                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == false) {
                    return@launch
                }
                val rankContext = SearchContextRanker.snapshot(context)

                val filteredApps = if (isAppsEnabled) {
                    rankApps(index.apps, trimmed, 6, rankContext)
                } else {
                    emptyList()
                }

                val contacts = if (isContactsEnabled && ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_CONTACTS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    ContactsSearchUtil.search(context, index.contacts, trimmed)
                } else {
                    emptyList()
                }

                val hasMediaPermission = PermissionUtils.hasMediaPermissions(context)
                val hasStoragePermission =
                    PermissionUtils.hasManageExternalStoragePermission(context) ||
                        PermissionUtils.hasStoragePermission(context)
                val indexedFiles = if (hasStoragePermission || hasMediaPermission) {
                    FileSearchUtil.searchIndexedFiles(
                        context = context,
                        index = index.files,
                        query = trimmed,
                        limit = 6,
                        searchMedia = isMediaEnabled && hasMediaPermission,
                        searchDocs = isFilesEnabled && hasStoragePermission,
                    )
                } else {
                    FileSearchUtil.FileSearchResults()
                }

                val shortcuts = if (isShortcutsEnabled) {
                    SearchProviderDiscovery.searchShortcutsIn(context, index.shortcuts, trimmed)
                } else {
                    emptyList()
                }
                val routes = SearchProviderDiscovery.resolveEntities(context, trimmed, shortcuts)
                val appFns = SearchProviderDiscovery.searchAppFunctionsIn(
                    context,
                    index.appFunctions,
                    trimmed,
                )

                spinnerJob.cancel()
                withContext(Dispatchers.Main) {
                    settledQuery = q
                    calcResultState.value = evalCalc
                    matchingToolActions = fastTools
                    matchingSnippets = fastSnippets
                    matchingQuicklinks = fastQuicklinks
                    notificationResults = fastNotifications
                    internalCommands = matchedCommands
                    appResults = filteredApps
                    contactResults = contacts
                    mediaResults = indexedFiles.mediaItems
                    fileResults = indexedFiles.documentItems
                    systemSettingResults = fastSystemSettings
                    settingResults = fastMappedSettings
                    shortcutResults = shortcuts
                    entityRoutes = routes
                    appFunctions = appFns
                    isSearching = false
                }
            } finally {
                spinnerJob.cancel()
            }
        }
    }

    LaunchedEffect(activeScope) {
        androidx.compose.runtime.snapshotFlow { queryState.value }
            .collect { latestQuery ->
                userHasScrolled = false
                performSearch(latestQuery, activeScope)
            }
    }

    LaunchedEffect(
        isAppsEnabled,
        isContactsEnabled,
        isMediaEnabled,
        isFilesEnabled,
        isSettingsEnabled,
        isShortcutsEnabled,
        isWebEnabled,
        notificationsSearchEnabled,
        typoToleranceEnabled,
    ) {
        if (settingsRefreshNonce > 0) {
            performSearch(queryState.value, activeScope)
        }
    }

    LaunchedEffect(
        activeScope,
        calcResult,
        appResults,
        contactResults,
        mediaResults,
        fileResults,
        systemSettingResults,
        settingResults,
        shortcutResults,
        entityRoutes,
        internalCommands,
        matchingToolActions,
        matchingSnippets,
        matchingQuicklinks,
        scopedPhotos,
        reachabilityLayout,
    ) {
        if (!userHasScrolled && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
            listState.scrollToItem(0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { insetsAnimController?.finish(false) }
            }
            insetsAnimController = null
        }
    }

    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
        if (asOverlay) {
            kotlinx.coroutines.delay(55L)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            view.post {
                if (view.isAttachedToWindow) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            if (asOverlay) {
                view.post {
                    if (view.isAttachedToWindow) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var searchBarHeightPx by remember { mutableIntStateOf(0) }
    val searchBarHeightDp = with(density) {
        if (searchBarHeightPx > 0) searchBarHeightPx.toDp() else 64.dp
    }
    val statusBarHeightPx = with(density) { statusBarHeight.toPx() }
    val bottomBlurHeightPx = with(density) { 140.dp.toPx() }
    val topBlurHeightPx = with(density) { (statusBarHeight + 24.dp).toPx() }

    // Single source of truth for the ONE highlighted most-likely result and Enter/Search execution
    val trimmedQuery = settledQuery.trim()
    val hasMediaPerm = remember(activeScope) { PermissionUtils.hasMediaPermissions(context) }
    val hasContactsPerm = remember(activeScope) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val hasStoragePerm = remember(activeScope) {
        PermissionUtils.hasManageExternalStoragePermission(context) ||
            PermissionUtils.hasStoragePermission(context)
    }
    val hasExactQuicklinkMatch =
        activeScope == null &&
            matchingQuicklinks.firstOrNull()?.let {
                it.keyword.isNotBlank() &&
                    trimmedQuery.split(Regex("\\s+"), limit = 2)[0].equals(it.keyword, ignoreCase = true)
            } == true
    val topTarget = SearchTopTargetResolver.resolve(
        SearchTopTargetInput(
            trimmedQuery = trimmedQuery,
            activeScopeKind = activeScope?.kind,
            hasMediaPermission = hasMediaPerm,
            hasScopedPhotos = scopedPhotos.isNotEmpty(),
            hasCalc = activeScope == null && calcResult != null,
            hasCommands = internalCommands.isNotEmpty(),
            hasTools = activeScope == null && matchingToolActions.isNotEmpty(),
            hasSnippets = activeScope == null && matchingSnippets.isNotEmpty(),
            hasExactQuicklink = hasExactQuicklinkMatch,
            hasQuicklinks = activeScope == null && matchingQuicklinks.isNotEmpty(),
            hasRoutes = activeScope == null && entityRoutes.isNotEmpty(),
            hasApps = isAppsEnabled && appResults.isNotEmpty(),
            hasPrefixSuggestion = activeScope == null && prefixSuggestion != null,
            hasExactPrefixSuggestion = activeScope == null && prefixSuggestion != null && prefixSuggestion.ghostSuffix.isEmpty(),
            hasContacts = (isContactsEnabled || activeScope?.kind == SearchProviderKind.CONTACTS) && contactResults.isNotEmpty(),
            hasNotifications = activeScope == null && notificationsSearchEnabled && notificationResults.isNotEmpty(),
            hasMedia = isMediaEnabled && mediaResults.isNotEmpty(),
            hasFiles = (isFilesEnabled || activeScope?.kind == SearchProviderKind.FILES) && fileResults.isNotEmpty(),
            hasSystemSettings = isSettingsEnabled && systemSettingResults.isNotEmpty(),
            hasEssentialSettings = isSettingsEnabled && settingResults.isNotEmpty(),
            hasShortcuts = activeScope == null && isShortcutsEnabled && shortcutResults.isNotEmpty(),
            hasAppFunctions = activeScope == null && appFunctions.isNotEmpty(),
            hasMatchingProviders = activeScope == null && matchingProviders.isNotEmpty(),
            isWebEnabled = isWebEnabled,
        ),
    )

    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val normalCardColor = MaterialTheme.colorScheme.surfaceBright

    fun openEssentialSetting(setting: com.sameerasw.essentials.domain.model.SearchableItem) {
        val feature = FeatureRegistry.ALL_FEATURES.find { it.id == setting.featureKey }
        val targetFeatureKey =
            if (feature?.searchTargetFeatureId != null) {
                feature.searchTargetFeatureId
            } else if (feature != null && !feature.hasMoreSettings && feature.parentFeatureId != null) {
                feature.parentFeatureId
            } else {
                setting.featureKey
            }
        val highlightKey =
            if (feature != null && !feature.hasMoreSettings && feature.parentFeatureId != null) {
                feature.id
            } else {
                setting.targetSettingHighlightKey
            }

        val intent =
            when (targetFeatureKey) {
                InternalSettingsCatalog.FEATURE_KEY_APP_SETTINGS ->
                    Intent(context, SettingsActivity::class.java)
                InternalSettingsCatalog.FEATURE_KEY_SEARCH_SETTINGS ->
                    Intent(context, PixelSearchbarSettingsActivity::class.java)
                else ->
                    Intent(context, FeatureSettingsActivity::class.java).apply {
                        putExtra("feature", targetFeatureKey)
                        highlightKey?.let {
                            putExtra("highlight_setting", it)
                        }
                    }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        SearchUsageTracker.record(
            context,
            SearchUsageTracker.settingKey(setting.featureKey, setting.title),
        )
        runCatching { context.startActivity(intent) }
        onFinish()
    }

    fun openContact(contact: PixelSearchResultItem.ContactItem) {
        SearchUsageTracker.record(
            context,
            SearchUsageTracker.suggestionKey("contact", contact.id),
        )
        val num = contact.phoneNumber
        if (!num.isNullOrBlank()) {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(num)}"))
            runCatching { context.startActivity(dialIntent) }
            onFinish()
        } else {
            val contactUri = Uri.withAppendedPath(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                contact.id,
            )
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, contactUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
            onFinish()
        }
    }

    fun executeHighlightedTopResult() {
        when (topTarget) {
            TopSearchTarget.NONE -> Unit

            TopSearchTarget.SCOPED_PHOTOS_PERMISSION -> {
                keyboardController?.hide()
                context.startActivity(
                    Intent().setClassName(context, "com.sameerasw.essentials.SettingsActivity").apply {
                        putExtra("expand_permissions", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                onFinish()
            }

            TopSearchTarget.SCOPED_PHOTO -> {
                val topPhoto = scopedPhotos.firstOrNull() ?: return
                keyboardController?.hide()
                SearchProviderDiscovery.launchPhotoInGallery(context, topPhoto)
                onFinish()
            }

            TopSearchTarget.SCOPED_APP_INTENT -> {
                val currentScope = activeScope ?: return
                keyboardController?.hide()
                if (SearchProviderDiscovery.launchScopedProviderSearch(context, currentScope, queryState.value)) {
                    onFinish()
                }
            }

            TopSearchTarget.PREFIX_SCOPE -> {
                val suggestion = prefixSuggestion ?: return
                enterScope(suggestion.provider)
            }

            TopSearchTarget.CALCULATOR -> {
                val calc = calcResult ?: return
                keyboardController?.hide()
                copyPlainText("Calculator Result", calc.copyValue, "Copied ${calc.formattedResult}")
                onFinish()
            }

            TopSearchTarget.INTERNAL_COMMAND -> {
                val cmd = internalCommands.firstOrNull() ?: return
                keyboardController?.hide()
                SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("command", cmd.id))
                cmd.onExecute(context)
                if (cmd.closesSearchUi) onFinish()
            }

            TopSearchTarget.TOOL_ACTION -> {
                val action = matchingToolActions.firstOrNull() ?: return
                keyboardController?.hide()
                executeBuiltInAction(action)
            }

            TopSearchTarget.SNIPPET -> {
                val snippet = matchingSnippets.firstOrNull() ?: return
                keyboardController?.hide()
                SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("snippet", snippet.id))
                val expanded = SnippetsRepository.expandAndCopy(context, snippet)
                Toast.makeText(context, "Copied snippet: ${expanded.take(40)}", Toast.LENGTH_SHORT).show()
                onFinish()
            }

            TopSearchTarget.QUICKLINK -> {
                val link = matchingQuicklinks.firstOrNull() ?: return
                keyboardController?.hide()
                val parts = trimmedQuery.split(Regex("\\s+"), limit = 2)
                val arg =
                    if (link.keyword.isNotBlank() && parts[0].equals(link.keyword, ignoreCase = true) && parts.size > 1) {
                        parts[1]
                    } else {
                        ""
                    }
                SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("quicklink", link.id))
                QuicklinksRepository.launch(context, link, arg)
                onFinish()
            }

            TopSearchTarget.ENTITY_ROUTE -> {
                val route = entityRoutes.firstOrNull() ?: return
                keyboardController?.hide()
                runCatching { context.startActivity(route.navigationIntent) }
                    .onFailure { runCatching { context.startActivity(route.mapIntent) } }
                onFinish()
            }

            TopSearchTarget.APP -> {
                val app = appResults.firstOrNull() ?: return
                keyboardController?.hide()
                launchApp(context, app.packageName)
                onFinish()
            }

            TopSearchTarget.CONTACT -> {
                val contact = contactResults.firstOrNull() ?: return
                keyboardController?.hide()
                openContact(contact)
            }

            TopSearchTarget.NOTIFICATION -> {
                val notification = notificationResults.firstOrNull() ?: return
                keyboardController?.hide()
                if (ActiveNotificationStore.open(context, notification)) {
                    onFinish()
                }
            }

            TopSearchTarget.MEDIA -> {
                val media = mediaResults.firstOrNull() ?: return
                keyboardController?.hide()
                launchFile(context, media)
                onFinish()
            }

            TopSearchTarget.FILE -> {
                val file = fileResults.firstOrNull() ?: return
                keyboardController?.hide()
                launchFile(context, file)
                onFinish()
            }

            TopSearchTarget.SYSTEM_SETTING -> {
                val systemSetting = systemSettingResults.firstOrNull() ?: return
                SearchUsageTracker.record(
                    context,
                    SearchUsageTracker.suggestionKey("syssetting", systemSetting.id),
                )
                val control = systemSetting.control
                if (control != null) {
                    HapticUtil.performVirtualKeyHaptic(view)
                    rippleController?.spawnBurst()
                    scope.launch(if (control.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                        triggerInteractiveSettingPrimaryAction(
                            context = context,
                            control = control,
                            onFallbackOpen = { intent ->
                                scope.launch(Dispatchers.Main) {
                                    keyboardController?.hide()
                                    runCatching { context.startActivity(intent) }
                                    onFinish()
                                }
                            },
                            onUpdated = {
                                scope.launch(Dispatchers.Main) { settingsRefreshNonce++ }
                            },
                        )
                    }
                } else {
                    keyboardController?.hide()
                    runCatching { context.startActivity(systemSetting.intent) }
                    onFinish()
                }
            }

            TopSearchTarget.ESSENTIAL_SETTING -> {
                val setting = settingResults.firstOrNull()?.searchableItem ?: return
                val control = setting.control
                if (control != null) {
                    HapticUtil.performVirtualKeyHaptic(view)
                    rippleController?.spawnBurst()
                    SearchUsageTracker.record(
                        context,
                        SearchUsageTracker.settingKey(setting.featureKey, setting.title),
                    )
                    triggerInteractiveSettingPrimaryAction(
                        context = context,
                        control = control,
                        onFallbackOpen = { intent ->
                            keyboardController?.hide()
                            runCatching { context.startActivity(intent) }
                            onFinish()
                        },
                        onUpdated = { settingsRefreshNonce++ },
                    )
                } else {
                    keyboardController?.hide()
                    openEssentialSetting(setting)
                }
            }

            TopSearchTarget.SHORTCUT -> {
                val shortcut = shortcutResults.firstOrNull() ?: return
                keyboardController?.hide()
                SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("shortcut", shortcut.id))
                SearchProviderDiscovery.launchShortcut(context, shortcut)
                onFinish()
            }

            TopSearchTarget.APP_FUNCTION -> {
                val fn = appFunctions.firstOrNull() ?: return
                keyboardController?.hide()
                fn.launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }
                }
                onFinish()
            }

            TopSearchTarget.MATCHING_PROVIDER -> {
                val provider = matchingProviders.firstOrNull() ?: return
                enterScope(provider)
            }

            TopSearchTarget.WEB_SEARCH -> {
                val currentQuery = queryState.value.trim()
                if (currentQuery.isNotEmpty()) {
                    keyboardController?.hide()
                    launchWebSearch(context, currentQuery, isBubblesWebEnabled, searchEngine)
                    onFinish()
                }
            }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .then(
                if (asOverlay) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            keyboardController?.hide()
                            onFinish()
                        },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
        ) {
            when (val mode = overlayMode) {
                is InlineOverlayMode.ExtensionsHub -> {
                    InlineExtensionsHubOverlay(
                        statusBarHeight = statusBarHeight,
                        navBarHeight = navBarHeight,
                        topBlurHeightPx = topBlurHeightPx,
                        bottomBlurHeightPx = bottomBlurHeightPx,
                        overscrollNestedScrollConnection = overscrollNestedScrollConnection,
                        reachabilityLayout = reachabilityLayout,
                        hubTab = hubTab,
                        onTabChange = { hubTab = it },
                        hubQuery = hubQuery,
                        onQueryChange = { hubQuery = it },
                        storeSource = storeSource,
                        onStoreSourceChange = { storeSource = it },
                        installedExtensions = installedExtensions,
                        storeListings = storeListings,
                        storeLoading = storeLoading,
                        storeError = storeError,
                        installingId = installingId,
                        onSearchStore = { loadStore() },
                        onInstallStoreItem = { listing ->
                            scope.launch {
                                installingId = listing.id
                                RaycastStoreClient.installExtension(context, listing)
                                    .onSuccess { ext ->
                                        Toast.makeText(context, "Installed ${ext.displayTitle}", Toast.LENGTH_SHORT).show()
                                        refreshInstalledExtensions()
                                        hubTab = 0
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Install failed", Toast.LENGTH_LONG).show()
                                    }
                                installingId = null
                            }
                        },
                        onUninstallExtension = { ext ->
                            RaycastExtensionRepository.uninstallExtension(context, ext.id)
                            refreshInstalledExtensions()
                        },
                        onRunCommand = { ext, cmd -> openInlineExtension(ext, cmd, returnToHub = true) },
                        onBack = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            overlayMode = InlineOverlayMode.Search
                            hubQuery = ""
                        },
                    )
                }

                is InlineOverlayMode.ExtensionRunner -> {
                    InlineExtensionRunnerOverlay(
                        extension = mode.extension,
                        command = mode.command,
                        statusBarHeight = statusBarHeight,
                        navBarHeight = navBarHeight,
                        topBlurHeightPx = topBlurHeightPx,
                        bottomBlurHeightPx = bottomBlurHeightPx,
                        overscrollNestedScrollConnection = overscrollNestedScrollConnection,
                        onBack = {
                            if (mode.returnToHub) {
                                overlayMode = InlineOverlayMode.ExtensionsHub(initialTab = hubTab)
                            } else {
                                overlayMode = InlineOverlayMode.Search
                            }
                        },
                        onCloseToSearch = {
                            overlayMode = InlineOverlayMode.Search
                        },
                    )
                }

                is InlineOverlayMode.Search -> {
            val listBottomPadding = searchBarHeightDp + 24.dp
            val initialResultsHidden = hideInitialResults && activeScope == null && settledQuery.isBlank()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        androidx.compose.foundation.layout.WindowInsets.ime
                            .union(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                    )
                    .progressiveBlur(
                        blurRadius = 0f,
                        height = topBlurHeightPx,
                        direction = BlurDirection.TOP,
                        showGradientOverlay = true,
                    )
                    .progressiveBlur(
                        blurRadius = 0f,
                        height = bottomBlurHeightPx,
                        direction = BlurDirection.BOTTOM,
                        showGradientOverlay = true,
                    ),
            ) {
                if (!initialResultsHidden) {
                LazyColumn(
                    state = listState,
                    reverseLayout = reachabilityLayout,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(overscrollNestedScrollConnection)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = statusBarHeight + 14.dp,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                assistantFeedback?.let { feedback ->
                    item(key = "assistant_feedback_section") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (feedback.success) normalCardColor else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = if (feedback.success) "Done" else "Couldn't do that",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (feedback.success) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                    )
                                    Text(
                                        text = feedback.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (feedback.success) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                    )
                                }
                                TextButton(onClick = { assistantFeedback = null }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                if (showUpdateBanner && availableUpdate != null && settledQuery.isBlank() && activeScope == null) {
                    item(key = "in_search_update_banner") {
                        InSearchUpdateBannerCard(
                            updateInfo = availableUpdate,
                            onOpenUpdateSheet = openInlineUpdates,
                            onDismiss = { AppUpdateState.dismissBannerForVersion(availableUpdate.versionName) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // STAGE 2: SCOPED PHOTOS GRID
                if (activeScope?.kind == SearchProviderKind.PHOTOS) {
                    val headerTitle = if (settledQuery.isBlank()) "Recent Photos" else "Photos matching \"$settledQuery\""
                    if (!hasMediaPerm) {
                        item(key = "scoped_photos_perm_section") {
                            Column(
                                modifier = Modifier.fillMaxWidth().animateItem(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SearchSectionHeader(title = headerTitle)
                                RoundedCardContainer {
                                    FeatureCard(
                                        title = "Grant Photos Permission",
                                        description = "Allow media access to search and browse device photos inline",
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            context.startActivity(
                                                Intent().setClassName(context, "com.sameerasw.essentials.SettingsActivity").apply {
                                                    putExtra("expand_permissions", true)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                            )
                                            onFinish()
                                        },
                                        containerColor = if (topTarget == TopSearchTarget.SCOPED_PHOTOS_PERMISSION) highlightColor else normalCardColor,
                                        iconRes = R.drawable.rounded_photo_library_24,
                                        showToggle = false,
                                        hasMoreSettings = false,
                                    )
                                }
                            }
                        }
                    } else if (scopedPhotos.isEmpty() && !isSearching) {
                        item(key = "scoped_photos_empty_section") {
                            Column(
                                modifier = Modifier.fillMaxWidth().animateItem(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SearchSectionHeader(title = headerTitle)
                                RoundedCardContainer {
                                    FeatureCard(
                                        title = "No matching photos",
                                        description = "Try searching by album (Screenshots, Camera), filename, or month",
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            activeScope?.packageName?.let { pkg ->
                                                launchApp(context, pkg)
                                                onFinish()
                                            }
                                        },
                                        containerColor = normalCardColor,
                                        iconRes = R.drawable.rounded_photo_library_24,
                                        showToggle = false,
                                        hasMoreSettings = false,
                                    )
                                }
                            }
                        }
                    } else {
                        if (!reachabilityLayout) {
                            item(key = "scoped_photos_header") {
                                SearchSectionHeader(
                                    title = headerTitle,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        val topScopedPhotoId = scopedPhotos.firstOrNull()?.id
                        val rows = scopedPhotos.chunked(3)
                        items(
                            items = rows,
                            key = { row -> "photo_row_${row.first().id}" },
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (photo in rowItems) {
                                    val isTopmostPhoto =
                                        topTarget == TopSearchTarget.SCOPED_PHOTO &&
                                            photo.id == topScopedPhotoId
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (isTopmostPhoto) {
                                            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    HapticUtil.performVirtualKeyHaptic(view)
                                                    SearchProviderDiscovery.launchPhotoInGallery(context, photo)
                                                    onFinish()
                                                },
                                                onLongClick = {
                                                    HapticUtil.performVirtualKeyHaptic(view)
                                                    selectedFileForAction = photo
                                                },
                                            ),
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = photo.uri,
                                                contentDescription = photo.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            photo.path?.let { album ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.Black.copy(alpha = 0.52f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp),
                                                ) {
                                                    Text(
                                                        text = album,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (reachabilityLayout) {
                            item(key = "scoped_photos_header") {
                                SearchSectionHeader(
                                    title = headerTitle,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }

                // STAGE 2: SCOPED APP INTENT SEARCH
                if (activeScope?.kind == SearchProviderKind.APP_INTENT) {
                    val scopeProvider = activeScope!!
                    item(key = "scoped_app_intent_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(title = "Search in ${scopeProvider.title}")
                            RoundedCardContainer {
                                FeatureCard(
                                    title = if (settledQuery.isBlank()) {
                                        "Open ${scopeProvider.title}"
                                    } else {
                                        "Search \"${settledQuery.trim()}\" in ${scopeProvider.title}"
                                    },
                                    description = scopeProvider.subtitle,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        SearchProviderDiscovery.launchScopedProviderSearch(context, scopeProvider, queryState.value)
                                        onFinish()
                                    },
                                    containerColor = if (topTarget == TopSearchTarget.SCOPED_APP_INTENT) highlightColor else normalCardColor,
                                    iconRes = scopeProvider.iconRes,
                                    iconPainter = scopeProvider.appIcon?.let { remember(it) { BitmapPainter(it) } },
                                    hasIconBackground = scopeProvider.appIcon == null,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    }
                }

                if (settledQuery.isBlank() && activeScope == null &&
                    overlayMode is InlineOverlayMode.Search && !hideInitialResults &&
                    clipboardContent != null && !clipboardCardDismissed
                ) {
                    item(key = "clipboard_actions_section") {
                        val clip = clipboardContent
                        if (clip != null) {
                            ClipboardSmartActionsCard(
                                content = clip,
                                containerColor = normalCardColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                onOpenUrl = { url ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }
                                },
                                onCopy = { label, value ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    copyPlainText(label, value, "Copied $value")
                                    assistantFeedback = AssistantFeedback(true, "Copied $value")
                                },
                                onSaveLink = { url, host ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    QuicklinksRepository.save(
                                        context,
                                        Quicklink(
                                            id = "clip_" + System.currentTimeMillis(),
                                            name = host,
                                            urlTemplate = url,
                                            iconName = "link",
                                        ),
                                    )
                                    launcherDataVersion++
                                    assistantFeedback = AssistantFeedback(true, "Saved $host to Quicklinks")
                                },
                                onShowQr = { text ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    qrDialogText = text
                                },
                                onCall = { number ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }
                                },
                                onText = { number ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }
                                },
                                onCompose = { address ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }
                                },
                                onUseColor = { code ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    clipboardCardDismissed = true
                                    queryState.value = code
                                },
                                onDismiss = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    clipboardCardDismissed = true
                                },
                            )
                        }
                    }
                }

                // STAGE 1: EMPTY QUERY — RAYCAST / VICINAE VERTICAL COMMAND LIST
                if (activeScope == null && settledQuery.isBlank()) {
                    val orderedBuiltInActions = builtInActions.take(maxResults).inReachabilityOrder(reachabilityLayout)
                    if (!reachabilityLayout) {
                        item(key = "core_tools_header") {
                            SearchSectionHeader("Core Tools & Commands")
                        }
                    }
                    itemsIndexed(
                        items = orderedBuiltInActions,
                        key = { _, action -> "core_tool_${action.id}" },
                    ) { _, action ->
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                            RaycastCommandRow(
                                title = action.title,
                                subtitle = action.subtitle,
                                badge = action.badge,
                                iconRes = action.iconRes,
                                containerColor = normalCardColor,
                                onClick = { executeBuiltInAction(action) },
                            )
                        }
                    }
                    if (reachabilityLayout) {
                        item(key = "core_tools_header") {
                            SearchSectionHeader("Core Tools & Commands")
                        }
                    }

                    if (installedExtensions.isNotEmpty()) {
                        item(key = "installed_ext_section") {
                            val compatReports by androidx.compose.runtime.produceState<Map<String, com.sameerasw.essentials.raycast.CompatibilityReport>>(
                                initialValue = installedExtensions.mapNotNull { ext ->
                                    RaycastCompatibilityAnalyzer.getCachedInstalledReport(ext)?.let { ext.id to it }
                                }.toMap(),
                                key1 = installedExtensions,
                            ) {
                                value = withContext(Dispatchers.IO) {
                                    installedExtensions.associate { ext ->
                                        ext.id to RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SearchSectionHeader("Installed Extensions")
                                RoundedCardContainer {
                                    val extensionEntries = installedExtensions.flatMap { ext ->
                                        val report = compatReports[ext.id]
                                        ext.manifest.commands.map { cmd -> Triple(ext, cmd, report) }
                                    }
                                    extensionEntries.take(maxResults.coerceAtMost(10)).inReachabilityOrder(reachabilityLayout).forEach { (ext, cmd, report) ->
                                        RaycastCommandRow(
                                            title = cmd.title.ifBlank { cmd.name },
                                            subtitle = "${ext.displayTitle}${if (cmd.description.isNotBlank()) " • ${cmd.description}" else ""}",
                                            badge = when (report?.level) {
                                                AndroidCompatLevel.COMPATIBLE, null -> "Extension"
                                                AndroidCompatLevel.PARTIAL -> "Partial"
                                                AndroidCompatLevel.INCOMPATIBLE -> "Desktop API"
                                            },
                                            iconRes = R.drawable.rounded_extension_24,
                                            containerColor = normalCardColor,
                                            onClick = { openInlineExtension(ext, cmd, returnToHub = false) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (visibleProviders.isNotEmpty()) {
                        val orderedProviders = visibleProviders.take(maxResults.coerceAtMost(12)).inReachabilityOrder(reachabilityLayout)
                        if (!reachabilityLayout) {
                            item(key = "providers_header") {
                                SearchSectionHeader("Search Providers & App Links")
                            }
                        }
                        itemsIndexed(
                            items = orderedProviders,
                            key = { _, provider -> "provider_${provider.id}" },
                        ) { _, provider ->
                            val aliasHint = provider.aliases.firstOrNull()?.let { "Alias: $it" }
                            val subtitleText = listOfNotNull(provider.subtitle.takeIf { it.isNotBlank() }, aliasHint).joinToString(" • ")
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                                RaycastCommandRow(
                                    title = provider.title,
                                    subtitle = subtitleText,
                                    badge = provider.category,
                                    iconRes = provider.iconRes,
                                    iconBitmap = provider.appIcon,
                                    containerColor = normalCardColor,
                                    onClick = { enterScope(provider) },
                                )
                            }
                        }
                        if (reachabilityLayout) {
                            item(key = "providers_header") {
                                SearchSectionHeader("Search Providers & App Links")
                            }
                        }
                    }
                }

                // STAGE 1: LIVE CALCULATOR / CONVERTER / TIMEZONE
                if (activeScope == null && calcResult != null) {
                    item(key = "calc_section") {
                        val isTopmost = topTarget == TopSearchTarget.CALCULATOR
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(calcResult.category)
                            RoundedCardContainer {
                                Surface(
                                    color = if (isTopmost) highlightColor else normalCardColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            copyPlainText("Calculator Result", calcResult.copyValue, "Copied ${calcResult.formattedResult}")
                                            onFinish()
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(R.drawable.rounded_calculate_24),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = calcResult.formattedResult,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${calcResult.expression} • ${calcResult.subtitle}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = "Copy",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // STAGE 1: MATCHING BUILT-IN LAUNCHER TOOLS
                if (activeScope == null && matchingToolActions.isNotEmpty()) {
                    item(key = "tools_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(
                                    if (matchingToolActions.any { it.id.startsWith("assistant_") || it.id.startsWith("tool_") }) {
                                        "Assistant"
                                    } else {
                                        "Launcher Tools"
                                    },
                                )
                            RoundedCardContainer {
                                matchingToolActions.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, action) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.TOOL_ACTION
                                    RaycastCommandRow(
                                        title = action.title,
                                        subtitle = action.subtitle,
                                        badge = action.badge,
                                        iconRes = action.iconRes,
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        onClick = { executeBuiltInAction(action) },
                                    )
                                }
                            }
                        }
                    }
                }

                // STAGE 1: MATCHING TEXT SNIPPETS
                if (activeScope == null && matchingSnippets.isNotEmpty()) {
                    item(key = "snippets_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Snippets")
                            RoundedCardContainer {
                                matchingSnippets.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, snippet) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.SNIPPET
                                    val preview = remember(snippet.id, snippet.content) {
                                        SnippetsRepository.expandContent(snippet.content).replace("\n", " ")
                                    }
                                    RaycastCommandRow(
                                        title = snippet.name,
                                        subtitle = preview,
                                        badge = snippet.keyword.ifBlank { "Snippet" },
                                        iconRes = R.drawable.rounded_edit_note_24,
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("snippet", snippet.id))
                                            val expanded = SnippetsRepository.expandAndCopy(context, snippet)
                                            Toast.makeText(context, "Copied snippet: ${expanded.take(40)}", Toast.LENGTH_SHORT).show()
                                            onFinish()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // STAGE 1: MATCHING QUICKLINKS
                if (activeScope == null && matchingQuicklinks.isNotEmpty()) {
                    item(key = "quicklinks_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Quicklinks")
                            RoundedCardContainer {
                                matchingQuicklinks.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, link) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.QUICKLINK
                                    val arg = remember(trimmedQuery, link.keyword) {
                                        val parts = trimmedQuery.split(Regex("\\s+"), limit = 2)
                                        if (link.keyword.isNotBlank() && parts[0].equals(link.keyword, ignoreCase = true) && parts.size > 1) {
                                            parts[1]
                                        } else {
                                            ""
                                        }
                                    }
                                    RaycastCommandRow(
                                        title = if (arg.isNotBlank()) "${link.name}: $arg" else link.name,
                                        subtitle = link.urlTemplate,
                                        badge = link.keyword.ifBlank { "Link" },
                                        iconRes = R.drawable.rounded_link_24,
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("quicklink", link.id))
                                            QuicklinksRepository.launch(context, link, arg)
                                            onFinish()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // STAGE 1: PREFIX SCOPE SUGGESTION
                if (activeScope == null && prefixSuggestion != null) {
                    val suggestion = prefixSuggestion
                    item(key = "prefix_scope_section") {
                        val isTopmost = topTarget == TopSearchTarget.PREFIX_SCOPE
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Scope Search")
                            RoundedCardContainer {
                                FeatureCard(
                                    title = "Search in ${suggestion.provider.title}",
                                    description = if (isTopmost) {
                                        "${suggestion.provider.subtitle} • Press Tab or Enter to lock scope"
                                    } else {
                                        "${suggestion.provider.subtitle} • Press Tab to lock scope"
                                    },
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = { enterScope(suggestion.provider) },
                                    containerColor = if (isTopmost) highlightColor else normalCardColor,
                                    iconRes = suggestion.provider.iconRes,
                                    iconPainter = suggestion.provider.appIcon?.let { remember(it) { BitmapPainter(it) } },
                                    hasIconBackground = suggestion.provider.appIcon == null,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    }
                }

                // STAGE 1 / SETTINGS SCOPE: INTERNAL COMMANDS
                if (internalCommands.isNotEmpty()) {
                    item(key = "commands_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Komorebi Commands")
                            RoundedCardContainer {
                                internalCommands.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, cmd) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.INTERNAL_COMMAND
                                    FeatureCard(
                                        title = cmd.title,
                                        description = cmd.description,
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("command", cmd.id))
                                            cmd.onExecute(context)
                                            if (cmd.closesSearchUi) onFinish()
                                        },
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        iconRes = cmd.iconRes,
                                        showToggle = false,
                                        hasMoreSettings = true,
                                    )
                                }
                            }
                        }
                    }
                }

                // STAGE 1: CONTEXTUAL ENTITY ROUTES (WORK / HOME / CALENDAR / CONTACTS)
                if (activeScope == null && entityRoutes.isNotEmpty()) {
                    item(key = "routes_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Directions & Places")
                            RoundedCardContainer {
                                entityRoutes.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, route) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.ENTITY_ROUTE
                                    Surface(
                                        color = if (isTopmost) highlightColor else normalCardColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                HapticUtil.performVirtualKeyHaptic(view)
                                                runCatching { context.startActivity(route.navigationIntent) }
                                                    .onFailure { runCatching { context.startActivity(route.mapIntent) } }
                                                onFinish()
                                            },
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.rounded_location_on_24),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = route.label,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        text = "${route.address} • ${route.sourceLabel}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                val options = route.navigationOptions.ifEmpty {
                                                    listOf(
                                                        com.sameerasw.essentials.search.EntityNavigationOption(
                                                            id = "default",
                                                            label = "Navigate",
                                                            intent = route.navigationIntent,
                                                        ),
                                                    )
                                                }
                                                options.forEach { option ->
                                                    FilledTonalButton(
                                                        onClick = {
                                                            HapticUtil.performVirtualKeyHaptic(view)
                                                            runCatching { context.startActivity(option.intent) }
                                                                .onFailure { runCatching { context.startActivity(route.mapIntent) } }
                                                            onFinish()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.rounded_navigation_24),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        HapticUtil.performVirtualKeyHaptic(view)
                                                        runCatching { context.startActivity(route.mapIntent) }
                                                        onFinish()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                ) {
                                                    Text("View Map", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // APPS
                if (isAppsEnabled && appResults.isNotEmpty()) {
                    item(key = "apps_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_apps))
                            RoundedCardContainer {
                                appResults.take(maxResults).withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, app) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.APP
                                    FeatureCard(
                                        title = app.appName,
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            launchApp(context, app.packageName)
                                            onFinish()
                                        },
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        showToggle = false,
                                        hasMoreSettings = false,
                                        iconPainter = if (app.icon != null) {
                                            BitmapPainter(app.icon)
                                        } else null,
                                        iconRes = if (app.icon == null) R.drawable.rounded_apps_24 else null,
                                        iconSize = 36.dp,
                                        hasIconBackground = app.icon == null,
                                    )
                                }
                            }
                        }
                    }
                }

                // CONTACTS
                if (activeScope?.kind == SearchProviderKind.CONTACTS && !hasContactsPerm) {
                    item(key = "scoped_contacts_perm_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_contacts))
                            RoundedCardContainer {
                                FeatureCard(
                                    title = "Grant Contacts Permission",
                                    description = "Allow contacts access to search and call people inline",
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        context.startActivity(
                                            Intent().setClassName(context, "com.sameerasw.essentials.SettingsActivity").apply {
                                                putExtra("expand_permissions", true)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                        onFinish()
                                    },
                                    containerColor = normalCardColor,
                                    iconRes = R.drawable.rounded_call_24,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    }
                }
                if ((isContactsEnabled || activeScope?.kind == SearchProviderKind.CONTACTS) && contactResults.isNotEmpty()) {
                    item(key = "contacts_section") {
                        val contactsToShow = if (activeScope?.kind == SearchProviderKind.CONTACTS) {
                            contactResults
                        } else {
                            contactResults.take(maxResults)
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_contacts))
                            RoundedCardContainer {
                                contactsToShow.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, contact) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.CONTACT
                                    ListItem(
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            openContact(contact)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingContent = {
                                            if (!contact.photoUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = contact.photoUri,
                                                    contentDescription = contact.name,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(ColorUtil.getPastelColorFor(contact.name)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = contact.name.take(1).uppercase(),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = ColorUtil.getVibrantColorFor(contact.name),
                                                    )
                                                }
                                            }
                                        },
                                        supportingContent = {
                                            contact.phoneNumber?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            contact.phoneNumber?.let { num ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(onClick = {
                                                        HapticUtil.performVirtualKeyHaptic(view)
                                                        SearchUsageTracker.record(
                                                            context,
                                                            SearchUsageTracker.suggestionKey("contact", contact.id),
                                                        )
                                                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(num)}"))
                                                        runCatching { context.startActivity(callIntent) }
                                                        onFinish()
                                                    }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.rounded_call_24),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(22.dp),
                                                        )
                                                    }
                                                    IconButton(onClick = {
                                                        HapticUtil.performVirtualKeyHaptic(view)
                                                        SearchUsageTracker.record(
                                                            context,
                                                            SearchUsageTracker.suggestionKey("contact", contact.id),
                                                        )
                                                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(num)}"))
                                                        runCatching { context.startActivity(smsIntent) }
                                                        onFinish()
                                                    }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.rounded_chat_bubble_24),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(22.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    ) {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (notificationsSearchEnabled && notificationResults.isNotEmpty()) {
                    item(key = "notifications_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Notifications")
                            RoundedCardContainer {
                                notificationResults.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, notification) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.NOTIFICATION
                                    RaycastCommandRow(
                                        title = notification.title,
                                        subtitle = notification.text.ifBlank { notification.appLabel },
                                        badge = notification.appLabel,
                                        iconRes = R.drawable.rounded_notifications_24,
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            if (ActiveNotificationStore.open(context, notification)) {
                                                onFinish()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (isMediaEnabled && mediaResults.isNotEmpty()) {
                    item(key = "media_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_media))
                            val cappedMedia = mediaResults.take(maxResults)
                            val mediaCarouselState = rememberCarouselState { cappedMedia.size }
                            HorizontalMultiBrowseCarousel(
                                state = mediaCarouselState,
                                preferredItemWidth = 140.dp,
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                            ) { index ->
                                val media = cappedMedia[index]
                                val isTopmost = index == 0 && topTarget == TopSearchTarget.MEDIA
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .maskClip(RoundedCornerShape(20.dp))
                                        .then(
                                            if (isTopmost) {
                                                Modifier.border(
                                                    width = 2.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(20.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            launchFile(context, media)
                                            onFinish()
                                        },
                                ) {
                                    AsyncImage(
                                        model = media.uri,
                                        contentDescription = media.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (media.isVideo || media.isGif) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (media.isVideo) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.rounded_play_arrow_24),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                } else {
                                                    Text(
                                                        text = "GIF",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            selectedFileForAction = media
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(32.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.rounded_more_vert_24),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.75f),
                                                    ),
                                                ),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    ) {
                                        Column {
                                            Text(
                                                text = media.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = FileSearchUtil.formatFileSize(media.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.75f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FILES
                if (activeScope?.kind == SearchProviderKind.FILES && !hasStoragePerm) {
                    item(key = "scoped_files_perm_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_files))
                            RoundedCardContainer {
                                FeatureCard(
                                    title = "Grant Files Permission",
                                    description = "Allow file access to search and browse device documents inline",
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        context.startActivity(
                                            Intent().setClassName(context, "com.sameerasw.essentials.SettingsActivity").apply {
                                                putExtra("expand_permissions", true)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                        onFinish()
                                    },
                                    containerColor = normalCardColor,
                                    iconRes = R.drawable.rounded_description_24,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    }
                }
                if ((isFilesEnabled || activeScope?.kind == SearchProviderKind.FILES) && fileResults.isNotEmpty()) {
                    item(key = "files_section") {
                        val filesToShow = if (activeScope?.kind == SearchProviderKind.FILES) {
                            fileResults
                        } else {
                            fileResults.take(maxResults)
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_files))
                            RoundedCardContainer {
                                filesToShow.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, file) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.FILE
                                    ListItem(
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            launchFile(context, file)
                                            onFinish()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorUtil.getPastelColorFor(file.displayName)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    painter = painterResource(file.iconRes),
                                                    contentDescription = null,
                                                    tint = ColorUtil.getVibrantColorFor(file.displayName),
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        },
                                        supportingContent = {
                                            Text(
                                                text = FileSearchUtil.formatFileSize(file.sizeBytes),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        trailingContent = {
                                            IconButton(onClick = {
                                                HapticUtil.performVirtualKeyHaptic(view)
                                                selectedFileForAction = file
                                            }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.rounded_more_vert_24),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            text = file.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SETTINGS (System Settings)
                if (isSettingsEnabled && systemSettingResults.isNotEmpty()) {
                    val orderedSystemSettings = systemSettingResults.take(maxResults).withIndex().toList().inReachabilityOrder(reachabilityLayout)
                    if (!reachabilityLayout) {
                        item(key = "system_settings_header") {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_system_settings))
                        }
                    }
                    itemsIndexed(
                        items = orderedSystemSettings,
                        key = { _, (origIndex, setting) -> "sys_setting_${setting.id}_$origIndex" },
                    ) { _, (origIndex, setting) ->
                        val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.SYSTEM_SETTING
                        val cardColor = if (isTopmost) highlightColor else normalCardColor
                        val control = setting.control
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                            if (control != null) {
                                InteractiveSearchSettingCard(
                                    id = setting.id,
                                    title = setting.title,
                                    subtitle = setting.subtitle,
                                    iconRes = setting.iconRes,
                                    control = control,
                                    containerColor = cardColor,
                                    refreshNonce = settingsRefreshNonce,
                                    onSettingChanged = {
                                        SearchUsageTracker.record(
                                            context,
                                            SearchUsageTracker.suggestionKey("syssetting", setting.id),
                                        )
                                        settingsRefreshNonce++
                                    },
                                    onOpenFallback = { intent ->
                                        keyboardController?.hide()
                                        runCatching { context.startActivity(intent) }
                                        onFinish()
                                    },
                                )
                            } else {
                                FeatureCard(
                                    title = setting.title,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        SearchUsageTracker.record(
                                            context,
                                            SearchUsageTracker.suggestionKey("syssetting", setting.id),
                                        )
                                        runCatching { context.startActivity(setting.intent) }
                                        onFinish()
                                    },
                                    containerColor = cardColor,
                                    iconRes = setting.iconRes,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                    description = setting.subtitle,
                                )
                            }
                        }
                    }
                    if (reachabilityLayout) {
                        item(key = "system_settings_header") {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_system_settings))
                        }
                    }
                }

                // ESSENTIALS (Komorebi Internal Settings)
                if (isSettingsEnabled && settingResults.isNotEmpty()) {
                    val orderedInternalSettings = settingResults.take(maxResults).withIndex().toList().inReachabilityOrder(reachabilityLayout)
                    if (!reachabilityLayout) {
                        item(key = "essentials_header") {
                            SearchSectionHeader(stringResource(R.string.app_name))
                        }
                    }
                    itemsIndexed(
                        items = orderedInternalSettings,
                        key = { _, (origIndex, item) ->
                            val s = item.searchableItem
                            "ess_setting_${s.featureKey}_${s.targetSettingHighlightKey ?: s.title}_$origIndex"
                        },
                    ) { _, (origIndex, item) ->
                        val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.ESSENTIAL_SETTING
                        val cardColor = if (isTopmost) highlightColor else normalCardColor
                        val setting = item.searchableItem
                        val control = setting.control
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                            if (control != null) {
                                InteractiveSearchSettingCard(
                                    id = "${setting.featureKey}:${setting.targetSettingHighlightKey ?: setting.title}",
                                    title = setting.title,
                                    subtitle = setting.description,
                                    iconRes = setting.icon ?: R.drawable.rounded_settings_24,
                                    control = control,
                                    containerColor = cardColor,
                                    refreshNonce = settingsRefreshNonce,
                                    onSettingChanged = {
                                        SearchUsageTracker.record(
                                            context,
                                            SearchUsageTracker.settingKey(setting.featureKey, setting.title),
                                        )
                                        settingsRefreshNonce++
                                    },
                                    onOpenFallback = { intent ->
                                        keyboardController?.hide()
                                        runCatching { context.startActivity(intent) }
                                        onFinish()
                                    },
                                )
                            } else {
                                FeatureCard(
                                    title = setting.title,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        openEssentialSetting(setting)
                                    },
                                    containerColor = cardColor,
                                    iconRes = setting.icon ?: R.drawable.rounded_settings_24,
                                    showToggle = false,
                                    hasMoreSettings = true,
                                    description = setting.description,
                                    isBeta = setting.isBeta,
                                    isLegacy = setting.isLegacy,
                                    isUnsupported = setting.isUnsupported,
                                    descriptionOverride =
                                        if (setting.parentFeature != null) {
                                            "${setting.parentFeature} > ${setting.description}"
                                        } else {
                                            setting.description
                                        },
                                )
                            }
                        }
                    }
                    if (reachabilityLayout) {
                        item(key = "essentials_header") {
                            SearchSectionHeader(stringResource(R.string.app_name))
                        }
                    }
                }

                // SHORTCUTS
                if ((activeScope == null || activeScope?.kind == SearchProviderKind.APP_INTENT) &&
                    isShortcutsEnabled &&
                    shortcutResults.isNotEmpty()
                ) {
                    item(key = "shortcuts_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_shortcuts))
                            RoundedCardContainer {
                                shortcutResults.take(maxResults).withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, shortcut) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.SHORTCUT
                                    FeatureCard(
                                        title = shortcut.shortLabel,
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            SearchUsageTracker.record(context, SearchUsageTracker.suggestionKey("shortcut", shortcut.id))
                                            SearchProviderDiscovery.launchShortcut(context, shortcut)
                                            onFinish()
                                        },
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        iconRes = shortcut.fallbackIconRes,
                                        iconPainter = shortcut.icon?.let { remember(it) { BitmapPainter(it) } },
                                        hasIconBackground = shortcut.icon == null,
                                        showToggle = false,
                                        hasMoreSettings = false,
                                        description = if (shortcut.fromShizuku) "${shortcut.appName} • Shizuku" else shortcut.appName,
                                    )
                                }
                            }
                        }
                    }
                }

                // APP FUNCTIONS (Android 16+)
                if (activeScope == null && appFunctions.isNotEmpty()) {
                    item(key = "app_functions_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("App Functions")
                            RoundedCardContainer {
                                appFunctions.take(maxResults).withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, fn) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.APP_FUNCTION
                                    FeatureCard(
                                        title = fn.functionLabel,
                                        description = "${fn.appName} • App Function",
                                        isEnabled = true,
                                        onToggle = {},
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            fn.launchIntent?.let {
                                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                runCatching { context.startActivity(it) }
                                            }
                                            onFinish()
                                        },
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        iconRes = R.drawable.rounded_extension_24,
                                        iconPainter = fn.icon?.let { remember(it) { BitmapPainter(it) } },
                                        hasIconBackground = fn.icon == null,
                                        showToggle = false,
                                        hasMoreSettings = false,
                                    )
                                }
                            }
                        }
                    }
                }

                // MATCHING SEARCH PROVIDERS & APP LINKS
                if (activeScope == null && matchingProviders.isNotEmpty()) {
                    item(key = "matching_providers_section") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader("Search Providers & App Links")
                            RoundedCardContainer {
                                matchingProviders.withIndex().toList().inReachabilityOrder(reachabilityLayout).forEach { (origIndex, provider) ->
                                    val isTopmost = origIndex == 0 && topTarget == TopSearchTarget.MATCHING_PROVIDER
                                    RaycastCommandRow(
                                        title = provider.title,
                                        subtitle = provider.subtitle.ifBlank { "Search in ${provider.title}" },
                                        badge = provider.category,
                                        iconRes = provider.iconRes,
                                        iconBitmap = provider.appIcon,
                                        containerColor = if (isTopmost) highlightColor else normalCardColor,
                                        onClick = {
                                            HapticUtil.performVirtualKeyHaptic(view)
                                            enterScope(provider)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // WEB SEARCH
                val showWebSection =
                    (activeScope?.kind == SearchProviderKind.WEB) ||
                        (activeScope == null && isWebEnabled && settledQuery.isNotBlank())
                if (showWebSection) {
                    val isTopmost = topTarget == TopSearchTarget.WEB_SEARCH
                    item(key = "web_section") {
                        val trimmedWebQuery = settledQuery.trim()
                        val isUrl = remember(trimmedWebQuery) {
                            trimmedWebQuery.isNotEmpty() && isLikelyUrl(trimmedWebQuery)
                        }
                        val actionTitle = when {
                            trimmedWebQuery.isEmpty() -> "Search the web with $searchEngine"
                            isUrl -> stringResource(R.string.pixel_search_open_url_action, trimmedWebQuery)
                            else -> stringResource(R.string.pixel_search_web_search_action, trimmedWebQuery)
                        }
                        val actionDescription = if (trimmedWebQuery.isEmpty()) {
                            "Type a search query or URL and press search"
                        } else {
                            null
                        }
                        val webIcon = if (isUrl) R.drawable.rounded_link_24 else R.drawable.rounded_language_24

                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchSectionHeader(stringResource(R.string.pixel_search_section_web))
                            RoundedCardContainer {
                                FeatureCard(
                                    title = actionTitle,
                                    description = actionDescription,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        if (trimmedWebQuery.isNotEmpty()) {
                                            launchWebSearch(context, trimmedWebQuery, isBubblesWebEnabled, searchEngine)
                                            onFinish()
                                        } else {
                                            runCatching { focusRequester.requestFocus() }
                                            keyboardController?.show()
                                        }
                                    },
                                    containerColor = if (isTopmost) highlightColor else normalCardColor,
                                    iconRes = webIcon,
                                    hasIconBackground = false,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    }
                }

                if (!reachabilityLayout) {
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
            }
        }

            Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(20f)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .onSizeChanged { searchBarHeightPx = it.height }
                .then(
                    if (asOverlay) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (initialResultsHidden && showUpdateBanner && availableUpdate != null) {
                InSearchUpdateBannerCard(
                    updateInfo = availableUpdate,
                    onOpenUpdateSheet = openInlineUpdates,
                    onDismiss = { AppUpdateState.dismissBannerForVersion(availableUpdate.versionName) },
                )
            }
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    AnimatedVisibility(
                        visible = activeScope != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.88f),
                        exit = fadeOut() + scaleOut(targetScale = 0.88f),
                    ) {
                        activeScope?.let { scopeProvider ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clip(CircleShape)
                                    .clickable { exitScope() },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                ) {
                                    if (scopeProvider.appIcon != null) {
                                        Image(
                                            painter = BitmapPainter(scopeProvider.appIcon),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(scopeProvider.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = scopeProvider.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_close_24),
                                        contentDescription = "Exit scope",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    val bodyLargeStyle = MaterialTheme.typography.bodyLarge
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)

                    PixelSearchInputField(
                        queryState = queryState,
                        activeScope = activeScope,
                        prefixSuggestion = prefixSuggestion,
                        isVoiceListening = isVoiceListening,
                        focusRequester = focusRequester,
                        bodyLargeStyle = bodyLargeStyle,
                        onSurfaceColor = onSurfaceColor,
                        hintColor = hintColor,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        onQueryEdited = { voiceQueryPending = false },
                        onExecuteTopResult = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            executeHighlightedTopResult()
                        },
                        onEnterScope = { enterScope(it) },
                        onExitScope = { exitScope() },
                    )

                    if (activeScope == null && prefixSuggestion != null) {
                        val suggestion = prefixSuggestion
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .clickable { enterScope(suggestion.provider) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = suggestion.provider.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.rounded_arrow_forward_24),
                                    contentDescription = "Scope to ${suggestion.provider.title}",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    if (voiceSearchEnabled && activeScope == null) {
                        IconButton(
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                if (isVoiceListening) {
                                    runCatching { speechRecognizer?.cancel() }
                                    isVoiceListening = false
                                } else if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    startVoiceSearch()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_mic_24),
                                contentDescription = if (isVoiceListening) "Stop voice search" else "Voice search",
                                tint = if (isVoiceListening) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    PixelSearchTrailingAction(
                        isSearching = isSearching,
                        queryState = queryState,
                        hasActiveScope = activeScope != null,
                        onClearQuery = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            voiceQueryPending = false
                            queryState.value = ""
                        },
                        onExitScope = { exitScope() },
                    )
                }
            }
        }
            } // end InlineOverlayMode.Search
        } // end when(overlayMode)

        selectedFileForAction?.let { fileItem ->
            PixelSearchFileActionSheet(
                fileItem = fileItem,
                onDismissRequest = { selectedFileForAction = null },
                onActionCompleted = {
                    selectedFileForAction = null
                    onFinish()
                },
            )
        }

        activeToolModal?.let { modal ->
            LauncherToolModalOverlay(
                modal = modal,
                initialQuery = settledQuery,
                reachabilityLayout = reachabilityLayout,
                onDismiss = {
                    activeToolModal = null
                    launcherDataVersion++
                },
                onCopiedAndClose = {
                    activeToolModal = null
                    launcherDataVersion++
                    onFinish()
                },
            )
        }

        qrDialogText?.let { text ->
            QrCodeDialog(
                text = text,
                onDismiss = { qrDialogText = null },
            )
        }

        pendingToolConfirmation?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingToolConfirmation = null },
                title = { Text("Confirm ${pending.title}") },
                text = {
                    Text(
                        pending.description.ifBlank {
                            "Are you sure you want to run \"${pending.title}\"?"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val action = pending.onConfirm
                            pendingToolConfirmation = null
                            action()
                        },
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingToolConfirmation = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showInlineUpdateSheet) {
            InSearchUpdateOverlay(
                updateInfo = updateInfo,
                isChecking = isCheckingUpdate,
                errorMessage = updateCheckError,
                checkCompleted = updateCheckCompleted,
                onRetry = { AppUpdateState.checkForUpdates(context, manual = true) },
                onDismiss = { showInlineUpdateSheet = false },
                onDismissSearchForInstall = {
                    showInlineUpdateSheet = false
                    onFinish()
                },
                modifier = Modifier.zIndex(40f),
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.InlineExtensionsHubOverlay(
    statusBarHeight: Dp,
    navBarHeight: Dp,
    topBlurHeightPx: Float,
    bottomBlurHeightPx: Float,
    overscrollNestedScrollConnection: NestedScrollConnection,
    reachabilityLayout: Boolean,
    hubTab: Int,
    onTabChange: (Int) -> Unit,
    hubQuery: String,
    onQueryChange: (String) -> Unit,
    storeSource: String,
    onStoreSourceChange: (String) -> Unit,
    installedExtensions: List<InstalledRaycastExtension>,
    storeListings: List<StoreExtensionListing>,
    storeLoading: Boolean,
    storeError: String?,
    installingId: String?,
    onSearchStore: () -> Unit,
    onInstallStoreItem: (StoreExtensionListing) -> Unit,
    onUninstallExtension: (InstalledRaycastExtension) -> Unit,
    onRunCommand: (InstalledRaycastExtension, RaycastCommandManifest) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val searchBarBottomPadding = with(density) {
        if (imeBottomPx > navBottomPx) imeBottomPx.toDp() + 8.dp
        else navBottomPx.toDp().coerceAtLeast(16.dp)
    }
    var hubBarHeightPx by remember { mutableIntStateOf(0) }
    val hubBarHeightDp = with(density) { hubBarHeightPx.toDp() }.coerceAtLeast(72.dp)
    val listBottomPadding = searchBarBottomPadding + hubBarHeightDp + 12.dp
    var prefsVersion by remember { mutableIntStateOf(0) }
    var inspectingCompat by remember { mutableStateOf<Pair<String, CompatibilityReport>?>(null) }
    var preferencesTarget by remember { mutableStateOf<InstalledRaycastExtension?>(null) }
    val filteredInstalled = remember(installedExtensions, hubQuery) {
        val q = hubQuery.trim().lowercase()
        if (q.isEmpty()) installedExtensions
        else installedExtensions.filter { ext ->
            ext.displayTitle.lowercase().contains(q) ||
                ext.authorName.lowercase().contains(q) ||
                (ext.manifest.description?.lowercase()?.contains(q) == true) ||
                ext.manifest.commands.any { cmd ->
                    cmd.title.lowercase().contains(q) || cmd.name.lowercase().contains(q)
                }
        }
    }
    val storeSourceLabel = when (storeSource) {
        "vicinae" -> "Vicinae"
        "komorebi" -> "Komorebi"
        else -> "Raycast"
    }

    // Top pinned controls adapting from the category carousel into Extensions / Store tabs
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .zIndex(20f)
            .padding(top = statusBarHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        onBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to search",
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    val isInstalledSelected = hubTab == 0
                    Surface(
                        shape = CircleShape,
                        color = if (isInstalledSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isInstalledSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable {
                                HapticUtil.performVirtualKeyHaptic(view)
                                onTabChange(0)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Installed (${installedExtensions.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isInstalledSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    val isStoreSelected = hubTab == 1
                    Surface(
                        shape = CircleShape,
                        color = if (isStoreSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isStoreSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable {
                                HapticUtil.performVirtualKeyHaptic(view)
                                onTabChange(1)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Store",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isStoreSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (hubTab == 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = storeSource == "raycast",
                    onClick = { onStoreSourceChange("raycast") },
                    label = { Text("Raycast Store") },
                )
                FilterChip(
                    selected = storeSource == "vicinae",
                    onClick = { onStoreSourceChange("vicinae") },
                    label = { Text("Vicinae Store") },
                )
                FilterChip(
                    selected = storeSource == "komorebi",
                    onClick = { onStoreSourceChange("komorebi") },
                    label = { Text("Komorebi") },
                )
            }
        }
    }

    // Middle scrollable content with the same progressive blur
    val topContentPadding = statusBarHeight + if (hubTab == 1) 114.dp else 66.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .progressiveBlur(
                blurRadius = 40f,
                height = topBlurHeightPx,
                direction = BlurDirection.TOP,
            )
            .progressiveBlur(
                blurRadius = 40f,
                height = bottomBlurHeightPx,
                direction = BlurDirection.BOTTOM,
            ),
    ) {
        if (hubTab == 0) {
            if (filteredInstalled.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding, bottom = listBottomPadding, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (installedExtensions.isEmpty()) "No extensions installed" else "No matching extensions",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        FilledTonalButton(onClick = { onTabChange(1) }) {
                            Text("Browse Extension Store")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(overscrollNestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topContentPadding,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    reverseLayout = reachabilityLayout,
                ) {
                    items(filteredInstalled, key = { "${it.id}_$prefsVersion" }) { ext ->
                        val report = remember(ext.id, ext.manifest) {
                            RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
                        }
                        val missingRequiredPrefs = remember(ext.id, prefsVersion) {
                            RaycastExtensionRepository.hasRequiredPreferencesMissing(context, ext, null) ||
                                ext.manifest.commands.any { cmd ->
                                    RaycastExtensionRepository.hasRequiredPreferencesMissing(context, ext, cmd)
                                }
                        }
                        InstalledExtensionCard(
                            extension = ext,
                            compatReport = report,
                            missingRequiredPrefs = missingRequiredPrefs,
                            onInspectCompatibility = { inspectingCompat = ext.displayTitle to report },
                            onOpenPreferences = { preferencesTarget = ext },
                            onRunCommand = { cmdName ->
                                val cmd = ext.manifest.commands.firstOrNull { it.name == cmdName }
                                    ?: return@InstalledExtensionCard
                                onRunCommand(ext, cmd)
                            },
                            onUninstall = { onUninstallExtension(ext) },
                        )
                    }
                }
            }
        } else {
            when {
                storeLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding, bottom = listBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                storeError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding, bottom = listBottomPadding, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = storeError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            FilledTonalButton(onClick = onSearchStore) {
                                Text("Retry")
                            }
                        }
                    }
                }
                storeListings.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding, bottom = listBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No extensions found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(overscrollNestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = topContentPadding,
                            bottom = listBottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        reverseLayout = reachabilityLayout,
                    ) {
                        itemsIndexed(
                            storeListings,
                            key = { index, item -> "${item.source}_${item.id}_${item.name}_$index" },
                        ) { _, item ->
                            val report = remember(item.source, item.id, item.name) {
                                RaycastCompatibilityAnalyzer.analyzeStoreListing(item)
                            }
                            StoreListingCard(
                                listing = item,
                                compatReport = report,
                                isInstalling = installingId == item.id,
                                onInspectCompatibility = {
                                    inspectingCompat = (item.title.ifBlank { item.name }) to report
                                },
                                onInstall = { onInstallStoreItem(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom floating search bar morphed for filtering Extensions / Store
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(20f)
            .padding(horizontal = 16.dp)
            .padding(bottom = searchBarBottomPadding)
            .onSizeChanged { hubBarHeightPx = it.height },
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Icon(
                            imageVector = if (hubTab == 1) Icons.Rounded.Storefront else Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (hubTab == 1) "Store" else "Extensions",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Back to search",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                val bodyLargeStyle = MaterialTheme.typography.bodyLarge
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)

                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    if (hubQuery.isEmpty()) {
                        Text(
                            text = if (hubTab == 1) "Search $storeSourceLabel extensions..." else "Filter installed extensions...",
                            style = bodyLargeStyle,
                            color = hintColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = hubQuery,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = bodyLargeStyle.copy(color = onSurfaceColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                if (hubTab == 1) {
                                    onSearchStore()
                                } else {
                                    val first = filteredInstalled.firstOrNull()
                                    val cmd = first?.manifest?.commands?.firstOrNull()
                                    if (first != null && cmd != null) {
                                        onRunCommand(first, cmd)
                                    }
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (hubTab == 1) {
                    FilledTonalButton(
                        onClick = {
                            keyboardController?.hide()
                            onSearchStore()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("Go", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (hubQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            onQueryChange("")
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    inspectingCompat?.let { (title, report) ->
        CompatibilityReportSheet(
            title = title,
            report = report,
            onDismiss = { inspectingCompat = null },
        )
    }

    preferencesTarget?.let { ext ->
        ExtensionPreferencesBottomSheet(
            extension = ext,
            command = null,
            onDismissRequest = {
                preferencesTarget = null
                prefsVersion++
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.InlineExtensionRunnerOverlay(
    extension: InstalledRaycastExtension,
    command: RaycastCommandManifest,
    statusBarHeight: Dp,
    navBarHeight: Dp,
    topBlurHeightPx: Float,
    bottomBlurHeightPx: Float,
    overscrollNestedScrollConnection: NestedScrollConnection,
    onBack: () -> Unit,
    onCloseToSearch: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val runtime = remember(extension.id, command.name) {
        RaycastRuntimeEngine(context.applicationContext, extension, command)
    }
    LaunchedEffect(runtime) {
        runtime.start(isDarkTheme = isDark)
    }
    DisposableEffect(runtime) {
        onDispose { runtime.close() }
    }

    val viewStack by runtime.viewStack.collectAsState()
    val errorState by runtime.errorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAlert by remember { mutableStateOf<RaycastAlertRequest?>(null) }
    var showPreferences by remember { mutableStateOf(false) }
    var inspectingReport by remember { mutableStateOf<CompatibilityReport?>(null) }
    val hasPreferences = remember(extension, command) {
        !extension.manifest.preferences.isNullOrEmpty() || !command.preferences.isNullOrEmpty()
    }

    LaunchedEffect(runtime) {
        runtime.toastEvents.collect { evt ->
            if (!evt.hidden) {
                val text = listOf(evt.title, evt.message).filter { it.isNotBlank() }.joinToString(" — ")
                if (text.isNotBlank()) snackbarHostState.showSnackbar(text)
            }
        }
    }
    LaunchedEffect(runtime) {
        runtime.hudEvents.collect { title ->
            if (title.isNotBlank()) Toast.makeText(context, title, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(runtime) {
        runtime.alertRequests.collect { req -> pendingAlert = req }
    }
    LaunchedEffect(runtime) {
        runtime.closeRequests.collect { onBack() }
    }
    LaunchedEffect(runtime) {
        runtime.openPreferencesRequests.collect { showPreferences = true }
    }

    val handleBack: () -> Unit = {
        if (viewStack.size > 1) {
            runtime.notifyViewPopped()
        } else {
            onBack()
        }
    }
    BackHandler(onBack = handleBack)

    val activeScreen = viewStack.lastOrNull()
    if (activeScreen != null && errorState == null) {
        InlineActiveExtensionRunner(
            screen = activeScreen,
            extension = extension,
            command = command,
            statusBarHeight = statusBarHeight,
            navBarHeight = navBarHeight,
            topBlurHeightPx = topBlurHeightPx,
            bottomBlurHeightPx = bottomBlurHeightPx,
            overscrollNestedScrollConnection = overscrollNestedScrollConnection,
            onBack = handleBack,
            onCloseToSearch = onCloseToSearch,
            onOpenPreferences = { showPreferences = true },
            onInvokeCallback = { id, args -> runtime.invokeCallback(id, *args) },
        )
    } else {
        // Loading or error state rendered within the exact same floating overlay frame
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(20f)
                .padding(top = statusBarHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                IconButton(onClick = handleBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        text = command.title.ifBlank { command.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = extension.displayTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCloseToSearch, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(top = statusBarHeight + 70.dp, bottom = 128.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (errorState != null) {
                val rawError = errorState ?: ""
                val errorReport = remember(rawError, extension.id) {
                    RaycastCompatibilityAnalyzer.analyzeError(rawError, extension)
                }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Extension Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = errorReport.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = rawError,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (errorReport.issues.isNotEmpty()) {
                            Text(
                                text = "Compatibility Diagnosis",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            errorReport.issues.forEach { issue ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = issue.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (issue.severity == AndroidCompatLevel.INCOMPATIBLE) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        )
                                        Text(
                                            text = issue.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilledTonalButton(onClick = { inspectingReport = errorReport }) {
                                Text("Full Diagnostics")
                            }
                            if (hasPreferences) {
                                FilledTonalButton(onClick = { showPreferences = true }) {
                                    Text("Preferences")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Extension Error", rawError))
                                    Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Text("Copy Error")
                            }
                            OutlinedButton(onClick = handleBack) {
                                Text("Back")
                            }
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Starting ${command.title.ifBlank { command.name }}…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(20f)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = navBarHeight.coerceAtLeast(16.dp)),
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = if (errorState != null) "Extension stopped" else "Loading ${command.title.ifBlank { command.name }}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onCloseToSearch) {
                        Text("Close")
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = navBarHeight.coerceAtLeast(16.dp) + 84.dp)
            .zIndex(30f),
    )

    pendingAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = {
                alert.deferred.complete(false)
                pendingAlert = null
            },
            title = { Text(alert.title) },
            text = if (alert.message.isNotBlank()) {
                { Text(alert.message) }
            } else null,
            confirmButton = {
                TextButton(
                    onClick = {
                        alert.deferred.complete(true)
                        pendingAlert = null
                    },
                ) {
                    Text(
                        text = alert.primaryTitle,
                        color = if (alert.primaryStyle == "Destructive") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        alert.deferred.complete(false)
                        pendingAlert = null
                    },
                ) {
                    Text(alert.dismissTitle)
                }
            },
        )
    }

    if (showPreferences) {
        ExtensionPreferencesBottomSheet(
            extension = extension,
            command = command,
            onDismissRequest = { showPreferences = false },
        )
    }

    inspectingReport?.let { report ->
        CompatibilityReportSheet(
            title = extension.displayTitle,
            report = report,
            onDismiss = { inspectingReport = null },
        )
    }
}

@Composable
private fun BoxScope.InlineActiveExtensionRunner(
    screen: RaycastViewScreen,
    extension: InstalledRaycastExtension,
    command: RaycastCommandManifest,
    statusBarHeight: Dp,
    navBarHeight: Dp,
    topBlurHeightPx: Float,
    bottomBlurHeightPx: Float,
    overscrollNestedScrollConnection: NestedScrollConnection,
    onBack: () -> Unit,
    onCloseToSearch: () -> Unit,
    onOpenPreferences: () -> Unit,
    onInvokeCallback: (String, Array<Any?>) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val searchBarBottomPadding = with(density) {
        if (imeBottomPx > navBottomPx) imeBottomPx.toDp() + 8.dp
        else navBottomPx.toDp().coerceAtLeast(16.dp)
    }
    var runnerBarHeightPx by remember { mutableIntStateOf(0) }
    val runnerBarHeightDp = with(density) { runnerBarHeightPx.toDp() }.coerceAtLeast(72.dp)
    var selectedItemId by remember(screen) { mutableStateOf<String?>(null) }
    var activeActionPanel by remember { mutableStateOf<RaycastActionPanelSpec?>(null) }
    val formState = rememberRaycastFormState(screen)
    val primaryPanel = rememberRaycastPrimaryPanel(screen, selectedItemId)

    val hasSearchField = screen is RaycastViewScreen.ListScreen || screen is RaycastViewScreen.GridScreen
    val initialSearchText = when (screen) {
        is RaycastViewScreen.ListScreen -> screen.searchText ?: ""
        is RaycastViewScreen.GridScreen -> screen.searchText ?: ""
        else -> ""
    }
    var runnerQuery by remember(extension.id, command.name, screen::class, screen.navigationTitle) {
        mutableStateOf(initialSearchText)
    }
    val dropdown: RaycastDropdownSpec? = when (screen) {
        is RaycastViewScreen.ListScreen -> screen.dropdown
        is RaycastViewScreen.GridScreen -> screen.dropdown
        else -> null
    }
    val hasDropdown = dropdown != null && dropdown.options.isNotEmpty()
    val hasPreferences = !extension.manifest.preferences.isNullOrEmpty() || !command.preferences.isNullOrEmpty()

    // Pinned top header adapting to the running extension screen
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .zIndex(20f)
            .padding(top = statusBarHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        onBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = screen.navigationTitle?.takeIf { it.isNotBlank() }
                            ?: command.title.ifBlank { command.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = extension.displayTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasPreferences) {
                    IconButton(
                        onClick = onOpenPreferences,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Extension Preferences",
                        )
                    }
                }
                IconButton(
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        onCloseToSearch()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close to search",
                    )
                }
            }
        }
        if (screen.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(CircleShape),
            )
        }
    }

    // Middle extension content rendered under progressive blur
    Box(
        modifier = Modifier
            .fillMaxSize()
            .progressiveBlur(
                blurRadius = 40f,
                height = topBlurHeightPx,
                direction = BlurDirection.TOP,
            )
            .progressiveBlur(
                blurRadius = 40f,
                height = bottomBlurHeightPx,
                direction = BlurDirection.BOTTOM,
            )
            .nestedScroll(overscrollNestedScrollConnection),
    ) {
        RaycastScreenBody(
            screen = screen,
            extension = extension,
            selectedItemId = selectedItemId,
            formState = formState,
            onSelectItem = { selectedItemId = it },
            onOpenActionPanel = { activeActionPanel = it },
            onInvokeCallback = onInvokeCallback,
            externalSearchQuery = if (hasSearchField) runnerQuery else null,
            hideSearchBar = true,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarHeight + 70.dp,
                bottom = searchBarBottomPadding + runnerBarHeightDp + (if (hasDropdown) 24.dp else 12.dp),
            ),
        )
    }

    // Bottom floating card morphs into extension search/dropdown or Form/Detail action bar
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(20f)
            .padding(horizontal = 16.dp)
            .padding(bottom = searchBarBottomPadding)
            .onSizeChanged { runnerBarHeightPx = it.height },
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (hasDropdown && dropdown != null) {
                    var selectedDropdown by remember(dropdown.value, dropdown.defaultValue) {
                        mutableStateOf(
                            dropdown.value ?: dropdown.defaultValue ?: dropdown.options.first().value,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dropdown.options.forEach { opt ->
                            FilterChip(
                                selected = selectedDropdown == opt.value,
                                onClick = {
                                    selectedDropdown = opt.value
                                    dropdown.onChangeId?.let { cbId ->
                                        onInvokeCallback(cbId, arrayOf(opt.value))
                                    }
                                },
                                label = { Text(opt.title, maxLines = 1) },
                            )
                        }
                    }
                }

                val primary = primaryPanel?.primaryAction
                if (hasSearchField) {
                    val searchChangeId = when (screen) {
                        is RaycastViewScreen.ListScreen -> screen.onSearchTextChangeId
                        is RaycastViewScreen.GridScreen -> screen.onSearchTextChangeId
                        else -> null
                    }
                    val placeholder = when (screen) {
                        is RaycastViewScreen.ListScreen -> screen.searchBarPlaceholder ?: "Search ${command.title.ifBlank { command.name }}..."
                        is RaycastViewScreen.GridScreen -> screen.searchBarPlaceholder ?: "Search ${command.title.ifBlank { command.name }}..."
                        else -> "Search..."
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        val bodyLargeStyle = MaterialTheme.typography.bodyLarge
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            if (runnerQuery.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = bodyLargeStyle,
                                    color = hintColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = runnerQuery,
                                onValueChange = { next ->
                                    runnerQuery = next
                                    searchChangeId?.let { cbId -> onInvokeCallback(cbId, arrayOf(next)) }
                                },
                                singleLine = true,
                                textStyle = bodyLargeStyle.copy(color = onSurfaceColor),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (runnerQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    runnerQuery = ""
                                    searchChangeId?.let { cbId -> onInvokeCallback(cbId, arrayOf("")) }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (primary != null) {
                            FilledTonalButton(
                                onClick = { executeRaycastAction(primary, formState, onInvokeCallback) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .height(36.dp),
                            ) {
                                Text(
                                    text = primary.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        primaryPanel?.let { panel ->
                            IconButton(
                                onClick = { activeActionPanel = panel },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = "Actions",
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (primary != null) {
                            Button(
                                onClick = { executeRaycastAction(primary, formState, onInvokeCallback) },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                RaycastIconView(
                                    spec = primary.icon,
                                    extension = extension,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(primary.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        primaryPanel?.let { panel ->
                            FilledTonalButton(
                                onClick = { activeActionPanel = panel },
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Actions")
                            }
                        }
                    }
                }
            }
        }
    }

    activeActionPanel?.let { panel ->
        RaycastActionPanelSheet(
            panel = panel,
            extension = extension,
            onDismissRequest = { activeActionPanel = null },
            onExecuteAction = { action ->
                activeActionPanel = null
                executeRaycastAction(action, formState, onInvokeCallback)
            },
        )
    }
}

private fun launchFile(context: Context, item: PixelSearchResultItem.FileItem) {
    SearchUsageTracker.record(context, SearchUsageTracker.fileKey(item.uri.toString()))
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, item.displayName, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun RaycastCommandRow(
    title: String,
    subtitle: String,
    badge: String,
    iconRes: Int,
    containerColor: Color,
    onClick: () -> Unit,
    iconBitmap: ImageBitmap? = null,
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (iconBitmap != null) {
                Image(
                    painter = remember(iconBitmap) { BitmapPainter(iconBitmap) },
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

private fun applyGrayscaleFilter(bitmap: ImageBitmap): ImageBitmap {
    val src = bitmap.asAndroidBitmap()
    val output = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()
    val matrix = android.graphics.ColorMatrix().apply {
        setSaturation(0.2f)
    }
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
    paint.alpha = (0.65f * 255).toInt()
    canvas.drawBitmap(src, 0f, 0f, paint)
    return output.asImageBitmap()
}


private fun launchApp(context: Context, packageName: String) {
    SearchUsageTracker.record(context, SearchUsageTracker.appKey(packageName))
    val launched = com.sameerasw.essentials.utils.AppLaunchUtil.launchApp(context, packageName)
    if (!launched) {
        runCatching {
            Toast.makeText(context.applicationContext, "Unable to open app", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun isLikelyUrl(query: String): Boolean {
    val lower = query.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return true
    if (query.contains(" ") || !query.contains(".")) return false
    val commonTlds = listOf(".com", ".org", ".net", ".io", ".dev", ".app", ".co", ".ai", ".me", ".info", ".edu", ".gov", ".lk", ".uk", ".ca", ".de", ".jp", ".fr", ".au", ".in")
    return commonTlds.any { lower.contains(it) } || android.util.Patterns.WEB_URL.matcher(query).matches()
}

private fun buildSearchOrUrl(query: String, searchEngine: String): Uri {
    val trimmed = query.trim()
    if (isLikelyUrl(trimmed)) {
        val target = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return Uri.parse(target)
    }

    val encoded = Uri.encode(trimmed)
    val url = when (searchEngine.lowercase()) {
        "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
        "brave search", "brave" -> "https://search.brave.com/search?q=$encoded"
        "startpage" -> "https://www.startpage.com/sp/search?query=$encoded"
        "kagi" -> "https://kagi.com/search?q=$encoded"
        "ecosia" -> "https://www.ecosia.org/search?q=$encoded"
        "bing" -> "https://www.bing.com/search?q=$encoded"
        else -> "https://www.google.com/search?q=$encoded"
    }
    return Uri.parse(url)
}

@Composable
private fun PixelSearchInputField(
    queryState: androidx.compose.runtime.MutableState<String>,
    activeScope: SearchProvider?,
    prefixSuggestion: PrefixSuggestion?,
    isVoiceListening: Boolean,
    focusRequester: FocusRequester,
    bodyLargeStyle: androidx.compose.ui.text.TextStyle,
    onSurfaceColor: Color,
    hintColor: Color,
    modifier: Modifier = Modifier,
    onQueryEdited: () -> Unit,
    onExecuteTopResult: () -> Unit,
    onEnterScope: (SearchProvider) -> Unit,
    onExitScope: () -> Unit,
) {
    val query = queryState.value
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
    ) {
        if (query.isEmpty()) {
            if (isVoiceListening) {
                Text(
                    text = "Listening…",
                    style = bodyLargeStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else if (activeScope != null) {
                Text(
                    text = "Search ${activeScope.title.lowercase()}...",
                    style = bodyLargeStyle,
                    color = hintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                TranslatableText(
                    stringResId = R.string.pixel_search_input_hint,
                    style = bodyLargeStyle,
                    color = hintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (activeScope == null && prefixSuggestion != null) {
            val ghostAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.Transparent)) {
                    append(query)
                }
                if (prefixSuggestion.ghostSuffix.isNotEmpty()) {
                    withStyle(SpanStyle(color = hintColor)) {
                        append(prefixSuggestion.ghostSuffix)
                    }
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))) {
                    append("  • Tab")
                }
            }
            Text(
                text = ghostAnnotated,
                style = bodyLargeStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }

        BasicTextField(
            value = query,
            onValueChange = {
                onQueryEdited()
                queryState.value = it
            },
            singleLine = true,
            textStyle = bodyLargeStyle.copy(color = onSurfaceColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onExecuteTopResult() },
                onDone = { onExecuteTopResult() },
                onGo = { onExecuteTopResult() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when {
                            keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter -> {
                                onExecuteTopResult()
                                true
                            }
                            (keyEvent.key == Key.Tab || keyEvent.key == Key.Spacebar) &&
                                activeScope == null &&
                                prefixSuggestion != null -> {
                                onEnterScope(prefixSuggestion.provider)
                                true
                            }
                            keyEvent.key == Key.Backspace && activeScope != null && queryState.value.isEmpty() -> {
                                onExitScope()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
        )
    }
}

@Composable
private fun PixelSearchTrailingAction(
    isSearching: Boolean,
    queryState: androidx.compose.runtime.MutableState<String>,
    hasActiveScope: Boolean,
    onClearQuery: () -> Unit,
    onExitScope: () -> Unit,
) {
    val hasQuery = queryState.value.isNotEmpty()
    when {
        isSearching -> Box(modifier = Modifier.padding(end = 6.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        hasQuery -> IconButton(
            onClick = onClearQuery,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_close_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hasActiveScope -> IconButton(
            onClick = onExitScope,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_close_24),
                contentDescription = "Exit scope",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun launchWebSearch(
    context: Context,
    query: String,
    useBubbles: Boolean = false,
    searchEngine: String = "Google",
) {
    val targetUri = buildSearchOrUrl(query, searchEngine)
    if (useBubbles) {
        val launched = runCatching { WindowingUtils.launchOverlayWindow(context, targetUri) }.getOrDefault(false)
        if (launched) return
    }

    val browserIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (com.sameerasw.essentials.utils.AppLaunchUtil.startActivityReliably(context, browserIntent)) {
        return
    }

    val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    com.sameerasw.essentials.utils.AppLaunchUtil.startActivityReliably(context, fallbackIntent)
}

internal fun <T> List<T>.inReachabilityOrder(reachabilityLayout: Boolean): List<T> =
    if (reachabilityLayout) asReversed() else this

private fun assistantRegistryActions(
    query: String,
    context: Context,
    scope: CoroutineScope,
    onRequestConfirmation: (title: String, description: String, proceed: () -> Unit) -> Unit,
    onResult: (Boolean, String) -> Unit,
): List<BuiltInLauncherAction> {
    if (query.isBlank()) return emptyList()
    val skip = setOf(
        "timer",
        "alarm",
        "open_timers",
        "open_alarms",
        "add_time",
        "create_event",
        "play_media",
        "web_search",
    )
    return AssistantToolRegistry.all()
        .mapNotNull { tool ->
            if (tool.id in skip) {
                null
            } else {
                val score = assistantToolMatchScore(tool, query)
                if (score > 0) tool to score else null
            }
        }
        .sortedByDescending { it.second }
        .take(6)
        .map { (tool, _) ->
            val args = assistantToolArgs(tool.id, query)
            val runTool: (Context) -> Unit = { ctx ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        AssistantToolRegistry.execute(ctx, tool.id, args)
                    }
                    val text = buildString {
                        append(result.message)
                        result.data?.takeIf { it.isNotBlank() }?.let {
                            append("\n")
                            append(it.take(4000))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onResult(result.success, text)
                    }
                }
            }
            BuiltInLauncherAction(
                id = "tool_${tool.id}",
                title = tool.title,
                subtitle = tool.description,
                badge = "Assistant",
                iconRes = assistantToolIcon(tool.id),
                keywords = tool.keywords,
                closesSearch = false,
                onExecute = { ctx ->
                    if (tool.requiresConfirmation) {
                        onRequestConfirmation(tool.title, tool.description) {
                            runTool(ctx)
                        }
                    } else {
                        runTool(ctx)
                    }
                },
            )
        }
        .toList()
}

private fun assistantMemoryActions(
    query: String,
    context: Context,
    scope: CoroutineScope,
    onResult: (Boolean, String) -> Unit,
): List<BuiltInLauncherAction> {
    val trimmed = query.trim()
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("remember ") -> {
            val value = trimmed.substringAfter(' ').trim().removePrefix("that ").trim()
            if (value.isBlank()) {
                emptyList()
            } else {
                listOf(
                    BuiltInLauncherAction(
                        id = "assistant_memory_save_${value.hashCode()}",
                        title = "Remember this",
                        subtitle = value.take(80),
                        badge = "Memory",
                        iconRes = R.drawable.rounded_robot_24,
                        keywords = emptyList(),
                        opensModal = null,
                        closesSearch = false,
                        onExecute = {
                            scope.launch {
                                AssistantMemoryRepository.remember(context, value.take(80), value)
                                onResult(true, "Saved to memory: $value")
                            }
                        },
                    ),
                )
            }
        }
        lower.startsWith("forget ") -> {
            val key = trimmed.substringAfter(' ').trim()
            if (key.isBlank()) {
                emptyList()
            } else {
                listOf(
                    BuiltInLauncherAction(
                        id = "assistant_memory_forget_${key.hashCode()}",
                        title = "Forget \"$key\"",
                        subtitle = "Remove this memory",
                        badge = "Memory",
                        iconRes = R.drawable.rounded_robot_24,
                        keywords = emptyList(),
                        opensModal = null,
                        closesSearch = false,
                        onExecute = {
                            scope.launch {
                                val removed = AssistantMemoryRepository.forget(context, key)
                                onResult(removed, if (removed) "Forgotten: $key" else "No saved memory for \"$key\"")
                            }
                        },
                    ),
                )
            }
        }
        lower.startsWith("recall ") || lower.startsWith("what do you remember") -> {
            val topic = trimmed.substringAfter(' ', "").removePrefix("about ").trim()
            listOf(
                BuiltInLauncherAction(
                    id = "assistant_memory_recall_${topic.hashCode()}",
                    title = "Recall memories",
                    subtitle = if (topic.isBlank()) "Show saved memories" else "About $topic",
                    badge = "Memory",
                    iconRes = R.drawable.rounded_robot_24,
                    keywords = emptyList(),
                    opensModal = null,
                    closesSearch = false,
                    onExecute = {
                        scope.launch {
                            val entries = AssistantMemoryRepository.recall(context, topic, 5)
                            val message = if (entries.isEmpty()) {
                                "No saved memories"
                            } else {
                                entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            }
                            onResult(true, message)
                        }
                    },
                ),
            )
        }
        else -> emptyList()
    }
}

private fun assistantToolArgs(id: String, query: String): Map<String, String> {
    val q = query.trim()
    val state = when {
        Regex("\\b(on|enable|start|activate)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "on"
        Regex("\\b(off|disable|stop|deactivate)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "off"
        else -> "toggle"
    }
    val number = Regex("(\\d{1,3})").find(q)?.value
    return when (id) {
        "wifi", "bluetooth", "mobile_data", "airplane_mode", "hotspot", "flashlight",
        "auto_rotate", "battery_saver", "dark_mode", "dnd", "caffeinate",
        -> mapOf("state" to state)
        "volume" -> mapOf(
            "stream" to when {
                q.contains("ring", true) -> "ring"
                q.contains("alarm", true) -> "alarm"
                q.contains("notif", true) -> "notification"
                else -> "media"
            },
            "action" to when {
                number != null -> "set"
                q.contains("mute", true) -> "mute"
                q.contains("unmute", true) -> "unmute"
                q.contains("down", true) -> "down"
                else -> "up"
            },
            "value" to (number ?: ""),
        )
        "brightness" -> mapOf(
            "action" to when {
                number != null -> "set"
                q.contains("down", true) -> "down"
                else -> "up"
            },
            "value" to (number ?: ""),
        )
        "open_settings" -> mapOf(
            "page" to when {
                q.contains("wifi", true) -> "wifi"
                q.contains("bluetooth", true) -> "bluetooth"
                q.contains("display", true) || q.contains("bright", true) -> "display"
                q.contains("sound", true) || q.contains("volume", true) -> "sound"
                q.contains("battery", true) -> "battery"
                q.contains("location", true) -> "location"
                q.contains("accessib", true) -> "accessibility"
                q.contains("notif", true) -> "notification"
                q.contains("nfc", true) -> "nfc"
                else -> "apps"
            },
        )
        "freeze_app", "unfreeze_app", "app_info" -> mapOf("package" to q.substringAfterLast(' ').trim())
        "screen_timeout" -> mapOf("seconds" to (Regex("(\\d{1,4})").find(q)?.value ?: ""))
        "add_task" -> assistantTaskEventArgs(q)
        "complete_task" -> mapOf(
            "title" to q.replace(Regex("^(complete|finish|done|check off)\\s+(task\\s+)?", RegexOption.IGNORE_CASE), "").trim(),
        )
        "delete_task" -> mapOf(
            "title" to q.replace(Regex("^(delete|remove)\\s+(task|todo|reminder)\\s+", RegexOption.IGNORE_CASE), "").trim(),
        )
        "create_event" -> assistantCalendarEventArgs(q)
        "delete_event" -> mapOf(
            "title" to q.replace(Regex("^(delete|cancel|remove)\\s+(event|meeting|appointment)\\s+", RegexOption.IGNORE_CASE), "").trim(),
        )
        "find_free_slots" -> mapOf(
            "duration_minutes" to (Regex("(\\d{1,3})\\s*(min|minute|minutes|m)\\b", RegexOption.IGNORE_CASE).find(q)?.groupValues?.get(1) ?: "30"),
        )
        "reply_last" -> mapOf(
            "message" to q.replace(Regex("^(reply|respond)(\\s+last|\\s+notification)?\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim(),
        )
        "fetch_url" -> mapOf("url" to (Regex("https?://\\S+").find(q)?.value ?: ""))
        "run_routine" -> mapOf(
            "name" to q.replace(Regex("^(run|start|trigger)\\s+(routine\\s+)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^routine\\s+", RegexOption.IGNORE_CASE), "")
                .trim(),
        )
        else -> emptyMap()
    }
}

private fun voiceErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "Speech recognition needs a network connection for this language."
    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again."
    SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
    else -> "Voice search failed (code $error)."
}

private data class AssistantFeedback(val success: Boolean, val message: String)

private data class PendingToolConfirmation(
    val title: String,
    val description: String,
    val onConfirm: () -> Unit,
)

private fun assistantToolMatchScore(
    tool: com.sameerasw.essentials.assistant.tools.AssistantTool,
    query: String,
): Int {
    fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
    val q = normalize(query)
    if (q.length < 3) return 0
    val fields = (listOf(tool.title) + tool.keywords).map(::normalize).filter { it.isNotBlank() }
    var best = 0
    for (field in fields) {
        val score = when {
            field == q -> 100
            q.startsWith(field) -> 90
            field.startsWith(q) -> 75
            q.contains(field) -> 65
            field.contains(q) -> 45
            else -> 0
        }
        if (score > best) best = score
    }
    return best
}

private fun assistantTaskEventArgs(query: String): Map<String, String> {
    var rest = query.trim()
    val prefixes = listOf(
        Regex("^(?:add|new|create)\\s+(?:a\\s+)?task\\s+", RegexOption.IGNORE_CASE),
        Regex("^(?:add|new|create)\\s+(?:a\\s+)?todo\\s+", RegexOption.IGNORE_CASE),
        Regex("^remind\\s+me\\s+to\\s+", RegexOption.IGNORE_CASE),
        Regex("^remind\\s+me\\s+", RegexOption.IGNORE_CASE),
        Regex("^task\\s+", RegexOption.IGNORE_CASE),
        Regex("^todo\\s+", RegexOption.IGNORE_CASE),
    )
    for (prefix in prefixes) {
        val match = prefix.find(rest) ?: continue
        rest = rest.removeRange(match.range).trim()
        break
    }
    if (rest.isBlank()) return emptyMap()
    val relative = Regex("\\s+(today|tomorrow)(?:\\s+(?:at\\s+)?(.+))?$", RegexOption.IGNORE_CASE)
        .find(rest)
    if (relative != null) {
        val title = rest.substring(0, relative.range.first).trim()
        val due = relative.value.trim()
        return buildMap {
            put("title", title.ifBlank { rest })
            put("due", due)
        }
    }
    val explicit = Regex("\\s+(?:due|by|before|at)\\s+(.+)$", RegexOption.IGNORE_CASE).find(rest)
    if (explicit != null) {
        return mapOf(
            "title" to rest.substring(0, explicit.range.first).trim(),
            "due" to explicit.groupValues[1].trim(),
        )
    }
    return mapOf("title" to rest)
}

private fun assistantCalendarEventArgs(query: String): Map<String, String> {
    val rest = query.replaceFirst(
        Regex(
            "^(?:create|add|new|schedule)\\s+(?:a\\s+)?(?:calendar\\s+)?event\\s+",
            RegexOption.IGNORE_CASE,
        ),
        "",
    ).trim()
    if (rest.isEmpty()) return emptyMap()
    val withTime = Regex(
        "^(?<title>.+?)\\s+(?:at\\s+)?(?<start>(?:tomorrow|today)\\s+(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{1,2}(?::\\d{2})?\\s*(?:am|pm))$",
        RegexOption.IGNORE_CASE,
    ).matchEntire(rest)
    if (withTime != null) {
        return mapOf(
            "title" to withTime.groups["title"]!!.value.trim(),
            "begin" to withTime.groups["start"]!!.value.trim(),
        )
    }
    val atSplit = rest.split(Regex("\\s+at\\s+", RegexOption.IGNORE_CASE), limit = 2)
    if (atSplit.size == 2) {
        return mapOf("title" to atSplit[0].trim(), "begin" to atSplit[1].trim())
    }
    return mapOf("title" to rest)
}

private fun assistantToolIcon(id: String): Int = when {
    id.startsWith("wifi") -> R.drawable.rounded_wifi_24
    id.contains("bluetooth") -> R.drawable.rounded_bluetooth_24
    id.contains("flashlight") -> R.drawable.rounded_flashlight_on_24
    id.contains("dark") -> R.drawable.rounded_dark_mode_24
    id.contains("battery") -> R.drawable.rounded_battery_android_frame_plus_24
    id.contains("airplane") -> R.drawable.rounded_flight_24
    id.contains("volume") -> R.drawable.rounded_volume_up_24
    id.contains("brightness") -> R.drawable.rounded_brightness_6_24
    id.contains("lock") -> R.drawable.rounded_lock_24
    id.contains("screenshot") -> R.drawable.rounded_photo_camera_24
    id.contains("split") -> R.drawable.rounded_splitscreen_24
    id.contains("settings") -> R.drawable.rounded_settings_24
    id.contains("media") -> R.drawable.rounded_play_arrow_24
    id.contains("task") -> R.drawable.rounded_checklist_24
    id.contains("calendar") || id.contains("event") -> R.drawable.rounded_calendar_month_24
    id.contains("routine") -> R.drawable.rounded_auto_awesome_24
    id.contains("chat") || id.contains("reply") -> R.drawable.rounded_chat_bubble_24
    id.contains("web") || id.contains("fetch") -> R.drawable.rounded_search_24
    else -> R.drawable.rounded_robot_24
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipboardSmartActionsCard(
    content: ClipboardContent,
    containerColor: Color,
    onOpenUrl: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onSaveLink: (String, String) -> Unit,
    onShowQr: (String) -> Unit,
    onCall: (String) -> Unit,
    onText: (String) -> Unit,
    onCompose: (String) -> Unit,
    onUseColor: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = when (content) {
        is ClipboardContent.Url -> "URL"
        is ClipboardContent.Phone -> "Phone"
        is ClipboardContent.Email -> "Email"
        is ClipboardContent.HexColor -> "Color"
    }
    val preview = when (content) {
        is ClipboardContent.Url -> content.url
        is ClipboardContent.Phone -> content.number
        is ClipboardContent.Email -> content.address
        is ClipboardContent.HexColor -> content.code
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Clipboard — $typeLabel",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = preview.take(70),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (content) {
                    is ClipboardContent.Url -> {
                        TextButton(onClick = { onOpenUrl(content.url) }) { Text("Open") }
                        TextButton(onClick = { onCopy(typeLabel, content.url) }) { Text("Copy") }
                        TextButton(onClick = { onSaveLink(content.url, content.host) }) { Text("Save Link") }
                        TextButton(onClick = { onShowQr(content.url) }) { Text("QR") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    is ClipboardContent.Phone -> {
                        TextButton(onClick = { onCall(content.number) }) { Text("Call") }
                        TextButton(onClick = { onText(content.number) }) { Text("Text") }
                        TextButton(onClick = { onCopy(typeLabel, content.number) }) { Text("Copy") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    is ClipboardContent.Email -> {
                        TextButton(onClick = { onCompose(content.address) }) { Text("Compose") }
                        TextButton(onClick = { onCopy(typeLabel, content.address) }) { Text("Copy") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    is ClipboardContent.HexColor -> {
                        TextButton(onClick = { onUseColor(content.code) }) { Text("Use") }
                        TextButton(onClick = { onCopy(typeLabel, content.code) }) { Text("Copy") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceBright,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Scan to open",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val qrBitmap = remember(text) { generateQrBitmap(text, 512) }
                if (qrBitmap == null) {
                    Text(
                        text = "Couldn't generate QR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR code",
                        modifier = Modifier
                            .width(320.dp)
                            .aspectRatio(1f),
                    )
                }
                Text(
                    text = text.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    TextButton(
                        onClick = {
                            runCatching {
                                ContextCompat.getSystemService(context, ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("QR Code", text))
                            }
                            onDismiss()
                        },
                    ) { Text("Copy") }
                    if (text.startsWith("http")) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(text)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        },
                                    )
                                }
                            },
                        ) { Text("Open") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): ImageBitmap? =
    runCatching {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size)
        var index = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[index++] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.asImageBitmap()
    }.getOrNull()
