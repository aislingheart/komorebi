package com.sameerasw.essentials.assistant

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object ByokAiProvider : AiProvider {
    override val id: String = "byok"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun status(context: Context): AiStatus {
        if (!AssistantAiSettings.isConfigured(context)) {
            return AiStatus(
                mode = AssistantAiMode.BYOK,
                available = false,
                detail = "Add a base URL, model, and API key in settings",
            )
        }
        val preset = AssistantProviderPresets.byId(AssistantAiSettings.getProviderId(context))
        val label = preset?.label ?: AssistantAiSettings.getApiStyle(context).name
        return AiStatus(
            mode = AssistantAiMode.BYOK,
            available = true,
            detail = "${AssistantAiSettings.getModel(context)} via $label",
        )
    }

    override suspend fun complete(
        context: Context,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse =
        withContext(Dispatchers.IO) {
            val baseUrl = AssistantAiSettings.getBaseUrl(context)
            val model = AssistantAiSettings.getModel(context)
            val apiKey = AssistantAiSettings.readApiKey(context)
            val preset = AssistantProviderPresets.byId(AssistantAiSettings.getProviderId(context))
            val requiresKey = preset?.requiresKey != false && !AssistantAiSettings.isLocalEndpoint(baseUrl)

            if (baseUrl.isBlank() || model.isBlank() || (requiresKey && apiKey.isBlank())) {
                return@withContext errorResponse("Base URL, model, and API key are required")
            }

            when (AssistantAiSettings.getApiStyle(context)) {
                AssistantApiStyle.OPENAI -> completeOpenAi(baseUrl, model, apiKey, systemPrompt, messages, tools, onToken)
                AssistantApiStyle.ANTHROPIC -> completeAnthropic(baseUrl, model, apiKey, systemPrompt, messages, tools, onToken)
                AssistantApiStyle.GOOGLE -> completeGoogle(baseUrl, model, apiKey, systemPrompt, messages, tools, onToken)
            }
        }

    private suspend fun completeOpenAi(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse {
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.4)
            addProperty("stream", false)
            add("messages", openAiMessages(systemPrompt, messages))
            if (tools.isNotEmpty()) add("tools", openAiTools(tools))
        }
        val request = Request.Builder()
            .url(AssistantAiSettings.normalizeBaseUrl(baseUrl))
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return execute(request) { body ->
            val root = JsonParser.parseString(body).asJsonObject
            val message = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?: return@execute errorResponse("Response contained no choices")
            val text = message.get("content")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            val toolCalls = message.getAsJsonArray("tool_calls")?.mapNotNull { element ->
                val call = element.asJsonObject
                val function = call.getAsJsonObject("function") ?: return@mapNotNull null
                AiToolCall(
                    id = call.get("id")?.takeUnless { it.isJsonNull }?.asString ?: "",
                    name = function.get("name")?.takeUnless { it.isJsonNull }?.asString ?: "",
                    argumentsJson = function.get("arguments")?.let {
                        if (it.isJsonPrimitive) it.asString else it.toString()
                    } ?: "{}",
                )
            } ?: emptyList()
            if (text != null) onToken?.invoke(text)
            AiResponse(text = text, toolCalls = toolCalls, provider = id)
        }
    }

    private suspend fun completeAnthropic(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse {
        val extraSystem = messages.filter { it.role == "system" && it.content.isNotBlank() }
            .joinToString("\n") { it.content }
        val normalizedTurns = normalizeAlternatingTurns(
            messages = messages,
            assistantRole = "assistant",
            userRole = "user",
        )
        val chat = normalizedTurns.map { (role, content) ->
            JsonObject().apply {
                addProperty("role", role)
                addProperty("content", content)
            }
        }
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 2048)
            addProperty("temperature", 0.4)
            addProperty("system", listOf(systemPrompt, extraSystem).filter { it.isNotBlank() }.joinToString("\n\n"))
            add("messages", JsonArray().apply { chat.forEach(::add) })
            if (tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    tools.forEach { tool ->
                        add(JsonObject().apply {
                            addProperty("name", tool.name)
                            addProperty("description", tool.description)
                            add("input_schema", parseSchema(tool.parametersJson))
                        })
                    }
                })
            }
        }
        val url = trimBase(baseUrl).let { if (it.endsWith("/messages")) it else "$it/messages" }
        val request = Request.Builder()
            .url(url)
            .apply { if (apiKey.isNotBlank()) header("x-api-key", apiKey) }
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return execute(request) { body ->
            val root = JsonParser.parseString(body).asJsonObject
            val content = root.getAsJsonArray("content") ?: return@execute errorResponse("Response contained no content")
            val text = content.mapNotNull { element ->
                val block = element.asJsonObject
                if (block.get("type")?.asString == "text") block.get("text")?.asString else null
            }.joinToString("\n").takeIf { it.isNotBlank() }
            val toolCalls = content.mapNotNull { element ->
                val block = element.asJsonObject
                if (block.get("type")?.asString != "tool_use") return@mapNotNull null
                AiToolCall(
                    id = block.get("id")?.asString ?: "",
                    name = block.get("name")?.asString ?: "",
                    argumentsJson = block.get("input")?.toString() ?: "{}",
                )
            }
            if (text != null) onToken?.invoke(text)
            AiResponse(text = text, toolCalls = toolCalls, provider = id)
        }
    }

    private suspend fun completeGoogle(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<AiMessage>,
        tools: List<AiToolSpec>,
        onToken: ((String) -> Unit)?,
    ): AiResponse {
        val extraSystem = messages.filter { it.role == "system" && it.content.isNotBlank() }
            .joinToString("\n") { it.content }
        val normalizedTurns = normalizeAlternatingTurns(
            messages = messages,
            assistantRole = "model",
            userRole = "user",
        )
        val contents = normalizedTurns.map { (role, content) ->
            JsonObject().apply {
                addProperty("role", role)
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", content) })
                })
            }
        }
        val payload = JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("text", listOf(systemPrompt, extraSystem).filter { it.isNotBlank() }.joinToString("\n\n"))
                    })
                })
            })
            add("contents", JsonArray().apply { contents.forEach(::add) })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.4)
                addProperty("maxOutputTokens", 2048)
            })
            if (tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    add(JsonObject().apply {
                        add("functionDeclarations", JsonArray().apply {
                            tools.forEach { tool ->
                                add(JsonObject().apply {
                                    addProperty("name", tool.name)
                                    addProperty("description", tool.description)
                                    add("parameters", parseSchema(tool.parametersJson))
                                })
                            }
                        })
                    })
                })
            }
        }
        val base = trimBase(baseUrl)
        val encodedModel = URLEncoder.encode(model, "UTF-8")
        val url = if (base.contains(":generateContent")) {
            base
        } else {
            "$base/models/$encodedModel:generateContent"
        }
        val request = Request.Builder()
            .url(url)
            .apply { if (apiKey.isNotBlank()) header("x-goog-api-key", apiKey) }
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return execute(request) { body ->
            val root = JsonParser.parseString(body).asJsonObject
            val blockReason = root.getAsJsonObject("promptFeedback")?.get("blockReason")?.asString
            if (!blockReason.isNullOrBlank()) return@execute errorResponse("Prompt blocked: $blockReason")
            val candidate = root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?: return@execute errorResponse("Response contained no candidates")
            val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?: return@execute errorResponse("Response contained no parts")
            val text = parts.mapNotNull { part ->
                part.asJsonObject.get("text")?.takeUnless { it.isJsonNull }?.asString
            }.joinToString("\n").takeIf { it.isNotBlank() }
            val toolCalls = parts.mapIndexedNotNull { index, part ->
                val call = part.asJsonObject.getAsJsonObject("functionCall") ?: return@mapIndexedNotNull null
                AiToolCall(
                    id = "call_$index",
                    name = call.get("name")?.asString ?: "",
                    argumentsJson = call.get("args")?.toString() ?: "{}",
                )
            }
            if (text != null) onToken?.invoke(text)
            AiResponse(text = text, toolCalls = toolCalls, provider = id)
        }
    }

    private suspend fun execute(request: Request, parse: (String) -> AiResponse): AiResponse =
        try {
            val call = httpClient.newCall(request)
            val response = suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                })
            }
            response.use { res ->
                val body = res.body.string()
                if (!res.isSuccessful) {
                    errorResponse("HTTP ${res.code}: ${body.take(300)}")
                } else {
                    parse(body)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResponse(e.message ?: "Request failed")
        }

    internal fun normalizeAlternatingTurns(
        messages: List<AiMessage>,
        assistantRole: String,
        userRole: String,
    ): List<Pair<String, String>> {
        val merged = mutableListOf<Pair<String, String>>()
        messages
            .filter { it.role != "system" && it.content.isNotBlank() }
            .forEach { message ->
                val role = if (message.role == "assistant") assistantRole else userRole
                val previous = merged.lastOrNull()
                if (previous != null && previous.first == role) {
                    merged[merged.lastIndex] = role to (previous.second + "\n\n" + message.content)
                } else {
                    merged += role to message.content
                }
            }
        return merged
    }

    private fun openAiMessages(systemPrompt: String, messages: List<AiMessage>): JsonArray =
        JsonArray().apply {
            if (systemPrompt.isNotBlank()) {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
            }
            messages
                .filter { it.content.isNotBlank() }
                .forEach { message ->
                    add(JsonObject().apply {
                        addProperty("role", message.role)
                        addProperty("content", message.content)
                    })
                }
        }

    private fun openAiTools(tools: List<AiToolSpec>): JsonArray =
        JsonArray().apply {
            tools.forEach { tool ->
                add(JsonObject().apply {
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", tool.name)
                        addProperty("description", tool.description)
                        add("parameters", parseSchema(tool.parametersJson))
                    })
                })
            }
        }

    internal fun parseSchema(schema: String): JsonElement {
        val parsed = runCatching { JsonParser.parseString(schema) }.getOrNull()
        val obj = if (parsed != null && parsed.isJsonObject) {
            parsed.asJsonObject.deepCopy()
        } else {
            JsonObject()
        }
        if (!obj.has("type")) {
            obj.addProperty("type", "object")
        }
        if (!obj.has("properties")) {
            obj.add("properties", JsonObject())
        }
        return obj
    }

    private fun trimBase(baseUrl: String): String {
        var url = baseUrl.trim()
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    private fun errorResponse(message: String): AiResponse =
        AiResponse(text = null, toolCalls = emptyList(), provider = id, error = message)
}
