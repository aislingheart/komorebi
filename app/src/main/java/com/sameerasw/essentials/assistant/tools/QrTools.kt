/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: QrTools.kt
 * Description: Deterministic QR/barcode scanning from the open-time screen snapshot.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.sameerasw.essentials.assistant.ScreenCaptureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object QrTools {
    private const val MAX_DECODE_DIMENSION = 1280

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "scan_screen_qr",
            title = "Scan QR on Screen",
            description = "Read a QR code or barcode visible on the current screen",
            keywords = listOf(
                "scan qr", "qr scanner", "read qr", "barcode", "scan code",
            ),
            parametersJson = """{"type":"object","properties":{}}""",
            execute = { context, _ -> scanScreenQr(context) },
        ),
    )

    private suspend fun scanScreenQr(context: Context): ToolResult {
        val bitmap = if (ScreenCaptureStore.isFresh()) {
            ScreenCaptureStore.openTimeScreenshot
        } else {
            null
        } ?: return ToolResult(
            false,
            "No screen snapshot available — open search with the accessibility gesture over the QR code first.",
        )

        val decoded = withContext(Dispatchers.Default) { decodeBitmap(bitmap) }
            ?: return ToolResult(false, "No QR/barcode found on the current screen.")
        copyToClipboard(context, decoded)
        return ToolResult(true, "QR code found (copied):", decoded)
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        var softwareCopy: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        return try {
            val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { softwareCopy = it }
                    ?: return null
            } else {
                bitmap
            }

            val working = run {
                val maxSide = max(readable.width, readable.height)
                if (maxSide > MAX_DECODE_DIMENSION) {
                    val scale = MAX_DECODE_DIMENSION.toFloat() / maxSide.toFloat()
                    val targetW = (readable.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (readable.height * scale).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(readable, targetW, targetH, true).also {
                        if (it !== readable) scaledBitmap = it
                    }
                } else {
                    readable
                }
            }

            val width = working.width
            val height = working.height
            val pixels = IntArray(width * height)
            working.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val inverted = InvertedLuminanceSource(source)
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to listOf(
                            BarcodeFormat.QR_CODE,
                            BarcodeFormat.CODE_128,
                            BarcodeFormat.EAN_13,
                            BarcodeFormat.PDF_417,
                        ),
                    ),
                )
            }

            tryDecode(reader, source) ?: tryDecode(reader, inverted)
        } catch (_: Throwable) {
            null
        } finally {
            scaledBitmap?.let { if (!it.isRecycled) it.recycle() }
            softwareCopy?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun tryDecode(reader: MultiFormatReader, source: LuminanceSource): String? {
        val candidates = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
        )
        for (candidate in candidates) {
            val decoded = runCatching { reader.decodeWithState(candidate).text }.getOrNull()
            reader.reset()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return null
    }

    private suspend fun copyToClipboard(
        context: Context,
        text: String,
    ) = withContext(Dispatchers.Main) {
        runCatching {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.setPrimaryClip(ClipData.newPlainText("QR code", text))
        }
    }
}
