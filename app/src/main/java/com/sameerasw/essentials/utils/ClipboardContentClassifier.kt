/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities
 * File: ClipboardContentClassifier.kt
 * Description: Pure classifier for clipboard text content (URL / phone / email / hex color).
 */

package com.sameerasw.essentials.utils

import android.net.Uri

sealed class ClipboardContent {
    data class Url(val url: String, val host: String) : ClipboardContent()
    data class Phone(val number: String) : ClipboardContent()
    data class Email(val address: String) : ClipboardContent()
    data class HexColor(val code: String) : ClipboardContent()
}

object ClipboardContentClassifier {
    private val URL_REGEX = Regex("^https?://\\S+$")
    private val PHONE_REGEX = Regex("^\\+?[0-9\\s().-]{7,16}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val HEX_COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3}|[0-9a-fA-F]{8})$")
    private val FOUR_DIGITS_REGEX = Regex("^\\d{4}$")

    fun classify(raw: String?): ClipboardContent? {
        val text = raw?.trim() ?: return null
        if (text.length < 4) return null

        if (URL_REGEX.matches(text)) {
            return ClipboardContent.Url(url = text, host = extractHost(text))
        }
        if (EMAIL_REGEX.matches(text)) {
            return ClipboardContent.Email(address = text)
        }
        if (PHONE_REGEX.matches(text)) {
            if (FOUR_DIGITS_REGEX.matches(text)) return null
            val digits = text.filter { it.isDigit() }
            if (digits.length < 7) return null
            return ClipboardContent.Phone(number = text)
        }
        if (HEX_COLOR_REGEX.matches(text)) {
            return ClipboardContent.HexColor(code = text)
        }
        return null
    }

    private fun extractHost(url: String): String =
        runCatching { Uri.parse(url).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfter("//").substringBefore("/").substringAfter("@").substringBefore(":")
}
