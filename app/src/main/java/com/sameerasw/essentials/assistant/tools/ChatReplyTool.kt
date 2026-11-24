/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: ChatReplyTool.kt
 * Description: Notification reply and messaging-app tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sameerasw.essentials.launcher.ActiveNotificationItem
import com.sameerasw.essentials.launcher.ActiveNotificationStore
import java.util.Locale

object ChatReplyTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val REPLY_LAST_SCHEMA =
        """{"type":"object","properties":{"message":{"type":"string","description":"Reply text to send"},"app":{"type":"string","description":"Optional app filter such as whatsapp, telegram, signal, or discord"}},"required":["message"]}"""

    private const val SEND_CHAT_SCHEMA =
        """{"type":"object","properties":{"app":{"type":"string","enum":["whatsapp","telegram","signal","discord"],"description":"Messaging app to open"},"contact":{"type":"string","description":"Phone number or username to message"},"message":{"type":"string","description":"Message text to send"}},"required":["app","contact","message"]}"""

    private const val MAX_NOTIFICATION_LIST_CHARS = 3000

    private val APP_PACKAGES = mapOf(
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "signal" to "org.thoughtcrime.securesms",
        "discord" to "com.discord",
    )

    private val PHONE_CONTACT_REGEX = Regex("^\\+?[0-9\\s\\-().]+$")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "reply_last",
            title = "Reply To Last Message",
            description = "Send a reply to the newest repliable notification, optionally filtered by app",
            keywords = listOf("reply", "reply to last", "answer message", "respond", "reply notification"),
            parametersJson = REPLY_LAST_SCHEMA,
            requiresConfirmation = true,
            execute = { context, args -> replyLast(context, args) },
        ),
        AssistantTool(
            id = "send_chat",
            title = "Send Chat Message",
            description = "Open WhatsApp, Telegram, Signal, or Discord with a prefilled message",
            keywords = listOf("send message", "message", "whatsapp", "telegram", "signal", "discord", "chat"),
            parametersJson = SEND_CHAT_SCHEMA,
            execute = { context, args -> sendChat(context, args) },
        ),
        AssistantTool(
            id = "list_chat_notifications",
            title = "List Chat Notifications",
            description = "List active notifications that can be replied to",
            keywords = listOf("chat notifications", "repliable notifications", "messages waiting", "unread messages"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> listChatNotifications(context) },
        ),
    )

    private fun replyLast(context: Context, args: Map<String, String>): ToolResult {
        val message = args["message"]?.trim().orEmpty()
        if (message.isEmpty()) {
            return ToolResult(false, "A reply message is required.")
        }
        if (!ActiveNotificationStore.hasAccess(context)) {
            return ToolResult(
                false,
                "Replying needs notification listener access. Grant it in Settings first.",
            )
        }
        val app = args["app"]?.trim().orEmpty()
        val candidates = ActiveNotificationStore.all().let { items ->
            if (app.isBlank()) items else items.filter { matchesApp(it, app) }
        }
        val item = candidates.firstOrNull { hasReplyAction(it) }
            ?: return ToolResult(
                false,
                if (app.isBlank()) {
                    "No repliable notification is available right now."
                } else {
                    "No repliable notification from '$app' is available right now."
                },
            )
        val sent = ActiveNotificationStore.reply(context, item.key, message)
        return if (sent) {
            ToolResult(true, "Replied to ${item.appLabel}.", item.appLabel)
        } else {
            ToolResult(
                false,
                "Could not reply to ${item.appLabel}. The notification may no longer accept replies.",
                item.appLabel,
            )
        }
    }

    private fun sendChat(context: Context, args: Map<String, String>): ToolResult {
        val app = args["app"]?.trim()?.lowercase(Locale.US).orEmpty()
        val contact = args["contact"]?.trim().orEmpty()
        val message = args["message"]?.trim().orEmpty()
        if (app.isEmpty()) {
            return ToolResult(false, "Choose an app: whatsapp, telegram, signal, or discord.")
        }
        if (contact.isEmpty()) {
            return ToolResult(false, "A contact phone number or username is required.")
        }
        if (message.isEmpty()) {
            return ToolResult(false, "A message is required.")
        }

        val phone = phoneDigits(contact)
        var copiedForPaste = false
        val intent = when (app) {
            "whatsapp" -> {
                if (phone != null) {
                    viewIntent("https://wa.me/$phone?text=${Uri.encode(message)}")
                } else {
                    sendIntent("com.whatsapp", message)
                }
            }

            "telegram" -> {
                val handle = contact.removePrefix("@").trim()
                if (handle.isNotEmpty() && !WHITESPACE_REGEX.containsMatchIn(handle)) {
                    viewIntent("https://t.me/${Uri.encode(handle)}?text=${Uri.encode(message)}")
                } else {
                    sendIntent("org.telegram.messenger", message)
                }
            }

            "signal" -> {
                if (phone != null) {
                    copiedForPaste = copyToClipboard(context, "Signal message", message)
                    viewIntent("https://signal.me/#p/+$phone")
                } else {
                    sendIntent("org.thoughtcrime.securesms", message)
                }
            }

            "discord" -> sendIntent("com.discord", message)

            else -> return ToolResult(
                false,
                "Unsupported app '$app'. Use whatsapp, telegram, signal, or discord.",
            )
        }

        val appDisplayName = app.replaceFirstChar { it.uppercase(Locale.US) }
        val successMessage = if (copiedForPaste) {
            "Opened $appDisplayName for $contact. Your message was copied to the clipboard so you can paste and send it."
        } else {
            "Opened $appDisplayName for $contact."
        }
        return launchOrShare(
            context = context,
            intent = intent,
            message = message,
            successMessage = successMessage,
        )
    }

    private fun copyToClipboard(context: Context, label: String, text: String): Boolean =
        runCatching {
            val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@runCatching false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)

    private fun listChatNotifications(context: Context): ToolResult {
        if (!ActiveNotificationStore.hasAccess(context)) {
            return ToolResult(
                false,
                "Listing notifications needs notification listener access. Grant it in Settings first.",
            )
        }
        val repliable = ActiveNotificationStore.all().filter { hasReplyAction(it) }
        if (repliable.isEmpty()) {
            return ToolResult(true, "No repliable notifications right now.", "")
        }
        val data = repliable
            .joinToString("\n") { "${it.appLabel}: ${it.title}" }
            .take(MAX_NOTIFICATION_LIST_CHARS)
        return ToolResult(
            true,
            "${repliable.size} repliable notification(s) available.",
            data,
        )
    }

    private fun hasReplyAction(item: ActiveNotificationItem): Boolean =
        item.actions.any { !it.remoteInputs.isNullOrEmpty() }

    private fun matchesApp(item: ActiveNotificationItem, app: String): Boolean {
        val target = normalize(app)
        if (target.isEmpty()) return true
        val packageName = normalize(item.packageName)
        val label = normalize(item.appLabel)
        if (packageName == target || label == target) return true
        val mappedPackage = APP_PACKAGES[target]
        if (mappedPackage != null) {
            val mapped = normalize(mappedPackage)
            if (packageName == mapped || packageName.startsWith("$mapped.")) return true
            if (packageName.contains(mapped)) return true
        }
        return packageName.contains(target) || label.contains(target)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.US)
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")

    private fun phoneDigits(contact: String): String? {
        val trimmed = contact.trim()
        if (trimmed.isEmpty() || !PHONE_CONTACT_REGEX.matches(trimmed)) return null
        val digits = trimmed.filter { it.isDigit() }
        return digits.takeIf { it.length in 5..15 }
    }

    private fun sendIntent(packageName: String, message: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun launchOrShare(
        context: Context,
        intent: Intent,
        message: String,
        successMessage: String,
    ): ToolResult {
        val primary = runCatching { context.startActivity(intent) }
        if (primary.isSuccess) return ToolResult(true, successMessage)

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = runCatching {
            context.startActivity(
                Intent.createChooser(share, "Send message").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
        return if (fallback.isSuccess) {
            ToolResult(
                true,
                "Opened a share chooser because the selected app could not be launched directly.",
            )
        } else {
            ToolResult(
                false,
                "Could not open a messaging app: ${primary.exceptionOrNull()?.message ?: "unknown error"}",
            )
        }
    }
}
