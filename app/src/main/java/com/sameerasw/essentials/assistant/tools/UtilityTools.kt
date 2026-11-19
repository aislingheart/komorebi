/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: UtilityTools.kt
 * Description: Clipboard sharing and app info tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UtilityTools {
    private const val APP_INFO_SCHEMA =
        """{"type":"object","properties":{"name":{"type":"string","description":"App name or name fragment, such as Chrome or settings"}},"required":["name"]}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "share_clipboard",
            title = "Share Clipboard",
            description = "Open the system share sheet with the current clipboard text",
            keywords = listOf("share", "share clipboard", "send clipboard", "share text"),
            parametersJson = """{"type":"object","properties":{}}""",
            execute = { context, _ -> shareClipboard(context) },
        ),
        AssistantTool(
            id = "app_info",
            title = "Open App Info",
            description = "Open the system app info page for an installed app",
            keywords = listOf(
                "app info", "application info", "app details", "app settings",
                "force stop", "uninstall app",
            ),
            parametersJson = APP_INFO_SCHEMA,
            execute = { context, args -> appInfo(context, args) },
        ),
    )

    private suspend fun shareClipboard(context: Context): ToolResult {
        val text = withContext(Dispatchers.Main) {
            runCatching {
                val clipboardManager =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0)?.coerceToText(context)?.toString()
                } else {
                    null
                }
            }.getOrNull()
        }?.trim()
        if (text.isNullOrEmpty()) return ToolResult(false, "Clipboard is empty")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(intent, "Share via")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            ToolResult(true, "Sharing clipboard text via the system share sheet…")
        } catch (e: Exception) {
            ToolResult(
                false,
                "Could not open the share sheet: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private suspend fun appInfo(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val name = (args["name"] ?: args["app"] ?: args["query"] ?: "").trim()
        if (name.isEmpty()) {
            return ToolResult(false, "Tell me which app to open, such as 'app info Chrome'.")
        }

        val match = withContext(Dispatchers.IO) {
            runCatching { resolveApp(context, name) }.getOrNull()
        } ?: return ToolResult(false, "No installed app matches '$name'.")

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${match.first}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                true,
                "Opening app info for ${match.second}",
                "Package: ${match.first}\n" +
                    "(App info has Force stop, Uninstall, permissions, storage)",
            )
        } catch (e: Exception) {
            ToolResult(
                false,
                "Could not open app info: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private fun resolveApp(
        context: Context,
        name: String,
    ): Pair<String, String>? {
        val packageManager = context.packageManager
        val applications = packageManager.getInstalledApplications(0)
        val labelled = applications.mapNotNull { info ->
            val label = runCatching {
                packageManager.getApplicationLabel(info)?.toString()
            }.getOrNull() ?: return@mapNotNull null
            info to label
        }

        val exact = labelled.firstOrNull { (_, label) ->
            label.equals(name, ignoreCase = true)
        }
        if (exact != null) return exact.first.packageName to exact.second

        val partial = labelled.firstOrNull { (info, label) ->
            info.packageName != context.packageName &&
                label.contains(name, ignoreCase = true)
        } ?: return null
        return partial.first.packageName to partial.second
    }
}
