/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: RoutinesEngine.kt
 * Description: Executes saved routines as deterministic assistant tool steps.
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import com.sameerasw.essentials.assistant.tools.AssistantTool
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import com.sameerasw.essentials.assistant.tools.ToolResult
import java.util.Locale

object RoutinesEngine {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val RUN_SCHEMA =
        """{"type":"object","properties":{"name":{"type":"string","description":"Routine name or alias"}},"required":["name"]}"""

    private val ROUTINE_TOOL_IDS = setOf("run_routine", "list_routines")

    private val STATE_TOGGLE_IDS = setOf(
        "wifi",
        "bluetooth",
        "mobile_data",
        "airplane_mode",
        "hotspot",
        "flashlight",
        "auto_rotate",
        "battery_saver",
        "dark_mode",
        "dnd",
        "caffeinate",
    )

    private val STATE_VALUES = setOf(
        "on",
        "off",
        "toggle",
        "enable",
        "disable",
        "enabled",
        "disabled",
        "true",
        "false",
        "1",
        "0",
        "yes",
        "no",
    )

    private val VOLUME_STREAMS = setOf("media", "ring", "alarm", "notification", "notifications")

    private val TIMER_DURATION_REGEX = Regex(
        """\b\d+\s*(?:hours?|hrs?|hr|h|minutes?|mins?|min|m|seconds?|secs?|sec|s)(?:\s*(?:and\s*)?\d+\s*(?:hours?|hrs?|hr|h|minutes?|mins?|min|m|seconds?|secs?|sec|s))*\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ALARM_TIME_REGEX = Regex(
        """\b\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)\b|\b\d{1,2}:\d{2}\b|\b\d{1,2}\b""",
        RegexOption.IGNORE_CASE,
    )

    private val KEY_VALUE_REGEX = Regex(
        """([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""",
    )

    suspend fun run(
        context: Context,
        routine: AssistantRoutine,
    ): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        for (rawStep in routine.steps) {
            val step = rawStep.trim()
            if (step.isEmpty()) {
                results += ToolResult(false, "Skipped an empty routine step.", rawStep)
                continue
            }

            val tool = resolveByCommand(step)
                ?: AssistantToolRegistry.match(step)
                    .map { it.first }
                    .firstOrNull { it.id !in ROUTINE_TOOL_IDS }
            if (tool == null) {
                results += ToolResult(false, "No assistant tool matches step '$step'.", step)
                continue
            }

            val args = parseArgs(tool.id, step)
            results += try {
                tool.execute(context, args)
            } catch (e: Exception) {
                ToolResult(false, "Step '$step' failed: ${e.message ?: e.javaClass.simpleName}", step)
            }
        }
        return results
    }

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "run_routine",
            title = "Run Routine",
            description = "Run a saved assistant routine by name",
            keywords = listOf("run routine", "routine", "start routine", "morning", "commute"),
            parametersJson = RUN_SCHEMA,
            execute = { context, args -> runRoutine(context, args) },
        ),
        AssistantTool(
            id = "list_routines",
            title = "List Routines",
            description = "List the saved assistant routines and their steps",
            keywords = listOf("list routines", "routines", "saved routines", "morning", "commute"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> listRoutines(context) },
        ),
    )

    private fun resolveByCommand(step: String): AssistantTool? {
        val normalized = step.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        val candidates = AssistantToolRegistry.all().filter { it.id !in ROUTINE_TOOL_IDS }

        val exact = candidates.firstOrNull { tool ->
            val id = tool.id.lowercase(Locale.ROOT)
            val title = tool.title.lowercase(Locale.ROOT)
            id == normalized ||
                title == normalized ||
                tokens.contains(id) ||
                tokens.contains(title) ||
                (id.contains('_') && normalized.startsWith(id.replace('_', ' ')))
        }
        if (exact != null) return exact

        return candidates.firstOrNull { tool ->
            tool.keywords.any { keyword ->
                val normalizedKeyword = keyword.trim().lowercase(Locale.ROOT)
                normalizedKeyword.isNotEmpty() && normalized.startsWith(normalizedKeyword)
            }
        }
    }

    internal fun parseArgs(
        toolId: String,
        step: String,
    ): Map<String, String> {
        val trimmedStep = step.trim()
        val tokens = trimmedStep
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyMap()

        val numeric = tokens.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()

        return when (toolId) {
            in STATE_TOGGLE_IDS -> {
                val state = tokens.firstOrNull { it in STATE_VALUES }
                if (state != null) mapOf("state" to state) else emptyMap()
            }

            "volume" -> {
                val action = when {
                    "set" in tokens -> "set"
                    "mute" in tokens || "silence" in tokens -> "mute"
                    "unmute" in tokens -> "unmute"
                    "up" in tokens || "raise" in tokens || "increase" in tokens -> "up"
                    "down" in tokens || "lower" in tokens || "decrease" in tokens -> "down"
                    numeric != null -> "set"
                    else -> "up"
                }
                val args = mutableMapOf("action" to action)
                if (action == "set" && numeric != null) args["value"] = numeric.toString()
                val stream = tokens.firstOrNull { it in VOLUME_STREAMS }
                if (stream != null) args["stream"] = if (stream == "notifications") "notification" else stream
                args
            }

            "brightness" -> {
                val action = when {
                    "set" in tokens -> "set"
                    "up" in tokens || "raise" in tokens || "increase" in tokens || "brighter" in tokens -> "up"
                    "down" in tokens || "lower" in tokens || "decrease" in tokens || "darker" in tokens -> "down"
                    numeric != null -> "set"
                    else -> "set"
                }
                val args = mutableMapOf("action" to action)
                if (action == "set" && numeric != null) args["value"] = numeric.toString()
                args
            }

            "timer" -> {
                val kv = parseKeyValue(trimmedStep)
                if (kv.isNotEmpty()) {
                    kv
                } else {
                    val duration = TIMER_DURATION_REGEX.find(trimmedStep)?.value?.trim()
                        ?: tokens.lastOrNull { token -> token != "timer" && token.any { it.isDigit() } }
                    if (duration != null) mapOf("duration" to duration) else emptyMap()
                }
            }

            "alarm" -> {
                val kv = parseKeyValue(trimmedStep)
                if (kv.isNotEmpty()) {
                    kv
                } else {
                    val time = ALARM_TIME_REGEX.find(trimmedStep)?.value?.trim()
                    if (time != null) mapOf("time" to time) else emptyMap()
                }
            }

            "notification_digest" -> emptyMap()

            else -> parseKeyValue(trimmedStep)
        }
    }

    private suspend fun runRoutine(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val name = (args["name"] ?: args["routine"] ?: args["value"] ?: "").trim()
        if (name.isEmpty()) return ToolResult(false, "A routine name is required.")
        val routine = RoutineRepository.find(context, name)
            ?: return ToolResult(false, "No routine named '$name'.")

        val results = run(context, routine)
        val succeeded = results.count { it.success }
        val summary = buildString {
            append("Routine '${routine.name}': $succeeded/${results.size} steps succeeded.")
            results.filterNot { it.success }.forEach { result ->
                append("\n- ${result.message}")
            }
        }
        val data = routine.steps.zip(results).joinToString("\n") { (step, result) ->
            "${if (result.success) "OK" else "FAIL"} $step: ${result.message}"
        }
        return ToolResult(results.isNotEmpty() && results.all { it.success }, summary, data)
    }

    private fun listRoutines(context: Context): ToolResult {
        val routines = RoutineRepository.list(context)
        if (routines.isEmpty()) {
            return ToolResult(true, "No routines saved.", "")
        }
        val data = routines.joinToString("\n") { routine ->
            "${routine.name}: ${routine.steps.size} step(s) — id=${routine.id}\n" +
                "  ${routine.steps.joinToString("; ")}"
        }
        return ToolResult(true, "${routines.size} routine(s) available.", data)
    }

    private fun parseKeyValue(step: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        KEY_VALUE_REGEX.findAll(step).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            if (key.isNotEmpty() && value.isNotEmpty()) {
                out[key] = value
            }
        }
        return out
    }
}
