/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: AssistantTool.kt
 * Description: Core data models for deterministic assistant tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: String? = null,
)

data class AssistantTool(
    val id: String,
    val title: String,
    val description: String,
    val keywords: List<String>,
    val parametersJson: String,
    val requiresConfirmation: Boolean = false,
    val execute: suspend (Context, Map<String, String>) -> ToolResult,
)
