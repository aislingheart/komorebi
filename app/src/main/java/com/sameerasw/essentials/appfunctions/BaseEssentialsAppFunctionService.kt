/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Appfunctions
 * File: BaseEssentialsAppFunctionService.kt
 * Description: Component file for BaseEssentialsAppFunctionService.kt.
 */

package com.sameerasw.essentials.appfunctions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.sameerasw.essentials.appfunctions.dto.AppFunctionResult
import com.sameerasw.essentials.appfunctions.dto.DeviceStatusResponse
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "EssentialsAppFunctionService",
    appFunctionXmlFileName = "essentials_app_function_service",
)
abstract class BaseEssentialsAppFunctionService : AppFunctionService() {
    /**
     * Sets the device ringer or sound mode.
     *
     * @param mode Target sound mode: "SOUND", "VIBRATE", or "SILENT".
     * @return Result of setting the sound mode.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setSoundMode(mode: String): AppFunctionResult =
        withContext(Dispatchers.IO) {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val ringerMode =
                when (mode.uppercase()) {
                    "VIBRATE" -> AudioManager.RINGER_MODE_VIBRATE
                    "SILENT" -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
            runCatching {
                audioManager.ringerMode = ringerMode
                AppFunctionResult(true, "Sound mode set to ${mode.uppercase()}")
            }.getOrElse {
                AppFunctionResult(false, "Unable to change sound mode: ${it.message}")
            }
        }

    /**
     * Returns the overall status of the device, including battery level, charging state, and sound mode.
     *
     * @return Current status details of the device.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceStatus(): DeviceStatusResponse =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val repo = SettingsRepository(context)

            val batteryIntent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val soundModeStr =
                when (audioManager.ringerMode) {
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    else -> "SOUND"
                }

            DeviceStatusResponse(
                batteryLevel = batteryPct,
                isCharging = isCharging,
                soundMode = soundModeStr,
                isCaffeinateActive = false,
                isFlashlightOn = false,
                isAodEnabled = repo.isAodEnabled(),
                isNotificationLightingEnabled = false,
            )
        }
}
