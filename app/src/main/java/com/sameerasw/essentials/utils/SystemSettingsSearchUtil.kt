/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: SystemSettingsSearchUtil.kt
 * Description: Comprehensive Android system settings index with live in-search toggles,
 * choice pickers, brightness/volume sliders, and deep settings navigation.
 */

package com.sameerasw.essentials.utils

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.assistant.tools.SystemToggleTool
import com.sameerasw.essentials.domain.controller.CaffeinateController
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.search.InteractiveSettingControl
import com.sameerasw.essentials.search.SettingActionOutcome
import com.sameerasw.essentials.search.SettingChoiceOption
import kotlin.math.roundToInt

object SystemSettingsSearchUtil {

    @Volatile
    private var torchEnabled: Boolean = false

    @Volatile
    private var torchCallbackRegistered: Boolean = false

    @Volatile
    private var torchRegistrationRequested: Boolean = false

    private fun ensureTorchTracked(context: Context) {
        if (torchCallbackRegistered || torchRegistrationRequested) return
        val appContext = context.applicationContext
        synchronized(this) {
            if (torchCallbackRegistered || torchRegistrationRequested) return
            torchRegistrationRequested = true
        }
        Thread {
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@Thread
            runCatching {
                manager.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            torchEnabled = enabled
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                torchCallbackRegistered = true
            }.onFailure {
                torchRegistrationRequested = false
            }
        }.apply {
            name = "KomorebiTorchTrack"
            isDaemon = true
            start()
        }
    }

    private fun newTaskIntent(action: String, data: Uri? = null): Intent =
        Intent(action).apply {
            if (data != null) this.data = data
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun writeSettingsPermissionIntent(context: Context): Intent =
        newTaskIntent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    private fun wifiPanelIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            newTaskIntent(Settings.Panel.ACTION_WIFI)
        } else {
            newTaskIntent(Settings.ACTION_WIFI_SETTINGS)
        }

    private fun internetPanelIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            newTaskIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            newTaskIntent(Settings.ACTION_WIRELESS_SETTINGS)
        }

    private fun invokeAssistantToggle(
        context: Context,
        toolId: String,
        desired: Boolean,
    ): SettingActionOutcome {
        val tool = SystemToggleTool.tools().firstOrNull { it.id == toolId }
            ?: return SettingActionOutcome(false, requiresFallbackIntent = true)
        val result = runCatching {
            kotlinx.coroutines.runBlocking {
                tool.execute(context, mapOf("state" to if (desired) "on" else "off"))
            }
        }.getOrNull() ?: return SettingActionOutcome(false, requiresFallbackIntent = true)

        return if (result.success) {
            SettingActionOutcome(applied = true)
        } else {
            SettingActionOutcome(
                applied = false,
                message = result.message,
                requiresFallbackIntent = true,
            )
        }
    }

    private fun hasPrivilegedShell(context: Context): Boolean =
        ShizukuUtils.hasPermission() || ShellUtils.hasPermission(context)

    private fun writeSystemInt(
        context: Context,
        key: String,
        value: Int,
        fallbackMessage: String,
    ): SettingActionOutcome {
        if (Settings.System.canWrite(context)) {
            val ok = runCatching {
                Settings.System.putInt(context.contentResolver, key, value)
            }.getOrDefault(false)
            if (ok) return SettingActionOutcome(applied = true)
        }
        if (hasPrivilegedShell(context)) {
            val ok = runCatching {
                ShellUtils.runCommand(context, "settings put system $key $value")
                true
            }.getOrDefault(false)
            if (ok) return SettingActionOutcome(applied = true)
        }
        return SettingActionOutcome(
            applied = false,
            message = fallbackMessage,
            requiresFallbackIntent = true,
        )
    }

    private fun readSystemInt(context: Context, key: String, default: Int): Int =
        runCatching { Settings.System.getInt(context.contentResolver, key, default) }.getOrDefault(default)

    private fun readGlobalInt(context: Context, key: String, default: Int): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, key, default) }.getOrDefault(default)

    private fun readSecureInt(context: Context, key: String, default: Int): Int =
        runCatching { Settings.Secure.getInt(context.contentResolver, key, default) }.getOrDefault(default)

    private fun volumeSlider(
        streamType: Int,
        fallbackAction: String = Settings.ACTION_SOUND_SETTINGS,
    ): InteractiveSettingControl.Slider =
        InteractiveSettingControl.Slider(
            readPercent = { ctx ->
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audio == null) {
                    0
                } else {
                    val max = audio.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    val cur = audio.getStreamVolume(streamType).coerceIn(0, max)
                    ((cur.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
                }
            },
            writePercent = { ctx, percent ->
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    ?: return@Slider SettingActionOutcome(false, requiresFallbackIntent = true)
                val max = audio.getStreamMaxVolume(streamType).coerceAtLeast(1)
                val target = ((percent.coerceIn(0, 100) / 100f) * max).roundToInt().coerceIn(0, max)
                runCatching {
                    audio.setStreamVolume(streamType, target, 0)
                    SettingActionOutcome(applied = true)
                }.getOrElse {
                    SettingActionOutcome(
                        applied = false,
                        message = "Could not adjust volume directly",
                        requiresFallbackIntent = true,
                    )
                }
            },
            stepPercent = 5,
            fallbackIntent = { _ -> newTaskIntent(fallbackAction) },
        )

    fun all(context: Context): List<PixelSearchResultItem.SystemSettingItem> {
        ensureTorchTracked(context)

        val items = mutableListOf<PixelSearchResultItem.SystemSettingItem>()

        // =========================================================================
        // 1. INTERACTIVE SYSTEM TOGGLES
        // =========================================================================
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_wifi",
            title = "Wi-Fi",
            subtitle = "Wireless network toggle",
            iconRes = R.drawable.rounded_android_wifi_3_bar_24,
            intent = wifiPanelIntent(),
            keywords = listOf("wifi", "wi-fi", "wireless", "internet", "wlan", "network", "toggle"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    runCatching { wm?.isWifiEnabled == true }.getOrDefault(false)
                },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "wifi", enabled) },
                fallbackIntent = { _ -> wifiPanelIntent() },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_bluetooth",
            title = "Bluetooth",
            subtitle = "Pair & connect wireless devices",
            iconRes = R.drawable.rounded_bluetooth_24,
            intent = newTaskIntent(Settings.ACTION_BLUETOOTH_SETTINGS),
            keywords = listOf("bluetooth", "bt", "headphones", "earbuds", "audio", "pair", "toggle"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    @Suppress("DEPRECATION")
                    val adapter = mgr?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    val canRead = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) == PackageManager.PERMISSION_GRANTED
                    if (canRead) {
                        runCatching { adapter?.isEnabled == true }.getOrDefault(false)
                    } else {
                        readGlobalInt(ctx, Settings.Global.BLUETOOTH_ON, 0) == 1
                    }
                },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "bluetooth", enabled) },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_BLUETOOTH_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_flashlight",
            title = "Flashlight",
            subtitle = "Camera torch light",
            iconRes = R.drawable.rounded_flashlight_on_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("flashlight", "torch", "light", "flash", "lamp", "toggle"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    ensureTorchTracked(ctx)
                    torchEnabled
                },
                writeState = { ctx, enabled ->
                    val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                        ?: return@Toggle SettingActionOutcome(false, "Camera service unavailable")
                    ensureTorchTracked(ctx)
                    val cameraId = runCatching {
                        manager.cameraIdList.firstOrNull { id ->
                            val chars = manager.getCameraCharacteristics(id)
                            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                        } ?: manager.cameraIdList.firstOrNull { id ->
                            manager.getCameraCharacteristics(id)
                                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        }
                    }.getOrNull() ?: return@Toggle SettingActionOutcome(false, "No camera flash found")
                    runCatching {
                        manager.setTorchMode(cameraId, enabled)
                        torchEnabled = enabled
                        SettingActionOutcome(applied = true)
                    }.getOrElse {
                        SettingActionOutcome(false, "Could not toggle flashlight")
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_auto_rotate",
            title = "Auto-Rotate Screen",
            subtitle = "Rotate display automatically",
            iconRes = R.drawable.rounded_mobile_rotate_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("auto rotate", "rotate", "rotation", "orientation", "portrait", "landscape", "screen"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    readSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                },
                writeState = { ctx, enabled ->
                    writeSystemInt(
                        context = ctx,
                        key = Settings.System.ACCELEROMETER_ROTATION,
                        value = if (enabled) 1 else 0,
                        fallbackMessage = "Grant Modify System Settings to toggle Auto-Rotate",
                    )
                },
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS)
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_dark_mode",
            title = "Dark Mode (System)",
            subtitle = "System-wide dark theme",
            iconRes = R.drawable.rounded_dark_mode_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("dark mode", "dark theme", "night mode", "light mode", "system theme"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "dark_mode", enabled) },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_dnd",
            title = "Do Not Disturb",
            subtitle = "Silence calls and notifications",
            iconRes = R.drawable.rounded_do_not_disturb_on_24,
            intent = newTaskIntent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            keywords = listOf("do not disturb", "dnd", "zen", "mute notifications", "focus", "quiet"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                    filter != NotificationManager.INTERRUPTION_FILTER_ALL
                },
                writeState = { ctx, enabled ->
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (nm != null && nm.isNotificationPolicyAccessGranted) {
                        val target = if (enabled) {
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        } else {
                            NotificationManager.INTERRUPTION_FILTER_ALL
                        }
                        runCatching {
                            nm.setInterruptionFilter(target)
                            SettingActionOutcome(applied = true)
                        }.getOrElse {
                            SettingActionOutcome(false, "Could not change Do Not Disturb", requiresFallbackIntent = true)
                        }
                    } else {
                        invokeAssistantToggle(ctx, "dnd", enabled)
                    }
                },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_adaptive_brightness",
            title = "Adaptive Brightness",
            subtitle = "Auto-adjust screen brightness",
            iconRes = R.drawable.rounded_brightness_auto_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("adaptive brightness", "auto brightness", "brightness", "display", "screen"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    readSystemInt(
                        ctx,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                },
                writeState = { ctx, enabled ->
                    writeSystemInt(
                        context = ctx,
                        key = Settings.System.SCREEN_BRIGHTNESS_MODE,
                        value = if (enabled) {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        } else {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        },
                        fallbackMessage = "Grant Modify System Settings to toggle Adaptive Brightness",
                    )
                },
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS)
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_battery_saver",
            title = "Battery Saver",
            subtitle = "Reduce background power usage",
            iconRes = R.drawable.rounded_battery_charging_60_24,
            intent = newTaskIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            keywords = listOf("battery saver", "low power", "power saving", "eco", "battery"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    pm?.isPowerSaveMode == true
                },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "battery_saver", enabled) },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_mobile_data",
            title = "Mobile Data",
            subtitle = "Cellular 4G / 5G connection",
            iconRes = R.drawable.rounded_mobiledata_arrows_24,
            intent = internetPanelIntent(),
            keywords = listOf("mobile data", "cellular", "5g", "4g", "lte", "sim", "data"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx -> readGlobalInt(ctx, "mobile_data", 1) == 1 },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "mobile_data", enabled) },
                fallbackIntent = { _ -> internetPanelIntent() },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_airplane_mode",
            title = "Airplane Mode",
            subtitle = "Disable all wireless radios",
            iconRes = R.drawable.rounded_flight_24,
            intent = newTaskIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            keywords = listOf("airplane mode", "flight mode", "offline", "radio", "plane"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx -> readGlobalInt(ctx, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "airplane_mode", enabled) },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_hotspot",
            title = "Wi-Fi Hotspot",
            subtitle = "Share cellular internet",
            iconRes = R.drawable.rounded_wifi_tethering_24,
            intent = newTaskIntent(Settings.ACTION_WIRELESS_SETTINGS),
            keywords = listOf("hotspot", "tethering", "tether", "wifi hotspot", "share internet"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    runCatching {
                        val method = wm?.javaClass?.getMethod("isWifiApEnabled")
                        method?.invoke(wm) as? Boolean ?: false
                    }.getOrDefault(false)
                },
                writeState = { ctx, enabled -> invokeAssistantToggle(ctx, "hotspot", enabled) },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_WIRELESS_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_location",
            title = "Location (GPS)",
            subtitle = "Device location services",
            iconRes = R.drawable.rounded_location_on_24,
            intent = newTaskIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            keywords = listOf("location", "gps", "maps", "position", "tracking"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        runCatching { lm?.isLocationEnabled == true }.getOrDefault(false)
                    } else {
                        readSecureInt(ctx, Settings.Secure.LOCATION_MODE, 0) != 0
                    }
                },
                writeState = { ctx, enabled ->
                    if (hasPrivilegedShell(ctx)) {
                        val mode = if (enabled) 3 else 0
                        runCatching {
                            ShellUtils.runCommand(ctx, "settings put secure location_mode $mode")
                            SettingActionOutcome(applied = true)
                        }.getOrElse {
                            SettingActionOutcome(false, "Open Location settings to change GPS", requiresFallbackIntent = true)
                        }
                    } else {
                        SettingActionOutcome(
                            applied = false,
                            message = "Opening Location settings",
                            requiresFallbackIntent = true,
                        )
                    }
                },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_nfc",
            title = "NFC",
            subtitle = "Contactless tap & pay",
            iconRes = R.drawable.rounded_nfc_24,
            intent = newTaskIntent(Settings.ACTION_NFC_SETTINGS),
            keywords = listOf("nfc", "tap to pay", "contactless", "wallet", "google pay"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    runCatching { NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true }.getOrDefault(false)
                },
                writeState = { ctx, enabled ->
                    if (hasPrivilegedShell(ctx)) {
                        val cmd = if (enabled) "svc nfc enable" else "svc nfc disable"
                        runCatching {
                            ShellUtils.runCommand(ctx, cmd)
                            SettingActionOutcome(applied = true)
                        }.getOrElse {
                            SettingActionOutcome(false, "Opening NFC settings", requiresFallbackIntent = true)
                        }
                    } else {
                        SettingActionOutcome(
                            applied = false,
                            message = "Opening NFC settings",
                            requiresFallbackIntent = true,
                        )
                    }
                },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_NFC_SETTINGS) },
                runOffMainThread = true,
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_touch_sounds",
            title = "Touch Sounds",
            subtitle = "Audible tap clicks across Android",
            iconRes = R.drawable.rounded_volume_up_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("touch sounds", "tap sound", "click sound", "keyboard sound", "audio"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx -> readSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1 },
                writeState = { ctx, enabled ->
                    writeSystemInt(
                        context = ctx,
                        key = Settings.System.SOUND_EFFECTS_ENABLED,
                        value = if (enabled) 1 else 0,
                        fallbackMessage = "Grant Modify System Settings to toggle Touch Sounds",
                    )
                },
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_SOUND_SETTINGS)
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_haptics",
            title = "System Touch Vibration",
            subtitle = "Haptic feedback for system navigation & taps",
            iconRes = R.drawable.rounded_mobile_vibrate_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("system haptics", "touch vibration", "vibrate on tap", "haptic feedback"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx -> readSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1 },
                writeState = { ctx, enabled ->
                    writeSystemInt(
                        context = ctx,
                        key = Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        value = if (enabled) 1 else 0,
                        fallbackMessage = "Grant Modify System Settings to toggle Touch Vibration",
                    )
                },
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_SOUND_SETTINGS)
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_toggle_caffeinate",
            title = "Stay Awake (Caffeinate)",
            subtitle = "Keep the screen awake indefinitely",
            iconRes = R.drawable.rounded_coffee_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("stay awake", "caffeinate", "keep screen on", "awake", "coffee", "screen timeout"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    CaffeinateController.check(ctx)
                    CaffeinateController.isActive.value
                },
                writeState = { ctx, enabled ->
                    if (enabled) {
                        CaffeinateController.start(ctx)
                    } else {
                        CaffeinateController.cancelAll(ctx)
                    }
                    SettingActionOutcome(applied = true)
                },
            ),
        )

        // =========================================================================
        // 2. INTERACTIVE SYSTEM CHOICES
        // =========================================================================
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_choice_ringer_mode",
            title = "Sound Mode",
            subtitle = "Switch between Sound, Vibrate, and Silent",
            iconRes = R.drawable.rounded_ring_volume_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("sound mode", "ringer", "vibrate", "silent", "mute", "ring", "audio profile"),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption(AudioManager.RINGER_MODE_NORMAL.toString(), "Sound"),
                    SettingChoiceOption(AudioManager.RINGER_MODE_VIBRATE.toString(), "Vibrate"),
                    SettingChoiceOption(AudioManager.RINGER_MODE_SILENT.toString(), "Silent"),
                ),
                readSelectedId = { ctx ->
                    val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    (audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL).toString()
                },
                writeSelectedId = { ctx, id ->
                    val mode = id.toIntOrNull() ?: AudioManager.RINGER_MODE_NORMAL
                    val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        ?: return@Choice SettingActionOutcome(false, requiresFallbackIntent = true)
                    runCatching {
                        audio.ringerMode = mode
                        SettingActionOutcome(applied = true)
                    }.getOrElse {
                        SettingActionOutcome(
                            applied = false,
                            message = "Grant Do Not Disturb access for Silent mode",
                            requiresFallbackIntent = true,
                        )
                    }
                },
                fallbackIntent = { _ -> newTaskIntent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_choice_screen_timeout",
            title = "Screen Timeout",
            subtitle = "Auto-lock display after inactivity",
            iconRes = R.drawable.rounded_timer_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("screen timeout", "sleep", "auto lock", "display off", "timeout", "lock"),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption("15000", "15s"),
                    SettingChoiceOption("30000", "30s"),
                    SettingChoiceOption("60000", "1m"),
                    SettingChoiceOption("120000", "2m"),
                    SettingChoiceOption("300000", "5m"),
                    SettingChoiceOption("600000", "10m"),
                ),
                readSelectedId = { ctx ->
                    val current = readSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
                    val choices = listOf(15000, 30000, 60000, 120000, 300000, 600000)
                    choices.minByOrNull { kotlin.math.abs(it - current) }?.toString() ?: "60000"
                },
                writeSelectedId = { ctx, id ->
                    val ms = id.toIntOrNull() ?: 60000
                    writeSystemInt(
                        context = ctx,
                        key = Settings.System.SCREEN_OFF_TIMEOUT,
                        value = ms,
                        fallbackMessage = "Grant Modify System Settings to change Screen Timeout",
                    )
                },
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS)
                    }
                },
            ),
        )

        // =========================================================================
        // 3. INTERACTIVE SYSTEM SLIDERS
        // =========================================================================
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_slider_brightness",
            title = "Screen Brightness",
            subtitle = "Adjust display brightness level",
            iconRes = R.drawable.rounded_brightness_6_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("brightness", "screen brightness", "dim", "bright", "backlight", "display"),
            control = InteractiveSettingControl.Slider(
                readPercent = { ctx ->
                    val raw = readSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(0, 255)
                    ((raw / 255f) * 100f).roundToInt().coerceIn(0, 100)
                },
                writePercent = { ctx, percent ->
                    val value = ((percent.coerceIn(1, 100) / 100f) * 255f).roundToInt().coerceIn(1, 255)
                    if (Settings.System.canWrite(ctx)) {
                        runCatching {
                            Settings.System.putInt(
                                ctx.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                            )
                            Settings.System.putInt(
                                ctx.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                value,
                            )
                            SettingActionOutcome(applied = true)
                        }.getOrElse {
                            SettingActionOutcome(false, requiresFallbackIntent = true)
                        }
                    } else if (hasPrivilegedShell(ctx)) {
                        runCatching {
                            ShellUtils.runCommand(
                                ctx,
                                "settings put system screen_brightness_mode 0 && settings put system screen_brightness $value",
                            )
                            SettingActionOutcome(applied = true)
                        }.getOrElse {
                            SettingActionOutcome(false, requiresFallbackIntent = true)
                        }
                    } else {
                        SettingActionOutcome(
                            applied = false,
                            message = "Grant Modify System Settings to adjust brightness",
                            requiresFallbackIntent = true,
                        )
                    }
                },
                stepPercent = 5,
                fallbackIntent = { ctx ->
                    if (!Settings.System.canWrite(ctx)) {
                        writeSettingsPermissionIntent(ctx)
                    } else {
                        newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS)
                    }
                },
            ),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_slider_media_volume",
            title = "Media Volume",
            subtitle = "Music, videos, and games volume",
            iconRes = R.drawable.rounded_volume_up_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("media volume", "volume", "music volume", "sound", "audio", "speaker"),
            control = volumeSlider(AudioManager.STREAM_MUSIC),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_slider_ring_volume",
            title = "Ring & Notification Volume",
            subtitle = "Incoming calls and alerts volume",
            iconRes = R.drawable.rounded_ring_volume_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("ring volume", "ringtone", "call volume", "notification volume", "volume"),
            control = volumeSlider(AudioManager.STREAM_RING),
        )

        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_slider_alarm_volume",
            title = "Alarm Volume",
            subtitle = "Clock alarms volume",
            iconRes = R.drawable.rounded_alarm_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("alarm volume", "alarm", "clock volume", "volume"),
            control = volumeSlider(AudioManager.STREAM_ALARM),
        )

        // =========================================================================
        // 4. DEEP SYSTEM SETTINGS NAVIGATION PAGES
        // =========================================================================
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_wifi",
            title = "Wi-Fi Settings",
            subtitle = context.getString(R.string.pixel_search_setting_network),
            iconRes = R.drawable.rounded_android_wifi_3_bar_24,
            intent = newTaskIntent(Settings.ACTION_WIFI_SETTINGS),
            keywords = listOf("wifi settings", "saved networks", "dns", "proxy", "internet"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_bluetooth",
            title = "Bluetooth Settings",
            subtitle = context.getString(R.string.pixel_search_setting_bluetooth),
            iconRes = R.drawable.rounded_bluetooth_24,
            intent = newTaskIntent(Settings.ACTION_BLUETOOTH_SETTINGS),
            keywords = listOf("bluetooth settings", "connected devices", "pair device"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_display",
            title = context.getString(R.string.cat_display),
            subtitle = context.getString(R.string.pixel_search_setting_display),
            iconRes = R.drawable.rounded_mobile_text_2_24,
            intent = newTaskIntent(Settings.ACTION_DISPLAY_SETTINGS),
            keywords = listOf("display", "screen", "refresh rate", "font size", "wallpaper", "night light"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_sound",
            title = context.getString(R.string.pixel_search_setting_sound_title),
            subtitle = context.getString(R.string.pixel_search_setting_sound),
            iconRes = R.drawable.rounded_volume_up_24,
            intent = newTaskIntent(Settings.ACTION_SOUND_SETTINGS),
            keywords = listOf("sound", "vibration", "ringtone", "audio", "haptics"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_battery",
            title = context.getString(R.string.icon_battery),
            subtitle = context.getString(R.string.pixel_search_setting_battery),
            iconRes = R.drawable.rounded_battery_charging_60_24,
            intent = newTaskIntent(Intent.ACTION_POWER_USAGE_SUMMARY),
            keywords = listOf("battery", "usage", "charging", "power", "health"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_apps",
            title = context.getString(R.string.tab_apps),
            subtitle = context.getString(R.string.pixel_search_setting_apps),
            iconRes = R.drawable.rounded_apps_24,
            intent = newTaskIntent(Settings.ACTION_APPLICATION_SETTINGS),
            keywords = listOf("apps", "applications", "uninstall", "permissions", "manage apps"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_default_apps",
            title = "Default Apps",
            subtitle = "Browser, launcher, assistant, and SMS defaults",
            iconRes = R.drawable.rounded_apps_24,
            intent = newTaskIntent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            keywords = listOf("default apps", "home app", "launcher", "browser", "assistant"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_notifications",
            title = context.getString(R.string.option_notifications),
            subtitle = context.getString(R.string.pixel_search_setting_notifications),
            iconRes = R.drawable.rounded_notifications_unread_24,
            intent = newTaskIntent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS),
            keywords = listOf("notifications", "history", "bubbles", "channels", "alerts"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_storage",
            title = context.getString(R.string.label_device_storage),
            subtitle = context.getString(R.string.pixel_search_setting_storage),
            iconRes = R.drawable.rounded_save_24,
            intent = newTaskIntent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            keywords = listOf("storage", "space", "disk", "memory", "clean"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_security",
            title = context.getString(R.string.pixel_search_setting_security_title),
            subtitle = context.getString(R.string.pixel_search_setting_security),
            iconRes = R.drawable.rounded_security_24,
            intent = newTaskIntent(Settings.ACTION_SECURITY_SETTINGS),
            keywords = listOf("security", "lock screen", "fingerprint", "biometrics", "pin", "password"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_privacy",
            title = "Privacy",
            subtitle = "Permissions, camera, microphone, and activity controls",
            iconRes = R.drawable.rounded_admin_panel_settings_24,
            intent = newTaskIntent(Settings.ACTION_PRIVACY_SETTINGS),
            keywords = listOf("privacy", "permissions", "dashboard", "ads", "telemetry"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_accessibility",
            title = "Accessibility",
            subtitle = "Services, magnification, captions, and shortcuts",
            iconRes = R.drawable.rounded_settings_accessibility_24,
            intent = newTaskIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            keywords = listOf("accessibility", "a11y", "talkback", "shortcut", "services"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_datetime",
            title = "Date & Time",
            subtitle = "Time zone, 24-hour clock, and automatic time",
            iconRes = R.drawable.rounded_calendar_clock_24,
            intent = newTaskIntent(Settings.ACTION_DATE_SETTINGS),
            keywords = listOf("date", "time", "clock", "timezone", "24 hour"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_language",
            title = "Languages & Keyboard",
            subtitle = "System language, region, and input methods",
            iconRes = R.drawable.rounded_language_24,
            intent = newTaskIntent(Settings.ACTION_LOCALE_SETTINGS),
            keywords = listOf("language", "locale", "keyboard", "input", "region"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_vpn",
            title = "VPN",
            subtitle = "Virtual private networks",
            iconRes = R.drawable.rounded_vpn_key_24,
            intent = newTaskIntent(Settings.ACTION_VPN_SETTINGS),
            keywords = listOf("vpn", "wireguard", "tailscale", "private dns", "tunnel"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_accounts",
            title = "Accounts & Passwords",
            subtitle = "Synced accounts and autofill services",
            iconRes = R.drawable.rounded_person_24,
            intent = newTaskIntent(Settings.ACTION_SYNC_SETTINGS),
            keywords = listOf("accounts", "sync", "google account", "passwords", "autofill"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_developer",
            title = "Developer Options (Android)",
            subtitle = "USB debugging, animation scale, and system tools",
            iconRes = R.drawable.rounded_code_24,
            intent = newTaskIntent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            keywords = listOf("developer options", "adb", "usb debugging", "animation scale", "dev"),
        )
        items += PixelSearchResultItem.SystemSettingItem(
            id = "sys_page_about",
            title = "About Phone",
            subtitle = "Android version, build number, and device info",
            iconRes = R.drawable.rounded_info_24,
            intent = newTaskIntent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            keywords = listOf("about phone", "device info", "android version", "build number", "model"),
        )

        return items
    }

    fun search(
        context: Context,
        settings: List<PixelSearchResultItem.SystemSettingItem>,
        query: String,
        limit: Int = 6,
    ): List<PixelSearchResultItem.SystemSettingItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // Surface interactive toggles/sliders/choices first when browsing settings with an empty query.
            return settings
                .sortedByDescending { it.control != null }
                .take(limit)
        }
        return settings.mapNotNull { item ->
            val baseScore = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = item.title,
                secondary = item.keywords,
                tertiary = listOf(item.subtitle),
            )
            if (baseScore <= 0) {
                null
            } else {
                val interactiveBonus = if (item.control != null) 6 else 0
                val usageBoost = SearchUsageTracker.boost(
                    context,
                    SearchUsageTracker.suggestionKey("syssetting", item.id),
                )
                item to (baseScore + interactiveBonus + usageBoost)
            }
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
