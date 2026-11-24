/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: AssistantToolRegistry.kt
 * Description: Registry and fuzzy matcher for deterministic assistant tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import com.sameerasw.essentials.assistant.AssistantMemoryRepository
import com.sameerasw.essentials.assistant.RoutinesEngine
import com.sameerasw.essentials.utils.FuzzySearchUtil
import java.util.Locale
import kotlinx.coroutines.CancellationException

object AssistantToolRegistry {

    private val allTools: List<AssistantTool> by lazy {
        SystemToggleTool.tools() +
            AudioDisplayTool.tools() +
            DeviceActionTool.tools() +
            MediaSessionTool.tools() +
            AlarmTimerTool.tools() +
            NotificationDigestTool.tools() +
            CalendarWriteTool.tools() +
            TasksTool.tools() +
            RoutinesEngine.tools() +
            ChatReplyTool.tools() +
            WebSearchAndFetchTool.tools() +
            ScreenTools.tools() +
            HealthTools.tools() +
            DeviceTools.tools() +
            QrTools.tools() +
            UtilityTools.tools()
    }

    private val toolsById: Map<String, AssistantTool> by lazy {
        allTools.associateBy { it.id }
    }

    fun all(): List<AssistantTool> = allTools

    fun find(id: String): AssistantTool? = toolsById[id]

    fun match(query: String): List<Pair<AssistantTool, Int>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all().map { it to 1000 }
        return all()
            .mapNotNull { tool ->
                val score =
                    FuzzySearchUtil.scoreFields(
                        query = trimmed,
                        primary = tool.title,
                        secondary = tool.keywords,
                        tertiary = listOf(tool.description),
                    )
                if (score > 0) tool to score else null
            }
            .sortedWith(
                compareByDescending<Pair<AssistantTool, Int>> { it.second }
                    .thenBy { it.first.title.lowercase(Locale.US) },
            )
    }

    suspend fun execute(
        context: Context,
        id: String,
        args: Map<String, String> = emptyMap(),
    ): ToolResult {
        when (id) {
            "save_memory" -> {
                val key = args["key"].orEmpty().trim()
                val value = args["value"].orEmpty().trim()
                val kind = args["kind"].orEmpty().trim().ifEmpty { "fact" }
                if (key.isEmpty() || value.isEmpty()) {
                    return ToolResult(false, "Both key and value are required to save a memory.")
                }
                val entry = AssistantMemoryRepository.remember(context, key, value, kind)
                return ToolResult(true, "Saved memory '${entry.key}'.", "${entry.key}: ${entry.value}")
            }
            "recall_memory" -> {
                val query = args["query"].orEmpty().trim()
                val limit = args["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5
                val entries = AssistantMemoryRepository.recall(context, query, limit)
                return if (entries.isEmpty()) {
                    ToolResult(true, "No matching memories found.", "")
                } else {
                    ToolResult(
                        true,
                        "Found ${entries.size} memory entries.",
                        entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    )
                }
            }
            "forget_memory" -> {
                val key = args["key"].orEmpty().trim()
                if (key.isEmpty()) {
                    return ToolResult(false, "A memory key is required to forget.")
                }
                val removed = AssistantMemoryRepository.forget(context, key)
                return if (removed) {
                    ToolResult(true, "Forgot memory '$key'.")
                } else {
                    ToolResult(false, "No saved memory found for '$key'.")
                }
            }
        }
        val tool = find(id)
            ?: return ToolResult(false, "Unknown assistant tool: $id")
        return try {
            tool.execute(context, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(false, "Tool '$id' failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
