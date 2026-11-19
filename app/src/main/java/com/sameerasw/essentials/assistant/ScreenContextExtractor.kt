/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: ScreenContextExtractor.kt
 * Description: Compact snapshot of the foreground screen for assistant context.
 */

package com.sameerasw.essentials.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenContext(
    val packageName: String,
    val windowTitle: String,
    val text: String,
    val selectedText: String?,
    val capturedAt: Long,
)

object ScreenContextExtractor {

    private const val MAX_NODES = 400
    private const val MAX_CHARS = 6000

    @Volatile
    private var current: ScreenContext? = null

    fun capture(service: AccessibilityService) {
        current = runCatching { read(service) }.getOrNull()
    }

    fun current(): ScreenContext? = current

    fun clear() {
        current = null
    }

    fun concisePrompt(): String? {
        val context = current ?: return null
        return buildString {
            append("Foreground app: ")
            append(context.packageName)
            if (context.windowTitle.isNotBlank()) {
                append(" (")
                append(context.windowTitle)
                append(")")
            }
            context.selectedText?.takeIf { it.isNotBlank() }?.let {
                append("\nSelected text: ")
                append(it.take(1500))
            }
            if (context.text.isNotBlank()) {
                append("\nVisible screen text:\n")
                append(context.text)
            }
        }
    }

    private fun read(service: AccessibilityService): ScreenContext? {
        val root = service.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == "com.android.systemui") return null

        val windowTitle = runCatching {
            service.windows.firstOrNull { it.isActive }?.title?.toString().orEmpty()
        }.getOrDefault("")

        val chunks = ArrayList<String>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var totalChars = 0
        while (queue.isNotEmpty() && visited < MAX_NODES && totalChars < MAX_CHARS) {
            val node = queue.removeFirst()
            visited++
            if (node.isPassword) continue
            val label = node.text?.toString()?.trim().orEmpty()
                .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
            if (label.isNotEmpty() && seen.add(label)) {
                chunks += label
                totalChars += label.length + 1
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        val selectedText = runCatching {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return@runCatching null
            if (focused.isPassword) return@runCatching null
            val value = focused.text?.toString() ?: return@runCatching null
            val start = focused.textSelectionStart
            val end = focused.textSelectionEnd
            if (start >= 0 && end > start && end <= value.length) value.substring(start, end) else null
        }.getOrNull()

        val text = chunks.joinToString("\n").take(MAX_CHARS)
        if (text.isBlank() && selectedText.isNullOrBlank()) return null
        return ScreenContext(
            packageName = packageName,
            windowTitle = windowTitle,
            text = text,
            selectedText = selectedText,
            capturedAt = System.currentTimeMillis(),
        )
    }
}
