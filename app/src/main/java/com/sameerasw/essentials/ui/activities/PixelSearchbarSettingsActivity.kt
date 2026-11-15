/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchbarSettingsActivity.kt
 * Description: Activity component for PixelSearchbarSettingsActivity.kt.
 */

package com.sameerasw.essentials.ui.activities

import com.sameerasw.essentials.translation.TranslatableText
import com.sameerasw.essentials.ui.navigation.ProvideBottomBar
import com.sameerasw.essentials.ui.navigation.BottomBarSpec
import com.sameerasw.essentials.ui.navigation.AppRoute
import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.Feature
import com.sameerasw.essentials.ui.components.animations.LottieFeatureAnimation
import com.sameerasw.essentials.ui.components.menus.SegmentedDropdownMenuItem
import com.sameerasw.essentials.ui.core.cards.ConfigPickerItem
import com.sameerasw.essentials.ui.core.cards.IconToggleItem
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.core.pickers.SegmentedPicker
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
    remember(context) { viewModel.check(context) }

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
                permissionKeys = listOf("WRITE_SECURE_SETTINGS"),
                showToggle = true,
                hasMoreSettings = true,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = viewModel.isPixelSearchbarEnabled.value

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {
                    viewModel.setPixelSearchbarEnabled(enabled, context)
                }
            }
        }

    val isBlurEnabled by viewModel.isBlurEnabled

    EssentialsTheme(pitchBlackTheme = isPitchBlackThemeEnabled) {
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
    val view = LocalView.current
    val isEnabled = viewModel.isPixelSearchbarEnabled.value
    var showPermissionSheet by remember { mutableStateOf(false) }
    var requestingPermissionKey by remember { mutableStateOf<String?>(null) }
    val currentType = viewModel.pixelSearchbarType.value

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

    val options = listOf("searchbar", "empty", "date")
    val labels =
        mapOf(
            "searchbar" to stringResource(R.string.pixel_searchbar_style_searchbar),
            "empty" to stringResource(R.string.pixel_searchbar_style_empty),
            "date" to stringResource(R.string.pixel_searchbar_style_date),
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundedCardContainer {
            IconToggleItem(
                iconRes = R.drawable.rounded_search_24,
                title = stringResource(R.string.feat_pixel_searchbar_title),
                description = stringResource(R.string.feat_pixel_searchbar_desc),
                isChecked = isEnabled,
                onCheckedChange = { enabled ->
                    if (viewModel.isWriteSecureSettingsEnabled.value ||
                        viewModel.isShizukuPermissionGranted.value ||
                        viewModel.isRootPermissionGranted.value
                    ) {
                        viewModel.setPixelSearchbarEnabled(enabled, context)
                    } else {
                        showPermissionSheet = true
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = isEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TranslatableText(
                    stringResId = R.string.label_replace_with,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RoundedCardContainer {
                    SegmentedPicker(
                        items = options,
                        selectedItem = currentType,
                        onItemSelected = { type ->
                            HapticUtil.performVirtualKeyHaptic(view)
                            viewModel.setPixelSearchbarType(type, context)
                        },
                        labelProvider = { labels[it] ?: it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AnimatedVisibility(
                    visible = currentType == "date",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val currentDateFormat = viewModel.pixelSearchbarDateFormat.value
                    val dateFormats =
                        listOf(
                            "EEEE, MMMM d",
                            "EEEE, MMM d",
                            "EEE, MMM d",
                            "EEEE, d MMMM",
                            "d MMMM",
                            "MMMM d",
                            "EEE, d MMM",
                            "yyyy-MM-dd",
                            "dd/MM/yyyy",
                        )
                    val currentDate = remember { java.util.Date() }
                    val googleSansFlexRound =
                        remember { FontFamily(Font(R.font.google_sans_flex_round)) }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RoundedCardContainer {
                            IconToggleItem(
                                iconRes = R.drawable.rounded_rounded_corner_24,
                                title = stringResource(R.string.pixel_searchbar_background_pill_title),
                                description = stringResource(R.string.pixel_searchbar_background_pill_desc),
                                isChecked = viewModel.pixelSearchbarBackgroundPill.value,
                                onCheckedChange = { enabled ->
                                    viewModel.setPixelSearchbarBackgroundPill(enabled, context)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TranslatableText(
                            stringResId = R.string.pixel_searchbar_date_format,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        RoundedCardContainer(spacing = 2.dp) {
                            dateFormats.forEach { format ->
                                val isSelected = currentDateFormat == format
                                val formattedDate =
                                    remember(format, currentDate) {
                                        try {
                                            java.text
                                                .SimpleDateFormat(
                                                    format,
                                                    java.util.Locale.getDefault(),
                                                ).format(currentDate)
                                        } catch (e: Exception) {
                                            format
                                        }
                                    }

                                ListItem(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        viewModel.setPixelSearchbarDateFormat(format, context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingContent = {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                HapticUtil.performVirtualKeyHaptic(view)
                                                viewModel.setPixelSearchbarDateFormat(
                                                    format,
                                                    context,
                                                )
                                            },
                                        )
                                    },
                                    colors =
                                        ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                                        ),
                                ) {
                                    Text(
                                        text = formattedDate,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = googleSansFlexRound,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TranslatableText(
                    stringResId = R.string.pixel_searchbar_tap_action,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RoundedCardContainer {
                    val tapActionEnabled = viewModel.pixelSearchbarTapActionEnabled.value
                    ListItem(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_rocket_launch_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint =
                                    if (tapActionEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.38f,
                                        )
                                    },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = tapActionEnabled,
                                onCheckedChange = { enabled ->
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    viewModel.setPixelSearchbarTapActionEnabled(enabled, context)
                                },
                            )
                        },
                        supportingContent = {
                            TranslatableText(
                                stringResId = R.string.pixel_searchbar_tap_action_enabled_desc,
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (tapActionEnabled) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.38f,
                                        )
                                    },
                            )
                        },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceBright,
                            ),
                    ) {
                        TranslatableText(
                            stringResId = R.string.pixel_searchbar_tap_action_enabled,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (tapActionEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.38f,
                                    )
                                },
                        )
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
        }
    }

    if (showPermissionSheet) {
        val permissionItem =
            remember(context, viewModel) {
                PermissionUIHelper.getPermissionItem(
                    "WRITE_SECURE_SETTINGS",
                    context,
                    viewModel,
                )
            }
        if (permissionItem != null) {
            PermissionsBottomSheet(
                onDismissRequest = { showPermissionSheet = false },
                featureTitle = "Pixel Searchbar",
                permissions = listOf(permissionItem),
            )
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
