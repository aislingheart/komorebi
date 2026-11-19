package com.sameerasw.essentials.assistant

data class AiMessage(
    val role: String,
    val content: String,
)

data class AiToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class AiResponse(
    val text: String?,
    val toolCalls: List<AiToolCall>,
    val provider: String,
    val error: String? = null,
)

data class AiStatus(
    val mode: AssistantAiMode,
    val available: Boolean,
    val detail: String,
)
