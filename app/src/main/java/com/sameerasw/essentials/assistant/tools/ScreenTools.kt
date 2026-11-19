/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant Tools
 * File: ScreenTools.kt
 * Description: Screen text capture and AI summarization tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.sameerasw.essentials.assistant.AiMessage
import com.sameerasw.essentials.assistant.AiProviderManager
import com.sameerasw.essentials.assistant.AssistantAiMode
import com.sameerasw.essentials.assistant.AssistantAiSettings
import com.sameerasw.essentials.assistant.ScreenTextGrabber
import com.sameerasw.essentials.launcher.ClipboardHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScreenTools {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

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
            title = "Summarize Screen",
            description = "Read the visible screen text and summarize it with AI",
            keywords = listOf("summarize screen", "screen summary", "tldr screen", "summarize"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> summarizeScreen(context) },
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

    private suspend fun summarizeScreen(context: Context): ToolResult {
        val grabbed = runCatching { ScreenTextGrabber.grabScreenText(context) }.getOrNull()
        if (grabbed == null || grabbed.text.isBlank()) {
            return ToolResult(
                false,
                "No screen text available. Open search over the content using the accessibility gesture first.",
            )
        }
        val mode = AssistantAiSettings.getMode(context)
        if (mode == AssistantAiMode.OFF || !AssistantAiSettings.isConfigured(context)) {
            return ToolResult(
                false,
                "Screen summaries need an AI backend. Enable one in Pixel Search settings > AI assistant.",
            )
        }
        val system = "You summarize screen content concisely. Reply with at most six short " +
            "bullet-style lines of the key information, no preamble."
        val response = AiProviderManager.complete(
            context = context,
            systemPrompt = system,
            messages = listOf(AiMessage("user", "Summarize this screen text:\n" + grabbed.text.take(6000))),
        )
        if (response.error != null) {
            return ToolResult(false, "AI summary failed: ${response.error}")
        }
        val answer = response.text?.trim().orEmpty()
        if (answer.isBlank()) {
            return ToolResult(false, "The AI returned an empty summary.")
        }
        return ToolResult(true, "Screen summary (${grabbed.source} text, ${grabbed.text.length} chars):", answer)
    }
}
