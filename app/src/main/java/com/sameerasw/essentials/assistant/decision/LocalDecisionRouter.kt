/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Decision Model
 * File: LocalDecisionRouter.kt
 * Description: Unified local decision router dispatching unmatched search queries across:
 *              - Off
 *              - Built-in Fast (BM25 + Fuzzy)
 *              - TinyDecide 10.4M Q4 / 13.8M Full (2-stage neural choice + span on CPU)
 *              - Self-hosted Local /v1/systemone (Kev / Laya)
 */

package com.sameerasw.essentials.assistant.decision

import android.content.Context
import com.sameerasw.essentials.assistant.LocalIntentDecision
import com.sameerasw.essentials.assistant.LocalIntentDecisionEngine
import com.sameerasw.essentials.assistant.tools.AssistantTool
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DecisionRouteResult(
    val decisions: List<LocalIntentDecision>,
    val activeModel: LocalDecisionModel,
    val statusNote: String? = null,
    val needsDownload: Boolean = false,
    val latencyMs: Long = 0L,
)

object LocalDecisionRouter {
    internal data class ToolCategory(
        val id: String,
        val promptLabel: String,
        val toolIds: List<String>,
        val extractSpan: Boolean = false,
    )

    internal val CATEGORIES: List<ToolCategory> = listOf(
        ToolCategory(
            id = "toggles",
            promptLabel = "device toggle like wifi, bluetooth, flashlight, dark mode, do not disturb, battery saver",
            toolIds = listOf(
                "wifi", "bluetooth", "mobile_data", "airplane_mode", "hotspot",
                "flashlight", "auto_rotate", "battery_saver", "dark_mode", "dnd", "caffeinate",
            ),
        ),
        ToolCategory(
            id = "audio_display",
            promptLabel = "volume, sound, screen brightness, screen timeout, screenshot, lock screen, or screen summary",
            toolIds = listOf(
                "volume", "brightness", "screen_timeout", "screenshot",
                "lock_screen", "split_screen", "copy_screen_text", "summarize_screen", "scan_screen_qr",
            ),
        ),
        ToolCategory(
            id = "time",
            promptLabel = "timer, countdown, alarm, or wake up time",
            toolIds = listOf("timer", "alarm", "add_time", "open_timers", "open_alarms"),
            extractSpan = true,
        ),
        ToolCategory(
            id = "media",
            promptLabel = "music, song, podcast, play, pause, skip, or previous track",
            toolIds = listOf("media_play", "media_pause", "media_toggle", "media_next", "media_previous", "play_media"),
            extractSpan = true,
        ),
        ToolCategory(
            id = "tasks_calendar",
            promptLabel = "task, reminder, todo list, calendar event, meeting, or free schedule slots",
            toolIds = listOf(
                "add_task", "complete_task", "list_tasks", "delete_task",
                "create_event", "update_event", "delete_event", "find_free_slots",
            ),
            extractSpan = true,
        ),
        ToolCategory(
            id = "comms_health",
            promptLabel = "notifications, chat reply, sleep stats, step count, or health summary",
            toolIds = listOf(
                "notification_digest", "reply_last", "send_chat", "list_chat_notifications",
                "health_summary", "sleep_stats", "steps",
            ),
        ),
        ToolCategory(
            id = "apps_web_device",
            promptLabel = "web search, open url, device info, settings, freeze app, clipboard, or routine",
            toolIds = listOf(
                "web_search", "fetch_url", "device_info", "open_settings",
                "freeze_app", "unfreeze_app", "app_info", "share_clipboard",
                "run_routine", "list_routines",
            ),
            extractSpan = true,
        ),
    )

    private const val NONE_LABEL = "none of these or unrelated"
    private const val MIN_CATEGORY_PROB = 0.16f
    private const val MIN_TOOL_PROB = 0.18f

    suspend fun route(
        context: Context,
        query: String,
        model: LocalDecisionModel,
        localEndpointUrl: String = LocalDecisionModel.DEFAULT_LOCAL_SYSTEMONE_URL,
        feedbackWeights: Map<String, Float> = emptyMap(),
        limit: Int = 3,
        tools: List<AssistantTool> = AssistantToolRegistry.all(),
    ): DecisionRouteResult {
        val trimmed = query.trim()
        if (trimmed.length < 2 || model == LocalDecisionModel.OFF) {
            return DecisionRouteResult(emptyList(), model)
        }

        return when (model) {
            LocalDecisionModel.OFF -> DecisionRouteResult(emptyList(), model)

            LocalDecisionModel.BUILT_IN_LEXICAL -> {
                val t0 = System.nanoTime()
                val decisions = LocalIntentDecisionEngine.decide(
                    query = trimmed,
                    tools = tools,
                    feedbackWeights = feedbackWeights,
                    limit = limit,
                )
                val ms = (System.nanoTime() - t0) / 1_000_000L
                DecisionRouteResult(
                    decisions = decisions,
                    activeModel = model,
                    latencyMs = ms,
                )
            }

            LocalDecisionModel.TINYDECIDE_Q4,
            LocalDecisionModel.TINYDECIDE_FULL,
            -> routeWithTinyDecide(
                context = context,
                query = trimmed,
                model = model,
                feedbackWeights = feedbackWeights,
                limit = limit,
                tools = tools,
            )

            LocalDecisionModel.LOCAL_SYSTEMONE -> routeWithSystemOne(
                query = trimmed,
                endpointUrl = localEndpointUrl,
                feedbackWeights = feedbackWeights,
                limit = limit,
                tools = tools,
            )
        }
    }

    private suspend fun routeWithTinyDecide(
        context: Context,
        query: String,
        model: LocalDecisionModel,
        feedbackWeights: Map<String, Float>,
        limit: Int,
        tools: List<AssistantTool>,
    ): DecisionRouteResult {
        val engine = LocalDecisionModelManager.getOrLoadTinyDecide(context, model)
        if (engine == null) {
            return DecisionRouteResult(
                decisions = emptyList(),
                activeModel = model,
                statusNote = "${model.shortLabel} (${model.sizeBadge}) is not downloaded yet",
                needsDownload = true,
            )
        }
        return withContext(Dispatchers.Default) {
            routeWithNeuralBackend(
                query = query,
                model = model,
                tools = tools,
                feedbackWeights = feedbackWeights,
                limit = limit,
                invoke = { req -> engine.answer(req) },
            )
        }
    }

    private suspend fun routeWithSystemOne(
        query: String,
        endpointUrl: String,
        feedbackWeights: Map<String, Float>,
        limit: Int,
        tools: List<AssistantTool>,
    ): DecisionRouteResult {
        val t0 = System.nanoTime()
        return try {
            routeWithNeuralBackend(
                query = query,
                model = LocalDecisionModel.LOCAL_SYSTEMONE,
                tools = tools,
                feedbackWeights = feedbackWeights,
                limit = limit,
                invoke = { req ->
                    LocalDecisionModelManager.queryLocalSystemOne(endpointUrl, req).getOrThrow()
                },
            )
        } catch (e: Exception) {
            // Graceful offline fallback to built-in lexical if the local Kev/Laya server isn't running
            val fallback = LocalIntentDecisionEngine.decide(
                query = query,
                tools = tools,
                feedbackWeights = feedbackWeights,
                limit = limit,
            )
            val ms = (System.nanoTime() - t0) / 1_000_000L
            DecisionRouteResult(
                decisions = fallback,
                activeModel = LocalDecisionModel.LOCAL_SYSTEMONE,
                statusNote = "Local server unreachable (${e.message?.take(48) ?: "offline"}) · fallback to Fast Local",
                latencyMs = ms,
            )
        }
    }

    internal suspend fun routeWithNeuralBackend(
        query: String,
        model: LocalDecisionModel,
        tools: List<AssistantTool>,
        feedbackWeights: Map<String, Float>,
        limit: Int,
        invoke: suspend (TinyDecideRequest) -> TinyDecideResponse,
    ): DecisionRouteResult {
        val toolMap = tools.associateBy { it.id }
        val activeCategories = CATEGORIES.filter { cat -> cat.toolIds.any { it in toolMap } }
        if (activeCategories.isEmpty()) return DecisionRouteResult(emptyList(), model)

        val totalStartNs = System.nanoTime()

        // Stage 1: Classify into one of the 7 compact categories + "none"
        val catOptions = activeCategories.map { it.promptLabel } + NONE_LABEL
        val stage1Resp = invoke(
            TinyDecideRequest(
                state = query,
                questions = listOf(
                    TinyDecideQuestion(
                        type = "choice",
                        text = "Which phone action category best fits this request?",
                        options = catOptions,
                    ),
                ),
            ),
        )
        val catAns = stage1Resp.answers.firstOrNull()
        val catProbs = catAns?.probs ?: FloatArray(0)
        if (catProbs.size != catOptions.size) {
            return DecisionRouteResult(emptyList(), model, latencyMs = stage1Resp.latencyMs)
        }

        val noneCatProb = catProbs.last()
        val bestCatIdx = (0 until activeCategories.size).maxByOrNull { catProbs[it] } ?: return DecisionRouteResult(emptyList(), model)
        val bestCatProb = catProbs[bestCatIdx]
        if (noneCatProb > bestCatProb && noneCatProb >= 0.45f) {
            val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000L
            return DecisionRouteResult(emptyList(), model, latencyMs = totalMs)
        }
        if (bestCatProb < MIN_CATEGORY_PROB) {
            val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000L
            return DecisionRouteResult(emptyList(), model, latencyMs = totalMs)
        }

        // Gather candidate tools from top category (+ runner-up if close) + any high-signal lexical hints
        val candidateIds = LinkedHashSet<String>()
        val winningCat = activeCategories[bestCatIdx]
        winningCat.toolIds.filter { it in toolMap }.forEach {
            if (candidateIds.size < 11) candidateIds.add(it)
        }

        // Include runner-up category tools if close and total options stay compact
        val secondCatIdx = (0 until activeCategories.size)
            .filter { it != bestCatIdx }
            .maxByOrNull { catProbs[it] }
        if (secondCatIdx != null && catProbs[secondCatIdx] >= bestCatProb * 0.65f) {
            for (id in activeCategories[secondCatIdx].toolIds) {
                if (id in toolMap && candidateIds.size < 11) candidateIds.add(id)
            }
        }
        // Also ensure strong lexical hits aren't missed if they were in another category
        val lexicalHints = LocalIntentDecisionEngine.decide(
            query = query,
            tools = tools,
            feedbackWeights = feedbackWeights,
            limit = 2,
        )
        for (hint in lexicalHints) {
            if (hint.confidence >= 0.45f && candidateIds.size < 12) {
                candidateIds.add(hint.tool.id)
            }
        }

        val candidates = candidateIds.mapNotNull { toolMap[it] }
        if (candidates.isEmpty()) return DecisionRouteResult(emptyList(), model)

        // Stage 2: Pick exact tool (+ optional span extraction in the same single encoder pass!)
        val toolOptions = candidates.map { tool -> "${tool.title} (${tool.description.take(32)})" } + NONE_LABEL
        val stage2Questions = buildList {
            add(
                TinyDecideQuestion(
                    type = "choice",
                    text = "Which exact action should be performed?",
                    options = toolOptions,
                ),
            )
            if (winningCat.extractSpan) {
                add(
                    TinyDecideQuestion(
                        type = "span",
                        text = "What is the main subject, title, or target text in the request?",
                    ),
                )
            }
        }
        val stage2Resp = invoke(TinyDecideRequest(state = query, questions = stage2Questions))
        val toolAns = stage2Resp.answers.firstOrNull()
        val toolProbs = toolAns?.probs ?: FloatArray(0)
        if (toolProbs.size != toolOptions.size) {
            val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000L
            return DecisionRouteResult(emptyList(), model, latencyMs = totalMs)
        }

        val spanAns = stage2Resp.answers.getOrNull(1)
        val spanHint = if (spanAns != null && spanAns.type == "span" && spanAns.pPresent >= 0.45f && spanAns.text.isNotBlank()) {
            spanAns.text
        } else {
            null
        }

        val noneToolProb = toolProbs.last()
        val lexicalByTool = lexicalHints.associateBy { it.tool.id }
        val scored = ArrayList<LocalIntentDecision>(candidates.size)

        for (i in candidates.indices) {
            val tool = candidates[i]
            val neuralProb = toolProbs[i]
            if (neuralProb < MIN_TOOL_PROB) continue
            if (noneToolProb > neuralProb && noneToolProb >= 0.50f) continue

            val feedbackBoost = LocalIntentDecisionEngine.feedbackBoostForQuery(query, tool.id, feedbackWeights)
            val lexicalBonus = (lexicalByTool[tool.id]?.confidence ?: 0f) * 0.12f
            val combinedConfidence = (neuralProb * 0.85f + bestCatProb * 0.15f + feedbackBoost + lexicalBonus)
                .coerceIn(0f, 0.99f)

            val args = LocalIntentDecisionEngine.extractArgsForTool(tool.id, query, spanHint)
            val reason = LocalIntentDecisionEngine.buildReasonForTool(
                tool = tool,
                args = args,
                confidence = combinedConfidence,
                modelTag = model.shortLabel,
            )
            scored += LocalIntentDecision(
                tool = tool,
                confidence = combinedConfidence,
                extractedArgs = args,
                reason = reason,
                requiresConfirmation = tool.requiresConfirmation,
            )
        }

        val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000L
        return DecisionRouteResult(
            decisions = scored
                .sortedByDescending { it.confidence }
                .take(limit),
            activeModel = model,
            latencyMs = totalMs,
        )
    }
}
