/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchbarSettingsActivity.kt
 * Description: Activity component for PixelSearchbarSettingsActivity.kt.
 */

package com.sameerasw.essentials.ui.activities

import com.sameerasw.essentials.assistant.AssistantTts
import com.sameerasw.essentials.translation.TranslatableText
import com.sameerasw.essentials.ui.navigation.ProvideBottomBar
import com.sameerasw.essentials.ui.navigation.BottomBarSpec
import com.sameerasw.essentials.ui.navigation.AppRoute
import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.essentials.R
import com.sameerasw.essentials.assistant.decision.LocalDecisionModel
import com.sameerasw.essentials.assistant.decision.LocalDecisionModelManager
import com.sameerasw.essentials.assistant.decision.ModelDownloadState
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import com.sameerasw.essentials.domain.model.Feature
import com.sameerasw.essentials.ui.components.animations.LottieFeatureAnimation
import com.sameerasw.essentials.ui.components.menus.SegmentedDropdownMenuItem
import com.sameerasw.essentials.ui.core.cards.ConfigPickerItem
import com.sameerasw.essentials.ui.core.cards.IconToggleItem
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.core.sheets.FeatureHelpBottomSheet
import com.sameerasw.essentials.ui.core.sheets.PermissionsBottomSheet
import com.sameerasw.essentials.ui.core.sheets.ShakeForHelp
import com.sameerasw.essentials.ui.modifiers.scrollMotionBlur
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.PermissionUIHelper
import com.sameerasw.essentials.utils.PermissionUtils
import com.sameerasw.essentials.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelSearchbarSettingsScreen(onBack: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    viewModel.check(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    remember(context) {
        viewModel.check(context)
        true
    }

    val isPitchBlackThemeEnabled by viewModel.isPitchBlackThemeEnabled
    var showHelpSheet by remember { mutableStateOf(false) }

    val pixelSearchbarFeature =
        remember {
            object : Feature(
                id = "Pixel Searchbar",
                title = R.string.pixel_searchbar_settings_title,
                iconRes = R.drawable.rounded_search_24,
                category = R.string.cat_display,
                description = R.string.feat_pixel_searchbar_desc,
                aboutDescription = R.string.about_desc_pixel_searchbar,
                showToggle = false,
                hasMoreSettings = true,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = false

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {}
            }
        }

    val isBlurEnabled by viewModel.isBlurEnabled

    EssentialsTheme(pitchBlackTheme = isPitchBlackThemeEnabled) {
        @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { _ ->
            val density = LocalDensity.current
            val statusBarHeightPx =
                with(density) {
                    WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                        .toPx()
                }

            val isMotionBlurEnabled by viewModel.isMotionBlurEnabled
            val scrollState = rememberScrollState()
            val view = LocalView.current
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

            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scrollMotionBlur(scrollState, enabled = isMotionBlurEnabled)
                            .nestedScroll(nestedScrollConnection)
                            .verticalScroll(scrollState),
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                            ),
                    )

                    LottieFeatureAnimation(
                        resId = R.raw.searchbar_motion,
                        height = headerHeight,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    PixelSearchbarSettingsUI(
                        viewModel = viewModel,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding() + 150.dp,
                            ),
                    )
                }

                ProvideBottomBar(
                    route = AppRoute.PixelSearchbarSettings,
                    spec =
                        BottomBarSpec(
                            title = stringResource(R.string.pixel_searchbar_settings_title),
                            onBack = onBack,
                            onHelpClick = { showHelpSheet = true },
                        ),
                )

                if (showHelpSheet) {
                    FeatureHelpBottomSheet(
                        onDismissRequest = { showHelpSheet = false },
                        feature = pixelSearchbarFeature,
                        viewModel = viewModel,
                    )
                }

                ShakeForHelp(feature = pixelSearchbarFeature, viewModel = viewModel)
            }
        }
    }
}

class PixelSearchbarSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelSearchbarSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelSearchbarSettingsUI(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val overlayRepository = remember(context) { SettingsRepository(context) }
    var searchOverlayEnabled by remember {
        mutableStateOf(overlayRepository.getPixelSearchOverlayEnabled())
    }
    var voiceSearchEnabled by remember { mutableStateOf(overlayRepository.isVoiceSearchEnabled()) }
    var voiceTtsEnabled by remember { mutableStateOf(overlayRepository.isVoiceTtsEnabled()) }
    var voicePreset by remember { mutableStateOf(overlayRepository.getVoiceTtsPreset()) }
    var selectedDecisionModel by remember {
        mutableStateOf(LocalDecisionModel.fromId(overlayRepository.getLocalDecisionModelId()))
    }
    var localSystemOneUrl by remember {
        mutableStateOf(overlayRepository.getLocalSystemOneUrl())
    }
    var endpointTestStatus by remember { mutableStateOf<String?>(null) }
    val modelDownloadStates by LocalDecisionModelManager.states.collectAsState()
    val settingsScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        LocalDecisionModelManager.refreshStates(context)
    }
    val view = LocalView.current
    var requestingPermissionKey by remember { mutableStateOf<String?>(null) }

    val mediaPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.values.isNotEmpty() && permissions.values.all { it }
            viewModel.setPixelSearchResultMediaEnabled(allGranted)
        }

    val contactsPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            viewModel.setPixelSearchResultContactsEnabled(isGranted)
        }

    var notificationAccessTick by remember { mutableStateOf(0) }
    val notificationAccessLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { notificationAccessTick++ }
    val hasNotificationAccess =
        remember(context, notificationAccessTick) {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

    val assistantRoleLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.getSystemService(android.app.role.RoleManager::class.java)
                    ?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
            viewModel.setPixelSearchAssistantEnabled(granted)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundedCardContainer {
            IconToggleItem(
                iconRes = R.drawable.rounded_widgets_24,
                title = stringResource(R.string.search_widget_add_title),
                description = stringResource(R.string.search_widget_add_desc),
                showToggle = false,
                onClick = {
                    val manager = android.appwidget.AppWidgetManager.getInstance(context)
                    val provider =
                        android.content.ComponentName(
                            context,
                            com.sameerasw.essentials.services.widgets.KomorebiSearchWidgetReceiver::class.java,
                        )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        manager.isRequestPinAppWidgetSupported
                    ) {
                        manager.requestPinAppWidget(provider, null, null)
                    } else {
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.search_widget_add_unavailable),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                    }
                },
            )
            IconToggleItem(
                iconRes = R.drawable.rounded_touch_app_24,
                title = stringResource(R.string.search_gesture_title),
                description = stringResource(R.string.search_gesture_desc),
                showToggle = false,
                onClick = {
                    runCatching {
                        val intent =
                            android.content.Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                                putExtra(
                                    "android.provider.extra.ACCESSIBILITY_COMPONENT_NAME",
                                    android.content.ComponentName(
                                        context,
                                        com.sameerasw.essentials.services.SearchGestureAccessibilityService::class.java,
                                    ).flattenToString(),
                                )
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    }.onFailure {
                        com.sameerasw.essentials.utils.PermissionUtils.openAccessibilitySettings(context)
                    }
                },
            )
            IconToggleItem(
                iconRes = R.drawable.rounded_window_open_24,
                title = stringResource(R.string.search_overlay_title),
                description = stringResource(R.string.search_overlay_desc),
                isChecked = searchOverlayEnabled,
                onCheckedChange = { enabled ->
                    searchOverlayEnabled = enabled
                    overlayRepository.setPixelSearchOverlayEnabled(enabled)
                },
            )
        }

        RoundedCardContainer {
            IconToggleItem(
                iconRes = R.drawable.rounded_mic_24,
                title = "Voice search",
                description = "Search by speaking, using the system on-device speech recognizer",
                isChecked = voiceSearchEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    voiceSearchEnabled = checked
                    overlayRepository.setVoiceSearchEnabled(checked)
                },
            )
            IconToggleItem(
                iconRes = R.drawable.rounded_record_voice_over_24,
                title = "Speak matched action responses",
                description = "Read matched local action responses aloud after a voice search",
                isChecked = voiceTtsEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    voiceTtsEnabled = checked
                    overlayRepository.setVoiceTtsEnabled(checked)
                    if (!checked) {
                        com.sameerasw.essentials.assistant.AssistantTts.stop()
                    }
                },
            )
            val voicePresets = AssistantTts.presets
            val selectedVoice = voicePresets.firstOrNull { it.id == voicePreset } ?: voicePresets.first()
            ConfigPickerItem(
                iconRes = R.drawable.rounded_record_voice_over_24,
                title = "Voice style",
                description = "Pitch and speaking-rate presets for spoken responses",
                selectedValue = selectedVoice.label,
            ) {
                voicePresets.forEach { preset ->
                    SegmentedDropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            voicePreset = preset.id
                            overlayRepository.setVoiceTtsPreset(preset.id)
                            AssistantTts.preview(context, preset.id)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Local Decision Fallback",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RoundedCardContainer {
            ConfigPickerItem(
                iconRes = R.drawable.rounded_bolt_24,
                title = "Decision model",
                description = "Runs locally only when a search query has no direct matches",
                selectedValue = selectedDecisionModel.shortLabel,
            ) {
                LocalDecisionModel.entries.forEach { model ->
                    val dlState = modelDownloadStates[model]
                    val suffix = when {
                        dlState is ModelDownloadState.Ready -> " · Downloaded"
                        dlState is ModelDownloadState.Downloading -> " · ${(dlState.progress * 100).toInt()}%"
                        else -> " · ${model.sizeBadge}"
                    }
                    SegmentedDropdownMenuItem(
                        text = { Text("${model.label}$suffix") },
                        onClick = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            selectedDecisionModel = model
                            overlayRepository.setLocalDecisionModelId(model.id)
                            endpointTestStatus = null
                            LocalDecisionModelManager.refreshStates(context)
                        },
                    )
                }
            }

            if (selectedDecisionModel.requiresDownload) {
                val dlState = modelDownloadStates[selectedDecisionModel]
                    ?: LocalDecisionModelManager.getState(context, selectedDecisionModel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = selectedDecisionModel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (dlState) {
                        is ModelDownloadState.Downloading -> {
                            Text(
                                text = "Downloading local weights… ${(dlState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            LinearProgressIndicator(
                                progress = { dlState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is ModelDownloadState.Ready -> {
                            val mb = dlState.sizeBytes / (1024f * 1024f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "Ready on device (%.1f MB)", mb),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                OutlinedButton(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        settingsScope.launch {
                                            LocalDecisionModelManager.deleteModel(context, selectedDecisionModel)
                                        }
                                    },
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                        is ModelDownloadState.Error -> {
                            Text(
                                text = "Download failed: ${dlState.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(
                                onClick = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    settingsScope.launch {
                                        LocalDecisionModelManager.downloadModel(context, selectedDecisionModel)
                                    }
                                },
                            ) {
                                Text("Retry download (${selectedDecisionModel.sizeBadge})")
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Not downloaded · ${selectedDecisionModel.sizeBadge}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        settingsScope.launch {
                                            LocalDecisionModelManager.downloadModel(context, selectedDecisionModel)
                                        }
                                    },
                                ) {
                                    Text("Download")
                                }
                            }
                        }
                    }
                }
            }

            if (selectedDecisionModel.isLocalEndpoint) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Point at a local or Tailscale /v1/systemone server (e.g., llama-server with Kev or laya serve).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = localSystemOneUrl,
                        onValueChange = { updated ->
                            localSystemOneUrl = updated
                            overlayRepository.setLocalSystemOneUrl(updated)
                        },
                        label = { Text("Local /v1/systemone URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = endpointTestStatus ?: "Falls back to Fast Local if offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                endpointTestStatus = "Testing…"
                                settingsScope.launch {
                                    val res = LocalDecisionModelManager.testLocalSystemOne(localSystemOneUrl)
                                    endpointTestStatus = res.fold(
                                        onSuccess = { ms -> "Connected (${ms}ms)" },
                                        onFailure = { err -> "Offline: ${err.message?.take(48) ?: "unreachable"}" },
                                    )
                                }
                            },
                        ) {
                            Text("Test")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TranslatableText(
            stringResId = R.string.pixel_search_results_title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val appsEnabled = viewModel.pixelSearchResultApps.value
        val mediaEnabled = viewModel.pixelSearchResultMedia.value
        val filesEnabled = viewModel.pixelSearchResultFiles.value
        val contactsEnabled = viewModel.pixelSearchResultContacts.value
        val settingsEnabled = viewModel.pixelSearchResultSettings.value
        val shortcutsEnabled = viewModel.pixelSearchResultShortcuts.value
        val webEnabled = viewModel.pixelSearchResultWeb.value
        val reachabilityEnabled = viewModel.pixelSearchReachability.value
        val usageRankingEnabled = viewModel.pixelSearchUsageRanking.value
        val typoToleranceEnabled = viewModel.pixelSearchTypoTolerance.value
        val maxResults = viewModel.pixelSearchMaxResults.value
        val hideInitialResults = viewModel.pixelSearchHideInitialResults.value
        val notificationsEnabled = viewModel.pixelSearchResultNotifications.value
        val assistantEnabled = viewModel.pixelSearchAssistantEnabled.value

        RoundedCardContainer {
            IconToggleItem(
                iconRes = R.drawable.rounded_apps_24,
                title = stringResource(R.string.pixel_search_results_apps_title),
                description = stringResource(R.string.pixel_search_results_apps_desc),
                isChecked = appsEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchResultAppsEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_image_24,
                title = stringResource(R.string.pixel_search_results_media_title),
                description = stringResource(R.string.pixel_search_results_media_desc),
                isChecked = mediaEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    if (checked) {
                        val hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                        if (!hasMediaPerm) {
                            requestingPermissionKey = "MEDIA"
                        } else {
                            viewModel.setPixelSearchResultMediaEnabled(true)
                        }
                    } else {
                        viewModel.setPixelSearchResultMediaEnabled(false)
                    }
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_description_24,
                title = stringResource(R.string.pixel_search_results_files_title),
                description = stringResource(R.string.pixel_search_results_files_desc),
                isChecked = filesEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    if (checked) {
                        val hasFilesPerm = PermissionUtils.hasManageExternalStoragePermission(context) ||
                            PermissionUtils.hasStoragePermission(context) ||
                            viewModel.isStoragePermissionGranted.value
                        if (!hasFilesPerm) {
                            requestingPermissionKey = "ALL_FILES"
                        } else {
                            viewModel.setPixelSearchResultFilesEnabled(true)
                        }
                    } else {
                        viewModel.setPixelSearchResultFilesEnabled(false)
                    }
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_call_24,
                title = stringResource(R.string.pixel_search_results_contacts_title),
                description = stringResource(R.string.pixel_search_results_contacts_desc),
                isChecked = contactsEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    if (checked) {
                        val hasContactsPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasContactsPerm) {
                            requestingPermissionKey = "READ_CONTACTS"
                        } else {
                            viewModel.setPixelSearchResultContactsEnabled(true)
                        }
                    } else {
                        viewModel.setPixelSearchResultContactsEnabled(false)
                    }
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_settings_24,
                title = stringResource(R.string.pixel_search_results_settings_title),
                description = stringResource(R.string.pixel_search_results_settings_desc),
                isChecked = settingsEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchResultSettingsEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_rocket_launch_24,
                title = stringResource(R.string.pixel_search_results_shortcuts_title),
                description = stringResource(R.string.pixel_search_results_shortcuts_desc),
                isChecked = shortcutsEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchResultShortcutsEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_web_24,
                title = stringResource(R.string.pixel_search_results_web_title),
                description = stringResource(R.string.pixel_search_results_web_desc),
                isChecked = webEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchResultWebEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_notifications_24,
                title = "Search active notifications",
                description = if (hasNotificationAccess) {
                    "Include notification titles and text in search results"
                } else {
                    "Grant notification access to search active notifications"
                },
                isChecked = notificationsEnabled && hasNotificationAccess,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    if (checked) {
                        if (hasNotificationAccess) {
                            viewModel.setPixelSearchResultNotificationsEnabled(true)
                        } else {
                            notificationAccessLauncher.launch(
                                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        }
                    } else {
                        viewModel.setPixelSearchResultNotificationsEnabled(false)
                    }
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_robot_24,
                title = "Use Komorebi as assistant",
                description = "Let the system launch Komorebi search as your assistant app",
                isChecked = assistantEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    if (!checked) {
                        viewModel.setPixelSearchAssistantEnabled(false)
                        return@IconToggleItem
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                        if (roleManager?.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT) == true) {
                            if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                                viewModel.setPixelSearchAssistantEnabled(true)
                            } else {
                                assistantRoleLauncher.launch(
                                    roleManager.createRequestRoleIntent(
                                        android.app.role.RoleManager.ROLE_ASSISTANT,
                                    ),
                                )
                            }
                        } else {
                            context.startActivity(
                                android.content.Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            )
                        }
                    } else {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                        )
                    }
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_keyboard_24,
                title = stringResource(R.string.pixel_search_reachability_title),
                description = stringResource(R.string.pixel_search_reachability_desc),
                isChecked = reachabilityEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchReachabilityEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_history_24,
                title = stringResource(R.string.pixel_search_usage_ranking_title),
                description = stringResource(R.string.pixel_search_usage_ranking_desc),
                isChecked = usageRankingEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchUsageRankingEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_auto_awesome_24,
                title = stringResource(R.string.pixel_search_typo_tolerance_title),
                description = stringResource(R.string.pixel_search_typo_tolerance_desc),
                isChecked = typoToleranceEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchTypoToleranceEnabled(checked)
                },
            )

            IconToggleItem(
                iconRes = R.drawable.rounded_visibility_off_24,
                title = "Hide results until typing",
                description = "Open with an empty list so the first screen stays uncluttered",
                isChecked = hideInitialResults,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchHideInitialResultsEnabled(checked)
                },
            )

            val resultLimitOptions = remember { listOf("3", "5", "8", "10", "15", "20", "All") }
            ConfigPickerItem(
                iconRes = R.drawable.rounded_view_headline_24,
                title = "Results per section",
                description = "Limit how many results each section shows on screen",
                selectedValue = if (maxResults <= 0) "All" else maxResults.toString(),
            ) {
                resultLimitOptions.forEach { label ->
                    SegmentedDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            viewModel.setPixelSearchMaxResults(if (label == "All") 0 else label.toInt())
                        },
                    )
                }
            }

            val bubblesWebEnabled = viewModel.pixelSearchBubblesWeb.value
            val searchEngine = viewModel.pixelSearchEngine.value
            val searchEngineOptions = remember {
                listOf(
                    "Google",
                    "DuckDuckGo",
                    "Brave Search",
                    "Startpage",
                    "Kagi",
                    "Ecosia",
                    "Bing",
                )
            }

            IconToggleItem(
                iconRes = R.drawable.rounded_bubble_24,
                title = stringResource(R.string.pixel_search_use_bubbles_title),
                description = stringResource(R.string.pixel_search_use_bubbles_desc),
                isChecked = bubblesWebEnabled,
                onCheckedChange = { checked ->
                    HapticUtil.performVirtualKeyHaptic(view)
                    viewModel.setPixelSearchBubblesWebEnabled(checked)
                },
            )

            if (bubblesWebEnabled) {
                ConfigPickerItem(
                    iconRes = R.drawable.rounded_search_24,
                    title = stringResource(R.string.pixel_search_engine_title),
                    description = stringResource(R.string.pixel_search_engine_desc),
                    selectedValue = searchEngine,
                ) {
                    searchEngineOptions.forEach { engine ->
                        SegmentedDropdownMenuItem(
                            text = { Text(engine) },
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                viewModel.setPixelSearchEngine(engine)
                            },
                        )
                    }
                }
            }
        }
    }

    if (requestingPermissionKey != null) {
        val key = requestingPermissionKey!!
        var isPermGranted by remember(key) {
            mutableStateOf(
                when (key) {
                    "MEDIA" -> PermissionUtils.hasMediaPermissions(context)
                    "ALL_FILES" -> PermissionUtils.hasManageExternalStoragePermission(context) || PermissionUtils.hasStoragePermission(context) || viewModel.isStoragePermissionGranted.value
                    "READ_CONTACTS" -> androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    else -> false
                }
            )
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner, key) {
            val observer =
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.check(context)
                        val granted = when (key) {
                            "MEDIA" -> PermissionUtils.hasMediaPermissions(context)
                            "ALL_FILES" -> PermissionUtils.hasManageExternalStoragePermission(context) || PermissionUtils.hasStoragePermission(context) || viewModel.isStoragePermissionGranted.value
                            "READ_CONTACTS" -> androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            else -> false
                        }
                        isPermGranted = granted
                        if (key == "MEDIA") {
                            viewModel.setPixelSearchResultMediaEnabled(granted)
                            if (granted) requestingPermissionKey = null
                        } else if (key == "ALL_FILES") {
                            viewModel.setPixelSearchResultFilesEnabled(granted)
                            if (granted) requestingPermissionKey = null
                        } else if (key == "READ_CONTACTS") {
                            viewModel.setPixelSearchResultContactsEnabled(granted)
                            if (granted) requestingPermissionKey = null
                        }
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val permItem =
            remember(key, isPermGranted, context, viewModel) {
                if (key == "MEDIA") {
                    com.sameerasw.essentials.ui.core.sheets.PermissionItem(
                        iconRes = R.drawable.rounded_image_24,
                        title = R.string.perm_files_media_title,
                        description = R.string.perm_files_media_desc,
                        dependentFeatures = listOf(R.string.pixel_search_results_media_title),
                        actionLabel = if (isPermGranted) R.string.perm_action_granted else R.string.perm_action_grant,
                        action = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                mediaPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO,
                                    ),
                                )
                            } else {
                                mediaPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                )
                            }
                        },
                        isGranted = isPermGranted,
                    )
                } else if (key == "ALL_FILES") {
                    com.sameerasw.essentials.ui.core.sheets.PermissionItem(
                        iconRes = R.drawable.rounded_folder_24,
                        title = R.string.perm_all_files_title,
                        description = R.string.perm_all_files_desc,
                        dependentFeatures = listOf(R.string.pixel_search_results_files_title),
                        actionLabel = if (isPermGranted) R.string.perm_action_granted else R.string.perm_action_grant,
                        action = {
                            PermissionUtils.openManageExternalStorageSettings(context)
                        },
                        isGranted = isPermGranted,
                    )
                } else if (key == "READ_CONTACTS") {
                    com.sameerasw.essentials.ui.core.sheets.PermissionItem(
                        iconRes = R.drawable.rounded_call_24,
                        title = R.string.perm_contacts_title,
                        description = R.string.perm_contacts_desc,
                        dependentFeatures = listOf(R.string.pixel_search_results_contacts_title),
                        actionLabel = if (isPermGranted) R.string.perm_action_granted else R.string.perm_action_grant,
                        action = {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                        isGranted = isPermGranted,
                    )
                } else {
                    PermissionUIHelper.getPermissionItem(
                        key,
                        context,
                        viewModel,
                        activity = context as? Activity,
                    )
                }
            }
        if (permItem != null) {
            PermissionsBottomSheet(
                onDismissRequest = {
                    val currentKey = requestingPermissionKey
                    requestingPermissionKey = null
                    if (currentKey == "READ_CONTACTS") {
                        val hasContactsPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        viewModel.setPixelSearchResultContactsEnabled(hasContactsPerm)
                    } else if (currentKey == "ALL_FILES") {
                        val hasFilesPerm = PermissionUtils.hasManageExternalStoragePermission(context) ||
                            PermissionUtils.hasStoragePermission(context) ||
                            viewModel.isStoragePermissionGranted.value
                        viewModel.setPixelSearchResultFilesEnabled(hasFilesPerm)
                    } else if (currentKey == "MEDIA") {
                        val hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                        viewModel.setPixelSearchResultMediaEnabled(hasMediaPerm)
                    }
                },
                featureTitle = stringResource(R.string.pixel_search_results_title),
                permissions = listOf(permItem),
            )
        }
    }
}
