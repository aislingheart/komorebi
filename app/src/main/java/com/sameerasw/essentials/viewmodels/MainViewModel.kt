/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Viewmodels
 * File: MainViewModel.kt
 * Description: Component file for MainViewModel.kt.
 */

package com.sameerasw.essentials.viewmodels

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sameerasw.essentials.EssentialsApp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.data.repository.UpdateRepository
import com.sameerasw.essentials.domain.model.AppIcon
import com.sameerasw.essentials.domain.model.AppStandbyInfo
import com.sameerasw.essentials.domain.model.BackGestureAppConfig
import com.sameerasw.essentials.domain.model.SearchableItem
import com.sameerasw.essentials.domain.model.UpdateInfo
import com.sameerasw.essentials.domain.registry.SearchRegistry
import com.sameerasw.essentials.utils.AppIconUtil
import com.sameerasw.essentials.utils.DeviceUtils
import com.sameerasw.essentials.utils.LogManager
import com.sameerasw.essentials.utils.PermissionGrantUtil
import com.sameerasw.essentials.utils.PermissionUtils
import com.sameerasw.essentials.utils.PrivilegedMode
import com.sameerasw.essentials.utils.RootUtils
import com.sameerasw.essentials.utils.ShellUtils
import com.sameerasw.essentials.utils.ShizukuUtils
import com.sameerasw.essentials.utils.UpdateNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel : ViewModel() {
    val isAccessibilityEnabled = mutableStateOf(false)
    val isStatusBarIconControlEnabled = mutableStateOf(false)
    val isWriteSecureSettingsEnabled = mutableStateOf(false)
    val isReadPhoneStateEnabled = mutableStateOf(false)
    val isPostNotificationsEnabled = mutableStateOf(false)
    val isShizukuPermissionGranted = mutableStateOf(false)
    val isShizukuAvailable = mutableStateOf(false)
    val pinnedFeatureKeys = mutableStateOf<List<String>>(emptyList())
    val isNotificationListenerEnabled = mutableStateOf(false)
    val isOverlayPermissionGranted = mutableStateOf(false)
    val defaultTab = mutableStateOf(com.sameerasw.essentials.domain.DIYTabs.ESSENTIALS)
    val selectedAppIcon = mutableStateOf(AppIcon.DEFAULT)
    val isDefaultBrowserSet = mutableStateOf(false)
    val isLockscreenShortcutsEnabled = mutableStateOf(false)
    val isDebugUiMarginOverlay = mutableStateOf(false)
    val isLocationPermissionGranted = mutableStateOf(false)
    val isBackgroundLocationPermissionGranted = mutableStateOf(false)
    val isBluetoothPermissionGranted = mutableStateOf(false)
    val isStoragePermissionGranted = mutableStateOf(false)
    val isUsageStatsPermissionGranted = mutableStateOf(false)
    val appLanguage = mutableStateOf("en")
    val isCalendarSyncEnabled = mutableStateOf(false)
    val isCalendarSyncPeriodicEnabled = mutableStateOf(false)
    val isFaceUnlockBrightnessEnabled = mutableStateOf(false)
    val isAutoAccessibilityEnabled = mutableStateOf(false)
    val isOnboardingCompleted =
        mutableStateOf(true) // Default to true so it doesn't flash on first check if not loaded
    val isHideGestureBarEnabled = mutableStateOf(false)
    val isHideGestureBarOnLauncherEnabled = mutableStateOf(false)
    val isCircleToSearchGestureEnabled = mutableStateOf(false)
    val backGestureLeftScale = mutableFloatStateOf(1f)
    val backGestureRightScale = mutableFloatStateOf(1f)
    val backGestureAppConfigs = mutableStateOf<List<BackGestureAppConfig>>(emptyList())
    val isDisableRotationSuggestionEnabled = mutableStateOf(false)
    val isAllowOverlaysInSettingsEnabled = mutableStateOf(false)
    val isTransparentNavigationBarEnabled = mutableStateOf(false)
    val isSplitScreenGestureEnabled = mutableStateOf(false)
    val isPreferGpuComposingEnabled = mutableStateOf(false)
    val standbyAppsList = mutableStateOf<List<AppStandbyInfo>>(emptyList())
    val isStandbyAppsLoading = mutableStateOf(false)
    val isPixelSearchbarEnabled = mutableStateOf(false)
    val pixelSearchbarType = mutableStateOf("empty")
    val pixelSearchbarDateFormat = mutableStateOf("EEEE, MMMM d")
    val pixelSearchbarBackgroundPill = mutableStateOf(false)
    val pixelSearchbarTapActionEnabled = mutableStateOf(true)
    val pixelSearchbarMusicTitle = mutableStateOf("")
    val pixelSearchbarMusicArtist = mutableStateOf("")
    val pixelSearchbarMusicPackage = mutableStateOf("")
    val lockScreenClockId = mutableStateOf<String?>(null)
    val shizukuAuthToken = mutableStateOf("")
    data class CalendarAccount(
        val id: Long,
        val name: String,
        val accountName: String,
        val isSelected: Boolean,
    )

    val availableCalendars = mutableStateListOf<CalendarAccount>()
    val selectedCalendarIds = mutableStateOf(setOf<String>())
    val isDeviceAdminEnabled = mutableStateOf(false)
    val isDeveloperModeEnabled = mutableStateOf(false)
    val isNotificationPolicyAccessGranted = mutableStateOf(false)
    val isUseUsageAccess = mutableStateOf(false)
    val isHiddenDebuggingSupport = mutableStateOf(false)
    val searchQuery = mutableStateOf("")
    val searchResults = mutableStateOf<List<SearchableItem>>(emptyList())
    val isSearching = mutableStateOf(false)
    val recentSearches = mutableStateOf<List<SearchableItem>>(emptyList())
    private var searchJob: Job? = null
    val updateInfo = mutableStateOf<UpdateInfo?>(null)
    val isUpdateAvailable = mutableStateOf(false)
    val isCheckingUpdate = mutableStateOf(false)
    val updateCheckError = mutableStateOf<String?>(null)
    val updateCheckCompleted = mutableStateOf(false)
    val isAutoUpdateEnabled = mutableStateOf(true)
    val isUpdateNotificationEnabled = mutableStateOf(true)
    val isPreReleaseCheckEnabled = mutableStateOf(false)
    val isRootEnabled = mutableStateOf(false)
    val privilegedMode = mutableStateOf(PrivilegedMode.AUTO)
    val usesAuthToken = mutableStateOf(true)
    val isRootAvailable = mutableStateOf(false)
    val isRootPermissionGranted = mutableStateOf(false)
    val isPitchBlackThemeEnabled = mutableStateOf(false)
    val isEnableUnsupportedFeatures = mutableStateOf(false)
    val isForceFlashlightControls = mutableStateOf(false)
    val isShowLegacyFeatures = mutableStateOf(true)
    val isBlurEnabled = mutableStateOf(true)
    val isBlurSettingEnabled = mutableStateOf(true)
    val isRippleEnabled = mutableStateOf(true)
    val isRippleSettingEnabled = mutableStateOf(true)
    val isMotionBlurEnabled = mutableStateOf(false)
    val isMotionBlurSettingEnabled = mutableStateOf(false)
    val motionBlurScale = mutableFloatStateOf(1.0f)
    val isOnlineHelpMediaEnabled = mutableStateOf(true)
    val isShakeForHelpEnabled = mutableStateOf(true)
    val sentryReportMode = mutableStateOf("auto")
    val isPowerSaveModeEnabled = mutableStateOf(false)
    private var powerSaveReceiver: BroadcastReceiver? = null
    val isKeyboardEnabled = mutableStateOf(false)
    val isKeyboardSelected = mutableStateOf(false)
    val isWriteSettingsEnabled = mutableStateOf(false)
    val isCalendarPermissionGranted = mutableStateOf(false)
    val isBubblePermissionGranted = mutableStateOf(false)
    val isSensitiveNotificationsGranted = mutableStateOf(true)
    val isLocalNetworkGranted = mutableStateOf(true)
    val fontScale = mutableFloatStateOf(1.0f)
    val fontWeight = mutableIntStateOf(0)
    val animatorDurationScale = mutableFloatStateOf(1.0f)
    val transitionAnimationScale = mutableFloatStateOf(1.0f)
    val windowAnimationScale = mutableFloatStateOf(1.0f)
    val smallestWidth = mutableIntStateOf(360)
    private var lastUpdateCheckTime: Long = 0
    lateinit var settingsRepository: SettingsRepository
    private lateinit var updateRepository: UpdateRepository
    private var appContext: Context? = null
    private val contentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(
                selfChange: Boolean,
                uri: Uri?,
            ) {
                uri?.let {
                    when (it) {
                        Settings.System.getUriFor(Settings.System.FONT_SCALE) -> {
                            fontScale.floatValue = settingsRepository.getFontScale()
                        }

                        Settings.Secure.getUriFor("font_weight_adjustment") -> {
                            fontWeight.intValue = settingsRepository.getFontWeight()
                        }

                        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE) -> {
                            animatorDurationScale.floatValue =
                                settingsRepository.getAnimationScale(Settings.Global.ANIMATOR_DURATION_SCALE)
                        }

                        Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE) -> {
                            transitionAnimationScale.floatValue =
                                settingsRepository.getAnimationScale(Settings.Global.TRANSITION_ANIMATION_SCALE)
                        }

                        Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE) -> {
                            windowAnimationScale.floatValue =
                                settingsRepository.getAnimationScale(Settings.Global.WINDOW_ANIMATION_SCALE)
                        }

                        Settings.Secure.getUriFor("display_density_forced") -> {
                            smallestWidth.intValue = settingsRepository.getSmallestWidth()
                        }
                    }
                }
            }
        }
    private val preferenceChangeListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener

            when (key.substringBefore('@')) {
                SettingsRepository.KEY_HIDE_GESTURE_BAR_ENABLED -> {
                    isHideGestureBarEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_CIRCLE_TO_SEARCH_GESTURE_ENABLED -> {
                    isCircleToSearchGestureEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_HIDE_GESTURE_BAR_ON_LAUNCHER_ENABLED -> {
                    isHideGestureBarOnLauncherEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_DISABLE_ROTATION_SUGGESTION -> {
                    isDisableRotationSuggestionEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_ALLOW_OVERLAYS_IN_SETTINGS -> {
                    isAllowOverlaysInSettingsEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_SPLIT_SCREEN_GESTURE -> {
                    isSplitScreenGestureEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_TRANSPARENT_NAVIGATION_BAR -> {
                    isTransparentNavigationBarEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_PREFER_GPU_COMPOSING -> {
                    isPreferGpuComposingEnabled.value =
                        settingsRepository.getBoolean(key, false)
                }

                SettingsRepository.KEY_PIXEL_SEARCHBAR -> {
                    isPixelSearchbarEnabled.value =
                        settingsRepository.getBoolean(key, false)
                    appContext?.let {
                        applyPixelSearchbarSetting(
                            it,
                            isPixelSearchbarEnabled.value,
                        )
                    }
                }
            }
        }
    fun setSentryReportMode(
        mode: String,
        context: Context,
    ) {
        sentryReportMode.value = mode
        settingsRepository.putString(SettingsRepository.KEY_SENTRY_REPORT_MODE, mode)
    }
    fun setAppLanguage(languageCode: String) {
        appLanguage.value = languageCode
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
    fun setShizukuAuthToken(token: String) {
        shizukuAuthToken.value = token
        settingsRepository.setShizukuAuthToken(token)
    }
    fun check(context: Context) {
        appContext = context.applicationContext
        settingsRepository = SettingsRepository(context)
        updateRepository = UpdateRepository()

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (!currentLocales.isEmpty) {
            val locale = currentLocales.get(0)
            val langTag = locale?.toLanguageTag() ?: "en"
            appLanguage.value =
                when {
                    langTag.startsWith("pt-BR") -> "pt-BR"
                    langTag.startsWith("pt-PT") -> "pt-PT"
                    langTag.startsWith("pt") -> "pt-BR"
                    else -> locale?.language ?: "en"
                }
        } else {
            appLanguage.value = "en"
        }

        isAccessibilityEnabled.value = PermissionUtils.isAccessibilityServiceEnabled(context)
        isWriteSecureSettingsEnabled.value = PermissionUtils.canWriteSecureSettings(context)
        isBubblePermissionGranted.value = PermissionUtils.hasBubblePermission(context)
        isSensitiveNotificationsGranted.value = PermissionGrantUtil.hasSensitiveNotifications(context)
        isLocalNetworkGranted.value = PermissionUtils.hasLocalNetworkPermission(context)
        isShizukuAvailable.value = ShizukuUtils.isShizukuAvailable()
        isShizukuPermissionGranted.value = ShizukuUtils.hasPermission()
        if (cachedUpdateInfo != null) {
            isUpdateAvailable.value = cachedIsUpdateAvailable
            updateInfo.value = cachedUpdateInfo
        }
        if (settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_UPDATE_ENABLED, true)) {
            com.sameerasw.essentials.utils.ServiceUtils.schedulePeriodicAppUpdateCheck(context)
        }
        isAutoAccessibilityEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_ACCESSIBILITY_ENABLED)
        isHideGestureBarEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_HIDE_GESTURE_BAR_ENABLED, false)
        isCircleToSearchGestureEnabled.value =
            settingsRepository.getBoolean(
                SettingsRepository.KEY_CIRCLE_TO_SEARCH_GESTURE_ENABLED,
                false,
            )
        isHideGestureBarOnLauncherEnabled.value =
            settingsRepository.getBoolean(
                SettingsRepository.KEY_HIDE_GESTURE_BAR_ON_LAUNCHER_ENABLED,
                false,
            )
        shizukuAuthToken.value = settingsRepository.getShizukuAuthToken()
        isDisableRotationSuggestionEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_DISABLE_ROTATION_SUGGESTION, false)
        isAllowOverlaysInSettingsEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_ALLOW_OVERLAYS_IN_SETTINGS, false)
        isTransparentNavigationBarEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_TRANSPARENT_NAVIGATION_BAR, false)
        isSplitScreenGestureEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_SPLIT_SCREEN_GESTURE, false)
        isPreferGpuComposingEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_PREFER_GPU_COMPOSING, false)
        isPixelSearchbarEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_PIXEL_SEARCHBAR, false)
        pixelSearchbarType.value = settingsRepository.getPixelSearchbarType()
        pixelSearchbarDateFormat.value = settingsRepository.getPixelSearchbarDateFormat()
        pixelSearchbarBackgroundPill.value = settingsRepository.getPixelSearchbarBackgroundPill()
        pixelSearchbarTapActionEnabled.value = settingsRepository.getPixelSearchbarTapActionEnabled()
        pixelSearchbarMusicTitle.value = settingsRepository.getPixelSearchbarMusicTitle()
        pixelSearchbarMusicArtist.value = settingsRepository.getPixelSearchbarMusicArtist()
        pixelSearchbarMusicPackage.value = settingsRepository.getPixelSearchbarMusicPackage()
        if (pixelSearchbarType.value == "music") {
            updateMediaFromActiveSession(context)
        }
        lockScreenClockId.value = null
        recentSearches.value = settingsRepository.getRecentSearches()

        isReadPhoneStateEnabled.value = PermissionUtils.hasReadPhoneStatePermission(context)
        isPostNotificationsEnabled.value =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        isNotificationListenerEnabled.value =
            PermissionUtils.hasNotificationListenerPermission(context)
        isOverlayPermissionGranted.value = PermissionUtils.canDrawOverlays(context)
        isDefaultBrowserSet.value = PermissionUtils.isDefaultBrowser(context)
        isLocationPermissionGranted.value = PermissionUtils.hasLocationPermission(context)
        isBackgroundLocationPermissionGranted.value =
            PermissionUtils.hasBackgroundLocationPermission(context)
        isKeyboardEnabled.value = PermissionUtils.isKeyboardEnabled(context)
        isKeyboardSelected.value = PermissionUtils.isKeyboardSelected(context)
        isWriteSettingsEnabled.value = PermissionUtils.canWriteSystemSettings(context)
        isNotificationPolicyAccessGranted.value =
            PermissionUtils.hasNotificationPolicyAccess(context)
        isCalendarPermissionGranted.value = PermissionUtils.hasReadCalendarPermission(context)
        isUsageStatsPermissionGranted.value = PermissionUtils.hasUsageStatsPermission(context)
        isBluetoothPermissionGranted.value = PermissionUtils.hasBluetoothPermission(context)
        isStoragePermissionGranted.value = PermissionUtils.hasStoragePermission(context)

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.FONT_SCALE),
            false,
            contentObserver,
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("font_weight_adjustment"),
            false,
            contentObserver,
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            contentObserver,
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            contentObserver,
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE),
            false,
            contentObserver,
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("display_density_forced"),
            false,
            contentObserver,
        )

        isPowerSaveModeEnabled.value = DeviceUtils.isPowerSaveMode(context)
        updateBlurState(context)
        updateRippleState(context)
        updateMotionBlurState(context)

        if (powerSaveReceiver == null) {
            powerSaveReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                            context?.let {
                                isPowerSaveModeEnabled.value = DeviceUtils.isPowerSaveMode(it)
                                updateBlurState(it)
                                updateRippleState(it)
                                updateMotionBlurState(it)
                            }
                        }
                    }
                }
            context.applicationContext.registerReceiver(
                powerSaveReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            )
        }

        settingsRepository.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        isStatusBarIconControlEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_STATUS_BAR_ICON_CONTROL_ENABLED)
        fontScale.floatValue = settingsRepository.getFontScale()
        fontWeight.intValue = settingsRepository.getFontWeight()
        animatorDurationScale.floatValue =
            settingsRepository.getAnimationScale(Settings.Global.ANIMATOR_DURATION_SCALE)
        transitionAnimationScale.floatValue =
            settingsRepository.getAnimationScale(Settings.Global.TRANSITION_ANIMATION_SCALE)
        windowAnimationScale.floatValue =
            settingsRepository.getAnimationScale(Settings.Global.WINDOW_ANIMATION_SCALE)
        smallestWidth.intValue = settingsRepository.getSmallestWidth()

        isUseUsageAccess.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_USE_USAGE_ACCESS)
        isHiddenDebuggingSupport.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_HIDDEN_DEBUGGING_SUPPORT)
        isOnboardingCompleted.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_ONBOARDING_COMPLETED, false)

        refreshPrivilegedMode()

        if (isRootEnabled.value) {
            isRootAvailable.value = RootUtils.isRootAvailable()
            isRootPermissionGranted.value = RootUtils.isRootPermissionGranted()
        } else {
            isRootAvailable.value = false
            isRootPermissionGranted.value = false
        }

        defaultTab.value = settingsRepository.getDIYTab()
        selectedAppIcon.value = settingsRepository.getAppIcon()
        isOnlineHelpMediaEnabled.value = settingsRepository.isOnlineHelpMediaEnabled()
        isShakeForHelpEnabled.value = settingsRepository.isShakeForHelpEnabled()
        sentryReportMode.value =
            settingsRepository.getString(SettingsRepository.KEY_SENTRY_REPORT_MODE, "auto")
                ?: "auto"

        isLockscreenShortcutsEnabled.value = settingsRepository.isLockscreenShortcutsEnabled()
        isPitchBlackThemeEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_PITCH_BLACK_THEME_ENABLED)
        isEnableUnsupportedFeatures.value = settingsRepository.isEnableUnsupportedFeatures()
        isForceFlashlightControls.value = settingsRepository.isForceFlashlightControls()
        isShowLegacyFeatures.value = settingsRepository.isShowLegacyFeatures()
        isDeviceAdminEnabled.value = PermissionUtils.isDeviceAdminActive(context)
        isAutoUpdateEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_UPDATE_ENABLED, true)
        isUpdateNotificationEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_UPDATE_NOTIFICATION_ENABLED, true)
        lastUpdateCheckTime =
            settingsRepository.getLong(SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME)
        isDeveloperModeEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_DEVELOPER_MODE_ENABLED)
        isPreReleaseCheckEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED)
        pinnedFeatureKeys.value = settingsRepository.getPinnedFeatures()
        isCalendarSyncEnabled.value =
            settingsRepository.getBoolean(SettingsRepository.KEY_CALENDAR_SYNC_ENABLED, false)
        isCalendarSyncPeriodicEnabled.value = settingsRepository.isCalendarSyncPeriodicEnabled()
        selectedCalendarIds.value = settingsRepository.getCalendarSyncSelectedCalendars()
        pixelSearchResultApps.value = settingsRepository.isPixelSearchResultAppsEnabled()
        pixelSearchResultMedia.value = settingsRepository.isPixelSearchResultMediaEnabled()
        pixelSearchResultFiles.value = settingsRepository.isPixelSearchResultFilesEnabled()
        pixelSearchResultContacts.value = settingsRepository.isPixelSearchResultContactsEnabled()
        pixelSearchResultSettings.value = settingsRepository.isPixelSearchResultSettingsEnabled()
        pixelSearchResultShortcuts.value = settingsRepository.isPixelSearchResultShortcutsEnabled()
        pixelSearchResultWeb.value = settingsRepository.isPixelSearchResultWebEnabled()
        pixelSearchResultNotifications.value = settingsRepository.isPixelSearchResultNotificationsEnabled()
        pixelSearchBubblesWeb.value = settingsRepository.isPixelSearchBubblesWebEnabled()
        pixelSearchEngine.value = settingsRepository.getPixelSearchEngine()
        pixelSearchReachability.value = settingsRepository.isPixelSearchReachabilityEnabled()
        pixelSearchUsageRanking.value = settingsRepository.isPixelSearchUsageRankingEnabled()
        pixelSearchTypoTolerance.value = settingsRepository.isPixelSearchTypoToleranceEnabled()
        com.sameerasw.essentials.utils.FuzzySearchUtil.setTypoToleranceEnabled(pixelSearchTypoTolerance.value)
        pixelSearchMaxResults.value = settingsRepository.getPixelSearchMaxResults()
        pixelSearchHideInitialResults.value = settingsRepository.isPixelSearchHideInitialResultsEnabled()
        pixelSearchAssistantEnabled.value = settingsRepository.isPixelSearchAssistantEnabled()
        isFaceUnlockBrightnessEnabled.value = settingsRepository.isFaceUnlockBrightnessEnabled()
    }
    fun setEnableUnsupportedFeatures(
        enabled: Boolean,
        context: Context,
    ) {
        isEnableUnsupportedFeatures.value = enabled
        settingsRepository.setEnableUnsupportedFeatures(enabled)
        if (searchQuery.value.isNotBlank()) {
            onSearchQueryChanged(searchQuery.value, context)
        }
    }
    fun setForceFlashlightControls(enabled: Boolean) {
        isForceFlashlightControls.value = enabled
        settingsRepository.setForceFlashlightControls(enabled)
    }
    fun setShowLegacyFeatures(
        enabled: Boolean,
        context: Context,
    ) {
        isShowLegacyFeatures.value = enabled
        settingsRepository.setShowLegacyFeatures(enabled)
        if (searchQuery.value.isNotBlank()) {
            onSearchQueryChanged(searchQuery.value, context)
        }
    }
    fun onSearchQueryChanged(
        query: String,
        context: Context,
    ) {
        searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            searchResults.value = emptyList()
            isSearching.value = false
            return
        }

        isSearching.value = true
        searchJob =
            viewModelScope.launch(Dispatchers.Default) {
                delay(300)
                val results =
                    SearchRegistry.search(
                        context,
                        query,
                        isEnableUnsupportedFeatures.value,
                        isShowLegacyFeatures.value,
                    )
                withContext(Dispatchers.Main) {
                    searchResults.value = results
                    isSearching.value = false
                }
            }
    }
    fun addRecentSearch(item: SearchableItem) {
        val current = recentSearches.value.toMutableList()
        // Remove existing to move to top
        current.removeAll {
            it.title == item.title &&
                it.featureKey == item.featureKey &&
                it.targetSettingHighlightKey == item.targetSettingHighlightKey
        }
        current.add(0, item)
        // Limit to 10
        val limited = current.take(10)
        recentSearches.value = limited
        settingsRepository.saveRecentSearches(limited)
    }
    fun clearRecentSearches() {
        recentSearches.value = emptyList()
        settingsRepository.saveRecentSearches(emptyList())
    }
    fun togglePinFeature(featureId: String) {
        val current = pinnedFeatureKeys.value.toMutableList()
        if (current.contains(featureId)) {
            current.remove(featureId)
        } else {
            current.add(featureId) // Append at the end to keep order
        }
        pinnedFeatureKeys.value = current
        settingsRepository.savePinnedFeatures(current)

        appContext?.let { context ->
            com.sameerasw.essentials.utils.ShortcutUtil
                .updateLauncherDynamicShortcuts(context)
            val intent =
                Intent("com.sameerasw.essentials.action.FAVORITES_WIDGET_UPDATE").apply {
                    setPackage(context.packageName)
                }
            context.sendBroadcast(intent)
        }
    }
    fun updatePinnedFeatures(newOrder: List<String>) {
        pinnedFeatureKeys.value = newOrder
        settingsRepository.savePinnedFeatures(newOrder)

        appContext?.let { context ->
            com.sameerasw.essentials.utils.ShortcutUtil
                .updateLauncherDynamicShortcuts(context)
            val intent =
                Intent("com.sameerasw.essentials.action.FAVORITES_WIDGET_UPDATE").apply {
                    setPackage(context.packageName)
                }
            context.sendBroadcast(intent)
        }
    }
    fun setAutoUpdateEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isAutoUpdateEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_AUTO_UPDATE_ENABLED, enabled)
        if (enabled) {
            com.sameerasw.essentials.utils.ServiceUtils.schedulePeriodicAppUpdateCheck(context)
        } else {
            com.sameerasw.essentials.utils.ServiceUtils.cancelPeriodicAppUpdateCheck(context)
        }
    }
    fun setUpdateNotificationEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isUpdateNotificationEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_UPDATE_NOTIFICATION_ENABLED, enabled)
    }
    fun setPreReleaseCheckEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isPreReleaseCheckEnabled.value = enabled
        settingsRepository.putBooleanSync(SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED, enabled)
        // Enabling pre-releases automatically enables Developer Mode; disabling turns it off
        isDeveloperModeEnabled.value = enabled
        settingsRepository.putBooleanSync(SettingsRepository.KEY_DEVELOPER_MODE_ENABLED, enabled)
        // Clear cached update state so the next check immediately queries the selected channel
        cachedIsUpdateAvailable = false
        cachedUpdateInfo = null
        lastUpdateCheckTime = 0L
        settingsRepository.putLong(SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME, 0L)
    }
    fun setDeveloperModeEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isDeveloperModeEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_DEVELOPER_MODE_ENABLED, enabled)
    }
    fun setPrivilegedMode(
        mode: PrivilegedMode,
        context: Context,
    ) {
        settingsRepository.putString(SettingsRepository.KEY_PRIVILEGED_MODE, mode.key)
        settingsRepository.putBoolean(SettingsRepository.KEY_USE_ROOT, mode == PrivilegedMode.ROOT)
        refreshPrivilegedMode()
        check(context)
    }
    private fun refreshPrivilegedMode() {
        val ctx = EssentialsApp.context
        privilegedMode.value = ShellUtils.getSelectedMode(ctx)
        isRootEnabled.value = ShellUtils.isRootEnabled(ctx)
        usesAuthToken.value = ShellUtils.usesAuthToken(ctx)
    }
    fun setPitchBlackThemeEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isPitchBlackThemeEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_PITCH_BLACK_THEME_ENABLED, enabled)
    }
    fun setBlurEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        settingsRepository.putBoolean(SettingsRepository.KEY_USE_BLUR, enabled)
        updateBlurState(context)
    }
    fun setRippleEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        settingsRepository.putBoolean(SettingsRepository.KEY_USE_RIPPLE, enabled)
        updateRippleState(context)
    }
    fun setMotionBlurEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        settingsRepository.putBoolean(SettingsRepository.KEY_MOTION_BLUR, enabled)
        updateMotionBlurState(context)
    }
    fun setMotionBlurScale(
        scale: Float,
        context: Context? = null,
    ) {
        motionBlurScale.floatValue = scale
        settingsRepository.putFloat(SettingsRepository.KEY_MOTION_BLUR_SCALE, scale)
    }
    fun setOnlineHelpMediaEnabled(
        enabled: Boolean,
        context: Context? = null,
    ) {
        settingsRepository.setOnlineHelpMediaEnabled(enabled)
        isOnlineHelpMediaEnabled.value = enabled
    }
    fun setShakeForHelpEnabled(
        enabled: Boolean,
        context: Context? = null,
    ) {
        settingsRepository.setShakeForHelpEnabled(enabled)
        isShakeForHelpEnabled.value = enabled
    }
    private fun updateBlurState(context: Context) {
        val useBlurSetting = settingsRepository.getBoolean(SettingsRepository.KEY_USE_BLUR, true)
        val isProblematic = DeviceUtils.isBlurProblematicDevice()
        val isPowerSave = DeviceUtils.isPowerSaveMode(context)

        isBlurSettingEnabled.value = useBlurSetting
        isBlurEnabled.value = useBlurSetting && !isProblematic && !isPowerSave
    }
    private fun updateRippleState(context: Context) {
        val useRippleSetting = settingsRepository.getBoolean(SettingsRepository.KEY_USE_RIPPLE, true)
        val isPowerSave = DeviceUtils.isPowerSaveMode(context)

        isRippleSettingEnabled.value = useRippleSetting
        isRippleEnabled.value = useRippleSetting && !isPowerSave
    }
    private fun updateMotionBlurState(context: Context) {
        val useMotionBlurSetting = settingsRepository.getBoolean(SettingsRepository.KEY_MOTION_BLUR, false)
        val isProblematic = DeviceUtils.isBlurProblematicDevice()
        val isPowerSave = DeviceUtils.isPowerSaveMode(context)

        isMotionBlurSettingEnabled.value = useMotionBlurSetting
        isMotionBlurEnabled.value = useMotionBlurSetting && !isProblematic && !isPowerSave
        motionBlurScale.floatValue = settingsRepository.getFloat(SettingsRepository.KEY_MOTION_BLUR_SCALE, 1.0f)
    }
    fun checkForUpdates(
        context: Context,
        manual: Boolean = false,
    ) {
        if (isCheckingUpdate.value) return
        if (isUpdateAvailable.value && !manual) {
            updateCheckCompleted.value = true
            return
        }

        if (!manual) {
            if (!isAutoUpdateEnabled.value) {
                updateCheckCompleted.value = true
                return
            }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateCheckTime < 900000) {
                updateCheckCompleted.value = true
                return
            }
        }

        isCheckingUpdate.value = true
        updateInfo.value = null // Clear stale data before checking
        updateCheckError.value = null
        updateCheckCompleted.value = false
        viewModelScope.launch {
            try {
                val currentVersion =
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (e: Exception) {
                        "0.0"
                    } ?: "0.0"

                val updateInfoResult =
                    updateRepository
                        .checkForUpdatesWithStatus(
                            context,
                            isPreReleaseCheckEnabled.value,
                            currentVersion,
                        ).getOrNull()

                if (updateInfoResult != null) {
                    updateInfo.value = updateInfoResult
                    isUpdateAvailable.value = updateInfoResult.isUpdateAvailable
                    cachedIsUpdateAvailable = updateInfoResult.isUpdateAvailable
                    cachedUpdateInfo = updateInfoResult
                    updateCheckError.value = null

                    if (updateInfoResult.isUpdateAvailable && updateInfoResult.downloadUrl.isNotEmpty()) {
                        if (isUpdateNotificationEnabled.value) {
                            UpdateNotificationHelper.showUpdateNotification(
                                context,
                                updateInfoResult.versionName,
                                updateInfoResult.downloadUrl,
                            )
                        }
                    }

                    lastUpdateCheckTime = System.currentTimeMillis()
                    settingsRepository.putLong(
                        SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME,
                        lastUpdateCheckTime,
                    )
                } else {
                    updateCheckError.value = updateRepository.lastError
                        ?: "Couldn't check for updates. GitHub may be rate-limiting; try again later."
                }
                updateCheckCompleted.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                updateCheckError.value = updateRepository.lastError
                    ?: "Couldn't check for updates. GitHub may be rate-limiting; try again later."
                updateCheckCompleted.value = true
            } finally {
                isCheckingUpdate.value = false
            }
        }
    }
    fun requestDeviceAdmin(context: Context) {
        PermissionUtils.openDeviceAdminSettings(context)
    }
    fun requestReadPhoneStatePermission(activity: Activity) {
        com.sameerasw.essentials.utils.PermissionUtils.requestPermissionsSafely(
            activity,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            1005,
        )
    }
    fun setStatusBarIconControlEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isStatusBarIconControlEnabled.value = enabled
        settingsRepository.putBoolean(
            SettingsRepository.KEY_STATUS_BAR_ICON_CONTROL_ENABLED,
            enabled,
        )
    }
    fun loadStandbyApps(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isStandbyAppsLoading.value = true
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(intent, 0)

            val appList =
                apps
                    .mapNotNull { resolveInfo ->
                        val pkg = resolveInfo.activityInfo.packageName
                        if (pkg == context.packageName) return@mapNotNull null

                        val label = resolveInfo.loadLabel(pm).toString()
                        val icon = resolveInfo.loadIcon(pm)

                        var bucket = 10
                        val output = ShellUtils.runCommandWithOutput(context, "am get-standby-bucket $pkg")
                        if (output != null) {
                            val text = output.lowercase().trim()
                            bucket =
                                when {
                                    text.contains("restricted") || text.contains("45") -> 45
                                    text.contains("rare") || text.contains("40") -> 40
                                    text.contains("frequent") || text.contains("30") -> 30
                                    text.contains("working") || text.contains("20") -> 20
                                    text.contains("active") || text.contains("10") -> 10
                                    else -> 10
                                }
                        }

                        AppStandbyInfo(pkg, label, icon, bucket)
                    }.sortedBy { it.label.lowercase() }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                standbyAppsList.value = appList
                isStandbyAppsLoading.value = false
            }
        }
    }
    fun setAppStandbyBucket(
        packageName: String,
        targetBucket: Int,
        context: Context,
    ) {
        val currentList = standbyAppsList.value
        val updatedList =
            currentList.map { app ->
                if (app.packageName == packageName) {
                    app.copy(bucket = targetBucket)
                } else {
                    app
                }
            }
        standbyAppsList.value = updatedList

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bucketName =
                when (targetBucket) {
                    10 -> "active"
                    20 -> "working_set"
                    30 -> "frequent"
                    40 -> "rare"
                    45 -> "restricted"
                    else -> "active"
                }
            ShellUtils.runCommand(context, "am set-standby-bucket $packageName $bucketName")
        }
    }
    fun setAppsStandbyBucket(
        packageNames: Set<String>,
        targetBucket: Int,
        context: Context,
    ) {
        val currentList = standbyAppsList.value
        val updatedList =
            currentList.map { app ->
                if (packageNames.contains(app.packageName)) {
                    app.copy(bucket = targetBucket)
                } else {
                    app
                }
            }
        standbyAppsList.value = updatedList

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bucketName =
                when (targetBucket) {
                    10 -> "active"
                    20 -> "working_set"
                    30 -> "frequent"
                    40 -> "rare"
                    45 -> "restricted"
                    else -> "active"
                }
            packageNames.forEach { pkg ->
                ShellUtils.runCommand(context, "am set-standby-bucket $pkg $bucketName")
            }
        }
    }
    fun setPixelSearchbarEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isPixelSearchbarEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_PIXEL_SEARCHBAR, enabled)
        applyPixelSearchbarSetting(context, enabled)
    }
    private fun applyPixelSearchbarSetting(
        context: Context,
        enabled: Boolean,
    ) {
        writePixelSearchbarSetting(context, enabled)
        forceStopNexusLauncher()
    }
    private fun writePixelSearchbarSetting(
        context: Context,
        enabled: Boolean,
    ) {
        val value = if (enabled) context.packageName else null
        val key = "selected_search_engine"

        var success = false
        if (PermissionUtils.canWriteSecureSettings(context)) {
            try {
                success = Settings.Secure.putString(context.contentResolver, key, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!success) {
            val command =
                if (enabled) {
                    "settings put secure $key ${context.packageName}"
                } else {
                    "settings delete secure $key"
                }
            if (ShizukuUtils.hasPermission()) {
                ShizukuUtils.runCommand(command)
            } else if (RootUtils.isRootPermissionGranted()) {
                RootUtils.runCommand(command)
            }
        }
    }
    private fun forceStopNexusLauncher() {
        val forceStopCommand = "am force-stop com.google.android.apps.nexuslauncher"
        if (ShizukuUtils.hasPermission()) {
            ShizukuUtils.runCommand(forceStopCommand)
        } else if (RootUtils.isRootPermissionGranted()) {
            RootUtils.runCommand(forceStopCommand)
        }
    }
    fun setPixelSearchbarType(
        type: String,
        context: Context,
    ) {
        pixelSearchbarType.value = type
        settingsRepository.setPixelSearchbarType(type)
        if (type == "music") {
            updateMediaFromActiveSession(context)
        }
        updatePixelSearchbarWidget(context)

        if (isPixelSearchbarEnabled.value) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                writePixelSearchbarSetting(context, false)
                kotlinx.coroutines.delay(300)
                writePixelSearchbarSetting(context, true)
                forceStopNexusLauncher()
            }
        } else {
            forceStopNexusLauncher()
        }
    }
    fun setPixelSearchbarDateFormat(
        format: String,
        context: Context,
    ) {
        pixelSearchbarDateFormat.value = format
        settingsRepository.setPixelSearchbarDateFormat(format)
        updatePixelSearchbarWidget(context)

        // Force stop nexus launcher to apply setting
        val forceStopCommand = "am force-stop com.google.android.apps.nexuslauncher"
        if (ShizukuUtils.hasPermission()) {
            ShizukuUtils.runCommand(forceStopCommand)
        } else if (RootUtils.isRootPermissionGranted()) {
            RootUtils.runCommand(forceStopCommand)
        }
    }
    fun setPixelSearchbarBackgroundPill(
        enabled: Boolean,
        context: Context,
    ) {
        pixelSearchbarBackgroundPill.value = enabled
        settingsRepository.setPixelSearchbarBackgroundPill(enabled)
        updatePixelSearchbarWidget(context)

        // Force stop nexus launcher to apply setting
        val forceStopCommand = "am force-stop com.google.android.apps.nexuslauncher"
        if (ShizukuUtils.hasPermission()) {
            ShizukuUtils.runCommand(forceStopCommand)
        } else if (RootUtils.isRootPermissionGranted()) {
            RootUtils.runCommand(forceStopCommand)
        }
    }
    val pixelSearchResultApps = mutableStateOf(true)
    val pixelSearchResultMedia = mutableStateOf(false)
    val pixelSearchResultFiles = mutableStateOf(false)
    val pixelSearchResultContacts = mutableStateOf(true)
    val pixelSearchResultSettings = mutableStateOf(true)
    val pixelSearchResultShortcuts = mutableStateOf(true)
    val pixelSearchResultWeb = mutableStateOf(true)
    val pixelSearchResultNotifications = mutableStateOf(true)
    val pixelSearchBubblesWeb = mutableStateOf(false)
    val pixelSearchEngine = mutableStateOf("Google")
    val pixelSearchReachability = mutableStateOf(false)
    val pixelSearchUsageRanking = mutableStateOf(true)
    val pixelSearchTypoTolerance = mutableStateOf(true)
    val pixelSearchMaxResults = mutableStateOf(8)
    val pixelSearchHideInitialResults = mutableStateOf(false)
    val pixelSearchAssistantEnabled = mutableStateOf(false)
    fun setPixelSearchResultAppsEnabled(enabled: Boolean) {
        pixelSearchResultApps.value = enabled
        settingsRepository.setPixelSearchResultAppsEnabled(enabled)
    }
    fun setPixelSearchResultMediaEnabled(enabled: Boolean) {
        pixelSearchResultMedia.value = enabled
        settingsRepository.setPixelSearchResultMediaEnabled(enabled)
    }
    fun setPixelSearchResultFilesEnabled(enabled: Boolean) {
        pixelSearchResultFiles.value = enabled
        settingsRepository.setPixelSearchResultFilesEnabled(enabled)
    }
    fun setPixelSearchResultContactsEnabled(enabled: Boolean) {
        pixelSearchResultContacts.value = enabled
        settingsRepository.setPixelSearchResultContactsEnabled(enabled)
    }
    fun setPixelSearchResultSettingsEnabled(enabled: Boolean) {
        pixelSearchResultSettings.value = enabled
        settingsRepository.setPixelSearchResultSettingsEnabled(enabled)
    }
    fun setPixelSearchResultShortcutsEnabled(enabled: Boolean) {
        pixelSearchResultShortcuts.value = enabled
        settingsRepository.setPixelSearchResultShortcutsEnabled(enabled)
    }
    fun setPixelSearchResultWebEnabled(enabled: Boolean) {
        pixelSearchResultWeb.value = enabled
        settingsRepository.setPixelSearchResultWebEnabled(enabled)
    }
    fun setPixelSearchResultNotificationsEnabled(enabled: Boolean) {
        pixelSearchResultNotifications.value = enabled
        settingsRepository.setPixelSearchResultNotificationsEnabled(enabled)
    }
    fun setPixelSearchBubblesWebEnabled(enabled: Boolean) {
        pixelSearchBubblesWeb.value = enabled
        settingsRepository.setPixelSearchBubblesWebEnabled(enabled)
    }
    fun setPixelSearchEngine(engine: String) {
        pixelSearchEngine.value = engine
        settingsRepository.setPixelSearchEngine(engine)
    }
    fun setPixelSearchReachabilityEnabled(enabled: Boolean) {
        pixelSearchReachability.value = enabled
        settingsRepository.setPixelSearchReachabilityEnabled(enabled)
    }
    fun setPixelSearchUsageRankingEnabled(enabled: Boolean) {
        pixelSearchUsageRanking.value = enabled
        settingsRepository.setPixelSearchUsageRankingEnabled(enabled)
        com.sameerasw.essentials.utils.SearchUsageTracker.invalidateRankingSetting()
        com.sameerasw.essentials.utils.SearchContextRanker.invalidate()
    }
    fun setPixelSearchTypoToleranceEnabled(enabled: Boolean) {
        pixelSearchTypoTolerance.value = enabled
        settingsRepository.setPixelSearchTypoToleranceEnabled(enabled)
    }
    fun setPixelSearchMaxResults(limit: Int) {
        val safe = limit.coerceIn(0, 50)
        pixelSearchMaxResults.value = safe
        settingsRepository.setPixelSearchMaxResults(safe)
    }
    fun setPixelSearchHideInitialResultsEnabled(enabled: Boolean) {
        pixelSearchHideInitialResults.value = enabled
        settingsRepository.setPixelSearchHideInitialResultsEnabled(enabled)
    }
    fun setPixelSearchAssistantEnabled(enabled: Boolean) {
        pixelSearchAssistantEnabled.value = enabled
        settingsRepository.setPixelSearchAssistantEnabled(enabled)
    }
    fun setPixelSearchbarTapActionEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        pixelSearchbarTapActionEnabled.value = enabled
        settingsRepository.setPixelSearchbarTapActionEnabled(enabled)
        updatePixelSearchbarWidget(context)
    }
    fun updateMediaFromActiveSession(context: Context) {
        try {
            val manager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                    ?: return
            val componentName =
                android.content.ComponentName(
                    context,
                    "com.sameerasw.essentials.services.NotificationListener",
                )
            val sessions = manager.getActiveSessions(componentName)
            val activeSession =
                sessions
                    .sortedWith(
                        compareByDescending<android.media.session.MediaController> {
                            val state = it.playbackState?.state
                            state == android.media.session.PlaybackState.STATE_PLAYING ||
                                state == android.media.session.PlaybackState.STATE_BUFFERING
                        }.thenByDescending {
                            val state = it.playbackState?.state
                            state == android.media.session.PlaybackState.STATE_PAUSED
                        },
                    ).firstOrNull()

            if (activeSession != null) {
                val metadata = activeSession.metadata
                val title =
                    metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist =
                    metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val packageName = activeSession.packageName

                val artwork =
                    metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)

                val filesDirFile = File(context.filesDir, "music_artwork.png")
                if (artwork != null) {
                    try {
                        FileOutputStream(filesDirFile).use { out ->
                            artwork.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (_: Exception) {
                    }
                } else {
                    if (filesDirFile.exists()) filesDirFile.delete()
                }

                pixelSearchbarMusicTitle.value = title
                pixelSearchbarMusicArtist.value = artist
                pixelSearchbarMusicPackage.value = packageName
                settingsRepository.setPixelSearchbarMusicTitle(title)
                settingsRepository.setPixelSearchbarMusicArtist(artist)
                settingsRepository.setPixelSearchbarMusicPackage(packageName)
                settingsRepository.incrementPixelSearchbarWidgetRevision()
            }
        } catch (_: Exception) {
        }
    }
    fun updatePixelSearchbarWidget(context: Context) {
        // Widget support removed in search-core build
    }
    fun resetLockScreenClock(context: Context): Boolean {
        val key = "lock_screen_custom_clock_face"
        var success = false

        if (PermissionUtils.canWriteSecureSettings(context)) {
            try {
                success = Settings.Secure.putString(context.contentResolver, key, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!success) {
            val command = "settings delete secure $key"
            if (ShizukuUtils.hasPermission()) {
                ShizukuUtils.runCommand(command)
                success = true
            } else if (RootUtils.isRootPermissionGranted()) {
                RootUtils.runCommand(command)
                success = true
            }
        }

        if (success) {
            lockScreenClockId.value = null
        }
        return success
    }
    fun setLockscreenShortcutsEnabled(enabled: Boolean) {
        isLockscreenShortcutsEnabled.value = enabled
        settingsRepository.setLockscreenShortcutsEnabled(enabled)
    }
    fun setDebugUiMarginOverlay(enabled: Boolean) {
        isDebugUiMarginOverlay.value = enabled
        settingsRepository.setDebugUiMarginOverlayEnabled(enabled)
    }
    fun setHiddenDebuggingSupport(enabled: Boolean) {
        isHiddenDebuggingSupport.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_HIDDEN_DEBUGGING_SUPPORT, enabled)
    }
    fun setUseUsageAccess(
        enabled: Boolean,
        context: Context,
    ) {
        isUseUsageAccess.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_USE_USAGE_ACCESS, enabled)
    }
    fun restartSystemUI() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (ShizukuUtils.isShizukuAvailable() && ShizukuUtils.hasPermission()) {
                    ShizukuUtils.runCommand("am crash com.android.systemui")
                } else if (RootUtils.isRootAvailable() && RootUtils.isRootPermissionGranted()) {
                    RootUtils.runCommand("am crash com.android.systemui")
                }
            }
        }
    }
    fun setCalendarSyncEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isCalendarSyncEnabled.value = enabled
        settingsRepository.putBoolean(SettingsRepository.KEY_CALENDAR_SYNC_ENABLED, enabled)
        if (enabled) {
            com.sameerasw.essentials.services.CalendarSyncManager
                .forceSync(context)
            if (isCalendarSyncPeriodicEnabled.value) {
                schedulePeriodicCalendarSync(context)
            }
        } else {
            cancelPeriodicCalendarSync(context)
        }
    }
    fun fetchCalendars(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val savedSelected = settingsRepository.getCalendarSyncSelectedCalendars()
            withContext(Dispatchers.Main) {
                selectedCalendarIds.value = savedSelected
            }

            val calendars = mutableListOf<CalendarAccount>()
            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                )

            context.contentResolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    val nameColumn = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val accountColumn = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: context.getString(R.string.calendar_unnamed)
                        val account = cursor.getString(accountColumn) ?: context.getString(R.string.calendar_local)

                        calendars.add(
                            CalendarAccount(
                                id,
                                name,
                                account,
                                selectedCalendarIds.value.contains(id.toString()),
                            ),
                        )
                    }
                }

            withContext(Dispatchers.Main) {
                availableCalendars.clear()
                availableCalendars.addAll(calendars)
            }
        }
    }
    fun toggleCalendarSelection(calendarId: Long, context: Context? = null) {
        val currentIds = selectedCalendarIds.value.toMutableSet()
        val idString = calendarId.toString()
        if (currentIds.contains(idString)) {
            currentIds.remove(idString)
        } else {
            currentIds.add(idString)
        }
        selectedCalendarIds.value = currentIds
        settingsRepository.saveCalendarSyncSelectedCalendars(currentIds)

        // Update availableCalendars list
        val index = availableCalendars.indexOfFirst { it.id == calendarId }
        if (index != -1) {
            availableCalendars[index] =
                availableCalendars[index].copy(isSelected = currentIds.contains(idString))
        }

        context?.let {
            com.sameerasw.essentials.services.CalendarSyncManager.forceSync(it)
        }
    }
    fun setCalendarSyncPeriodicEnabled(
        enabled: Boolean,
        context: Context,
    ) {
        isCalendarSyncPeriodicEnabled.value = enabled
        settingsRepository.setCalendarSyncPeriodicEnabled(enabled)
        if (enabled && isCalendarSyncEnabled.value) {
            schedulePeriodicCalendarSync(context)
        } else {
            cancelPeriodicCalendarSync(context)
        }
    }
    private fun schedulePeriodicCalendarSync(context: Context) {
        val workRequest =
            PeriodicWorkRequestBuilder<com.sameerasw.essentials.services.CalendarSyncWorker>(
                15,
                java.util.concurrent.TimeUnit.MINUTES,
            ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "calendar_sync_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }
    private fun cancelPeriodicCalendarSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("calendar_sync_work")
    }
    fun triggerCalendarSyncNow(context: Context) {
        com.sameerasw.essentials.services.CalendarSyncManager
            .forceSync(context)
    }
    fun requestWriteSecureSettingsPermission(context: Context) {
        val adbCommand =
            "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("adb_command", adbCommand)
        clipboard.setPrimaryClip(clip)
    }
    fun requestUsageStatsPermission(context: Context) {
        PermissionUtils.openUsageStatsSettings(context)
    }
    fun requestWriteSettingsPermission(context: Context) {
        PermissionUtils.openWriteSettings(context)
    }
    fun setDefaultTab(
        tab: com.sameerasw.essentials.domain.DIYTabs,
        context: Context,
    ) {
        defaultTab.value = tab
        settingsRepository.saveDIYTab(tab)
    }
    fun setAppIcon(
        appIcon: AppIcon,
        context: Context,
    ) {
        selectedAppIcon.value = appIcon
        settingsRepository.setAppIcon(appIcon)
        AppIconUtil.setAppIcon(context, appIcon)
    }
    fun canWriteSecureSettings(context: Context): Boolean = PermissionUtils.canWriteSecureSettings(context)
    fun requestReadPhoneStatePermission(activity: androidx.activity.ComponentActivity) {
        com.sameerasw.essentials.utils.PermissionUtils.requestPermissionsSafely(
            activity,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            1001,
        )
    }
    fun requestLocationPermission(activity: androidx.activity.ComponentActivity) {
        com.sameerasw.essentials.utils.PermissionUtils.requestPermissionsSafely(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            1003,
        )
    }
    fun requestBackgroundLocationPermission(activity: androidx.activity.ComponentActivity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            com.sameerasw.essentials.utils.PermissionUtils.requestPermissionsSafely(
                activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                1004,
            )
        }
    }
    fun requestNotificationListenerPermission(context: Context) {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }
    fun requestShizukuPermission() {
        ShizukuUtils.requestPermission()
        isShizukuPermissionGranted.value = ShizukuUtils.hasPermission()
    }
    fun exportConfigs(
        context: Context,
        outputStream: java.io.OutputStream,
    ) {
        settingsRepository.exportConfigs(outputStream)
    }
    fun importConfigs(
        context: Context,
        inputStream: java.io.InputStream,
        keepPrefs: Boolean,
    ): Boolean {
        val success = settingsRepository.importConfigs(inputStream, keepPrefs)
        if (success) {
            settingsRepository.syncSystemSettingsWithSaved()
            check(context)
        }
        return success
    }
    fun setAutoAccessibilityEnabled(
        isEnabled: Boolean,
        context: Context,
    ) {
        settingsRepository.putBoolean(SettingsRepository.KEY_AUTO_ACCESSIBILITY_ENABLED, isEnabled)
        isAutoAccessibilityEnabled.value = isEnabled
    }
    fun setFaceUnlockBrightnessEnabled(enabled: Boolean) {
        settingsRepository.setFaceUnlockBrightnessEnabled(enabled)
        isFaceUnlockBrightnessEnabled.value = enabled
    }
    override fun onCleared() {
        super.onCleared()
        appContext?.let { context ->
            try {
                context.contentResolver.unregisterContentObserver(contentObserver)
            } catch (_: Exception) {
            }
            try {
                powerSaveReceiver?.let { context.unregisterReceiver(it) }
            } catch (_: Exception) {
            }
        }
        if (::settingsRepository.isInitialized) {
            settingsRepository.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
    }
    fun setOnboardingCompleted(
        completed: Boolean,
        context: Context,
    ) {
        isOnboardingCompleted.value = completed
        settingsRepository.putBoolean(SettingsRepository.KEY_ONBOARDING_COMPLETED, completed)
        if (completed) {
            settingsRepository.putInt(
                SettingsRepository.KEY_WHATS_NEW_LAST_SHOWN_COUNTER,
                com.sameerasw.essentials.BuildConfig.WHATS_NEW_COUNTER,
            )
        }
    }
    fun resetOnboarding(context: Context) {
        setOnboardingCompleted(false, context)
        // Reset tab to ESSENTIALS
        setDefaultTab(com.sameerasw.essentials.domain.DIYTabs.ESSENTIALS, context)
    }
    fun resetUpdateNote(context: Context) {
        settingsRepository.putInt(SettingsRepository.KEY_WHATS_NEW_LAST_SHOWN_COUNTER, 0)
    }
    companion object {
        var cachedIsUpdateAvailable: Boolean = false
        var cachedUpdateInfo: UpdateInfo? = null
    }
}
