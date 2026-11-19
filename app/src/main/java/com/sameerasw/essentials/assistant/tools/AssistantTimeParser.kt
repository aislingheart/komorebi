/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: AssistantTimeParser.kt
 * Description: Shared date and time parsing helpers for assistant tools.
 */

package com.sameerasw.essentials.assistant.tools

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AssistantTimeParser {
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
        DateTimeFormatter.ofPattern("h:mma", Locale.US),
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("ha", Locale.US),
        DateTimeFormatter.ofPattern("h a", Locale.US),
    )
    private val RELATIVE_PREFIX = Regex(
        "^(today|tomorrow)\\b(?:\\s+at)?[\\s,]*",
        RegexOption.IGNORE_CASE,
    )

    fun parseDateTimeMillis(
        raw: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val lower = text.lowercase(Locale.ROOT)
        val dayOffset = when {
            lower.startsWith("tomorrow") -> 1L
            else -> 0L
        }
        val stripped = text.replaceFirst(RELATIVE_PREFIX, "").trim()
        if (stripped.isEmpty()) {
            return now.toLocalDate().plusDays(dayOffset).atStartOfDay(now.zone).toInstant().toEpochMilli()
        }

        return attempt { ZonedDateTime.parse(stripped) }?.toInstant()?.toEpochMilli()
            ?: attempt { OffsetDateTime.parse(stripped) }?.toInstant()?.toEpochMilli()
            ?: attempt { Instant.parse(stripped) }?.toEpochMilli()
            ?: attempt { LocalDateTime.parse(stripped) }
                ?.atZone(now.zone)?.toInstant()?.toEpochMilli()
            ?: attempt { LocalDate.parse(stripped) }
                ?.atStartOfDay(now.zone)?.toInstant()?.toEpochMilli()
            ?: attempt { LocalDateTime.parse(stripped, DATE_TIME_FORMATTER) }
                ?.atZone(now.zone)?.toInstant()?.toEpochMilli()
            ?: parseTime(stripped)?.let { time ->
                now.toLocalDate().plusDays(dayOffset).atTime(time)
                    .atZone(now.zone).toInstant().toEpochMilli()
            }
    }

    fun parseLocalDate(
        raw: String,
        today: LocalDate = LocalDate.now(),
    ): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        when (text.lowercase(Locale.ROOT)) {
            "today" -> return today
            "tomorrow" -> return today.plusDays(1)
        }
        return attempt { LocalDate.parse(text) }
            ?: attempt { LocalDate.parse(text, DATE_FORMATTER) }
            ?: attempt { LocalDateTime.parse(text).toLocalDate() }
            ?: attempt { LocalDateTime.parse(text, DATE_TIME_FORMATTER).toLocalDate() }
            ?: parseDateTimeMillis(text)
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    fun parseLocalTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return attempt { LocalTime.parse(text) }
            ?: parseTime(text)
            ?: Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(text)?.let { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: return@let null
                val minute = match.groupValues[2].toIntOrNull() ?: return@let null
                if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
            }
    }

    private fun parseTime(text: String): LocalTime? {
        when (text.trim().lowercase(Locale.ROOT)) {
            "noon" -> return LocalTime.of(12, 0)
            "midnight" -> return LocalTime.of(0, 0)
        }
        Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$", RegexOption.IGNORE_CASE)
            .matchEntire(text)
            ?.let { match ->
                var hour = match.groupValues[1].toIntOrNull() ?: return@let
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val isPm = match.groupValues[3].equals("pm", ignoreCase = true)
                if (hour !in 1..12 || minute !in 0..59) return@let
                if (isPm && hour < 12) hour += 12
                if (!isPm && hour == 12) hour = 0
                return LocalTime.of(hour, minute)
            }
        for (formatter in TIME_FORMATTERS) {
            attempt { LocalTime.parse(text, formatter) }?.let { return it }
        }
        return null
    }

    private inline fun <T> attempt(block: () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }
}
