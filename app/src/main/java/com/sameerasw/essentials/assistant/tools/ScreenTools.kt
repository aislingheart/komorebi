/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant Tools
 * File: ScreenTools.kt
 * Description: Screen text capture and local extractive summarization tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.sameerasw.essentials.assistant.ScreenTextGrabber
import com.sameerasw.essentials.launcher.ClipboardHistoryManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScreenTools {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private val STOP_WORDS = setOf(
        "the", "and", "for", "that", "with", "this", "from", "have", "are", "was", "were",
        "been", "your", "you", "about", "into", "more", "some", "will", "just", "than",
        "then", "them", "they", "their", "what", "when", "where", "which", "while", "also",
    )

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "copy_screen_text",
            title = "Copy Screen Text",
            description = "Grab the visible text from the screen behind search and copy it",
            keywords = listOf("screen text", "copy screen", "grab text", "ocr", "screen", "extract text"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> copyScreenText(context) },
        ),
        AssistantTool(
            id = "summarize_screen",
            title = "Screen Highlights",
            description = "Extract key lines and highlights from the visible screen locally",
            keywords = listOf("summarize screen", "screen summary", "tldr screen", "screen highlights", "key points"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> summarizeScreenLocally(context) },
        ),
    )

    private suspend fun copyScreenText(context: Context): ToolResult {
        val grabbed = runCatching { ScreenTextGrabber.grabScreenText(context) }.getOrNull()
        if (grabbed == null || grabbed.text.isBlank()) {
            return ToolResult(
                false,
                "No screen text available. Open search over the app you want to grab using the " +
                    "accessibility gesture so the screen can be captured first.",
            )
        }
        val appContext = context.applicationContext
        withContext(Dispatchers.Main) {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Screen", grabbed.text))
        }
        ClipboardHistoryManager.recordCopiedText(appContext, grabbed.text)
        return ToolResult(
            true,
            "Copied ${grabbed.text.length} characters from the screen (${grabbed.source}).",
            grabbed.text.take(4000),
        )
    }

    private suspend fun summarizeScreenLocally(context: Context): ToolResult {
        val grabbed = runCatching { ScreenTextGrabber.grabScreenText(context) }.getOrNull()
        if (grabbed == null || grabbed.text.isBlank()) {
            return ToolResult(
                false,
                "No screen text available. Open search over the content using the accessibility gesture first.",
            )
        }
        val highlights = extractHighlights(grabbed.text, maxLines = 6)
        if (highlights.isEmpty()) {
            return ToolResult(true, "Captured screen text (${grabbed.source}):", grabbed.text.take(600))
        }
        val body = highlights.joinToString("\n") { "• $it" }
        return ToolResult(
            true,
            "Screen highlights (${grabbed.source}, ${grabbed.text.length} chars):",
            body,
        )
    }

    internal fun extractHighlights(text: String, maxLines: Int = 6): List<String> {
        val candidates = text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 12 }
            .distinctBy { it.lowercase(Locale.US) }
        if (candidates.size <= maxLines) return candidates

        val tokenFreq = mutableMapOf<String, Int>()
        val tokenized = candidates.map { line ->
            val tokens = line.lowercase(Locale.US)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 && it !in STOP_WORDS }
            tokens.forEach { t -> tokenFreq[t] = (tokenFreq[t] ?: 0) + 1 }
            tokens
        }

        val scored = candidates.mapIndexed { idx, line ->
            val tokens = tokenized[idx]
            val tfScore = tokens.sumOf { tokenFreq[it] ?: 0 }.toDouble() / (tokens.size + 2)
            val positionBoost = if (idx < 2) 1.2 else 1.0
            val lengthPenalty = if (line.length > 220) 0.7 else 1.0
            Triple(idx, line, tfScore * positionBoost * lengthPenalty)
        }

        return scored
            .sortedByDescending { it.third }
            .take(maxLines)
            .sortedBy { it.first }
            .map { it.second }
    }
}
