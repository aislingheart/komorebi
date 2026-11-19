package com.sameerasw.essentials.assistant

import android.content.Context

object AiProviderManager {
    fun mode(context: Context): AssistantAiMode = AssistantAiSettings.getMode(context)

    fun deterministicOnly(context: Context): Boolean = mode(context) == AssistantAiMode.OFF

    suspend fun status(context: Context): AiStatus = providerFor(mode(context)).status(context)

    suspend fun complete(
        context: Context,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec> = emptyList(),
        onToken: ((String) -> Unit)? = null,
    ): AiResponse =
        providerFor(mode(context)).complete(
            context = context,
            systemPrompt = systemPrompt,
            messages = messages,
            tools = tools,
            onToken = onToken,
        )

    /**
     * ON_DEVICE uses AICore and must be invoked while the app is in the foreground.
     */
    private fun providerFor(mode: AssistantAiMode): AiProvider =
        when (mode) {
            AssistantAiMode.OFF -> OffAiProvider
            AssistantAiMode.ON_DEVICE -> OnDeviceAiProvider
            AssistantAiMode.BYOK -> ByokAiProvider
        }
}
