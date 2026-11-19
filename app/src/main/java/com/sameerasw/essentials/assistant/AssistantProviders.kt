/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: AssistantProviders.kt
 * Description: Provider presets and API styles for bring-your-own-key backends.
 */

package com.sameerasw.essentials.assistant

enum class AssistantApiStyle {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
}

data class AssistantProviderPreset(
    val id: String,
    val label: String,
    val style: AssistantApiStyle,
    val baseUrl: String,
    val model: String,
    val requiresKey: Boolean = true,
)

object AssistantProviderPresets {
    val all: List<AssistantProviderPreset> = listOf(
        AssistantProviderPreset(
            id = "openai",
            label = "OpenAI",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
        ),
        AssistantProviderPreset(
            id = "openrouter",
            label = "OpenRouter",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://openrouter.ai/api/v1",
            model = "openai/gpt-4o-mini",
        ),
        AssistantProviderPreset(
            id = "groq",
            label = "Groq",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
        ),
        AssistantProviderPreset(
            id = "deepseek",
            label = "DeepSeek",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
        ),
        AssistantProviderPreset(
            id = "mistral",
            label = "Mistral",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.mistral.ai/v1",
            model = "mistral-large-latest",
        ),
        AssistantProviderPreset(
            id = "xai",
            label = "xAI (Grok)",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.x.ai/v1",
            model = "grok-3-mini",
        ),
        AssistantProviderPreset(
            id = "together",
            label = "Together AI",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.together.xyz/v1",
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        ),
        AssistantProviderPreset(
            id = "fireworks",
            label = "Fireworks AI",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.fireworks.ai/inference/v1",
            model = "accounts/fireworks/models/llama-v3p3-70b-instruct",
        ),
        AssistantProviderPreset(
            id = "perplexity",
            label = "Perplexity",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "https://api.perplexity.ai",
            model = "sonar",
        ),
        AssistantProviderPreset(
            id = "anthropic",
            label = "Anthropic (Claude)",
            style = AssistantApiStyle.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-3-5-sonnet-latest",
        ),
        AssistantProviderPreset(
            id = "google",
            label = "Google AI Studio (Gemini)",
            style = AssistantApiStyle.GOOGLE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-2.0-flash",
        ),
        AssistantProviderPreset(
            id = "ollama",
            label = "Ollama (LAN)",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "http://127.0.0.1:11434/v1",
            model = "llama3.2",
            requiresKey = false,
        ),
        AssistantProviderPreset(
            id = "lmstudio",
            label = "LM Studio",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "http://127.0.0.1:1234/v1",
            model = "local-model",
            requiresKey = false,
        ),
        AssistantProviderPreset(
            id = "custom",
            label = "Custom (OpenAI-compatible)",
            style = AssistantApiStyle.OPENAI,
            baseUrl = "",
            model = "",
        ),
    )

    fun byId(id: String): AssistantProviderPreset? = all.firstOrNull { it.id == id }

    fun infer(baseUrl: String, model: String): AssistantProviderPreset? {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isEmpty()) return null
        return all.firstOrNull { preset ->
            preset.baseUrl.isNotBlank() &&
                preset.baseUrl.trimEnd('/').equals(normalized, ignoreCase = true) &&
                (preset.model.isBlank() || preset.model.equals(model, ignoreCase = true))
        }
    }
}
