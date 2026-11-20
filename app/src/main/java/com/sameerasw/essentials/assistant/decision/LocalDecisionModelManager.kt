/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Decision Model
 * File: LocalDecisionModelManager.kt
 * Description: Manages app-private storage, atomic downloads, caching, and deletion for on-device
 *              TinyDecide weights, plus a local-only HTTP client for self-hosted Kev / Laya
 *              /v1/systemone endpoints.
 */

package com.sameerasw.essentials.assistant.decision

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object LocalDecisionModelManager {
    private const val MODELS_DIR = "decision_models"
    private const val META_FILE = "meta.json"
    private const val MODEL_FILE = "model.bin"

    private data class ModelDownloadSpec(
        val metaUrl: String,
        val modelUrl: String,
        val expectedMinModelBytes: Long,
    )

    private val SPECS: Map<LocalDecisionModel, ModelDownloadSpec> = mapOf(
        LocalDecisionModel.TINYDECIDE_Q4 to ModelDownloadSpec(
            metaUrl = "https://huggingface.co/TheREZOR/TinyDecide/resolve/main/meta.json",
            modelUrl = "https://huggingface.co/TheREZOR/TinyDecide/resolve/main/model.bin",
            expectedMinModelBytes = 5_000_000L,
        ),
        LocalDecisionModel.TINYDECIDE_FULL to ModelDownloadSpec(
            metaUrl = "https://huggingface.co/TheREZOR/TinyDecide/resolve/main/full/meta.json",
            modelUrl = "https://huggingface.co/TheREZOR/TinyDecide/resolve/main/full/model.bin",
            expectedMinModelBytes = 8_000_000L,
        ),
    )

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val localServerClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(4500, TimeUnit.MILLISECONDS)
            .writeTimeout(2000, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _states = MutableStateFlow<Map<LocalDecisionModel, ModelDownloadState>>(emptyMap())
    val states: StateFlow<Map<LocalDecisionModel, ModelDownloadState>> = _states.asStateFlow()

    private val engineMutex = Mutex()
    private val downloadMutex = Mutex()

    @Volatile
    private var loadedModel: LocalDecisionModel? = null

    @Volatile
    private var loadedEngine: TinyDecideEngine? = null

    private fun modelDir(context: Context, model: LocalDecisionModel): File =
        File(File(context.applicationContext.filesDir, MODELS_DIR), model.id)

    fun isModelDownloaded(context: Context, model: LocalDecisionModel): Boolean {
        if (!model.requiresDownload) return true
        val spec = SPECS[model] ?: return false
        val dir = modelDir(context, model)
        val meta = File(dir, META_FILE)
        val bin = File(dir, MODEL_FILE)
        return meta.isFile && meta.length() > 10_000L && bin.isFile && bin.length() >= spec.expectedMinModelBytes
    }

    fun getState(context: Context, model: LocalDecisionModel): ModelDownloadState {
        if (!model.requiresDownload) return ModelDownloadState.NotRequired
        val current = _states.value[model]
        if (current is ModelDownloadState.Downloading) return current
        return computeDiskState(context, model)
    }

    fun refreshStates(context: Context) {
        val current = _states.value.toMutableMap()
        for (model in LocalDecisionModel.entries) {
            if (current[model] is ModelDownloadState.Downloading) continue
            current[model] = computeDiskState(context, model)
        }
        _states.value = current
    }

    private fun computeDiskState(context: Context, model: LocalDecisionModel): ModelDownloadState {
        if (!model.requiresDownload) return ModelDownloadState.NotRequired
        val dir = modelDir(context, model)
        val meta = File(dir, META_FILE)
        val bin = File(dir, MODEL_FILE)
        val spec = SPECS[model] ?: return ModelDownloadState.NotDownloaded
        return if (meta.isFile && meta.length() > 10_000L && bin.isFile && bin.length() >= spec.expectedMinModelBytes) {
            ModelDownloadState.Ready(meta.length() + bin.length())
        } else {
            ModelDownloadState.NotDownloaded
        }
    }

    private fun updateState(model: LocalDecisionModel, state: ModelDownloadState) {
        _states.value = _states.value.toMutableMap().apply { put(model, state) }
    }

    suspend fun downloadModel(context: Context, model: LocalDecisionModel): Result<Unit> =
        withContext(Dispatchers.IO) {
            val spec = SPECS[model]
                ?: return@withContext Result.failure(IllegalArgumentException("${model.label} does not require download"))
            downloadMutex.withLock {
                if (isModelDownloaded(context, model)) {
                    refreshStates(context)
                    return@withContext Result.success(Unit)
                }
                val dir = modelDir(context, model).apply { mkdirs() }
                val metaTmp = File(dir, "$META_FILE.tmp")
                val binTmp = File(dir, "$MODEL_FILE.tmp")
                val metaFinal = File(dir, META_FILE)
                val binFinal = File(dir, MODEL_FILE)

                try {
                    updateState(model, ModelDownloadState.Downloading(0.02f, 0L, spec.expectedMinModelBytes))

                    // 1. Download meta.json
                    downloadFile(spec.metaUrl, metaTmp) { read, total ->
                        val frac = if (total > 0) (read.toFloat() / total.toFloat()) * 0.05f else 0.03f
                        updateState(model, ModelDownloadState.Downloading(frac.coerceIn(0.01f, 0.05f), read, total))
                    }
                    // Validate JSON parses cleanly before pulling the multi-MB weights
                    JSONObject(metaTmp.readText(Charsets.UTF_8)).getJSONArray("tensors")

                    // 2. Download model.bin
                    downloadFile(spec.modelUrl, binTmp) { read, total ->
                        val denom = if (total > 0L) total else spec.expectedMinModelBytes
                        val frac = 0.05f + 0.93f * (read.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                        updateState(model, ModelDownloadState.Downloading(frac, read, denom))
                    }
                    if (binTmp.length() < spec.expectedMinModelBytes) {
                        throw IllegalStateException("Downloaded model is incomplete (${binTmp.length()} bytes)")
                    }

                    if (metaFinal.exists()) metaFinal.delete()
                    if (binFinal.exists()) binFinal.delete()
                    check(metaTmp.renameTo(metaFinal)) { "Failed to commit meta.json" }
                    check(binTmp.renameTo(binFinal)) { "Failed to commit model.bin" }

                    updateState(model, ModelDownloadState.Ready(metaFinal.length() + binFinal.length()))
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    metaTmp.delete()
                    binTmp.delete()
                    updateState(model, computeDiskState(context, model))
                    throw e
                } catch (e: Exception) {
                    metaTmp.delete()
                    binTmp.delete()
                    val msg = e.message?.take(120) ?: "Download failed"
                    updateState(model, ModelDownloadState.Error(msg))
                    Result.failure(e)
                }
            }
        }

    private fun downloadFile(
        url: String,
        dest: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Komorebi-Android")
            .get()
            .build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} while downloading model")
            }
            val body = resp.body
            val total = body.contentLength()
            var readTotal = 0L
            var lastReported = 0L
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (readTotal - lastReported >= 64 * 1024 || readTotal == total) {
                            lastReported = readTotal
                            onProgress(readTotal, total)
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    suspend fun deleteModel(context: Context, model: LocalDecisionModel) {
        engineMutex.withLock {
            if (loadedModel == model) {
                loadedEngine = null
                loadedModel = null
            }
        }
        withContext(Dispatchers.IO) {
            modelDir(context, model).deleteRecursively()
        }
        refreshStates(context)
    }

    suspend fun getOrLoadTinyDecide(
        context: Context,
        model: LocalDecisionModel,
    ): TinyDecideEngine? {
        if (!model.requiresDownload) return null
        if (!isModelDownloaded(context, model)) return null
        if (loadedModel == model) {
            loadedEngine?.let { return it }
        }
        return engineMutex.withLock {
            if (loadedModel == model) {
                loadedEngine?.let { return@withLock it }
            }
            withContext(Dispatchers.Default) {
                val dir = modelDir(context, model)
                val metaFile = File(dir, META_FILE)
                val binFile = File(dir, MODEL_FILE)
                if (!metaFile.isFile || !binFile.isFile) return@withContext null
                val engine = TinyDecideEngine.load(metaFile, binFile)
                loadedEngine = engine
                loadedModel = model
                engine
            }
        }
    }

    /**
     * Calls a local or LAN/Tailscale `/v1/systemone` server (e.g., llama-server with Kev or laya serve).
     */
    suspend fun queryLocalSystemOne(
        endpointUrl: String,
        request: TinyDecideRequest,
    ): Result<TinyDecideResponse> = withContext(Dispatchers.IO) {
        val cleanUrl = endpointUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Endpoint must start with http:// or https://"))
        }
        val startNs = System.nanoTime()
        try {
            val payload = JSONObject().apply {
                put("state", request.state)
                val qs = JSONArray()
                for (q in request.questions) {
                    qs.put(
                        JSONObject().apply {
                            put("type", q.type)
                            put("text", q.text)
                            if (q.options.isNotEmpty()) {
                                put("options", JSONArray(q.options))
                            }
                        },
                    )
                }
                put("questions", qs)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpReq = Request.Builder()
                .url(cleanUrl)
                .post(body)
                .build()
            localServerClient.newCall(httpReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} from local server"))
                }
                val raw = resp.body.string()
                val json = JSONObject(raw)
                val answersArr = json.optJSONArray("answers") ?: json.optJSONArray("results") ?: JSONArray()
                val parsed = ArrayList<TinyDecideAnswer>(answersArr.length())
                for (i in 0 until answersArr.length()) {
                    val obj = answersArr.getJSONObject(i)
                    val type = obj.optString("type", request.questions.getOrNull(i)?.type ?: "choice")
                    val probsArr = obj.optJSONArray("probs") ?: obj.optJSONArray("probabilities")
                    val probs = if (probsArr != null) {
                        FloatArray(probsArr.length()) { probsArr.optDouble(it, 0.0).toFloat() }
                    } else {
                        FloatArray(0)
                    }
                    val pick = if (obj.has("pick") && obj.opt("pick") is Number) {
                        obj.optInt("pick", -1)
                    } else if (probs.isNotEmpty()) {
                        probs.indices.maxByOrNull { probs[it] } ?: -1
                    } else {
                        -1
                    }
                    parsed.add(
                        TinyDecideAnswer(
                            type = type,
                            probs = probs,
                            pick = pick,
                            confidence = obj.optDouble("confidence", 0.0).toFloat(),
                            p = obj.optDouble("p", 0.0).toFloat(),
                            score = obj.optDouble("score", 0.0).toFloat(),
                            pPresent = obj.optDouble("p_present", 0.0).toFloat(),
                            pSpan = obj.optDouble("p_span", 0.0).toFloat(),
                            text = obj.optString("text", ""),
                        ),
                    )
                }
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                Result.success(
                    TinyDecideResponse(
                        answers = parsed,
                        truncated = json.optBoolean("truncated", false),
                        latencyMs = elapsedMs,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testLocalSystemOne(endpointUrl: String): Result<Long> {
        val probe = TinyDecideRequest(
            state = "turn on flashlight",
            questions = listOf(
                TinyDecideQuestion(
                    type = "choice",
                    text = "Which category?",
                    options = listOf("flashlight", "timer", "none"),
                ),
            ),
        )
        return queryLocalSystemOne(endpointUrl, probe).map { it.latencyMs }
    }
}
