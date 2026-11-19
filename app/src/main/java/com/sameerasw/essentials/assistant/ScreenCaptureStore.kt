/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: ScreenCaptureStore.kt
 * Description: Holds the screenshot captured when the search overlay opens, for OCR fallback.
 */

package com.sameerasw.essentials.assistant

import android.graphics.Bitmap

object ScreenCaptureStore {
    @Volatile
    var openTimeScreenshot: Bitmap? = null
        private set

    @Volatile
    var capturedAt: Long = 0L
        private set

    fun store(bitmap: Bitmap?) {
        openTimeScreenshot = bitmap
        capturedAt = if (bitmap != null) System.currentTimeMillis() else 0L
    }

    fun isFresh(maxAgeMs: Long = 120_000L): Boolean =
        openTimeScreenshot != null && System.currentTimeMillis() - capturedAt < maxAgeMs

    fun clear() {
        openTimeScreenshot = null
        capturedAt = 0L
    }
}
