/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant Tools
 * File: HealthTools.kt
 * Description: Health Connect-backed wellness tools (sleep, steps, heart rate, weight, exercise).
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import com.sameerasw.essentials.assistant.HealthConnectManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HealthTools {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "health_summary",
            title = "Health Overview",
            description = "Sleep, steps, heart rate, weight and exercise from Health Connect",
            keywords = listOf("health", "health summary", "wellness", "my health", "health stats", "fitness"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> healthSummary(context) },
        ),
        AssistantTool(
            id = "sleep_stats",
            title = "Sleep Stats",
            description = "Last night's sleep stages plus a 7-night history from Health Connect",
            keywords = listOf("sleep", "sleep stats", "sleep last night", "sleep quality", "how did i sleep"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> sleepStats(context) },
        ),
        AssistantTool(
            id = "steps",
            title = "Step Count",
            description = "Today's steps, yesterday and 7-day average from Health Connect",
            keywords = listOf("steps", "step count", "steps today", "how many steps", "pedometer"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> stepsStats(context) },
        ),
    )

    private suspend fun runHealth(
        context: Context,
        body: (Map<String, Any?>) -> ToolResult,
    ): ToolResult {
        when (HealthConnectManager.availability(context)) {
            "available" -> Unit
            "update_required" -> {
                HealthConnectManager.openHealthConnectSettings(context)
                return ToolResult(false, "Health Connect needs an update. Open it once to finish updating, then retry.")
            }
            else -> {
                HealthConnectManager.openHealthConnectSettings(context)
                return ToolResult(
                    false,
                    "Health Connect isn't available on this device. It's built into Android 14+, " +
                        "or installable from Google Play on 13 and earlier.",
                )
            }
        }
        val result = HealthConnectManager.readSnapshot(context).getOrNull()
            ?: return ToolResult(false, "Couldn't read health data. Try again in a moment.")
        if (result.missingPermissions) {
            val opened = HealthConnectManager.requestPermissions(context)
            return ToolResult(
                false,
                if (opened) {
                    "Grant Sleep, Steps, Heart rate, Weight and Exercise in the Health Connect " +
                        "screen that just opened, then run this again."
                } else {
                    "Health Connect permissions are missing. Open Health Connect > App permissions > " +
                        "Komorebi and grant Sleep, Steps, Heart rate, Weight and Exercise."
                },
            )
        }
        return body(result.map)
    }

    private suspend fun healthSummary(context: Context): ToolResult = runHealth(context) { data ->
        val lines = mutableListOf<String>()
        sleepLines(data).let { if (it.isNotEmpty()) lines += it }
        stepsLines(data).let { if (it.isNotEmpty()) lines += it.first() }
        (data["latestHr"] as? Int)?.let { hr ->
            val avg = data["hrAvg7"] as? Int
            lines += "Heart rate: $hr bpm now${if (avg != null && avg > 0) " · 7-day avg $avg bpm" else ""}"
        }
        (data["latestWeightKg"] as? Double)?.let { lines += "Weight: %.1f kg".format(it) }
        exerciseLines(data).let { if (it.isNotEmpty()) lines += it }
        if (lines.isEmpty()) {
            ToolResult(false, "No health data recorded in the last week. Track something with a " +
                "Health Connect app (Samsung Health, Google Fit, Fitbit…) and try again.")
        } else {
            ToolResult(true, "Health overview (from Health Connect, on-device):", lines.joinToString("\n"))
        }
    }

    private suspend fun sleepStats(context: Context): ToolResult = runHealth(context) { data ->
        val lines = sleepLines(data)
        val history = (data["sleepHistory"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                val minutes = (it["minutes"] as? Long) ?: 0L
                "${shortDay(it["date"]?.toString())} ${formatMinutes(minutes)}"
            }
        }.orEmpty()
        if (lines.isEmpty()) {
            ToolResult(false, "No sleep sessions found in the last 7 days. Make sure a sleep tracker " +
                "writes to Health Connect (Samsung Health, Google Fit, Sleep as Android…).")
        } else {
            val body = buildString {
                append(lines.joinToString("\n"))
                if (history.isNotEmpty()) {
                    append("\n\n7-night history:\n")
                    append(history.joinToString(" · "))
                }
            }
            ToolResult(true, "Sleep stats (from Health Connect, on-device):", body)
        }
    }

    private suspend fun stepsStats(context: Context): ToolResult = runHealth(context) { data ->
        val lines = stepsLines(data)
        val history = (data["stepsHistory"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                val steps = (it["steps"] as? Long) ?: 0L
                "${shortDay(it["date"]?.toString())} ${formatSteps(steps)}"
            }
        }.orEmpty()
        if (lines.isEmpty()) {
            ToolResult(false, "No step data found in the last 7 days.")
        } else {
            ToolResult(true, "Steps (from Health Connect, on-device):",
                lines.joinToString("\n") + "\n\n7-day: " + history.joinToString(" · "))
        }
    }

    private fun sleepLines(data: Map<String, Any?>): List<String> {
        val total = (data["sleepMinutesLastNight"] as? Long) ?: 0L
        if (total <= 0L) return emptyList()
        val deep = (data["sleepDeepMin"] as? Long) ?: 0L
        val rem = (data["sleepRemMin"] as? Long) ?: 0L
        val light = (data["sleepLightMin"] as? Long) ?: 0L
        val awake = (data["sleepAwakeMin"] as? Long) ?: 0L
        val avg7 = (data["sleepAvg7Min"] as? Long) ?: 0L
        val start = (data["sleepStart"] as? String)?.let(::formatTime)
        val end = (data["sleepEnd"] as? String)?.let(::formatTime)
        val first = buildString {
            append("Sleep: ")
            append(formatMinutes(total))
            if (start != null && end != null) append(" ($start–$end)")
        }
        val stages = listOf("Deep ${formatMinutes(deep)}", "REM ${formatMinutes(rem)}", "Light ${formatMinutes(light)}")
            .joinToString(" · ")
        val avg = if (avg7 > 0) "7-night avg ${formatMinutes(avg7)}" else null
        return listOfNotNull(
            first,
            stages.takeIf { deep + rem + light > 0 },
            awake.takeIf { it > 0 }?.let { "Awake in bed ${formatMinutes(it)}" },
            avg,
        )
    }

    private fun stepsLines(data: Map<String, Any?>): List<String> {
        val today = (data["stepsToday"] as? Long) ?: 0L
        val yesterday = (data["stepsYesterday"] as? Long) ?: 0L
        val avg7 = (data["stepsAvg7"] as? Long) ?: 0L
        if (today == 0L && yesterday == 0L && avg7 == 0L) return emptyList()
        val lines = mutableListOf<String>()
        lines += "Steps today: ${formatSteps(today)}"
        if (yesterday > 0) lines += "Yesterday: ${formatSteps(yesterday)}"
        if (avg7 > 0) lines += "7-day avg: ${formatSteps(avg7)}/day"
        return lines
    }

    private fun exerciseLines(data: Map<String, Any?>): List<String> {
        val exercises = (data["exercises"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                val title = it["title"]?.toString() ?: "Workout"
                val minutes = (it["minutes"] as? Long) ?: 0L
                if (minutes > 0) "$title ${formatMinutes(minutes)}" else null
            }
        }.orEmpty()
        return if (exercises.isEmpty()) emptyList() else listOf("Recent: " + exercises.joinToString(" · "))
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatSteps(steps: Long): String =
        if (steps >= 10000) "${"%.1f".format(steps / 1000.0)}k" else steps.toString()

    private fun formatTime(iso: String): String = runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }.getOrDefault("")

    private fun shortDay(dateIso: String?): String = runCatching {
        when (java.time.LocalDate.parse(dateIso).dayOfWeek.name.take(3)) {
            "MON" -> "Mon"; "TUE" -> "Tue"; "WED" -> "Wed"; "THU" -> "Thu"
            "FRI" -> "Fri"; "SAT" -> "Sat"; else -> "Sun"
        }
    }.getOrDefault("")
}
