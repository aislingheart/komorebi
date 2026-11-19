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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.sameerasw.essentials.assistant.ScreenCaptureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrTools {

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
        val decoded = runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            reader.setHints(
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
            reader.decodeWithState(binaryBitmap)
        }.getOrNull() ?: return null
        return decoded.text?.takeIf { it.isNotBlank() }
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
