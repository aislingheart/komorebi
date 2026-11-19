/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: EssentialsRecognitionService.kt
 * Description: Speech recognition service stub required for system assistant integration.
 */

package com.sameerasw.essentials.services

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class EssentialsRecognitionService : RecognitionService() {
    override fun onStartListening(
        recognizerIntent: Intent?,
        listener: Callback?,
    ) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onStopListening(listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) {
    }
}
