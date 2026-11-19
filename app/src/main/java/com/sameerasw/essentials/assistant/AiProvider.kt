package com.sameerasw.essentials.assistant

import android.content.Context

interface AiProvider {
    val id: String

    suspend fun status(context: Context): AiStatus

    suspend fun complete(
        context: Context,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec> = emptyList(),
        onToken: ((String) -> Unit)? = null,
    ): AiResponse
}
