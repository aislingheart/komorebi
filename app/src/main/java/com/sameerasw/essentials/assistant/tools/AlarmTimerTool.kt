/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: AlarmTimerTool.kt
 * Description: Alarm and timer tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.sameerasw.essentials.launcher.ClockIntentLauncher
import java.util.Locale
import kotlin.math.roundToLong

object AlarmTimerTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val TIMER_SCHEMA =
        """{"type":"object","properties":{"duration":{"type":"string","description":"Timer length such as 10, 10m, 1h30m, or 05:00"},"message":{"type":"string","description":"Optional timer label"}},"required":["duration"]}"""

    private const val ALARM_SCHEMA =
        """{"type":"object","properties":{"time":{"type":"string","description":"Clock time such as 07:30, 7:30pm, or 19:30"},"message":{"type":"string","description":"Optional alarm label"}},"required":["time"]}"""

    private const val ADD_TIME_SCHEMA =
        """{"type":"object","properties":{"minutes":{"type":"integer","minimum":1,"description":"Minutes to add to the active timer"}},"required":["minutes"]}"""

    private val TIME_OF_DAY_REGEX = Regex(
        "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
        RegexOption.IGNORE_CASE,
    )

    private val DURATION_WORD_REGEX = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-zA-Z])",
    )

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "timer",
            title = "Set Timer",
            description = "Start a countdown timer in the Clock app",
            keywords = listOf("timer", "countdown", "set timer", "start timer", "minutes", "seconds"),
            parametersJson = TIMER_SCHEMA,
            execute = { context, args -> setTimer(context, args) },
        ),
        AssistantTool(
            id = "alarm",
            title = "Set Alarm",
            description = "Create an alarm in the Clock app",
            keywords = listOf("alarm", "wake me", "set alarm", "clock", "morning"),
            parametersJson = ALARM_SCHEMA,
            execute = { context, args -> setAlarm(context, args) },
        ),
        AssistantTool(
            id = "open_timers",
            title = "Open Timers",
            description = "Show active timers in the Clock app",
            keywords = listOf("open timers", "timers", "clock", "countdown"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> openTimers(context) },
        ),
        AssistantTool(
            id = "open_alarms",
            title = "Open Alarms",
            description = "Show alarms in the Clock app",
            keywords = listOf("open alarms", "alarms", "clock", "wake up"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> openAlarms(context) },
        ),
        AssistantTool(
            id = "add_time",
            title = "Add Timer Time",
            description = "Add minutes to the active timer when the platform supports it",
            keywords = listOf("add time", "extend timer", "more time", "snooze timer", "timer minutes"),
            parametersJson = ADD_TIME_SCHEMA,
            execute = { _, args -> addTime(args) },
        ),
    )

    private fun setTimer(context: Context, args: Map<String, String>): ToolResult {
        val rawDuration = (args["duration"] ?: args["time"] ?: args["value"])?.trim().orEmpty()
        if (rawDuration.isBlank()) {
            return ToolResult(false, "A timer duration is required, for example 10m or 00:10.")
        }
        val seconds = parseDurationSeconds(rawDuration)
            ?: return ToolResult(
                false,
                "Could not parse the timer duration '$rawDuration'. Use a number of minutes, 10m, 1h30m, or 05:00.",
            )
        val message = (args["message"] ?: args["label"] ?: "").trim()
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
        }
        return launch(context, intent, "Timer started for ${formatDuration(seconds)}")
    }

    private fun setAlarm(context: Context, args: Map<String, String>): ToolResult {
        val rawTime = (args["time"] ?: args["at"] ?: args["value"])?.trim().orEmpty()
        if (rawTime.isBlank()) {
            return ToolResult(false, "An alarm time is required, for example 07:30 or 7:30pm.")
        }
        val parsed = parseClockTime(rawTime)
            ?: return ToolResult(
                false,
                "Could not parse the alarm time '$rawTime'. Use 07:30, 7:30pm, or 19:30.",
            )
        val (hour, minute) = parsed
        val message = (args["message"] ?: args["label"] ?: "").trim()
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
        }
        val label = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        return launch(context, intent, "Alarm set for $label")
    }

    private fun openTimers(context: Context): ToolResult =
        launch(
            context,
            Intent(AlarmClock.ACTION_SHOW_TIMERS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened timers",
        )

    private fun openAlarms(context: Context): ToolResult =
        launch(
            context,
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened alarms",
        )

    private fun addTime(args: Map<String, String>): ToolResult {
        val minutes = (args["minutes"] ?: args["value"] ?: args["duration"])
            ?.trim()
            ?.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            return ToolResult(false, "Adding timer time needs a positive integer number of minutes.")
        }
        return ToolResult(
            false,
            "Adding time to a running timer is not supported by Android's public AlarmClock API. " +
                "Start a new ${minutes}-minute timer or extend it in the Clock app.",
            minutes.toString(),
        )
    }

    private fun launch(
        context: Context,
        intent: Intent,
        successMessage: String,
    ): ToolResult {
        val result = ClockIntentLauncher.launch(context, intent)
        return if (result.success) {
            ToolResult(true, successMessage)
        } else {
            ToolResult(
                false,
                result.error ?: "No Clock app can handle ${intent.action} on this device.",
            )
        }
    }

    private fun parseDurationSeconds(raw: String): Long? {
        val normalized = raw.trim().lowercase(Locale.ROOT).replace(',', '.')
        if (normalized.isBlank()) return null
        Regex("^(\\d{1,3}):(\\d{2})(?::(\\d{2}))?$").matchEntire(normalized)?.let { match ->
            val first = match.groupValues[1].toLong()
            val second = match.groupValues[2].toLong()
            val third = match.groupValues[3].toLongOrNull()
            return if (third != null) {
                first * 3600 + second * 60 + third
            } else {
                first * 60 + second
            }
        }
        normalized.toDoubleOrNull()?.let { bare ->
            if (bare > 0) return (bare * 60).roundToLong()
        }
        var total = 0.0
        var found = false
        DURATION_WORD_REGEX.findAll(normalized).forEach { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val unit = match.groupValues[2]
            total += when {
                unit.startsWith("h") -> value * 3600
                unit.startsWith("m") -> value * 60
                else -> value
            }
            found = true
        }
        return if (found && total > 0) total.roundToLong() else null
    }

    private fun parseClockTime(raw: String): Pair<Int, Int>? {
        val match = TIME_OF_DAY_REGEX.find(raw) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val suffix = match.groupValues[3].lowercase(Locale.ROOT)
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 || isEmpty()) append("${secs}s")
        }.trim()
    }
}
