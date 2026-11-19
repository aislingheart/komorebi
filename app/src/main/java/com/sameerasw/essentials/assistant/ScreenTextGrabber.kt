/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: ScreenTextGrabber.kt
 * Description: Extracts visible text from the screen behind search (a11y tree, OCR fallback).
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object ScreenTextGrabber {

    data class ScreenText(
        val text: String,
        val source: String,
    )

    suspend fun grabScreenText(context: Context): ScreenText? = withContext(Dispatchers.IO) {
        val snapshot = ScreenContextExtractor.current()
        val fresh = snapshot != null && System.currentTimeMillis() - snapshot.capturedAt < 120_000L
        if (fresh && !snapshot.text.isNullOrBlank()) {
            return@withContext ScreenText(snapshot.text, "accessibility")
        }
        val bitmap = if (ScreenCaptureStore.isFresh()) ScreenCaptureStore.openTimeScreenshot else null
        if (bitmap != null) {
            val ocr = runCatching { recognizeText(bitmap) }.getOrNull()
            if (!ocr.isNullOrBlank()) {
                return@withContext ScreenText(ocr, "ocr")
            }
        }
        if (fresh && !snapshot.selectedText.isNullOrBlank()) {
            return@withContext ScreenText(snapshot.selectedText, "selection")
        }
        null
    }

    private suspend fun recognizeText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { runCatching { recognizer.close() } }
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                    }
            }
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
