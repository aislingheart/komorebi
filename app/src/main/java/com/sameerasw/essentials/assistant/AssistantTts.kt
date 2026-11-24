/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: AssistantTts.kt
 * Description: System text-to-speech output for voice search responses.
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object AssistantTts {

    private const val TAG = "AssistantTts"

    data class VoicePreset(
        val id: String,
        val label: String,
        val pitch: Float,
        val rate: Float,
    )

    val presets: List<VoicePreset> = listOf(
        VoicePreset("default", "Default", 1.0f, 1.0f),
        VoicePreset("deep", "Deep", 0.78f, 0.95f),
        VoicePreset("fast", "Fast", 1.0f, 1.35f),
    )

    fun presetFor(id: String): VoicePreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pending: Pair<String, VoicePreset>? = null
    @Volatile private var retryingAfterError = false

    fun speak(context: Context, text: String, presetId: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val preset = presetFor(presetId)
        val appContext = context.applicationContext
        val readyEngine: TextToSpeech? = synchronized(this) {
            val current = ensureEngineLocked(appContext)
            if (current == null || !ready) {
                pending = clean to preset
                null
            } else {
                current
            }
        }
        if (readyEngine != null) {
            val status = runCatching {
                applyPreset(readyEngine, preset)
                readyEngine.speak(clean.take(4000), TextToSpeech.QUEUE_FLUSH, null, "komorebi-tts")
            }.getOrDefault(TextToSpeech.ERROR)
            if (status != TextToSpeech.ERROR) {
                retryingAfterError = false
                return
            }
            Log.w(TAG, "TTS speak returned ERROR; resetting engine for retry")
            synchronized(this) {
                if (retryingAfterError) {
                    retryingAfterError = false
                    return
                }
                retryingAfterError = true
                resetEngineLocked()
                pending = clean to preset
                ensureEngineLocked(appContext)
            }
        }
    }

    fun stop() {
        val current = synchronized(this) {
            pending = null
            engine
        }
        runCatching { current?.stop() }
            .onFailure { Log.w(TAG, "Could not stop TTS", it) }
    }

    fun shutdown() {
        synchronized(this) {
            pending = null
            retryingAfterError = false
            resetEngineLocked()
        }
    }

    fun preview(context: Context, presetId: String) {
        speak(context, "Hi, I'm Komorebi. Voice output is ready.", presetId)
    }

    private fun resetEngineLocked() {
        val old = engine
        engine = null
        ready = false
        runCatching { old?.stop() }
        runCatching { old?.shutdown() }
            .onFailure { Log.w(TAG, "Could not shut down TTS", it) }
    }

    private fun ensureEngineLocked(context: Context): TextToSpeech? {
        engine?.let { return it }
        val created = TextToSpeech(context.applicationContext) { status ->
            var toSpeak: Triple<TextToSpeech, String, VoicePreset>? = null
            synchronized(this) {
                val tts = engine
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    val preferred = Locale.getDefault()
                    val result = tts.setLanguage(preferred)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US)
                    }
                    ready = true
                    retryingAfterError = false
                    val queued = pending
                    pending = null
                    if (queued != null) {
                        toSpeak = Triple(tts, queued.first, queued.second)
                    }
                } else {
                    pending = null
                    retryingAfterError = false
                    resetEngineLocked()
                }
            }
            toSpeak?.let { (tts, message, preset) ->
                runCatching {
                    applyPreset(tts, preset)
                    tts.speak(message.take(4000), TextToSpeech.QUEUE_FLUSH, null, "komorebi-tts")
                }.onFailure { Log.w(TAG, "Could not speak queued TTS message", it) }
            }
        }
        engine = created
        return created
    }

    private fun applyPreset(tts: TextToSpeech, preset: VoicePreset) {
        tts.setPitch(preset.pitch)
        tts.setSpeechRate(preset.rate)
    }
}
