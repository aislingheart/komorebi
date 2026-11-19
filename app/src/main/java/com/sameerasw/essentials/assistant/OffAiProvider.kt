package com.sameerasw.essentials.assistant

import android.content.Context

object OffAiProvider : AiProvider {
    override val id: String = "off"

    override suspend fun status(context: Context): AiStatus =
        AiStatus(
            mode = AssistantAiMode.OFF,
            available = false,
            detail = "Deterministic mode",
        )

    override suspend fun complete(
        context: Context,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse =
        AiResponse(
            text = null,
            toolCalls = emptyList(),
            provider = id,
            error = "AI is disabled",
        )
}
