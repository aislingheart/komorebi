/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: DeviceActionTool.kt
 * Description: Device-level action tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.Settings
import com.sameerasw.essentials.utils.DeviceLockUtils
import com.sameerasw.essentials.utils.FreezeManager
import com.sameerasw.essentials.utils.ShellUtils
import com.sameerasw.essentials.utils.SplitScreenUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object DeviceActionTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val OPEN_SETTINGS_SCHEMA =
        """{"type":"object","properties":{"page":{"type":"string","enum":["wifi","bluetooth","display","sound","battery","apps","location","accessibility","notification","internet","nfc","dark_mode"],"description":"Settings page to open"}},"required":["page"]}"""

    private const val FREEZE_SCHEMA =
        """{"type":"object","properties":{"package":{"type":"string","description":"Android package name of the app"}},"required":["package"]}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "lock_screen",
            title = "Lock Screen",
            description = "Lock the device immediately",
            keywords = listOf("lock", "lock screen", "sleep", "screen off", "secure"),
            parametersJson = EMPTY_SCHEMA,
            requiresConfirmation = true,
            execute = { context, _ -> lockScreen(context) },
        ),
        AssistantTool(
            id = "screenshot",
            title = "Take Screenshot",
            description = "Capture the screen to a PNG file using privileged screencap",
            keywords = listOf("screenshot", "capture", "screen", "snap", "screencap"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> takeScreenshot(context) },
        ),
        AssistantTool(
            id = "split_screen",
            title = "Split Screen",
            description = "Toggle split screen for the current foreground app",
            keywords = listOf("split screen", "split", "multitask", "side by side", "window"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> toggleSplitScreen(context) },
        ),
        AssistantTool(
            id = "open_settings",
            title = "Open Settings",
            description = "Open a specific Android settings page",
            keywords = listOf("settings", "open settings", "wifi settings", "bluetooth settings", "display settings", "sound settings", "battery settings", "notification settings", "location settings"),
            parametersJson = OPEN_SETTINGS_SCHEMA,
            execute = { context, args -> openSettings(context, args) },
        ),
        AssistantTool(
            id = "freeze_app",
            title = "Freeze App",
            description = "Disable or suspend an app by package name",
            keywords = listOf("freeze app", "disable app", "suspend app", "block app"),
            parametersJson = FREEZE_SCHEMA,
            requiresConfirmation = true,
            execute = { context, args -> freezeApp(context, args, freeze = true) },
        ),
        AssistantTool(
            id = "unfreeze_app",
            title = "Unfreeze App",
            description = "Enable or unsuspend an app by package name",
            keywords = listOf("unfreeze app", "enable app", "unsuspend app", "restore app"),
            parametersJson = FREEZE_SCHEMA,
            execute = { context, args -> freezeApp(context, args, freeze = false) },
        ),
    )

    private fun lockScreen(context: Context): ToolResult {
        val locked = DeviceLockUtils.performLockdownTileAction(context, isLongPress = false)
        return if (locked) {
            ToolResult(true, "Screen locked")
        } else {
            ToolResult(
                false,
                "Could not lock the screen. Enable accessibility or device admin, or grant Shizuku/root.",
            )
        }
    }

    private suspend fun takeScreenshot(context: Context): ToolResult {
        if (!shellReady(context)) {
            return ToolResult(
                false,
                "Screenshot capture needs Shizuku/root, or press Power + Volume Down.",
            )
        }
        val directory = "/sdcard/Pictures/Komorebi"
        val path = "$directory/screenshot_${System.currentTimeMillis()}.png"
        val command =
            "mkdir -p $directory; screencap -p $path; [ -f $path ] && echo __komorebi_ok__"
        val output = withContext(Dispatchers.IO) {
            ShellUtils.runCommandWithOutput(context, command, notifyOnError = false)
        }
        return if (output?.contains("__komorebi_ok__") == true) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(path),
                    arrayOf("image/png"),
                    null,
                )
            }
            ToolResult(true, "Screenshot saved", path)
        } else {
            ToolResult(false, "Screenshot failed. Check Shizuku/root access and try again.")
        }
    }

    private fun toggleSplitScreen(context: Context): ToolResult {
        if (!shellReady(context)) {
            return ToolResult(false, "Split screen needs Shizuku/root.")
        }
        return try {
            SplitScreenUtil.toggle(context)
            ToolResult(true, "Split screen toggle requested")
        } catch (e: Exception) {
            ToolResult(false, "Could not toggle split screen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun openSettings(context: Context, args: Map<String, String>): ToolResult {
        val page = (args["page"] ?: args["section"] ?: "").trim().lowercase(Locale.US)
        val action = when (page) {
            "wifi", "wi-fi", "wireless" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "audio", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery", "power" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notification", "notifications" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
                } else {
                    Settings.ACTION_SETTINGS
                }
            "internet", "network" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIRELESS_SETTINGS
                }
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "dark_mode", "dark mode", "theme" -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, "Opened ${if (page.isBlank()) "settings" else page} settings")
        } catch (_: ActivityNotFoundException) {
            ToolResult(false, "Settings page '$page' is not available on this device.")
        } catch (e: Exception) {
            ToolResult(false, "Could not open settings: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun freezeApp(
        context: Context,
        args: Map<String, String>,
        freeze: Boolean,
    ): ToolResult {
        val packageName = (
            args["package"]
                ?: args["package_name"]
                ?: args["app"]
                ?: args["pkg"]
            )?.trim().orEmpty()
        if (packageName.isBlank()) {
            return ToolResult(false, "A package name is required.")
        }
        if (packageName == context.packageName) {
            return ToolResult(false, "Refusing to ${if (freeze) "freeze" else "unfreeze"} Komorebi itself.")
        }
        val installed = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.isSuccess
        if (!installed) {
            return ToolResult(false, "Package '$packageName' is not installed.")
        }
        val changed = try {
            if (freeze) {
                FreezeManager.freezeApp(context, packageName)
            } else {
                FreezeManager.unfreezeApp(context, packageName)
            }
        } catch (e: Exception) {
            return ToolResult(
                false,
                "Could not ${if (freeze) "freeze" else "unfreeze"} $packageName: " +
                    (e.message ?: e.javaClass.simpleName),
            )
        }
        return if (changed) {
            ToolResult(
                true,
                "${if (freeze) "Froze" else "Unfroze"} $packageName",
                packageName,
            )
        } else {
            ToolResult(
                false,
                "${if (freeze) "Freezing" else "Unfreezing"} $packageName needs Shizuku/root.",
            )
        }
    }

    private fun shellReady(context: Context): Boolean =
        ShellUtils.isAvailable(context) && ShellUtils.hasPermission(context)
}
