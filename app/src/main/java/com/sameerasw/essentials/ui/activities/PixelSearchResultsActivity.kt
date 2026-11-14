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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import com.sameerasw.essentials.domain.model.PixelSearchTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sameerasw.essentials.FeatureSettingsActivity
import com.sameerasw.essentials.R
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.domain.registry.FeatureRegistry
import com.sameerasw.essentials.domain.registry.SearchRegistry
import com.sameerasw.essentials.search.AppFunctionItem
import com.sameerasw.essentials.search.DiscoveredShortcut
import com.sameerasw.essentials.search.EntityRouteItem
import com.sameerasw.essentials.search.InternalCommandItem
import com.sameerasw.essentials.search.PrefixSuggestion
import com.sameerasw.essentials.search.SearchProvider
import com.sameerasw.essentials.search.SearchProviderDiscovery
import com.sameerasw.essentials.search.SearchProviderKind
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.ui.core.cards.FeatureCard
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.modifiers.BlurDirection
import com.sameerasw.essentials.ui.modifiers.progressiveBlur
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.ui.core.sheets.PixelSearchFileActionSheet
import com.sameerasw.essentials.utils.AppUtil
import com.sameerasw.essentials.utils.ColorUtil
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.PermissionUtils
import com.sameerasw.essentials.utils.WindowingUtils
import com.sameerasw.essentials.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PixelSearchResultsActivity : ComponentActivity() {

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

        val initialQuery = intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra("query")
            ?: ""

        setContent {
            val repository = remember { SettingsRepository(this) }
            val isPitchBlack by repository.isPitchBlackThemeEnabled.collectAsState(initial = false)

            EssentialsTheme(pitchBlackTheme = isPitchBlack) {
                PixelSearchResultsScreen(
                    initialQuery = initialQuery,
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PixelSearchResultsScreen(
    initialQuery: String,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    var selectedTab by rememberSaveable { mutableStateOf(PixelSearchTab.ALL) }
    val isAllTab = selectedTab == PixelSearchTab.ALL

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

    val viewModel: MainViewModel = viewModel()
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
    var isSearching by remember { mutableStateOf(false) }

    val prefixSuggestion = remember(query, activeScope, discoveredProviders) {
        if (activeScope == null) {
            SearchProviderDiscovery.matchPrefixSuggestion(query, discoveredProviders)
        } else {
            null
        }
    }

    val isAppsEnabled = remember { repository.isPixelSearchResultAppsEnabled() }
    val isContactsEnabled = remember { repository.isPixelSearchResultContactsEnabled() }
    val isMediaEnabled = remember { repository.isPixelSearchResultMediaEnabled() }
    val isFilesEnabled = remember { repository.isPixelSearchResultFilesEnabled() }
    val isSettingsEnabled = remember { repository.isPixelSearchResultSettingsEnabled() }
    val isShortcutsEnabled = remember { repository.isPixelSearchResultShortcutsEnabled() }
    val isWebEnabled = remember { repository.isPixelSearchResultWebEnabled() }
    val isBubblesWebEnabled = remember { repository.isPixelSearchBubblesWebEnabled() }
    val searchEngine = remember { repository.getPixelSearchEngine() }

    LaunchedEffect(Unit) {
        discoveredProviders = SearchProviderDiscovery.discoverProviders(context)
    }

    BackHandler(enabled = activeScope != null) {
        activeScope = null
        query = ""
    }

    fun enterScope(provider: SearchProvider) {
        HapticUtil.performVirtualKeyHaptic(view)
        activeScope = provider
        query = ""
        focusRequester.requestFocus()
    }

    fun exitScope() {
        HapticUtil.performVirtualKeyHaptic(view)
        activeScope = null
        query = ""
        focusRequester.requestFocus()
    }

    fun performSearch(q: String, currentScope: SearchProvider?) {
        val trimmed = q.trim()
        if (currentScope != null) {
            isSearching = true
            scope.launch(Dispatchers.IO) {
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
                        val installed = AppUtil.getInstalledApps(context, includeSelf = true)
                        val filteredApps = installed
                            .filter { trimmed.isEmpty() || it.appName.contains(trimmed, ignoreCase = true) }
                            .map {
                                PixelSearchResultItem.AppItem(
                                    appName = it.appName,
                                    packageName = it.packageName,
                                    icon = it.icon,
                                    isSystemApp = it.isSystemApp,
                                    isFrozen = false,
                                )
                            }
                            .sortedBy { it.appName.lowercase() }
                            .take(24)
                        withContext(Dispatchers.Main) {
                            appResults = filteredApps
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.CONTACTS -> {
                        val contacts = if (trimmed.isNotEmpty() && ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CONTACTS,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            loadContacts(context, trimmed)
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            contactResults = contacts
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.FILES -> {
                        val docs = if (trimmed.isNotEmpty() &&
                            (PermissionUtils.hasManageExternalStoragePermission(context) || PermissionUtils.hasStoragePermission(context))
                        ) {
                            FileSearchUtil.searchFiles(context, trimmed, limit = 20, searchMedia = false, searchDocs = true).documentItems
                        } else {
                            emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            fileResults = docs
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.SETTINGS -> {
                        val sys = loadSystemSettings(context, trimmed)
                        val mapped = if (trimmed.isNotEmpty()) {
                            SearchRegistry.search(
                                context,
                                trimmed,
                                repository.isEnableUnsupportedFeatures(),
                                repository.isShowLegacyFeatures(),
                            ).filter { it.featureKey != "Quick settings tiles" }
                                .take(12)
                                .map { PixelSearchResultItem.SettingItem(it) }
                        } else {
                            emptyList()
                        }
                        val cmds = SearchProviderDiscovery.matchInternalCommands(
                            context = context,
                            query = trimmed.ifEmpty { "settings" },
                            onOpenExtensions = {
                                context.startActivity(ExtensionsActivity.createIntent(context, openStore = false))
                                onFinish()
                            },
                            onOpenStore = {
                                context.startActivity(ExtensionsActivity.createIntent(context, openStore = true))
                                onFinish()
                            },
                        )
                        withContext(Dispatchers.Main) {
                            systemSettingResults = sys
                            settingResults = mapped
                            internalCommands = cmds
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.APP_INTENT -> {
                        val pkgShortcuts = SearchProviderDiscovery.discoverShortcuts(
                            context = context,
                            query = trimmed,
                            packageFilter = currentScope.packageName,
                        )
                        withContext(Dispatchers.Main) {
                            shortcutResults = pkgShortcuts
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }

                    SearchProviderKind.WEB,
                    SearchProviderKind.EXTENSION,
                    -> {
                        withContext(Dispatchers.Main) {
                            scopedPhotos = emptyList()
                            isSearching = false
                        }
                    }
                }
            }
            return
        }

        if (trimmed.isEmpty()) {
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
            return
        }

        isSearching = true
        scope.launch(Dispatchers.IO) {
            val matchedCommands = SearchProviderDiscovery.matchInternalCommands(
                context = context,
                query = trimmed,
                onOpenExtensions = {
                    context.startActivity(ExtensionsActivity.createIntent(context, openStore = false))
                    onFinish()
                },
                onOpenStore = {
                    context.startActivity(ExtensionsActivity.createIntent(context, openStore = true))
                    onFinish()
                },
            )
            withContext(Dispatchers.Main) {
                internalCommands = matchedCommands
            }

            if (isAppsEnabled) {
                val installed = AppUtil.getInstalledApps(context, includeSelf = true)
                val filteredApps = installed
                    .filter { app ->
                        app.appName.contains(trimmed, ignoreCase = true)
                    }
                    .map {
                        PixelSearchResultItem.AppItem(
                            appName = it.appName,
                            packageName = it.packageName,
                            icon = it.icon,
                            isSystemApp = it.isSystemApp,
                            isFrozen = false,
                        )
                    }
                    .sortedBy { it.appName.lowercase() }
                    .take(6)
                withContext(Dispatchers.Main) {
                    appResults = filteredApps
                }
            }

            if (isContactsEnabled && ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val contacts = loadContacts(context, trimmed)
                withContext(Dispatchers.Main) {
                    contactResults = contacts
                }
            }

            if (isMediaEnabled && PermissionUtils.hasMediaPermissions(context)) {
                val mediaSearchResults = FileSearchUtil.searchFiles(context, trimmed, limit = 6, searchMedia = true, searchDocs = false)
                withContext(Dispatchers.Main) {
                    mediaResults = mediaSearchResults.mediaItems
                }
            } else {
                withContext(Dispatchers.Main) {
                    mediaResults = emptyList()
                }
            }

            if (isFilesEnabled && (PermissionUtils.hasManageExternalStoragePermission(context) || PermissionUtils.hasStoragePermission(context))) {
                val fileSearchResults = FileSearchUtil.searchFiles(context, trimmed, limit = 6, searchMedia = false, searchDocs = true)
                withContext(Dispatchers.Main) {
                    fileResults = fileSearchResults.documentItems
                }
            } else {
                withContext(Dispatchers.Main) {
                    fileResults = emptyList()
                }
            }

            if (isSettingsEnabled) {
                val systemSettings = loadSystemSettings(context, trimmed)
                val results =
                    SearchRegistry.search(
                        context,
                        trimmed,
                        repository.isEnableUnsupportedFeatures(),
                        repository.isShowLegacyFeatures(),
                    )
                val mappedSettings = results
                    .filter { it.featureKey != "Quick settings tiles" }
                    .take(6)
                    .map {
                        PixelSearchResultItem.SettingItem(it)
                    }
                withContext(Dispatchers.Main) {
                    systemSettingResults = systemSettings
                    settingResults = mappedSettings
                }
            }

            val shortcuts = if (isShortcutsEnabled) {
                SearchProviderDiscovery.discoverShortcuts(context, trimmed)
            } else {
                emptyList()
            }
            val routes = SearchProviderDiscovery.resolveEntities(context, trimmed, shortcuts)
            val appFns = SearchProviderDiscovery.discoverAppFunctions(context, trimmed)
            withContext(Dispatchers.Main) {
                shortcutResults = shortcuts
                entityRoutes = routes
                appFunctions = appFns
                isSearching = false
            }
        }
    }

    LaunchedEffect(query, activeScope) {
        userHasScrolled = false
        performSearch(query, activeScope)
    }

    LaunchedEffect(selectedTab) {
        userHasScrolled = false
        listState.scrollToItem(0)
    }

    LaunchedEffect(
        selectedTab,
        activeScope,
        appResults,
        contactResults,
        mediaResults,
        fileResults,
        systemSettingResults,
        settingResults,
        shortcutResults,
        entityRoutes,
        internalCommands,
        scopedPhotos,
    ) {
        if (!userHasScrolled && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarHeightPx = with(LocalDensity.current) { statusBarHeight.toPx() }
    val bottomBlurHeightPx = with(LocalDensity.current) { 140.dp.toPx() }
    val topBlurHeightPx = with(LocalDensity.current) { (statusBarHeight + 56.dp).toPx() }

    // Identify which section is topmost
    val hasCommands = activeScope == null && internalCommands.isNotEmpty()
    val hasRoutes = activeScope == null && !hasCommands && entityRoutes.isNotEmpty()
    val hasApps = (isAllTab || selectedTab == PixelSearchTab.APPS) && isAppsEnabled && appResults.isNotEmpty()
    val hasContacts = !hasCommands && !hasRoutes && !hasApps && (isAllTab || selectedTab == PixelSearchTab.CONTACTS) && isContactsEnabled && contactResults.isNotEmpty()
    val hasMedia = !hasCommands && !hasRoutes && !hasApps && !hasContacts && (isAllTab || selectedTab == PixelSearchTab.MEDIA) && isMediaEnabled && mediaResults.isNotEmpty()
    val hasFiles = !hasCommands && !hasRoutes && !hasApps && !hasContacts && !hasMedia && (isAllTab || selectedTab == PixelSearchTab.FILES) && isFilesEnabled && fileResults.isNotEmpty()
    val hasSystemSettings = !hasCommands && !hasRoutes && !hasApps && !hasContacts && !hasMedia && !hasFiles && (isAllTab || selectedTab == PixelSearchTab.SETTINGS) && isSettingsEnabled && systemSettingResults.isNotEmpty()
    val hasEssentials = !hasCommands && !hasRoutes && !hasApps && !hasContacts && !hasMedia && !hasFiles && !hasSystemSettings && (isAllTab || selectedTab == PixelSearchTab.SETTINGS) && isSettingsEnabled && settingResults.isNotEmpty()
    val hasShortcuts = !hasCommands && !hasRoutes && !hasApps && !hasContacts && !hasMedia && !hasFiles && !hasSystemSettings && !hasEssentials && (isAllTab || selectedTab == PixelSearchTab.SETTINGS) && isShortcutsEnabled && shortcutResults.isNotEmpty()

    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val normalCardColor = MaterialTheme.colorScheme.surfaceBright

    fun launchTopmostOrWeb() {
        val currentScope = activeScope
        if (currentScope != null) {
            when (currentScope.kind) {
                SearchProviderKind.PHOTOS -> {
                    val topPhoto = scopedPhotos.firstOrNull()
                    if (topPhoto != null) {
                        SearchProviderDiscovery.launchPhotoInGallery(context, topPhoto)
                        onFinish()
                    }
                }
                SearchProviderKind.APP_INTENT -> {
                    if (SearchProviderDiscovery.launchScopedProviderSearch(context, currentScope, query)) {
                        onFinish()
                    }
                }
                SearchProviderKind.APPS -> {
                    appResults.firstOrNull()?.let {
                        launchApp(context, it.packageName)
                        onFinish()
                    }
                }
                SearchProviderKind.CONTACTS -> {
                    contactResults.firstOrNull()?.phoneNumber?.let {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                        onFinish()
                    }
                }
                SearchProviderKind.FILES -> {
                    fileResults.firstOrNull()?.let {
                        launchFile(context, it)
                        onFinish()
                    }
                }
                SearchProviderKind.SETTINGS -> {
                    when {
                        internalCommands.isNotEmpty() -> {
                            internalCommands.first().onExecute(context)
                            onFinish()
                        }
                        systemSettingResults.isNotEmpty() -> {
                            context.startActivity(systemSettingResults.first().intent)
                            onFinish()
                        }
                        settingResults.isNotEmpty() -> {
                            val setting = settingResults.first().searchableItem
                            context.startActivity(
                                Intent(context, FeatureSettingsActivity::class.java).apply {
                                    putExtra("feature", setting.featureKey)
                                    setting.targetSettingHighlightKey?.let { putExtra("highlight_setting", it) }
                                },
                            )
                            onFinish()
                        }
                    }
                }
                SearchProviderKind.WEB -> {
                    if (query.isNotBlank()) {
                        launchWebSearch(context, query, isBubblesWebEnabled, searchEngine)
                        onFinish()
                    }
                }
                SearchProviderKind.EXTENSION -> Unit
            }
            return
        }

        if (prefixSuggestion != null) {
            enterScope(prefixSuggestion.provider)
            return
        }

        when {
            hasCommands -> {
                internalCommands.first().onExecute(context)
                onFinish()
            }
            hasRoutes -> {
                runCatching { context.startActivity(entityRoutes.first().navigationIntent) }
                    .onFailure { runCatching { context.startActivity(entityRoutes.first().mapIntent) } }
                onFinish()
            }
            hasApps -> {
                launchApp(context, appResults.first().packageName)
                onFinish()
            }
            hasContacts -> {
                contactResults.first().phoneNumber?.let {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))
                    context.startActivity(dialIntent)
                    onFinish()
                } ?: run {
                    if (query.isNotBlank()) launchWebSearch(context, query, isBubblesWebEnabled, searchEngine)
                    onFinish()
                }
            }
            hasMedia -> {
                launchFile(context, mediaResults.first())
                onFinish()
            }
            hasFiles -> {
                launchFile(context, fileResults.first())
                onFinish()
            }
            hasSystemSettings -> {
                context.startActivity(systemSettingResults.first().intent)
                onFinish()
            }
            hasEssentials -> {
                val setting = settingResults.first().searchableItem
                val intent = Intent(context, FeatureSettingsActivity::class.java).apply {
                    putExtra("feature", setting.featureKey)
                    setting.targetSettingHighlightKey?.let {
                        putExtra("highlight_setting", it)
                    }
                }
                context.startActivity(intent)
                onFinish()
            }
            hasShortcuts -> {
                SearchProviderDiscovery.launchShortcut(context, shortcutResults.first())
                onFinish()
            }
            else -> {
                if (query.isNotBlank()) {
                    launchWebSearch(context, query, isBubblesWebEnabled, searchEngine)
                }
                onFinish()
            }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
        ) {
            // Pinned top category filter tabs
            val tabs = remember(isAppsEnabled, isContactsEnabled, isMediaEnabled, isFilesEnabled, isSettingsEnabled, isWebEnabled) {
                PixelSearchTab.entries.filter { tab ->
                    when (tab) {
                        PixelSearchTab.ALL -> true
                        PixelSearchTab.APPS -> isAppsEnabled
                        PixelSearchTab.CONTACTS -> isContactsEnabled
                        PixelSearchTab.MEDIA -> isMediaEnabled
                        PixelSearchTab.FILES -> isFilesEnabled
                        PixelSearchTab.SETTINGS -> isSettingsEnabled
                        PixelSearchTab.WEB -> isWebEnabled
                    }
                }
            }
            val carouselState = rememberCarouselState { tabs.size }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(20f)
                        .padding(top = statusBarHeight),
            ) {
                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = 112.dp,
                    itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(44.dp),
                ) { index ->
                    val tab = tabs[index]
                    val isSelected = selectedTab == tab
                    val containerColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    val contentColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .maskClip(CircleShape)
                                .background(containerColor)
                                .clickable {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    selectedTab = tab
                                }
                                .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tab.iconRes?.let { iconRes ->
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            TranslatableText(
                                stringResId = tab.labelRes,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(overscrollNestedScrollConnection)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = statusBarHeight + 56.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                // STAGE 2: SCOPED PHOTOS GRID
                if (activeScope?.kind == SearchProviderKind.PHOTOS) {
                    val hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                    item(key = "scoped_photos_header") {
                        SearchSectionHeader(
                            title = if (query.isBlank()) "Recent Photos" else "Photos matching \"$query\"",
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!hasMediaPerm) {
                        item(key = "scoped_photos_perm") {
                            RoundedCardContainer(modifier = Modifier.animateItem()) {
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
                                    containerColor = highlightColor,
                                    iconRes = R.drawable.rounded_photo_library_24,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                )
                            }
                        }
                    } else if (scopedPhotos.isEmpty() && !isSearching) {
                        item(key = "scoped_photos_empty") {
                            RoundedCardContainer(modifier = Modifier.animateItem()) {
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
                    } else {
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
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    }
                }

                // STAGE 2: SCOPED APP INTENT SEARCH
                if (activeScope?.kind == SearchProviderKind.APP_INTENT) {
                    val scopeProvider = activeScope!!
                    item(key = "scoped_app_intent_header") {
                        SearchSectionHeader(
                            title = "Search in ${scopeProvider.title}",
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "scoped_app_intent_card") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            FeatureCard(
                                title = if (query.isBlank()) {
                                    "Open ${scopeProvider.title}"
                                } else {
                                    "Search \"${query.trim()}\" in ${scopeProvider.title}"
                                },
                                description = scopeProvider.subtitle,
                                isEnabled = true,
                                onToggle = {},
                                onClick = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    SearchProviderDiscovery.launchScopedProviderSearch(context, scopeProvider, query)
                                    onFinish()
                                },
                                containerColor = highlightColor,
                                iconRes = scopeProvider.iconRes,
                                iconPainter = scopeProvider.appIcon?.let { remember(it) { BitmapPainter(it) } },
                                hasIconBackground = scopeProvider.appIcon == null,
                                showToggle = false,
                                hasMoreSettings = false,
                            )
                        }
                    }
                }

                // STAGE 1: EMPTY QUERY QUICK PROVIDERS
                if (activeScope == null && query.isBlank() && discoveredProviders.isNotEmpty()) {
                    item(key = "quick_providers_header") {
                        SearchSectionHeader("Search Providers", Modifier.animateItem())
                    }
                    item(key = "quick_providers_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.animateItem(),
                        ) {
                            items(
                                items = discoveredProviders.take(12),
                                key = { it.id },
                            ) { provider ->
                                AssistChip(
                                    onClick = { enterScope(provider) },
                                    label = {
                                        Text(
                                            text = provider.title,
                                            maxLines = 1,
                                        )
                                    },
                                    leadingIcon = {
                                        if (provider.appIcon != null) {
                                            Image(
                                                painter = BitmapPainter(provider.appIcon),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape),
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(provider.iconRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                                    ),
                                )
                            }
                        }
                    }
                }

                // STAGE 1: PREFIX SCOPE SUGGESTION
                if (activeScope == null && prefixSuggestion != null) {
                    val suggestion = prefixSuggestion
                    item(key = "prefix_scope_header") {
                        SearchSectionHeader("Scope Search", Modifier.animateItem())
                    }
                    item(key = "prefix_scope_card") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            FeatureCard(
                                title = "Search in ${suggestion.provider.title}",
                                description = "${suggestion.provider.subtitle} • Press Tab or Enter to lock scope",
                                isEnabled = true,
                                onToggle = {},
                                onClick = { enterScope(suggestion.provider) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconRes = suggestion.provider.iconRes,
                                iconPainter = suggestion.provider.appIcon?.let { remember(it) { BitmapPainter(it) } },
                                hasIconBackground = suggestion.provider.appIcon == null,
                                showToggle = false,
                                hasMoreSettings = false,
                            )
                        }
                    }
                }

                // STAGE 1 / SETTINGS SCOPE: INTERNAL COMMANDS
                if (internalCommands.isNotEmpty()) {
                    item(key = "commands_header") {
                        SearchSectionHeader("Essentials Commands", Modifier.animateItem())
                    }
                    item(key = "commands_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            internalCommands.forEachIndexed { index, cmd ->
                                val isTopmost = index == 0 && hasCommands
                                FeatureCard(
                                    title = cmd.title,
                                    description = cmd.description,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        cmd.onExecute(context)
                                        onFinish()
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

                // STAGE 1: CONTEXTUAL ENTITY ROUTES (WORK / HOME / CALENDAR / CONTACTS)
                if (activeScope == null && entityRoutes.isNotEmpty()) {
                    item(key = "routes_header") {
                        SearchSectionHeader("Directions & Places", Modifier.animateItem())
                    }
                    item(key = "routes_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            entityRoutes.forEachIndexed { index, route ->
                                val isTopmost = index == 0 && hasRoutes
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
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    HapticUtil.performVirtualKeyHaptic(view)
                                                    runCatching { context.startActivity(route.navigationIntent) }
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
                                                Text("Navigate", style = MaterialTheme.typography.labelMedium)
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

                // APPS
                if ((isAllTab || selectedTab == PixelSearchTab.APPS) && isAppsEnabled && appResults.isNotEmpty()) {
                    item(key = "apps_header") {
                        SearchSectionHeader(stringResource(R.string.pixel_search_section_apps), Modifier.animateItem())
                    }
                    item(key = "apps_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            appResults.forEachIndexed { index, app ->
                                val isTopmost = index == 0 && hasApps
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

                // CONTACTS
                if ((isAllTab || selectedTab == PixelSearchTab.CONTACTS) && isContactsEnabled && contactResults.isNotEmpty()) {
                    item(key = "contacts_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_contacts),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "contacts_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            contactResults.forEachIndexed { index, contact ->
                                val isTopmost = index == 0 && hasContacts
                                ListItem(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        contact.phoneNumber?.let {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))
                                            context.startActivity(dialIntent)
                                            onFinish()
                                        }
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
                                                    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                                                    context.startActivity(callIntent)
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
                                                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
                                                    context.startActivity(smsIntent)
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

                if ((isAllTab || selectedTab == PixelSearchTab.MEDIA) && isMediaEnabled && mediaResults.isNotEmpty()) {
                    item(key = "media_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_media),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "media_carousel") {
                        val mediaCarouselState = rememberCarouselState { mediaResults.size }
                        HorizontalMultiBrowseCarousel(
                            state = mediaCarouselState,
                            preferredItemWidth = 140.dp,
                            itemSpacing = 8.dp,
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .animateItem(),
                        ) { index ->
                            val media = mediaResults[index]
                            val isTopmost = index == 0 && hasMedia
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .maskClip(RoundedCornerShape(20.dp))
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

                // FILES
                if ((isAllTab || selectedTab == PixelSearchTab.FILES) && isFilesEnabled && fileResults.isNotEmpty()) {
                    item(key = "files_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_files),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "files_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            fileResults.forEachIndexed { index, file ->
                                val isTopmost = index == 0 && hasFiles
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

                // SETTINGS (System Settings)
                if ((isAllTab || selectedTab == PixelSearchTab.SETTINGS) && isSettingsEnabled && systemSettingResults.isNotEmpty()) {
                    item(key = "system_settings_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_system_settings),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "system_settings_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            systemSettingResults.forEachIndexed { index, setting ->
                                val isTopmost = index == 0 && hasSystemSettings
                                FeatureCard(
                                    title = setting.title,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        context.startActivity(setting.intent)
                                        onFinish()
                                    },
                                    containerColor = if (isTopmost) highlightColor else normalCardColor,
                                    iconRes = setting.iconRes,
                                    showToggle = false,
                                    hasMoreSettings = false,
                                    description = setting.subtitle,
                                )
                            }
                        }
                    }
                }

                // ESSENTIALS
                if ((isAllTab || selectedTab == PixelSearchTab.SETTINGS) && isSettingsEnabled && settingResults.isNotEmpty()) {
                    item(key = "essentials_header") {
                        SearchSectionHeader(
                            stringResource(R.string.app_name),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "essentials_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            settingResults.forEachIndexed { index, item ->
                                val isTopmost = index == 0 && hasEssentials
                                val setting = item.searchableItem
                                FeatureCard(
                                    title = setting.title,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
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
                                            if (targetFeatureKey == "Pixel Searchbar") {
                                                Intent(context, PixelSearchbarSettingsActivity::class.java)
                                            } else {
                                                Intent(context, FeatureSettingsActivity::class.java).apply {
                                                    putExtra("feature", targetFeatureKey)
                                                    highlightKey?.let {
                                                        putExtra("highlight_setting", it)
                                                    }
                                                }
                                            }
                                        context.startActivity(intent)
                                        onFinish()
                                    },
                                    containerColor = if (isTopmost) highlightColor else normalCardColor,
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
                }

                // SHORTCUTS
                if ((activeScope == null || activeScope?.kind == SearchProviderKind.APP_INTENT) &&
                    (isAllTab || selectedTab == PixelSearchTab.SETTINGS) &&
                    isShortcutsEnabled &&
                    shortcutResults.isNotEmpty()
                ) {
                    item(key = "shortcuts_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_shortcuts),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "shortcuts_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            shortcutResults.forEachIndexed { index, shortcut ->
                                val isTopmost = index == 0 && hasShortcuts
                                FeatureCard(
                                    title = shortcut.shortLabel,
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
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

                // APP FUNCTIONS (Android 16+)
                if (activeScope == null && appFunctions.isNotEmpty()) {
                    item(key = "app_functions_header") {
                        SearchSectionHeader("App Functions", modifier = Modifier.animateItem())
                    }
                    item(key = "app_functions_cards") {
                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            appFunctions.forEach { fn ->
                                FeatureCard(
                                    title = fn.functionLabel,
                                    description = "${fn.appName} • App Function",
                                    isEnabled = true,
                                    onToggle = {},
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        fn.launchIntent?.let {
                                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(it)
                                        }
                                        onFinish()
                                    },
                                    containerColor = normalCardColor,
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

                // WEB SEARCH
                if ((activeScope == null || activeScope?.kind == SearchProviderKind.WEB) &&
                    (isAllTab || selectedTab == PixelSearchTab.WEB) &&
                    isWebEnabled &&
                    query.isNotBlank()
                ) {
                    val isTopmost = !hasCommands && !hasRoutes && !hasApps && !hasContacts && !hasMedia && !hasFiles && !hasSystemSettings && !hasEssentials && !hasShortcuts
                    item(key = "web_header") {
                        SearchSectionHeader(
                            stringResource(R.string.pixel_search_section_web),
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item(key = "web_cards") {
                        val isUrl = remember(query) { isLikelyUrl(query.trim()) }
                        val actionTitle = if (isUrl) {
                            stringResource(R.string.pixel_search_open_url_action, query)
                        } else {
                            stringResource(R.string.pixel_search_web_search_action, query)
                        }
                        val webIcon = if (isUrl) R.drawable.rounded_link_24 else R.drawable.rounded_language_24

                        RoundedCardContainer(modifier = Modifier.animateItem()) {
                            FeatureCard(
                                title = actionTitle,
                                isEnabled = true,
                                onToggle = {},
                                onClick = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    launchWebSearch(context, query, isBubblesWebEnabled, searchEngine)
                                    onFinish()
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

                item {
                    Spacer(modifier = Modifier.height(48.dp))
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

                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        if (query.isEmpty()) {
                            val currentScope = activeScope
                            if (currentScope != null) {
                                Text(
                                    text = "Search ${currentScope.title.lowercase()}...",
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
                            val suggestion = prefixSuggestion
                            val ghostAnnotated = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.Transparent)) {
                                    append(query)
                                }
                                if (suggestion.ghostSuffix.isNotEmpty()) {
                                    withStyle(SpanStyle(color = hintColor)) {
                                        append(suggestion.ghostSuffix)
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
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = bodyLargeStyle.copy(color = onSurfaceColor),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    if (activeScope == null && prefixSuggestion != null) {
                                        enterScope(prefixSuggestion.provider)
                                    } else {
                                        keyboardController?.hide()
                                        launchTopmostOrWeb()
                                    }
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when {
                                            keyEvent.key == Key.Tab && activeScope == null && prefixSuggestion != null -> {
                                                enterScope(prefixSuggestion.provider)
                                                true
                                            }
                                            keyEvent.key == Key.Backspace && activeScope != null && query.isEmpty() -> {
                                                exitScope()
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

                    when {
                        isSearching -> Box(modifier = Modifier.padding(end = 6.dp)) { LoadingIndicator() }
                        query.isNotEmpty() -> IconButton(
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                query = ""
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_close_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        activeScope != null -> IconButton(
                            onClick = { exitScope() },
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
            }
        }

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
    }
}
}

private fun launchFile(context: Context, item: PixelSearchResultItem.FileItem) {
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
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    } catch (_: Exception) {
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

private fun launchWebSearch(
    context: Context,
    query: String,
    useBubbles: Boolean = false,
    searchEngine: String = "Google",
) {
    try {
        val targetUri = buildSearchOrUrl(query, searchEngine)
        if (useBubbles) {
            val launched = WindowingUtils.launchOverlayWindow(context, targetUri)
            if (launched) return
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
        }
    }
}

private fun loadContacts(context: Context, query: String): List<PixelSearchResultItem.ContactItem> {
    val results = mutableListOf<PixelSearchResultItem.ContactItem>()
    try {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 5",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getString(idIdx) else ""
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                if (name.isNotBlank()) {
                    results.add(
                        PixelSearchResultItem.ContactItem(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            photoUri = photo,
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
    }
    return results
}

private fun loadSystemSettings(context: Context, query: String): List<PixelSearchResultItem.SystemSettingItem> {
    val q = query.lowercase()
    val all = listOf(
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.island_network_wifi),
            subtitle = context.getString(R.string.pixel_search_setting_network),
            iconRes = R.drawable.rounded_android_wifi_3_bar_24,
            intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.icon_bluetooth),
            subtitle = context.getString(R.string.pixel_search_setting_bluetooth),
            iconRes = R.drawable.rounded_bluetooth_24,
            intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.cat_display),
            subtitle = context.getString(R.string.pixel_search_setting_display),
            iconRes = R.drawable.rounded_mobile_text_2_24,
            intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.pixel_search_setting_sound_title),
            subtitle = context.getString(R.string.pixel_search_setting_sound),
            iconRes = R.drawable.rounded_volume_up_24,
            intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.icon_battery),
            subtitle = context.getString(R.string.pixel_search_setting_battery),
            iconRes = R.drawable.rounded_battery_charging_60_24,
            intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.tab_apps),
            subtitle = context.getString(R.string.pixel_search_setting_apps),
            iconRes = R.drawable.rounded_apps_24,
            intent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.option_notifications),
            subtitle = context.getString(R.string.pixel_search_setting_notifications),
            iconRes = R.drawable.rounded_notifications_unread_24,
            intent = Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.label_device_storage),
            subtitle = context.getString(R.string.pixel_search_setting_storage),
            iconRes = R.drawable.rounded_save_24,
            intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
        PixelSearchResultItem.SystemSettingItem(
            title = context.getString(R.string.pixel_search_setting_security_title),
            subtitle = context.getString(R.string.pixel_search_setting_security),
            iconRes = R.drawable.rounded_security_24,
            intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        ),
    )

    return all.filter {
        it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
    }.take(4)
}

private fun loadShortcuts(context: Context, query: String): List<PixelSearchResultItem.ShortcutItem> {
    return emptyList()
}
