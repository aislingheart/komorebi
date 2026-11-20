/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: FeatureSettingsActivity.kt
 * Description: Activity component for FeatureSettingsActivity.kt.
 */

package com.sameerasw.essentials

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.essentials.domain.registry.FeatureRegistry
import com.sameerasw.essentials.ui.components.animations.LottieFeatureAnimation
import com.sameerasw.essentials.ui.components.linkActions.LinkPickerScreen
import com.sameerasw.essentials.ui.core.cards.FeatureCard
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.core.sheets.PermissionsBottomSheet
import com.sameerasw.essentials.ui.core.sheets.ShakeForHelp
import com.sameerasw.essentials.ui.features.system.ActivityLauncherSearchBar
import com.sameerasw.essentials.ui.features.system.ActivityLauncherSettingsUI
import com.sameerasw.essentials.ui.features.system.CalendarSyncSettingsUI
import com.sameerasw.essentials.ui.modifiers.highlight
import com.sameerasw.essentials.ui.navigation.AppRoute
import com.sameerasw.essentials.ui.navigation.BottomBarSpec
import com.sameerasw.essentials.ui.navigation.LocalSelectedFeatureIds
import com.sameerasw.essentials.ui.navigation.ProvideBottomBar
import com.sameerasw.essentials.ui.navigation.findFragmentActivity
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.utils.BiometricSecurityHelper
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeatureSettingsScreen(
    route: AppRoute.FeatureSettings,
    onBack: () -> Unit,
) {
    val featureId = route.featureId
    val featureObj = remember(featureId) { FeatureRegistry.ALL_FEATURES.find { it.id == featureId } }
    val highlightSetting = route.highlightSetting
    val activity = LocalContext.current.findFragmentActivity()

    if (featureId == "Link actions" || featureId == "URL Shortener") {
        val viewModel: MainViewModel = viewModel()
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            viewModel.check(context)
        }
        LinkPickerScreen(
            uri = "https://github.com/aislingheart/komorebi".toUri(),
            onFinish = onBack,
            modifier = Modifier.fillMaxSize(),
            demo = false,
            initialTab = if (featureId == "URL Shortener") 2 else 0,
            initialOpenShorten = featureId == "URL Shortener",
        )
        return
    }

    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()

    // Automatic refresh on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.check(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialize synchronously so settingsRepository is ready before first composition
    remember(context) {
        viewModel.check(context)
        true
    }

    val isPitchBlackThemeEnabled by viewModel.isPitchBlackThemeEnabled
    val pinnedFeatureKeys by viewModel.pinnedFeatureKeys

    EssentialsTheme(pitchBlackTheme = isPitchBlackThemeEnabled) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.sameerasw.essentials.ui.state.LocalMenuStateManager provides
                remember {
                    com.sameerasw.essentials.ui.state
                        .MenuStateManager()
                },
        ) {
            // Permission sheet state
            var showPermissionSheet by remember { mutableStateOf(false) }
            var childFeatureForPermissions by remember { mutableStateOf<String?>(null) }
            var activityLauncherQuery by remember { mutableStateOf("") }

            val isAccessibilityEnabled by viewModel.isAccessibilityEnabled
            val isWriteSecureSettingsEnabled by viewModel.isWriteSecureSettingsEnabled
            val isOverlayPermissionGranted by viewModel.isOverlayPermissionGranted
            val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled
            val isReadPhoneStateEnabled by viewModel.isReadPhoneStateEnabled
            val isShizukuPermissionGranted by viewModel.isShizukuPermissionGranted

            // Help Sheet State
            var showHelpSheet by remember { mutableStateOf(false) }
            var showInstructionsSheet by remember { mutableStateOf(false) }
            var selectedHelpFeature by remember {
                mutableStateOf<com.sameerasw.essentials.domain.model.Feature?>(
                    null,
                )
            }

            // Show permission sheet if feature has missing permissions
            LaunchedEffect(
                featureId,
                isAccessibilityEnabled,
                isWriteSecureSettingsEnabled,
                isOverlayPermissionGranted,
                isNotificationListenerEnabled,
                isReadPhoneStateEnabled,
                isShizukuPermissionGranted,
            ) {
                val hasMissingPermissions =
                    when (featureId) {
                        "Face unlock brightness" -> !isAccessibilityEnabled
                        "Lock screen shortcuts" -> !isAccessibilityEnabled
                        "Text and animations" -> !viewModel.isWriteSettingsEnabled.value || !isWriteSecureSettingsEnabled
                        "Lock screen clock" -> !isWriteSecureSettingsEnabled
                        "Other customizations" ->
                            !com.sameerasw.essentials.utils.ShellUtils.hasPermission(
                                context,
                            )
                        else -> false
                    }
                if (hasMissingPermissions) {
                    showPermissionSheet = true
                }
            }

            if (showPermissionSheet) {
                val featureIdForPermissions = childFeatureForPermissions ?: featureId
                val featureObjForPermissions =
                    FeatureRegistry.ALL_FEATURES.find { it.id == featureIdForPermissions }
                val permissionItems =
                    if (featureObjForPermissions != null) {
                        com.sameerasw.essentials.utils.PermissionUIHelper.getPermissionItems(
                            featureObjForPermissions.permissionKeys,
                            context,
                            viewModel,
                            activity,
                        )
                    } else {
                        emptyList()
                    }

                if (permissionItems.isNotEmpty()) {
                    PermissionsBottomSheet(
                        onDismissRequest = {
                            showPermissionSheet = false
                            childFeatureForPermissions = null
                        },
                        featureTitle =
                            if (featureObjForPermissions != null && childFeatureForPermissions == null) {
                                stringResource(
                                    featureObjForPermissions.title,
                                )
                            } else {
                                featureIdForPermissions
                            },
                        permissions = permissionItems,
                    )
                }
            }

            if (showHelpSheet && selectedHelpFeature != null) {
                com.sameerasw.essentials.ui.core.sheets.FeatureHelpBottomSheet(
                    onDismissRequest = {
                        showHelpSheet = false
                        selectedHelpFeature = null
                    },
                    feature = selectedHelpFeature!!,
                )
            }

            if (showInstructionsSheet) {
                com.sameerasw.essentials.ui.core.sheets.InstructionsBottomSheet(
                    onDismissRequest = { showInstructionsSheet = false },
                )
            }

            ShakeForHelp(feature = featureObj, viewModel = viewModel)

            var showWhatIsThis by remember { mutableStateOf(false) }
            if (showWhatIsThis) {
                com.sameerasw.essentials.ui.core.sheets.FeatureHelpSheet(featureObj, viewModel) { showWhatIsThis = false }
            }

            val pageTitle =
                if (featureObj != null) stringResource(featureObj.title) else featureId
            val hasMenu = featureObj != null && featureObj.aboutDescription != null
            val view = LocalView.current

            val density = LocalDensity.current
            val minHeaderHeight = 200.dp
            val maxHeaderHeight = 400.dp
            var headerHeight by remember { mutableStateOf(minHeaderHeight) }

            val nestedScrollConnection =
                remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val delta = available.y
                            if (delta < 0 && headerHeight > minHeaderHeight) {
                                val oldHeight = headerHeight
                                headerHeight =
                                    with(density) {
                                        (oldHeight.toPx() + delta).toDp()
                                    }.coerceAtLeast(minHeaderHeight)
                                val consumed = oldHeight - headerHeight
                                return Offset(0f, with(density) { -consumed.toPx() })
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val delta = available.y
                            if (delta > 0) {
                                val oldHeight = headerHeight
                                headerHeight =
                                    with(density) {
                                        (oldHeight.toPx() + delta).toDp()
                                    }.coerceAtMost(maxHeaderHeight)

                                if (headerHeight == maxHeaderHeight && oldHeight < maxHeaderHeight) {
                                    HapticUtil.performLightHaptic(view)
                                }

                                val produced = headerHeight - oldHeight
                                return Offset(0f, with(density) { produced.toPx() })
                            }
                            return Offset.Zero
                        }
                    }
                }

            val statusBarHeight =
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            val scrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(com.sameerasw.essentials.ui.core.komorebi.pixelSettingsCanvasColor()),
                ) {
                    val hasScroll = featureId != "Activity launcher"
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (hasScroll) {
                                        Modifier
                                            .nestedScroll(nestedScrollConnection)
                                            .verticalScroll(scrollState)
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        if (featureId != "Activity launcher") {
                            androidx.compose.foundation.layout.Spacer(
                                modifier =
                                    Modifier.height(
                                        statusBarHeight,
                                    ),
                            )
                        }

                        if (featureObj != null && featureObj.animationRes != 0) {
                            LottieFeatureAnimation(
                                resId = featureObj.animationRes,
                                height = headerHeight,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }

                        val children =
                            FeatureRegistry
                                .getFilteredFeatures(
                                    context,
                                    viewModel.isEnableUnsupportedFeatures.value,
                                    viewModel.isShowLegacyFeatures.value,
                                ).filter { it.parentFeatureId == featureId }
                        if (children.isNotEmpty() && featureId != "Navigation") {
                            val sectionChildLists =
                                run {
                                    val childMap = children.associateBy { it.id }
                                    val definedSections =
                                        when (featureId) {
                                            "Display" ->
                                                listOf(
                                                    listOf(
                                                        "Lock screen clock",
                                                    ),
                                                    listOf(
                                                        "Text and animations",
                                                    ),
                                                    listOf(
                                                        "Other customizations",
                                                    ),
                                                )

                                            "Widgets" ->
                                                listOf(
                                                    listOf(
                                                        "Pixel Searchbar",
                                                    ),
                                                )

                                            "Input" ->
                                                listOf(
                                                    listOf(
                                                        "Activity launcher",
                                                        "Lock screen shortcuts",
                                                    ),
                                                    listOf(
                                                        "Navigation",
                                                        "Link actions",
                                                        "System Keyboard",
                                                    ),
                                                )

                                            "Security" ->
                                                listOf(
                                                    listOf(
                                                        "Face unlock brightness",
                                                    ),
                                                )

                                            else -> null
                                        }

                                    if (definedSections != null) {
                                        val assignedIds = definedSections.flatten().toSet()
                                        val unassigned = children.filter { it.id !in assignedIds }
                                        definedSections
                                            .map { ids -> ids.mapNotNull { childMap[it] } }
                                            .filter { it.isNotEmpty() } +
                                            if (unassigned.isNotEmpty()) {
                                                listOf(
                                                    unassigned,
                                                )
                                            } else {
                                                emptyList()
                                            }
                                    } else {
                                        listOf(children)
                                    }
                                }

                            sectionChildLists.forEach { sectionChildren ->
                                RoundedCardContainer(
                                    modifier =
                                        Modifier
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 16.dp),
                                ) {
                                    sectionChildren.forEach { child ->
                                        val permissionAwareToggle: (Boolean) -> Unit =
                                            { enabled ->
                                                val missingPermission =
                                                    when (child.id) {
                                                        "Face unlock brightness" -> !isAccessibilityEnabled
                                                        "Lock screen shortcuts" -> !isAccessibilityEnabled
                                                        "Calendar Sync" ->
                                                            arrayOf(
                                                                android.Manifest.permission.READ_CALENDAR,
                                                                android.Manifest.permission.WRITE_CALENDAR,
                                                            ).any {
                                                                androidx.core.content.ContextCompat.checkSelfPermission(
                                                                    context,
                                                                    it,
                                                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                                            }

                                                        "Text and animations" ->
                                                            !viewModel.isWriteSettingsEnabled.value ||
                                                                !isWriteSecureSettingsEnabled
                                                        "Lock screen clock" -> !isWriteSecureSettingsEnabled
                                                        else -> false
                                                    }

                                                if (missingPermission) {
                                                    childFeatureForPermissions = child.id
                                                    showPermissionSheet = true
                                                } else {
                                                    BiometricSecurityHelper.runWithAuth(
                                                        activity = activity,
                                                        feature = child,
                                                        isToggle = true,
                                                        action = {
                                                            child.onToggle(
                                                                viewModel,
                                                                context,
                                                                enabled,
                                                            )
                                                        },
                                                    )
                                                }
                                            }

                                        FeatureCard(
                                            modifier = Modifier.highlight(highlightSetting == child.id),
                                            title = child.title,
                                            isSelected = child.id in LocalSelectedFeatureIds.current,
                                            description = child.description,
                                            iconRes = child.iconRes,
                                            isEnabled = child.isEnabled(viewModel),
                                            isToggleEnabled =
                                                child.isToggleEnabled(
                                                    viewModel,
                                                    context,
                                                ),
                                            showToggle = child.showToggle,
                                            onDisabledToggleClick = {
                                                permissionAwareToggle(true)
                                            },
                                            hasMoreSettings = child.hasMoreSettings,
                                            isBeta = child.isBeta,
                                            isLegacy = child.isLegacy,
                                            isUnsupported = !child.isDeviceSupported(context),
                                            onToggle = permissionAwareToggle,
                                            onClick = {
                                                if (child.hasMoreSettings) {
                                                    BiometricSecurityHelper.runWithAuth(
                                                        activity = activity,
                                                        feature = child,
                                                        action = {
                                                            child.onClick(context, viewModel)
                                                        },
                                                    )
                                                }
                                            },
                                            isPinned = pinnedFeatureKeys.contains(child.id),
                                            onPinToggle = { viewModel.togglePinFeature(child.id) },
                                            onHelpClick =
                                                if (child.aboutDescription != null) {
                                                    {
                                                        selectedHelpFeature = child
                                                        showHelpSheet = true
                                                    }
                                                } else {
                                                    null
                                                },
                                        )
                                    }
                                }
                            }
                        } else {
                            when (featureId) {
                                "Activity launcher" -> {
                                    ActivityLauncherSettingsUI(
                                        query = activityLauncherQuery,
                                        modifier = Modifier.nestedScroll(nestedScrollConnection),
                                    )
                                }

                                "Calendar Sync" -> {
                                    CalendarSyncSettingsUI(
                                        viewModel = viewModel,
                                        modifier = Modifier.padding(top = 16.dp),
                                        highlightKey = highlightSetting,
                                    )
                                }
                            }
                        }
                        if (featureObj != null && hasScroll) {
                            com.sameerasw.essentials.ui.core.sheets.WhatIsThisButton(
                                onClick = { showWhatIsThis = true },
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                        // Bottom padding for toolbar
                        androidx.compose.foundation.layout.Spacer(
                            modifier =
                                Modifier.height(
                                    150.dp,
                                ),
                        )
                    }

                    ProvideBottomBar(
                        route = route,
                        spec =
                            BottomBarSpec(
                                title = pageTitle,
                                isBeta = featureObj?.isBeta ?: false,
                                onBack = { onBack() },
                                fabIconRes = null,
                                fabAction = null,
                                fabContentDescription = null,
                                onHelpClick = {
                                    if (hasMenu) {
                                        selectedHelpFeature = featureObj
                                        showHelpSheet = true
                                    } else {
                                        showInstructionsSheet = true
                                    }
                                },
                            ),
                    )
                }

                if (featureId == "Activity launcher") {
                    ActivityLauncherSearchBar(
                        query = activityLauncherQuery,
                        onQueryChange = { activityLauncherQuery = it },
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(start = 16.dp, end = 16.dp, top = statusBarHeight + 8.dp),
                    )
                }
            }
        }
    }
}

class FeatureSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val featureId = intent.getStringExtra("feature") ?: ""
        val highlightSetting = intent.getStringExtra("highlight_setting")
        val route = AppRoute.FeatureSettings(featureId = featureId, highlightSetting = highlightSetting)
        setContent {
            FeatureSettingsScreen(
                route = route,
                onBack = { finish() },
            )
        }
    }
}
