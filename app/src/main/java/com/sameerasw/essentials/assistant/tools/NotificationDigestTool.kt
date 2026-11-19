/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: NotificationDigestTool.kt
 * Description: Notification digest tool for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import com.sameerasw.essentials.launcher.ActiveNotificationStore

object NotificationDigestTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "notification_digest",
            title = "Notification Digest",
            description = "Summarize the notifications currently active on the device",
            keywords = listOf("notifications", "notification digest", "summarize notifications", "what did i miss", "alerts"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> digest(context) },
        ),
    )

    private fun digest(context: Context): ToolResult {
        if (!ActiveNotificationStore.hasAccess(context)) {
            return ToolResult(
                false,
                "Notification digest needs notification listener access. Grant it in Settings first.",
            )
        }
        val items = ActiveNotificationStore.all()
        if (items.isEmpty()) {
            return ToolResult(true, "No active notifications.", "0")
        }
        val grouped = items.groupBy { it.appLabel }
        val summary = grouped.entries.joinToString("\n\n") { (app, notifications) ->
            val lines = notifications.take(3).joinToString("\n") { notification ->
                "- ${notification.title}${if (notification.text.isNotBlank()) ": ${notification.text}" else ""}"
            }
            "$app (${notifications.size})\n$lines"
        }
        return ToolResult(true, "Active notifications by app", summary.take(4000))
    }
}
