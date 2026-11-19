/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: SystemToggleTool.kt
 * Description: Deterministic system toggle tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.Manifest
import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.domain.controller.CaffeinateController
import com.sameerasw.essentials.domain.controller.HotspotController
import com.sameerasw.essentials.utils.DeviceUtils
import com.sameerasw.essentials.utils.ShizukuUtils
import com.sameerasw.essentials.utils.ShellUtils
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Locale

object SystemToggleTool {
    private const val STATE_SCHEMA =
        """{"type":"object","properties":{"state":{"type":"string","enum":["on","off","toggle"],"description":"Desired state; omit or use toggle to flip the current state"}}}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "wifi",
            title = "Wi-Fi",
            description = "Turn Wi-Fi on or off, or toggle it",
            keywords = listOf("wifi", "wi-fi", "wireless", "network", "internet", "connect"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setWifi(context, args) },
        ),
        AssistantTool(
            id = "bluetooth",
            title = "Bluetooth",
            description = "Turn Bluetooth on or off, or toggle it",
            keywords = listOf("bluetooth", "bt", "pair", "headphones", "devices"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setBluetooth(context, args) },
        ),
        AssistantTool(
            id = "mobile_data",
            title = "Mobile Data",
            description = "Turn cellular data on or off",
            keywords = listOf("mobile data", "cellular", "data", "4g", "5g", "network"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setMobileData(context, args) },
        ),
        AssistantTool(
            id = "airplane_mode",
            title = "Airplane Mode",
            description = "Turn Airplane Mode on or off",
            keywords = listOf("airplane mode", "flight mode", "offline", "radio"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setAirplaneMode(context, args) },
        ),
        AssistantTool(
            id = "hotspot",
            title = "Wi-Fi Hotspot",
            description = "Turn the Wi-Fi hotspot on or off",
            keywords = listOf("hotspot", "tethering", "tether", "wifi hotspot", "share internet"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setHotspot(context, args) },
        ),
        AssistantTool(
            id = "flashlight",
            title = "Flashlight",
            description = "Turn the rear camera torch on or off",
            keywords = listOf("flashlight", "torch", "light", "lamp", "flash"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setFlashlight(context, args) },
        ),
        AssistantTool(
            id = "auto_rotate",
            title = "Auto Rotate",
            description = "Enable or disable automatic screen rotation",
            keywords = listOf("auto rotate", "rotation", "orientation", "screen rotation", "accelerometer"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setAutoRotate(context, args) },
        ),
        AssistantTool(
            id = "battery_saver",
            title = "Battery Saver",
            description = "Enable or disable the system battery saver",
            keywords = listOf("battery saver", "power saving", "low power", "battery", "saver"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setBatterySaver(context, args) },
        ),
        AssistantTool(
            id = "dark_mode",
            title = "Dark Mode",
            description = "Switch the system dark theme on or off",
            keywords = listOf("dark mode", "night mode", "dark theme", "theme", "appearance"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setDarkMode(context, args) },
        ),
        AssistantTool(
            id = "dnd",
            title = "Do Not Disturb",
            description = "Turn Do Not Disturb on or off",
            keywords = listOf("do not disturb", "dnd", "silence", "mute notifications", "zen"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setDnd(context, args) },
        ),
        AssistantTool(
            id = "caffeinate",
            title = "Caffeinate",
            description = "Keep the device awake using the Caffeinate controller",
            keywords = listOf("caffeinate", "keep awake", "stay awake", "screen on", "wake lock"),
            parametersJson = STATE_SCHEMA,
            execute = { context, args -> setCaffeinate(context, args) },
        ),
    )

    private fun setWifi(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(wifiEnabled(context), args)
            ?: return ToolResult(false, "Current Wi-Fi state is unknown. Pass state=on or state=off.")
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "svc wifi ${if (desired) "enable" else "disable"}")
            return ToolResult(true, "Wi-Fi ${onOff(desired)} requested", desired.toString())
        }
        val manager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult(false, "Wi-Fi service unavailable.")
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            return ToolResult(false, "Wi-Fi ${onOff(desired)} needs Shizuku/root or the CHANGE_WIFI_STATE permission.")
        }
        val changed = try {
            @Suppress("DEPRECATION")
            manager.setWifiEnabled(desired)
        } catch (_: Exception) {
            false
        }
        return if (changed) {
            ToolResult(true, "Wi-Fi ${onOff(desired)} requested", desired.toString())
        } else {
            ToolResult(false, "Could not change Wi-Fi state.")
        }
    }

    private fun setBluetooth(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(bluetoothEnabled(), args)
            ?: return ToolResult(false, "Current Bluetooth state is unknown. Pass state=on or state=off.")
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "svc bluetooth ${if (desired) "enable" else "disable"}")
            return ToolResult(true, "Bluetooth ${onOff(desired)} requested", desired.toString())
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ToolResult(false, "Bluetooth is not available on this device.")
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            return ToolResult(false, "Bluetooth ${onOff(desired)} needs Shizuku/root or the BLUETOOTH_CONNECT permission.")
        }
        val changed = try {
            @Suppress("DEPRECATION")
            if (desired) adapter.enable() else adapter.disable()
        } catch (_: Exception) {
            false
        }
        return if (changed) {
            ToolResult(true, "Bluetooth ${onOff(desired)} requested", desired.toString())
        } else {
            ToolResult(false, "Could not change Bluetooth state.")
        }
    }

    private fun setMobileData(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(mobileDataEnabled(context), args)
            ?: return ToolResult(false, "Current mobile data state is unknown. Pass state=on or state=off.")
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "svc data ${if (desired) "enable" else "disable"}")
            return ToolResult(true, "Mobile data ${onOff(desired)} requested", desired.toString())
        }
        return ToolResult(false, "Mobile data ${onOff(desired)} needs Shizuku/root.")
    }

    private fun setAirplaneMode(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(airplaneModeEnabled(context), args)
            ?: return ToolResult(false, "Current Airplane Mode state is unknown. Pass state=on or state=off.")
        if (!shellReady(context)) {
            return ToolResult(false, "Airplane Mode ${onOff(desired)} needs Shizuku/root.")
        }
        val verb = if (desired) "enable" else "disable"
        val value = if (desired) "true" else "false"
        val command =
            "cmd connectivity airplane-mode $verb 2>/dev/null || " +
                "(settings put global airplane_mode_on ${if (desired) 1 else 0} && " +
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $value)"
        ShellUtils.runCommand(context, command)
        return ToolResult(true, "Airplane Mode ${onOff(desired)} requested", desired.toString())
    }

    private fun setHotspot(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(hotspotEnabled(context), args)
            ?: return ToolResult(false, "Current hotspot state is unknown. Pass state=on or state=off.")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult(false, "Hotspot control requires Android 11 or newer.")
        }
        if (!ShizukuUtils.isShizukuAvailable() || !ShizukuUtils.hasPermission()) {
            return ToolResult(false, "Hotspot ${onOff(desired)} needs Shizuku access.")
        }
        return try {
            HotspotController.setEnabled(context, desired)
            ToolResult(true, "Hotspot ${onOff(desired)} requested", desired.toString())
        } catch (e: Exception) {
            ToolResult(false, "Could not change hotspot state: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun setFlashlight(context: Context, args: Map<String, String>): ToolResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult(false, "Camera service unavailable.")
        registerTorchCallback(manager)
        val desired = resolveDesiredState(TorchState.enabled, args)
            ?: return ToolResult(false, "Current flashlight state is unknown. Pass state=on or state=off.")
        return try {
            val cameraId = torchCameraId(manager)
                ?: return ToolResult(false, "No camera flash found.")
            manager.setTorchMode(cameraId, desired)
            TorchState.enabled = desired
            ToolResult(true, "Flashlight ${onOff(desired)}", desired.toString())
        } catch (e: Exception) {
            ToolResult(false, "Could not change flashlight state: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun setAutoRotate(context: Context, args: Map<String, String>): ToolResult {
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (_: Exception) {
            null
        }
        val desired = resolveDesiredState(current, args)
            ?: return ToolResult(false, "Current auto-rotate state is unknown. Pass state=on or state=off.")
        if (Settings.System.canWrite(context)) {
            val written = try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (desired) 1 else 0,
                )
            } catch (_: Exception) {
                false
            }
            if (written) return ToolResult(true, "Auto-rotate ${onOff(desired)}", desired.toString())
        }
        if (shellReady(context)) {
            ShellUtils.runCommand(
                context,
                "settings put system accelerometer_rotation ${if (desired) 1 else 0}",
            )
            return ToolResult(true, "Auto-rotate ${onOff(desired)} requested", desired.toString())
        }
        return ToolResult(false, "Auto-rotate ${onOff(desired)} needs WRITE_SETTINGS or Shizuku/root.")
    }

    private fun setBatterySaver(context: Context, args: Map<String, String>): ToolResult {
        val desired = resolveDesiredState(DeviceUtils.isPowerSaveMode(context), args)
            ?: return ToolResult(false, "Current battery saver state is unknown. Pass state=on or state=off.")
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "cmd power set-mode ${if (desired) 1 else 0}")
            return ToolResult(true, "Battery saver ${onOff(desired)} requested", desired.toString())
        }
        val changed = try {
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "low_power",
                if (desired) 1 else 0,
            )
        } catch (_: Exception) {
            false
        }
        return if (changed) {
            ToolResult(true, "Battery saver ${onOff(desired)}", desired.toString())
        } else {
            ToolResult(false, "Battery saver ${onOff(desired)} needs Shizuku/root.")
        }
    }

    private fun setDarkMode(context: Context, args: Map<String, String>): ToolResult {
        val current =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val desired = resolveDesiredState(current, args)
            ?: return ToolResult(false, "Current dark mode state is unknown. Pass state=on or state=off.")
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "cmd uimode night ${if (desired) "yes" else "no"}")
            return ToolResult(true, "Dark mode ${onOff(desired)} requested", desired.toString())
        }
        return ToolResult(false, "Dark mode ${onOff(desired)} needs Shizuku/root.")
    }

    private fun setDnd(context: Context, args: Map<String, String>): ToolResult {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ToolResult(false, "Notification service unavailable.")
        val current = try {
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) {
            null
        }
        val desired = resolveDesiredState(current, args)
            ?: return ToolResult(false, "Current Do Not Disturb state is unknown. Pass state=on or state=off.")
        if (manager.isNotificationPolicyAccessGranted) {
            return try {
                manager.setInterruptionFilter(
                    if (desired) {
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    },
                )
                ToolResult(true, "Do Not Disturb ${onOff(desired)}", desired.toString())
            } catch (e: Exception) {
                ToolResult(false, "Could not change Do Not Disturb: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        if (shellReady(context)) {
            ShellUtils.runCommand(context, "cmd notification set_dnd ${if (desired) "on" else "off"}")
            return ToolResult(true, "Do Not Disturb ${onOff(desired)} requested", desired.toString())
        }
        return ToolResult(false, "Do Not Disturb needs notification policy access or Shizuku/root.")
    }

    private fun setCaffeinate(context: Context, args: Map<String, String>): ToolResult {
        CaffeinateController.check(context)
        val current = CaffeinateController.isActive.value
        val desired = resolveDesiredState(current, args)
            ?: return ToolResult(false, "Current Caffeinate state is unknown. Pass state=on or state=off.")
        if (desired == current) {
            return ToolResult(true, "Caffeinate is already ${onOff(desired)}", desired.toString())
        }
        return try {
            if (desired) CaffeinateController.start(context) else CaffeinateController.cancelAll(context)
            ToolResult(true, "Caffeinate ${onOff(desired)} requested", desired.toString())
        } catch (e: Exception) {
            ToolResult(false, "Could not change Caffeinate: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun wifiEnabled(context: Context): Boolean? {
        val fromManager = try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
        } catch (_: Exception) {
            null
        }
        if (fromManager != null) return fromManager
        return try {
            when (Settings.Global.getInt(context.contentResolver, Settings.Global.WIFI_ON, -1)) {
                1 -> true
                0 -> false
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun bluetoothEnabled(): Boolean? = try {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()?.isEnabled
    } catch (_: Exception) {
        null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun mobileDataEnabled(context: Context): Boolean? = try {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephony?.isDataEnabled
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun airplaneModeEnabled(context: Context): Boolean? = try {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
    } catch (_: Exception) {
        null
    }

    private fun hotspotEnabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        return try {
            HiddenApiBypass.invoke(WifiManager::class.java, wifi, "isWifiApEnabled") as? Boolean
        } catch (_: Exception) {
            null
        }
    }

    private fun registerTorchCallback(manager: CameraManager) {
        if (TorchState.callbackRegistered) return
        TorchState.callbackRegistered = true
        val targetCameraId = runCatching { torchCameraId(manager) }.getOrNull()
        runCatching {
            manager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(
                        cameraId: String,
                        enabled: Boolean,
                    ) {
                        if (targetCameraId == null || cameraId == targetCameraId) {
                            TorchState.enabled = enabled
                        }
                    }
                },
                null,
            )
        }
    }

    private fun torchCameraId(manager: CameraManager): String? {
        val ids = manager.cameraIdList
        return ids.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val isBack = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
            hasFlash && isBack
        } ?: ids.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private fun requestedState(args: Map<String, String>): Boolean? {
        val raw = (args["state"] ?: args["value"] ?: args["action"] ?: "")
            .trim()
            .lowercase(Locale.US)
        return when (raw) {
            "on", "enable", "enabled", "true", "1", "yes", "start" -> true
            "off", "disable", "disabled", "false", "0", "no", "stop" -> false
            else -> null
        }
    }

    private fun resolveDesiredState(
        current: Boolean?,
        args: Map<String, String>,
    ): Boolean? = requestedState(args) ?: current?.not()

    private fun shellReady(context: Context): Boolean =
        ShellUtils.isAvailable(context) && ShellUtils.hasPermission(context)

    private fun onOff(enabled: Boolean): String = if (enabled) "on" else "off"

    private object TorchState {
        @Volatile
        var enabled: Boolean = false

        @Volatile
        var callbackRegistered: Boolean = false
    }
}
