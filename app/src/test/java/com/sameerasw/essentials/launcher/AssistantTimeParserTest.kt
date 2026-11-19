package com.sameerasw.essentials.launcher

import com.sameerasw.essentials.assistant.tools.AssistantTimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AssistantTimeParserTest {

    private val now = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, ZoneId.of("UTC"))

    @Test
    fun parsesAmPmTimes() {
        assertEquals(LocalTime.of(22, 0), AssistantTimeParser.parseLocalTime("10pm"))
        assertEquals(LocalTime.of(15, 30), AssistantTimeParser.parseLocalTime("3:30pm"))
        assertEquals(LocalTime.of(10, 0), AssistantTimeParser.parseLocalTime("10 am"))
    }

    @Test
    fun parsesRelativeAmPmDateTime() {
        val millis = AssistantTimeParser.parseDateTimeMillis("tomorrow at 5pm", now)
        assertNotNull(millis)
        val result = java.time.Instant.ofEpochMilli(millis!!).atZone(ZoneId.of("UTC"))
        assertEquals(2026, result.year)
        assertEquals(11, result.monthValue)
        assertEquals(18, result.dayOfMonth)
        assertEquals(17, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun parsesDateAndTime() {
        val millis = AssistantTimeParser.parseDateTimeMillis("2026-11-18 14:00", now)
        assertNotNull(millis)
        val result = java.time.Instant.ofEpochMilli(millis!!).atZone(ZoneId.of("UTC"))
        assertEquals(2026, result.year)
        assertEquals(11, result.monthValue)
        assertEquals(18, result.dayOfMonth)
        assertEquals(14, result.hour)
    }
}
