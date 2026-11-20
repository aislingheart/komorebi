/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Architecture
 * File: SearchCoordinator.kt
 * Description: Structured concurrent search coordinator & UDF state holder for search-miss
 *              local decision routing across selectable on-device models (Built-in Fast,
 *              TinyDecide 10.4M Q4, TinyDecide 13.8M Full, Local Kev/Laya /v1/systemone, or Off).
 */

package com.sameerasw.essentials.search

import android.content.Context
import com.sameerasw.essentials.assistant.LocalIntentDecision
import com.sameerasw.essentials.assistant.decision.LocalDecisionModel
import com.sameerasw.essentials.assistant.decision.LocalDecisionModelManager
import com.sameerasw.essentials.assistant.decision.LocalDecisionRouter
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import com.sameerasw.essentials.assistant.tools.ToolResult
import com.sameerasw.essentials.data.local.IntentFeedbackStore
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LocalDecisionUiState(
    val activeModel: LocalDecisionModel = LocalDecisionModel.BUILT_IN_LEXICAL,
    val decisions: List<LocalIntentDecision> = emptyList(),
    val pendingConfirmation: LocalIntentDecision? = null,
    val lastResult: ToolResult? = null,
    val isEvaluating: Boolean = false,
    val isExecuting: Boolean = false,
    val needsDownload: Boolean = false,
    val statusNote: String? = null,
    val latencyMs: Long = 0L,
)

/**
 * Coordinates free, local tool matching and execution ONLY when standard search yields no matches,
 * with selectable local decision models and reinforcement feedback in [IntentFeedbackStore].
 */
class LocalSearchDecisionCoordinator(private val appContext: Context) {
    private val settingsRepository = SettingsRepository(appContext)
    private val _state = MutableStateFlow(
        LocalDecisionUiState(
            activeModel = LocalDecisionModel.fromId(settingsRepository.getLocalDecisionModelId()),
        ),
    )
    val state: StateFlow<LocalDecisionUiState> = _state.asStateFlow()

    @Volatile
    private var cachedFeedbackWeights: Map<String, Float> = emptyMap()

    suspend fun warmUp() {
        cachedFeedbackWeights = IntentFeedbackStore.loadWeights(appContext)
        LocalDecisionModelManager.refreshStates(appContext)
        refreshSelectedModelFromPrefs()
    }

    fun refreshSelectedModelFromPrefs() {
        val selected = LocalDecisionModel.fromId(settingsRepository.getLocalDecisionModelId())
        if (_state.value.activeModel != selected) {
            _state.value = _state.value.copy(
                activeModel = selected,
                needsDownload = selected.requiresDownload &&
                    !LocalDecisionModelManager.isModelDownloaded(appContext, selected),
                statusNote = null,
            )
        }
    }

    fun selectModel(model: LocalDecisionModel) {
        settingsRepository.setLocalDecisionModelId(model.id)
        LocalDecisionModelManager.refreshStates(appContext)
        _state.value = _state.value.copy(
            activeModel = model,
            decisions = emptyList(),
            pendingConfirmation = null,
            isEvaluating = false,
            needsDownload = model.requiresDownload &&
                !LocalDecisionModelManager.isModelDownloaded(appContext, model),
            statusNote = null,
            latencyMs = 0L,
        )
    }

    suspend fun downloadModel(model: LocalDecisionModel): Result<Unit> {
        val result = LocalDecisionModelManager.downloadModel(appContext, model)
        if (result.isSuccess && _state.value.activeModel == model) {
            _state.value = _state.value.copy(needsDownload = false, statusNote = null)
        }
        return result
    }

    fun clear() {
        val cur = _state.value
        if (cur.decisions.isNotEmpty() || cur.isEvaluating || cur.statusNote != null || cur.needsDownload) {
            _state.value = cur.copy(
                decisions = emptyList(),
                isEvaluating = false,
                needsDownload = false,
                statusNote = null,
                latencyMs = 0L,
            )
        }
    }

    /**
     * Evaluates an unmatched query using the currently selected local decision model.
     * Should only be called when standard launcher search returned zero direct matches.
     */
    suspend fun evaluateUnmatchedQuery(
        query: String,
        limit: Int = 3,
    ): List<LocalIntentDecision> {
        val trimmed = query.trim()
        val model = LocalDecisionModel.fromId(settingsRepository.getLocalDecisionModelId())
        if (trimmed.length < 2 || model == LocalDecisionModel.OFF) {
            _state.value = _state.value.copy(
                activeModel = model,
                decisions = emptyList(),
                isEvaluating = false,
                needsDownload = false,
                statusNote = null,
                latencyMs = 0L,
            )
            return emptyList()
        }

        if (model.requiresDownload && !LocalDecisionModelManager.isModelDownloaded(appContext, model)) {
            _state.value = _state.value.copy(
                activeModel = model,
                decisions = emptyList(),
                isEvaluating = false,
                needsDownload = true,
                statusNote = "${model.shortLabel} (${model.sizeBadge}) needs a one-time local download",
                latencyMs = 0L,
            )
            return emptyList()
        }

        // Light debounce for neural/local-server models so typing doesn't thrash CPU
        if (model != LocalDecisionModel.BUILT_IN_LEXICAL) {
            _state.value = _state.value.copy(
                activeModel = model,
                isEvaluating = true,
                needsDownload = false,
                statusNote = null,
            )
            delay(180L)
        }

        return try {
            val result = LocalDecisionRouter.route(
                context = appContext,
                query = trimmed,
                model = model,
                localEndpointUrl = settingsRepository.getLocalSystemOneUrl(),
                feedbackWeights = cachedFeedbackWeights,
                limit = limit,
            )
            _state.value = _state.value.copy(
                activeModel = model,
                decisions = result.decisions,
                pendingConfirmation = null,
                isEvaluating = false,
                needsDownload = result.needsDownload,
                statusNote = result.statusNote,
                latencyMs = result.latencyMs,
            )
            result.decisions
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                activeModel = model,
                decisions = emptyList(),
                isEvaluating = false,
                statusNote = e.message?.take(80),
            )
            emptyList()
        }
    }

    fun requestExecution(decision: LocalIntentDecision): Boolean {
        if (decision.requiresConfirmation) {
            _state.value = _state.value.copy(pendingConfirmation = decision)
            return false
        }
        return true
    }

    fun cancelConfirmation() {
        _state.value = _state.value.copy(pendingConfirmation = null)
    }

    suspend fun executeDecision(
        query: String,
        decision: LocalIntentDecision,
    ): ToolResult {
        _state.value = _state.value.copy(pendingConfirmation = null, isExecuting = true)
        val result = withContext(Dispatchers.IO) {
            IntentFeedbackStore.recordSelection(appContext, query, decision.tool.id)
            cachedFeedbackWeights = IntentFeedbackStore.loadWeights(appContext)
            AssistantToolRegistry.execute(appContext, decision.tool.id, decision.extractedArgs)
        }
        _state.value = _state.value.copy(isExecuting = false, lastResult = result)
        return result
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(lastResult = null, pendingConfirmation = null)
    }
}
