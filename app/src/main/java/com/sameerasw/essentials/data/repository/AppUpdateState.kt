/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Data Repository
 * File: AppUpdateState.kt
 * Description: Reactive process-wide update state shared by Search, Settings, and AppUpdateWorker.
 */

package com.sameerasw.essentials.data.repository

import android.content.Context
import android.util.Log
import com.sameerasw.essentials.domain.model.UpdateInfo
import com.sameerasw.essentials.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateState {
    private const val TAG = "AppUpdateState"
    private const val AUTO_CHECK_THROTTLE_MS = 15L * 60L * 1000L // 15 minutes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = AtomicBoolean(false)
    private val updateRepository = UpdateRepository()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(MainViewModel.cachedUpdateInfo)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _checkCompleted = MutableStateFlow(MainViewModel.cachedUpdateInfo != null)
    val checkCompleted: StateFlow<Boolean> = _checkCompleted.asStateFlow()

    private val _dismissedVersion = MutableStateFlow<String?>(null)
    val dismissedVersion: StateFlow<String?> = _dismissedVersion.asStateFlow()

    fun publish(info: UpdateInfo?) {
        _updateInfo.value = info
        _lastError.value = null
        _checkCompleted.value = info != null
        MainViewModel.cachedUpdateInfo = info
        MainViewModel.cachedIsUpdateAvailable = info?.isUpdateAvailable == true
        if (info != null && info.versionName != _dismissedVersion.value) {
            _dismissedVersion.value = null
        }
    }

    fun clearCache() {
        _updateInfo.value = null
        _lastError.value = null
        _checkCompleted.value = false
        _dismissedVersion.value = null
        MainViewModel.cachedUpdateInfo = null
        MainViewModel.cachedIsUpdateAvailable = false
    }

    fun dismissBannerForVersion(versionName: String) {
        _dismissedVersion.value = versionName
    }

    fun checkForUpdates(
        context: Context,
        manual: Boolean = false,
    ) {
        val appContext = context.applicationContext ?: context
        val settingsRepo = SettingsRepository(appContext)

        if (!manual) {
            val autoEnabled = settingsRepo.getBoolean(SettingsRepository.KEY_AUTO_UPDATE_ENABLED, true)
            if (!autoEnabled) return
            val lastCheck = settingsRepo.getLong(SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME, 0L)
            if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_THROTTLE_MS && _updateInfo.value != null) {
                return
            }
            if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_THROTTLE_MS && _checkCompleted.value) {
                return
            }
        }

        if (!checking.compareAndSet(false, true)) return
        _isChecking.value = true
        if (manual) {
            _lastError.value = null
        }

        scope.launch {
            try {
                val currentVersion =
                    runCatching {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0.0.0"
                    }.getOrDefault("0.0.0")
                val includePreReleases =
                    settingsRepo.getBoolean(SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED, false)

                val result =
                    updateRepository.checkForUpdatesWithStatus(
                        context = appContext,
                        isPreReleaseCheckEnabled = includePreReleases,
                        currentVersion = currentVersion,
                    )

                result
                    .onSuccess { info ->
                        settingsRepo.putLong(
                            SettingsRepository.KEY_LAST_UPDATE_CHECK_TIME,
                            System.currentTimeMillis(),
                        )
                        publish(info)
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Update check failed: ${error.message}")
                        _lastError.value = error.message ?: "Unable to check for updates"
                        _checkCompleted.value = true
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "Unexpected update check failure", t)
                _lastError.value = t.message ?: "Unable to check for updates"
                _checkCompleted.value = true
            } finally {
                _isChecking.value = false
                checking.set(false)
            }
        }
    }
}
