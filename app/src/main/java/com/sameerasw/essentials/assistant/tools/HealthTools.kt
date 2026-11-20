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
        requiredMetric: String? = null,
        body: (HealthConnectManager.HealthSnapshot) -> ToolResult,
    ): ToolResult {
        when (HealthConnectManager.availability(context)) {
            "available" -> Unit
            "update_required" -> {
                HealthConnectManager.openHealthConnectSettings(context)
                return ToolResult(false, "Health Connect needs an update. Open it once to finish updating, then retry.")
            }
            else -> {
                return ToolResult(
                    false,
                    "Health Connect isn't available on this device. It's built into Android 14+, " +
                        "or installable from Google Play on Android 13 and earlier.",
                )
            }
        }
        val readResult = HealthConnectManager.readSnapshot(context)
        val snapshot = readResult.getOrElse { err ->
            val reason = err.localizedMessage?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
            return ToolResult(false, "Couldn't read health data ($reason). Try again or check Health Connect permissions.")
        }
        if (snapshot.missingPermissions) {
            val opened = HealthConnectManager.requestPermissions(context)
            return ToolResult(
                false,
                if (opened) {
                    "Grant Sleep, Steps, Heart rate, Weight and Exercise in Health Connect, then run this again."
                } else {
                    "Health Connect permissions are missing. Open Settings > Permissions > Health Connect to grant access."
                },
            )
        }
        if (requiredMetric != null && requiredMetric in snapshot.missingMetrics) {
            HealthConnectManager.requestPermissions(context)
            return ToolResult(
                false,
                "$requiredMetric permission isn't granted yet. Allow $requiredMetric in Health Connect and try again.",
            )
        }
        if (snapshot.backgroundReadBlocked && !hasAnyMetricData(snapshot.map)) {
            HealthConnectManager.requestPermissions(context)
            return ToolResult(
                false,
                "Android blocked reading Health Connect from the search overlay. " +
                    "Allow 'Access data in background' for Komorebi in Health Connect (just opened) and try again.",
            )
        }
        return body(snapshot)
    }

    private suspend fun healthSummary(context: Context): ToolResult = runHealth(context) { snapshot ->
        val data = snapshot.map
        val lines = mutableListOf<String>()
        sleepLines(data).let { if (it.isNotEmpty()) lines += it }
        stepsLines(data).let { if (it.isNotEmpty()) lines += it.first() }
        (data["latestHr"] as? Int)?.let { hr ->
            val avg = data["hrAvg7"] as? Int
            val min = data["hrMin7"] as? Int
            val max = data["hrMax7"] as? Int
            val details = buildList {
                if (avg != null && avg > 0) add("7-day avg $avg bpm")
                if (min != null && max != null && min > 0 && max > 0) add("range $min–$max")
            }.joinToString(" · ")
            lines += if (details.isNotEmpty()) "Heart rate: $hr bpm ($details)" else "Heart rate: $hr bpm"
        }
        (data["latestWeightKg"] as? Double)?.let { lines += "Weight: %.1f kg".format(it) }
        exerciseLines(data).let { if (it.isNotEmpty()) lines += it }
        if (snapshot.missingMetrics.isNotEmpty()) {
            lines += "Not granted: ${snapshot.missingMetrics.joinToString(", ")}"
        }
        if (lines.isEmpty()) {
            ToolResult(
                false,
                "No health data recorded in the last week. Make sure a tracker writes to Health Connect " +
                    "(Fitbit, Google Fit, Samsung Health, Pixel Watch…) and try again.",
            )
        } else {
            ToolResult(true, "Health overview (from Health Connect, on-device):", lines.joinToString("\n"))
        }
    }

    private suspend fun sleepStats(context: Context): ToolResult = runHealth(context, requiredMetric = "Sleep") { snapshot ->
        val data = snapshot.map
        val lines = sleepLines(data).toMutableList()
        val history = (data["sleepHistory"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                val minutes = (it["minutes"] as? Long) ?: 0L
                if (minutes > 0L) "${shortDay(it["date"]?.toString())} ${formatMinutes(minutes)}" else null
            }
        }.orEmpty()
        val avg7 = (data["sleepAvg7Min"] as? Long) ?: 0L

        if (lines.isEmpty() && history.isEmpty()) {
            ToolResult(
                false,
                "No sleep sessions found in the last 7 days. Make sure a sleep tracker " +
                    "writes to Health Connect (Fitbit, Google Fit, Samsung Health, Sleep as Android…).",
            )
        } else {
            if (lines.isEmpty()) {
                lines += "No sleep synced yet for last night."
                if (avg7 > 0L) lines += "7-night avg: ${formatMinutes(avg7)}"
            }
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

    private suspend fun stepsStats(context: Context): ToolResult = runHealth(context, requiredMetric = "Steps") { snapshot ->
        val data = snapshot.map
        val lines = stepsLines(data)
        val history = (data["stepsHistory"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                val steps = (it["steps"] as? Long) ?: 0L
                "${shortDay(it["date"]?.toString())} ${formatSteps(steps)}"
            }
        }.orEmpty()
        val hasAnySteps = ((data["stepsHistory"] as? List<*>)?.any {
            ((it as? Map<*, *>)?.get("steps") as? Long ?: 0L) > 0L
        }) == true

        if (lines.isEmpty() && !hasAnySteps) {
            ToolResult(
                false,
                "No step data found in the last 7 days. Check that a step source (Google Fit, Fitbit, Samsung Health…) " +
                    "syncs Steps to Health Connect.",
            )
        } else {
            val body = buildString {
                append(lines.ifEmpty { listOf("Steps today: 0") }.joinToString("\n"))
                if (history.isNotEmpty()) {
                    append("\n\n7-day: ")
                    append(history.joinToString(" · "))
                }
            }
            ToolResult(true, "Steps (from Health Connect, on-device):", body)
        }
    }

    private fun hasAnyMetricData(data: Map<String, Any?>): Boolean {
        val sleep = (data["sleepMinutesLastNight"] as? Long) ?: 0L
        val sleepHist = (data["sleepHistory"] as? List<*>)?.isNotEmpty() == true
        val stepsToday = (data["stepsToday"] as? Long) ?: 0L
        val stepsYest = (data["stepsYesterday"] as? Long) ?: 0L
        val stepsAvg = (data["stepsAvg7"] as? Long) ?: 0L
        val hr = data["latestHr"] as? Int
        val weight = data["latestWeightKg"] as? Double
        val ex = (data["exercises"] as? List<*>)?.isNotEmpty() == true
        return sleep > 0L || sleepHist || stepsToday > 0L || stepsYest > 0L || stepsAvg > 0L || hr != null || weight != null || ex
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
            if (!start.isNullOrEmpty() && !end.isNullOrEmpty()) append(" ($start–$end)")
        }
        val stages = listOfNotNull(
            if (deep > 0) "Deep ${formatMinutes(deep)}" else null,
            if (rem > 0) "REM ${formatMinutes(rem)}" else null,
            if (light > 0) "Light ${formatMinutes(light)}" else null,
        ).joinToString(" · ")
        val avg = if (avg7 > 0) "7-night avg ${formatMinutes(avg7)}" else null
        return listOfNotNull(
            first,
            stages.takeIf { it.isNotEmpty() },
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
