package com.sameerasw.essentials.assistant

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini Nano provider backed by ML Kit GenAI prompt.
 *
 * AICore inference is foreground-only: call [complete] from a foreground
 * activity or another context that is allowed to keep AICore active.
 */
object OnDeviceAiProvider : AiProvider {
    override val id: String = "on_device"

    override suspend fun status(context: Context): AiStatus =
        withContext(Dispatchers.IO) {
            try {
                when (val featureStatus = Generation.getClient().checkStatus()) {
                    FeatureStatus.AVAILABLE -> AiStatus(
                        mode = AssistantAiMode.ON_DEVICE,
                        available = true,
                        detail = "Gemini Nano is ready",
                    )
                    FeatureStatus.DOWNLOADABLE -> AiStatus(
                        mode = AssistantAiMode.ON_DEVICE,
                        available = false,
                        detail = "Gemini Nano is available for download",
                    )
                    FeatureStatus.DOWNLOADING -> AiStatus(
                        mode = AssistantAiMode.ON_DEVICE,
                        available = false,
                        detail = "Gemini Nano is downloading",
                    )
                    FeatureStatus.UNAVAILABLE -> AiStatus(
                        mode = AssistantAiMode.ON_DEVICE,
                        available = false,
                        detail = "Gemini Nano is not available on this device",
                    )
                    else -> AiStatus(
                        mode = AssistantAiMode.ON_DEVICE,
                        available = false,
                        detail = "Unknown Gemini Nano status ($featureStatus)",
                    )
                }
            } catch (e: Exception) {
                AiStatus(
                    mode = AssistantAiMode.ON_DEVICE,
                    available = false,
                    detail = e.message ?: "Gemini Nano is unavailable",
                )
            }
        }

    suspend fun downloadModel(
        context: Context,
        onProgress: (String) -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Preparing Gemini Nano download…")
                Generation.getClient().download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadProgress -> {
                            val megabytes = downloadStatus.totalBytesDownloaded / (1024.0 * 1024.0)
                            onProgress(
                                "Downloading Gemini Nano… ${String.format(Locale.US, "%.1f", megabytes)} MB",
                            )
                        }
                        is DownloadStatus.DownloadCompleted ->
                            onProgress("Gemini Nano download complete")
                        is DownloadStatus.DownloadFailed ->
                            onProgress(
                                "Gemini Nano download failed: ${downloadStatus.e.message ?: "unknown error"}",
                            )
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                onProgress("Gemini Nano download failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    override suspend fun complete(
        context: Context,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(systemPrompt, messages, tools)
                val request = generateContentRequest(TextPart(prompt)) {
                    temperature = 0.3f
                }
                val response = Generation.getClient().generateContent(request)
                val text = response.candidates.firstOrNull()?.text
                if (text != null) onToken?.invoke(text)
                AiResponse(
                    text = text,
                    toolCalls = emptyList(),
                    provider = id,
                )
            } catch (e: Exception) {
                AiResponse(
                    text = null,
                    toolCalls = emptyList(),
                    provider = id,
                    error = e.message ?: "On-device generation failed",
                )
            }
        }

    private fun buildPrompt(
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
    ): String =
        buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt.trim())
            }
            if (messages.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Conversation:\n")
                messages.forEach { message ->
                    append(message.role)
                    append(": ")
                    append(message.content)
                    append('\n')
                }
            }
            if (tools.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Available tools (schemas are informational only):\n")
                tools.forEach { tool ->
                    append("- ")
                    append(tool.name)
                    append(": ")
                    append(tool.description)
                    append("\n  parameters: ")
                    append(tool.parametersJson)
                    append('\n')
                }
            }
        }.trim()
}
