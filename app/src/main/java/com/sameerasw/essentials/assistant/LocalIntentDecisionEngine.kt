/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Intent Decision Model
 * File: LocalIntentDecisionEngine.kt
 * Description: Zero-cost, 100% offline hybrid decision model (TF-IDF/BM25 + char trigram cosine
 *              + synonym frames + decision-tree slot/polarity boosters + local feedback weights)
 *              that routes natural free-form text directly to local Komorebi tools without an LLM.
 */

package com.sameerasw.essentials.assistant

import com.sameerasw.essentials.assistant.tools.AssistantTool
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

data class LocalIntentDecision(
    val tool: AssistantTool,
    val confidence: Float,
    val extractedArgs: Map<String, String>,
    val reason: String,
    val requiresConfirmation: Boolean = tool.requiresConfirmation,
)

object LocalIntentDecisionEngine {
    private const val MIN_CONFIDENCE = 0.24f
    private const val BM25_K1 = 1.2
    private const val BM25_B = 0.55

    private val STOP_WORDS = setOf(
        "a", "an", "the", "please", "pls", "can", "could", "would", "you", "me", "my",
        "i", "to", "for", "of", "in", "on", "at", "is", "it", "this", "that", "just",
        "now", "hey", "komorebi", "want", "need", "help", "with", "do", "does", "did",
    )

    // Canonical synonym map: maps everyday phrasing tokens to canonical domain concepts.
    private val SYNONYM_MAP: Map<String, List<String>> = mapOf(
        "torch" to listOf("flashlight", "light"),
        "lamp" to listOf("flashlight"),
        "beam" to listOf("flashlight"),
        "dim" to listOf("brightness", "lower", "darker"),
        "dimmer" to listOf("brightness", "lower"),
        "darker" to listOf("brightness", "lower", "dark"),
        "brighter" to listOf("brightness", "higher"),
        "bright" to listOf("brightness"),
        "blinding" to listOf("brightness", "lower"),
        "eyes" to listOf("brightness", "dark"),
        "louder" to listOf("volume", "higher"),
        "quieter" to listOf("volume", "lower"),
        "softer" to listOf("volume", "lower"),
        "sound" to listOf("volume", "audio"),
        "audio" to listOf("volume"),
        "silent" to listOf("volume", "dnd", "mute"),
        "silence" to listOf("dnd", "mute"),
        "quiet" to listOf("dnd", "volume", "mute"),
        "mute" to listOf("volume", "dnd"),
        "unmute" to listOf("volume"),
        "disturb" to listOf("dnd"),
        "focus" to listOf("dnd"),
        "wake" to listOf("alarm"),
        "wakeup" to listOf("alarm"),
        "nap" to listOf("timer"),
        "countdown" to listOf("timer"),
        "boil" to listOf("timer"),
        "pasta" to listOf("timer"),
        "stopwatch" to listOf("timer"),
        "skip" to listOf("media", "next", "track"),
        "song" to listOf("media", "music", "track"),
        "music" to listOf("media", "play"),
        "spotify" to listOf("media", "play"),
        "podcast" to listOf("media", "play"),
        "pause" to listOf("media", "pause"),
        "resume" to listOf("media", "play"),
        "rewind" to listOf("media", "previous"),
        "previous" to listOf("media", "previous"),
        "back" to listOf("media", "previous"),
        "bt" to listOf("bluetooth"),
        "headphones" to listOf("bluetooth"),
        "earbuds" to listOf("bluetooth"),
        "internet" to listOf("wifi", "mobile", "data"),
        "wlan" to listOf("wifi"),
        "cellular" to listOf("mobile", "data"),
        "lte" to listOf("mobile", "data"),
        "5g" to listOf("mobile", "data"),
        "tether" to listOf("hotspot"),
        "tethering" to listOf("hotspot"),
        "flight" to listOf("airplane"),
        "plane" to listOf("airplane"),
        "awake" to listOf("caffeinate", "screen", "timeout"),
        "insomnia" to listOf("caffeinate"),
        "rotate" to listOf("rotation", "auto_rotate"),
        "orientation" to listOf("rotation", "auto_rotate"),
        "portrait" to listOf("rotation", "auto_rotate"),
        "landscape" to listOf("rotation", "auto_rotate"),
        "power" to listOf("battery", "saver"),
        "eco" to listOf("battery", "saver"),
        "night" to listOf("dark", "mode"),
        "theme" to listOf("dark", "mode"),
        "todo" to listOf("task", "tasks"),
        "todos" to listOf("task", "tasks"),
        "remind" to listOf("task", "reminder"),
        "reminder" to listOf("task", "reminder"),
        "reminders" to listOf("task", "reminder"),
        "checklist" to listOf("task", "tasks"),
        "meeting" to listOf("event", "calendar"),
        "appointment" to listOf("event", "calendar"),
        "schedule" to listOf("calendar", "event", "free"),
        "busy" to listOf("calendar", "free", "slots"),
        "available" to listOf("calendar", "free", "slots"),
        "free" to listOf("calendar", "free", "slots"),
        "slept" to listOf("sleep", "health"),
        "rest" to listOf("sleep", "health"),
        "walked" to listOf("steps", "health"),
        "walk" to listOf("steps", "health"),
        "fitness" to listOf("health", "steps"),
        "vitals" to listOf("health"),
        "missed" to listOf("notifications", "digest"),
        "unread" to listOf("notifications", "digest"),
        "alerts" to listOf("notifications", "digest"),
        "ping" to listOf("notifications", "digest"),
        "text" to listOf("reply", "message", "screen"),
        "reply" to listOf("reply", "message"),
        "respond" to listOf("reply", "message"),
        "barcode" to listOf("qr", "scan"),
        "qrcode" to listOf("qr", "scan"),
        "ocr" to listOf("screen", "text", "copy"),
        "tldr" to listOf("summarize", "highlights", "screen"),
        "highlights" to listOf("summarize", "screen"),
        "summary" to listOf("summarize", "digest", "health"),
        "snapshot" to listOf("screenshot"),
        "capture" to listOf("screenshot", "screen"),
        "split" to listOf("split", "screen"),
        "multitask" to listOf("split", "screen"),
        "hibernate" to listOf("freeze", "app"),
        "thaw" to listOf("unfreeze", "app"),
        "specs" to listOf("device", "info"),
        "storage" to listOf("device", "info"),
        "ram" to listOf("device", "info"),
        "clipboard" to listOf("clipboard", "share"),
        "paste" to listOf("clipboard"),
        "copied" to listOf("clipboard"),
        "lookup" to listOf("web", "search"),
        "google" to listOf("web", "search"),
        "browse" to listOf("fetch", "url"),
        "website" to listOf("fetch", "url"),
        "webpage" to listOf("fetch", "url"),
        "macro" to listOf("routine"),
        "workflow" to listOf("routine"),
        "automate" to listOf("routine"),
    )

    // Natural phrasing exemplars per tool ID so free-form user speech matches accurately.
    private val INTENT_EXEMPLARS: Map<String, List<String>> = mapOf(
        "wifi" to listOf("turn wifi on", "turn wifi off", "toggle wifi", "disconnect wireless internet"),
        "bluetooth" to listOf("turn bluetooth on", "kill bluetooth", "toggle bt", "connect headphones"),
        "mobile_data" to listOf("turn mobile data on", "disable cellular data", "toggle 5g lte"),
        "airplane_mode" to listOf("enable airplane mode", "turn on flight mode", "disable plane mode"),
        "hotspot" to listOf("turn on hotspot", "enable wifi tethering", "share internet hotspot"),
        "flashlight" to listOf("turn on flashlight", "torch on", "light off", "toggle flash light"),
        "auto_rotate" to listOf("toggle auto rotate", "lock screen orientation", "enable rotation"),
        "battery_saver" to listOf("enable battery saver", "low power mode", "save battery"),
        "dark_mode" to listOf("turn on dark mode", "switch to light theme", "night theme"),
        "dnd" to listOf("turn on do not disturb", "silence notifications", "focus mode", "quiet notifications"),
        "caffeinate" to listOf("keep screen awake", "prevent screen sleep", "stay awake caffeinate"),
        "volume" to listOf("volume 50 percent", "turn up sound", "louder", "quieter", "mute volume"),
        "brightness" to listOf("brightness 30 percent", "dim the screen", "too bright eyes hurt", "make screen brighter"),
        "timer" to listOf("set a 5 minute timer", "countdown 10 minutes", "nap for 20 min", "timer for pasta"),
        "alarm" to listOf("set alarm for 7am", "wake me up at 6:30", "morning alarm"),
        "open_timers" to listOf("show my timers", "open active timers", "check countdowns"),
        "open_alarms" to listOf("show my alarms", "check alarms", "open alarm clock"),
        "add_time" to listOf("add 5 minutes to timer", "extend timer"),
        "media_play" to listOf("resume music", "play song", "continue audio"),
        "media_pause" to listOf("pause music", "stop playing", "pause podcast"),
        "media_toggle" to listOf("play pause media", "toggle music playback"),
        "media_next" to listOf("next song", "skip track", "play next music"),
        "media_previous" to listOf("previous song", "go back track", "rewind music"),
        "play_media" to listOf("play jazz music", "play spotify playlist", "listen to song"),
        "notification_digest" to listOf("what did i miss", "unread notifications", "notification summary", "show alerts"),
        "reply_chat" to listOf("reply to message", "respond to latest chat", "send text reply"),
        "create_event" to listOf("schedule meeting tomorrow", "add calendar event", "book appointment"),
        "find_free_slots" to listOf("am i free today", "when am i available", "check open calendar slots", "am i busy"),
        "add_task" to listOf("remind me to buy milk", "add todo task", "new reminder"),
        "complete_task" to listOf("mark task done", "complete todo", "finish reminder"),
        "list_tasks" to listOf("show my tasks", "what is on my todo list", "list reminders"),
        "delete_task" to listOf("delete task", "remove todo", "cancel reminder"),
        "run_routine" to listOf("run morning routine", "start bedtime workflow", "execute macro"),
        "list_routines" to listOf("show my routines", "list saved workflows"),
        "health_summary" to listOf("health summary", "daily fitness vitals", "how is my health today"),
        "sleep_stats" to listOf("how much did i sleep", "last night sleep duration", "sleep history"),
        "steps" to listOf("how many steps today", "step count", "how far did i walk"),
        "copy_screen_text" to listOf("copy text from screen", "ocr screen", "extract visible text"),
        "summarize_screen" to listOf("screen highlights", "summarize this screen", "tldr screen", "key points on screen"),
        "scan_screen_qr" to listOf("scan qr code on screen", "read barcode on screen"),
        "lock_screen" to listOf("lock my phone", "lock screen now", "turn off screen"),
        "screenshot" to listOf("take a screenshot", "capture screen snapshot"),
        "split_screen" to listOf("open split screen", "multitask two apps"),
        "open_settings" to listOf("open wifi settings", "go to system settings", "bluetooth settings"),
        "freeze_app" to listOf("freeze app", "hibernate app", "disable background app"),
        "unfreeze_app" to listOf("unfreeze app", "thaw app", "re-enable frozen app"),
        "device_info" to listOf("device battery and storage info", "phone specs ram level"),
        "screen_timeout" to listOf("set screen timeout to 2 minutes", "change display sleep timeout"),
        "share_clipboard" to listOf("share clipboard text", "send what i copied"),
        "app_info" to listOf("open app info settings", "app permissions details"),
        "web_search" to listOf("search the web for", "look up online", "duckduckgo search"),
        "fetch_url" to listOf("fetch webpage content", "read url article", "download page text"),
    )

    private val ON_WORDS = setOf("on", "enable", "enabled", "start", "activate", "open", "resume", "unmute")
    private val OFF_WORDS = setOf("off", "disable", "disabled", "stop", "kill", "deactivate", "close", "mute")
    private val UP_WORDS = setOf("up", "higher", "louder", "brighter", "increase", "raise", "boost", "max", "full")
    private val DOWN_WORDS = setOf("down", "lower", "quieter", "dim", "dimmer", "darker", "decrease", "reduce", "min", "soft")

    private val PERCENT_REGEX = Regex("(\\d{1,3})\\s*%")
    private val DURATION_REGEX = Regex("(\\d+)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)\\b", RegexOption.IGNORE_CASE)
    private val TIME_REGEX = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b", RegexOption.IGNORE_CASE)
    private val URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    private data class IndexedTool(
        val tool: AssistantTool,
        val tokenCounts: Map<String, Double>,
        val docLength: Double,
        val trigrams: Map<String, Float>,
        val trigramNorm: Float,
        val keywordEntries: List<Triple<String, Map<String, Float>, Float>>,
    )

    private data class CompiledIndex(
        val items: List<IndexedTool>,
        val idf: Map<String, Double>,
        val avgDocLen: Double,
    )

    @Volatile
    private var cachedIndex: Pair<Int, CompiledIndex>? = null

    /**
     * Ranks local tools for a free-form natural-language query using the offline decision model.
     *
     * @param query User's raw input string.
     * @param tools Optional custom tool list (defaults to [AssistantToolRegistry.all]).
     * @param feedbackWeights Optional map of `"token->toolId"` -> learned boost (`0.0f..0.4f`).
     * @param limit Maximum number of decisions to return.
     */
    fun decide(
        query: String,
        tools: List<AssistantTool> = AssistantToolRegistry.all(),
        feedbackWeights: Map<String, Float> = emptyMap(),
        limit: Int = 3,
    ): List<LocalIntentDecision> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || tools.isEmpty()) return emptyList()

        val index = getOrBuildIndex(tools)
        val rawTokens = tokenize(trimmed)
        if (rawTokens.isEmpty()) return emptyList()

        val expandedTokens = expandTokens(rawTokens)
        val queryTrigrams = buildTrigrams(trimmed.lowercase(Locale.US))
        val queryTriNorm = vectorNorm(queryTrigrams)
        val queryTokenEntries = rawTokens
            .filter { it.length >= 4 }
            .map { tok ->
                val tri = buildTrigrams(tok)
                Triple(tok, tri, vectorNorm(tri))
            }
        val features = extractQueryFeatures(trimmed, rawTokens)

        val scored = ArrayList<LocalIntentDecision>(index.items.size)
        for (item in index.items) {
            val bm25 = scoreBm25(expandedTokens, item, index.idf, index.avgDocLen)
            val docTrigramSim = cosineSimilarity(queryTrigrams, queryTriNorm, item.trigrams, item.trigramNorm)
            var bestTokenSim = 0f
            for ((qTok, qTri, qNorm) in queryTokenEntries) {
                for ((kTok, kTri, kNorm) in item.keywordEntries) {
                    val triSim = cosineSimilarity(qTri, qNorm, kTri, kNorm)
                    if (triSim >= 0.45f) {
                        val editSim = normalizedEditSimilarity(qTok, kTok)
                        val combined = (0.45f * triSim) + (0.55f * editSim)
                        if (combined > bestTokenSim) bestTokenSim = combined
                    }
                }
            }
            val typoSim = if (bestTokenSim >= 0.62f) bestTokenSim else 0f
            val trigramSim = maxOf(docTrigramSim, typoSim)
            val hasLexicalSignal = bm25 > 0.0 || typoSim >= 0.62f
            val ruleAdjustment = decisionTreeAdjustment(
                toolId = item.tool.id,
                raw = trimmed,
                rawTokens = rawTokens,
                expandedTokens = expandedTokens,
                f = features,
                hasLexicalSignal = hasLexicalSignal,
            )
            val feedbackBoost = computeFeedbackBoost(rawTokens, item.tool.id, feedbackWeights)

            // Normalize BM25 smoothly to [0, 1] via saturation curve x / (x + 2.6)
            val bm25Norm = if (bm25 > 0.0) (bm25 / (bm25 + 2.6)).toFloat() else 0f
            val lexicalBase = if (bm25 > 0.0) {
                (0.58f * bm25Norm) + (0.34f * trigramSim)
            } else {
                0.56f * trigramSim
            }
            val rawScore = lexicalBase + ruleAdjustment + feedbackBoost
            val confidence = rawScore.coerceIn(0f, 1f)

            if (confidence >= MIN_CONFIDENCE) {
                val args = extractArgs(item.tool.id, trimmed, rawTokens, features)
                val reason = buildReason(item.tool, args, confidence)
                scored += LocalIntentDecision(
                    tool = item.tool,
                    confidence = confidence,
                    extractedArgs = args,
                    reason = reason,
                    requiresConfirmation = item.tool.requiresConfirmation,
                )
            }
        }

        return scored
            .sortedWith(
                compareByDescending<LocalIntentDecision> { it.confidence }
                    .thenBy { it.tool.title.lowercase(Locale.US) },
            )
            .take(limit)
    }

    private fun getOrBuildIndex(tools: List<AssistantTool>): CompiledIndex {
        val signature = tools.fold(17) { acc, t -> 31 * acc + t.id.hashCode() }
        cachedIndex?.let { (sig, idx) -> if (sig == signature) return idx }

        val indexed = tools.map { tool ->
            val counts = mutableMapOf<String, Double>()
            fun addWeighted(text: String, weight: Double) {
                val toks = tokenize(text)
                for (t in toks) {
                    counts[t] = (counts[t] ?: 0.0) + weight
                    SYNONYM_MAP[t]?.forEach { syn ->
                        counts[syn] = (counts[syn] ?: 0.0) + (weight * 0.45)
                    }
                }
            }
            addWeighted(tool.title, 2.4)
            addWeighted(tool.id.replace('_', ' '), 2.0)
            tool.keywords.forEach { addWeighted(it, 1.8) }
            addWeighted(tool.description, 0.9)
            INTENT_EXEMPLARS[tool.id].orEmpty().forEach { addWeighted(it, 1.5) }

            val combinedText = buildString {
                append(tool.title).append(' ')
                append(tool.keywords.joinToString(" ")).append(' ')
                append(INTENT_EXEMPLARS[tool.id].orEmpty().joinToString(" "))
            }.lowercase(Locale.US)
            val trigrams = buildTrigrams(combinedText)
            val keywordTokenSet = linkedSetOf<String>()
            tokenize(tool.title).filter { it.length >= 4 }.forEach(keywordTokenSet::add)
            tokenize(tool.id.replace('_', ' ')).filter { it.length >= 4 }.forEach(keywordTokenSet::add)
            tool.keywords.flatMap(::tokenize).filter { it.length >= 4 }.forEach(keywordTokenSet::add)
            val keywordEntries = keywordTokenSet.map { tok ->
                val tri = buildTrigrams(tok)
                Triple(tok, tri, vectorNorm(tri))
            }
            IndexedTool(
                tool = tool,
                tokenCounts = counts,
                docLength = counts.values.sum().coerceAtLeast(1.0),
                trigrams = trigrams,
                trigramNorm = vectorNorm(trigrams),
                keywordEntries = keywordEntries,
            )
        }

        val docFreq = mutableMapOf<String, Int>()
        indexed.forEach { item ->
            item.tokenCounts.keys.forEach { term ->
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }
        val n = indexed.size.toDouble().coerceAtLeast(1.0)
        val idf = docFreq.mapValues { (_, df) ->
            ln(1.0 + (n - df + 0.5) / (df + 0.5))
        }
        val avgLen = indexed.map { it.docLength }.average().coerceAtLeast(1.0)
        val compiled = CompiledIndex(indexed, idf, avgLen)
        cachedIndex = signature to compiled
        return compiled
    }

    private fun scoreBm25(
        queryTokens: Map<String, Double>,
        item: IndexedTool,
        idf: Map<String, Double>,
        avgDocLen: Double,
    ): Double {
        var score = 0.0
        for ((term, qWeight) in queryTokens) {
            val tf = item.tokenCounts[term] ?: continue
            val termIdf = idf[term] ?: 0.5
            val denom = tf + BM25_K1 * (1.0 - BM25_B + BM25_B * (item.docLength / avgDocLen))
            score += qWeight * termIdf * ((tf * (BM25_K1 + 1.0)) / denom)
        }
        return score
    }

    private data class QueryFeatures(
        val lower: String,
        val hasPercent: Boolean,
        val percentValue: Int?,
        val hasDuration: Boolean,
        val durationText: String?,
        val hasClockTime: Boolean,
        val timeText: String?,
        val hasUrl: Boolean,
        val urlText: String?,
        val polarityOn: Boolean,
        val polarityOff: Boolean,
        val polarityUp: Boolean,
        val polarityDown: Boolean,
        val isQuestion: Boolean,
    )

    private fun extractQueryFeatures(raw: String, tokens: List<String>): QueryFeatures {
        val lower = raw.lowercase(Locale.US)
        val pctMatch = PERCENT_REGEX.find(lower)
        val durMatch = DURATION_REGEX.find(lower)
        val timeMatch = TIME_REGEX.find(lower)
        val urlMatch = URL_REGEX.find(raw)
        val rawWords = lower.split(Regex("[^a-z0-9_]+")).filter { it.isNotEmpty() }.toSet()
        val tokenSet = tokens.toSet() + rawWords
        val isQuestion = lower.endsWith("?") ||
            lower.startsWith("how ") ||
            lower.startsWith("what ") ||
            lower.startsWith("when ") ||
            lower.startsWith("am i ") ||
            lower.startsWith("do i ")
        return QueryFeatures(
            lower = lower,
            hasPercent = pctMatch != null,
            percentValue = pctMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100),
            hasDuration = durMatch != null,
            durationText = durMatch?.value,
            hasClockTime = timeMatch != null,
            timeText = timeMatch?.value,
            hasUrl = urlMatch != null,
            urlText = urlMatch?.value,
            polarityOn = tokenSet.any { it in ON_WORDS },
            polarityOff = tokenSet.any { it in OFF_WORDS },
            polarityUp = tokenSet.any { it in UP_WORDS },
            polarityDown = tokenSet.any { it in DOWN_WORDS },
            isQuestion = isQuestion,
        )
    }

    private fun decisionTreeAdjustment(
        toolId: String,
        raw: String,
        rawTokens: List<String>,
        expandedTokens: Map<String, Double>,
        f: QueryFeatures,
        hasLexicalSignal: Boolean,
    ): Float {
        var boost = 0f
        val t = expandedTokens.keys

        // Exact phrase / high-signal decision rules
        when (toolId) {
            "timer" -> {
                if (f.hasDuration && ("timer" in t || "countdown" in t || "nap" in t || "boil" in t || "pasta" in t)) boost += 0.34f
                else if (f.hasDuration && !f.hasClockTime && "timeout" !in t && "event" !in t && "meeting" !in t) boost += 0.16f
                if (f.hasClockTime && "timer" !in t) boost -= 0.18f
            }
            "alarm" -> {
                if (f.hasClockTime && ("alarm" in t || "wake" in t || "wakeup" in t || "morning" in t)) boost += 0.35f
                if (f.hasDuration && "alarm" !in t && "wake" !in t) boost -= 0.15f
            }
            "screen_timeout" -> {
                if ("timeout" in t || ("screen" in t && "sleep" in t && f.hasDuration)) boost += 0.30f
            }
            "brightness" -> {
                if (("dim" in rawTokens || "brighter" in rawTokens || "darker" in rawTokens || "eyes" in rawTokens) && "mode" !in rawTokens) {
                    boost += 0.28f
                }
                if (f.hasPercent && ("brightness" in t || "screen" in t || "display" in t)) boost += 0.25f
                if ("volume" in rawTokens || "sound" in rawTokens) boost -= 0.22f
            }
            "volume" -> {
                if ("louder" in rawTokens || "quieter" in rawTokens || "mute" in rawTokens || "unmute" in rawTokens) boost += 0.26f
                if (f.hasPercent && ("volume" in t || "sound" in t || "audio" in t)) boost += 0.25f
                if ("brightness" in rawTokens || "dim" in rawTokens) boost -= 0.22f
            }
            "dark_mode" -> {
                if (hasLexicalSignal && (f.polarityOn || f.polarityOff)) boost += 0.08f
                if (("dark" in rawTokens && "mode" in rawTokens) || ("light" in rawTokens && "mode" in rawTokens) || "theme" in rawTokens) {
                    boost += 0.30f
                }
            }
            "flashlight" -> {
                if (hasLexicalSignal && (f.polarityOn || f.polarityOff)) boost += 0.08f
                if ("flashlight" in rawTokens || "torch" in rawTokens) boost += 0.34f
            }
            "wifi", "bluetooth", "mobile_data", "airplane_mode", "hotspot", "auto_rotate", "battery_saver", "dnd", "caffeinate" -> {
                if (hasLexicalSignal && (f.polarityOn || f.polarityOff)) boost += 0.08f
                if (toolId == "dnd" && ("disturb" in rawTokens || "dnd" in rawTokens || ("silence" in rawTokens && "notifications" in t))) {
                    boost += 0.32f
                }
                if (toolId == "caffeinate" && ("awake" in rawTokens || "caffeinate" in rawTokens)) {
                    boost += 0.30f
                }
            }
            "find_free_slots" -> {
                if (f.isQuestion && ("free" in t || "busy" in t || "available" in t || "calendar" in t || "schedule" in t)) {
                    boost += 0.32f
                }
            }
            "create_event" -> {
                if (!f.isQuestion && ("meeting" in rawTokens || "appointment" in rawTokens || "event" in rawTokens || "schedule" in rawTokens)) {
                    boost += 0.24f
                }
                if (f.isQuestion) boost -= 0.14f
            }
            "add_task" -> {
                if (f.lower.startsWith("remind me") || f.lower.startsWith("add task") || f.lower.startsWith("todo ")) {
                    boost += 0.36f
                }
            }
            "list_tasks" -> {
                if ((f.isQuestion || "show" in rawTokens || "list" in rawTokens) && ("task" in t || "tasks" in t || "todo" in rawTokens || "reminders" in rawTokens)) {
                    boost += 0.28f
                }
            }
            "sleep_stats" -> {
                if ("sleep" in t && (f.isQuestion || "stats" in rawTokens || "last" in rawTokens || "slept" in rawTokens)) {
                    boost += 0.34f
                }
            }
            "steps" -> {
                if ("steps" in t || "walked" in rawTokens) boost += 0.32f
            }
            "notification_digest" -> {
                if ("missed" in rawTokens || "unread" in rawTokens || ("notifications" in t && !f.polarityOff)) {
                    boost += 0.28f
                }
            }
            "fetch_url" -> {
                if (f.hasUrl) boost += 0.40f else boost -= 0.15f
            }
            "web_search" -> {
                if (f.lower.startsWith("search web ") || f.lower.startsWith("web search ") || f.lower.startsWith("look up ")) {
                    boost += 0.30f
                }
            }
            "scan_screen_qr" -> {
                if ("qr" in t || "barcode" in rawTokens) boost += 0.34f
            }
            "copy_screen_text" -> {
                if ("ocr" in rawTokens || ("copy" in rawTokens && "screen" in rawTokens)) boost += 0.30f
            }
            "summarize_screen" -> {
                if (("summarize" in rawTokens || "tldr" in rawTokens || "highlights" in rawTokens) && "screen" in t) {
                    boost += 0.32f
                }
            }
            "screenshot" -> {
                if ("screenshot" in rawTokens || "snapshot" in rawTokens) boost += 0.36f
            }
            "lock_screen" -> {
                if ("lock" in rawTokens && ("screen" in rawTokens || "phone" in rawTokens || "device" in rawTokens)) {
                    boost += 0.32f
                }
            }
        }

        // Slight penalty for single vague words on destructive tools
        if (rawTokens.size == 1 && (toolId == "delete_task" || toolId == "freeze_app" || toolId == "reply_chat")) {
            boost -= 0.15f
        }

        // Exact title match boost
        if (raw.equals(toolId.replace('_', ' '), ignoreCase = true)) {
            boost += 0.35f
        }
        return boost
    }

    private fun extractArgs(
        toolId: String,
        raw: String,
        rawTokens: List<String>,
        f: QueryFeatures,
    ): Map<String, String> {
        val args = mutableMapOf<String, String>()
        when (toolId) {
            "wifi", "bluetooth", "mobile_data", "airplane_mode", "hotspot",
            "flashlight", "auto_rotate", "battery_saver", "dark_mode", "dnd", "caffeinate",
            -> {
                when {
                    f.polarityOff -> args["state"] = "off"
                    f.polarityOn -> args["state"] = "on"
                    else -> args["state"] = "toggle"
                }
            }
            "brightness" -> {
                when {
                    f.percentValue != null -> {
                        args["action"] = "set"
                        args["value"] = f.percentValue.toString()
                    }
                    f.polarityDown -> args["action"] = "down"
                    f.polarityUp -> args["action"] = "up"
                }
            }
            "volume" -> {
                val stream = when {
                    "ring" in rawTokens || "ringer" in rawTokens || "ringtone" in rawTokens -> "ring"
                    "alarm" in rawTokens -> "alarm"
                    "notification" in rawTokens || "notifications" in rawTokens -> "notification"
                    else -> "media"
                }
                args["stream"] = stream
                when {
                    "unmute" in rawTokens -> args["action"] = "unmute"
                    "mute" in rawTokens || "silence" in rawTokens -> args["action"] = "mute"
                    f.percentValue != null -> {
                        args["action"] = "set"
                        args["value"] = f.percentValue.toString()
                    }
                    f.polarityDown -> args["action"] = "down"
                    f.polarityUp -> args["action"] = "up"
                }
            }
            "timer", "add_time", "screen_timeout" -> {
                f.durationText?.let { args["duration"] = it }
                val label = raw
                    .replace(Regex("^(set|start|add|create)\\s+(a\\s+)?", RegexOption.IGNORE_CASE), "")
                    .replace(DURATION_REGEX, "")
                    .replace(Regex("\\b(timer|countdown|for|to|screen|timeout)\\b", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (label.isNotBlank() && toolId == "timer") {
                    args["message"] = label
                }
            }
            "alarm" -> {
                f.timeText?.let { args["time"] = it }
                val label = raw
                    .replace(Regex("^(set|create)\\s+(an?\\s+)?", RegexOption.IGNORE_CASE), "")
                    .replace(TIME_REGEX, "")
                    .replace(Regex("\\b(alarm|wake|me|up|at|for)\\b", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (label.isNotBlank()) args["message"] = label
            }
            "add_task" -> {
                val title = raw
                    .replace(Regex("^(remind me to|remind me|add task|add todo|new task|new reminder|todo)\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (title.isNotBlank()) args["title"] = title
            }
            "complete_task", "delete_task" -> {
                val title = raw
                    .replace(Regex("^(complete|finish|done|mark|delete|remove|cancel)\\s+(task|todo|reminder)?\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (title.isNotBlank()) args["title"] = title
            }
            "create_event" -> {
                val title = raw
                    .replace(Regex("^(schedule|create|add|book)\\s+(a\\s+)?(calendar\\s+)?(event|meeting|appointment)?\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (title.isNotBlank()) args["title"] = title
                f.timeText?.let { args["time"] = it }
            }
            "play_media" -> {
                val q = raw.replace(Regex("^(play|listen to)\\s+", RegexOption.IGNORE_CASE), "").trim()
                if (q.isNotBlank()) args["query"] = q
            }
            "run_routine" -> {
                val name = raw
                    .replace(Regex("^(run|start|execute|trigger)\\s+(my\\s+)?", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*(routine|workflow|macro)$", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (name.isNotBlank()) args["name"] = name
            }
            "web_search" -> {
                val q = raw
                    .replace(Regex("^(search the web for|search web for|web search|search for|look up|google)\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (q.isNotBlank()) args["query"] = q
            }
            "fetch_url" -> {
                f.urlText?.let { args["url"] = it }
            }
            "freeze_app", "unfreeze_app", "app_info" -> {
                val app = raw
                    .replace(Regex("^(freeze|hibernate|unfreeze|thaw|app info|open app info)\\s+(for\\s+)?", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (app.isNotBlank()) args["app"] = app
            }
            "open_settings" -> {
                val target = raw
                    .replace(Regex("^(open|go to|show)\\s+", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*settings$", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (target.isNotBlank()) args["section"] = target
            }
        }
        return args
    }

    private fun buildReason(
        tool: AssistantTool,
        args: Map<String, String>,
        confidence: Float,
        modelTag: String = "local match",
    ): String {
        val pct = (confidence * 100).toInt().coerceIn(1, 99)
        val summary = when {
            args["state"] != null -> args["state"]!!.replaceFirstChar { it.uppercaseChar() }
            args["value"] != null -> "${args["value"]}%"
            args["action"] != null -> args["action"]!!.replaceFirstChar { it.uppercaseChar() }
            args["duration"] != null -> args["duration"]!!
            args["time"] != null -> args["time"]!!
            args["title"] != null -> args["title"]!!.take(32)
            args["query"] != null -> args["query"]!!.take(32)
            args["name"] != null -> args["name"]!!.take(32)
            else -> tool.description.take(48)
        }
        return "$summary · $pct% $modelTag"
    }

    internal fun extractArgsForTool(
        toolId: String,
        query: String,
        spanHint: String? = null,
    ): Map<String, String> {
        val trimmed = query.trim()
        val rawTokens = tokenize(trimmed)
        val features = extractQueryFeatures(trimmed, rawTokens)
        val args = extractArgs(toolId, trimmed, rawTokens, features).toMutableMap()
        val cleanSpan = spanHint?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanSpan != null) {
            when (toolId) {
                "add_task", "complete_task", "delete_task", "create_event" -> {
                    val existing = args["title"]
                    if (existing.isNullOrBlank() || existing.equals(trimmed, ignoreCase = true)) {
                        args["title"] = cleanSpan
                    }
                }
                "web_search", "play_media" -> {
                    val existing = args["query"]
                    if (existing.isNullOrBlank() || existing.equals(trimmed, ignoreCase = true)) {
                        args["query"] = cleanSpan
                    }
                }
                "timer", "alarm" -> {
                    if (args["message"].isNullOrBlank() &&
                        !cleanSpan.equals(args["duration"], ignoreCase = true) &&
                        !cleanSpan.equals(args["time"], ignoreCase = true)
                    ) {
                        args["message"] = cleanSpan
                    }
                }
                "run_routine" -> {
                    if (args["name"].isNullOrBlank()) args["name"] = cleanSpan
                }
                "freeze_app", "unfreeze_app", "app_info" -> {
                    if (args["app"].isNullOrBlank()) args["app"] = cleanSpan
                }
            }
        }
        return args
    }

    internal fun buildReasonForTool(
        tool: AssistantTool,
        args: Map<String, String>,
        confidence: Float,
        modelTag: String,
    ): String = buildReason(tool, args, confidence, modelTag)

    internal fun feedbackBoostForQuery(
        query: String,
        toolId: String,
        feedbackWeights: Map<String, Float>,
    ): Float = computeFeedbackBoost(tokenize(query), toolId, feedbackWeights)

    private fun computeFeedbackBoost(
        rawTokens: List<String>,
        toolId: String,
        feedbackWeights: Map<String, Float>,
    ): Float {
        if (feedbackWeights.isEmpty()) return 0f
        var sum = 0f
        for (tok in rawTokens) {
            sum += feedbackWeights["$tok->$toolId"] ?: 0f
        }
        return min(sum, 0.25f)
    }

    internal fun tokenize(text: String): List<String> =
        text.lowercase(Locale.US)
            .split(Regex("[^a-z0-9_]+"))
            .map { stem(it) }
            .filter { it.length >= 2 && it !in STOP_WORDS }

    private fun expandTokens(rawTokens: List<String>): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        for (tok in rawTokens) {
            map[tok] = (map[tok] ?: 0.0) + 1.0
            SYNONYM_MAP[tok]?.forEach { syn ->
                map[syn] = (map[syn] ?: 0.0) + 0.65
            }
        }
        return map
    }

    private fun stem(word: String): String {
        if (word.length <= 4) return word
        return when {
            word.endsWith("ing") && word.length > 5 -> word.dropLast(3)
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("ed") && word.length > 4 && !word.endsWith("eed") -> word.dropLast(2)
            word.endsWith("es") && word.length > 4 && !word.endsWith("ses") -> word.dropLast(1)
            word.endsWith("s") && word.length > 4 && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    }

    private fun buildTrigrams(text: String): Map<String, Float> {
        val padded = "  ${text.replace(Regex("\\s+"), " ").trim()}  "
        if (padded.length < 3) return emptyMap()
        val counts = HashMap<String, Float>(padded.length)
        for (i in 0..(padded.length - 3)) {
            val tri = padded.substring(i, i + 3)
            counts[tri] = (counts[tri] ?: 0f) + 1f
        }
        return counts
    }

    private fun vectorNorm(vec: Map<String, Float>): Float {
        var sumSq = 0f
        for (v in vec.values) sumSq += v * v
        return sqrt(sumSq)
    }

    private fun cosineSimilarity(
        a: Map<String, Float>,
        normA: Float,
        b: Map<String, Float>,
        normB: Float,
    ): Float {
        if (normA <= 0f || normB <= 0f) return 0f
        var dot = 0f
        val (smaller, larger) = if (a.size <= b.size) a to b else b to a
        for ((k, v) in smaller) {
            val other = larger[k]
            if (other != null) dot += v * other
        }
        return dot / (normA * normB)
    }

    private fun normalizedEditSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost,
                )
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        return (1f - (prev[b.length].toFloat() / maxLen.toFloat())).coerceIn(0f, 1f)
    }
}
