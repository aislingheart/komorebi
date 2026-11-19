/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: DeviceTools.kt
 * Description: Deterministic device overview and screen timeout tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.net.NetworkInterface
import java.util.Locale
import kotlin.math.roundToInt

object DeviceTools {
    private const val GIB = 1024.0 * 1024.0 * 1024.0

    private const val TIMEOUT_SCHEMA =
        """{"type":"object","properties":{"duration":{"type":"string","description":"Screen timeout such as 30s, 1m, 2m, 5m, 10m, 30m, or never (capped at 1h)"}},"required":["duration"]}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "device_info",
            title = "Device Info",
            description = "Show battery, RAM, storage, screen, and system details",
            keywords = listOf(
                "device", "device info", "system info", "about phone",
                "storage", "battery", "ram",
            ),
            parametersJson = """{"type":"object","properties":{}}""",
            execute = { context, _ -> deviceInfo(context) },
        ),
        AssistantTool(
            id = "screen_timeout",
            title = "Set Screen Timeout",
            description = "Change how long the screen stays on before sleeping",
            keywords = listOf("screen timeout", "display timeout", "sleep after", "screen off"),
            parametersJson = TIMEOUT_SCHEMA,
            execute = { context, args -> screenTimeout(context, args) },
        ),
    )

    private fun deviceInfo(context: Context): ToolResult {
        val lines = mutableListOf<String?>()
        lines += batteryLines(context)
        lines += ramLines(context)
        lines += storageLines()
        lines += screenLines(context)
        lines += buildLines()
        lines += uptimeLines()
        lines += localIpLine()
        return ToolResult(true, "Device overview", lines.filterNotNull().joinToString("\n"))
    }

    private fun batteryLines(context: Context): String? {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Discharging"
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Overvoltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        return buildString {
            append("Battery: ")
            if (percent != null) {
                append("$percent% ($status), ")
            } else {
                append("$status, ")
            }
            if (temperature >= 0) {
                append(
                    String.format(Locale.US, "%.1f", temperature / 10.0) + "°C, "
                )
            }
            append("health: $health")
        }
    }

    private fun ramLines(context: Context): String? {
        val memoryInfo = runCatching {
            ActivityManager.MemoryInfo().also { info ->
                (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                    ?.getMemoryInfo(info)
            }
        }.getOrNull() ?: return null
        if (memoryInfo.totalMem <= 0) return null
        val freePercent = (memoryInfo.availMem * 100.0 / memoryInfo.totalMem).roundToInt()
        return "RAM: ${formatGib(memoryInfo.availMem)} / ${formatGib(memoryInfo.totalMem)} GB free" +
            " ($freePercent% free)"
    }

    private fun storageLines(): String? {
        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            ?: return null
        val total = runCatching { statFs.totalBytes }.getOrDefault(0L)
        val available = runCatching { statFs.availableBytes }.getOrDefault(0L)
        if (total <= 0) return null
        val freePercent = (available * 100.0 / total).roundToInt()
        return "Storage: ${formatGib(available)} / ${formatGib(total)} GB free ($freePercent% free)"
    }

    private fun screenLines(context: Context): String? {
        val metrics = runCatching { context.resources.displayMetrics }.getOrNull() ?: return null
        return "Screen: ${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi} dpi"
    }

    private fun buildLines(): String? = runCatching {
        listOf(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ).joinToString("\n")
    }.getOrNull()

    private fun uptimeLines(): String? {
        val elapsed = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(-1L)
        if (elapsed < 0) return null
        val totalMinutes = elapsed / 60_000L
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L
        return "Uptime: ${days}d ${hours}h ${minutes}m"
    }

    private fun localIpLine(): String? {
        val address = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { interfaceName -> !interfaceName.isLoopback && interfaceName.isUp }
                .flatMap { interfaceName -> interfaceName.inetAddresses.asSequence() }
                .filter { candidate ->
                    !candidate.isLoopbackAddress &&
                        candidate is java.net.Inet4Address &&
                        candidate.isSiteLocalAddress
                }
                .map { candidate -> candidate.hostAddress }
                .firstOrNull()
        }.getOrNull() ?: return null
        return "Local IP: $address"
    }

    private fun screenTimeout(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val raw = (args["duration"] ?: args["timeout"] ?: "").trim().lowercase(Locale.US)
        if (raw.isEmpty()) {
            return ToolResult(
                false,
                "Tell me a timeout such as 30s, 1m, 2m, 5m, 10m, 30m, or never.",
            )
        }
        val millis = parseTimeoutMillis(raw)
            ?: return ToolResult(
                false,
                "Could not parse '$raw'. Use 30s, 1m, 2m, 5m, 10m, 30m, or never.",
            )
        if (!Settings.System.canWrite(context)) {
            return ToolResult(false, "Komorebi needs Modify system settings permission.")
        }
        val capped = millis.coerceAtMost(60L * 60L * 1000L)
        return runCatching {
            Settings.System.putLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                capped,
            )
        }.fold(
            onSuccess = {
                val description = if (raw == "never") "1h (maximum)" else raw
                ToolResult(true, "Screen timeout set to $description.")
            },
            onFailure = { e ->
                ToolResult(
                    false,
                    "Could not set the screen timeout: ${e.message ?: e.javaClass.simpleName}",
                )
            },
        )
    }

    private fun parseTimeoutMillis(raw: String): Long? {
        if (raw == "never") return 60L * 60L * 1000L
        val match = Regex("""^(\d+)\s*([smh])$""").find(raw) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        if (amount <= 0) return null
        return when (match.groupValues[2]) {
            "s" -> amount * 1000L
            "m" -> amount * 60L * 1000L
            "h" -> amount * 60L * 60L * 1000L
            else -> null
        }
    }

    private fun formatGib(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / GIB)
}
