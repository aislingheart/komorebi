/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: CalendarWriteTool.kt
 * Description: Calendar read, create, edit, delete, and free-slot tools.
 */

package com.sameerasw.essentials.assistant.tools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

object CalendarWriteTool {
    private const val CREATE_SCHEMA =
        """{"type":"object","properties":{"title":{"type":"string","description":"Event title"},"begin":{"type":"string","description":"Start time as ISO-8601, yyyy-MM-dd HH:mm, HH:mm, or today/tomorrow"},"end":{"type":"string","description":"End time as ISO-8601, yyyy-MM-dd HH:mm, HH:mm, or today/tomorrow"},"allDay":{"type":"boolean","description":"Whether this is an all-day event"},"location":{"type":"string","description":"Optional event location"},"description":{"type":"string","description":"Optional event notes"}},"required":["title","begin","end"]}"""

    private const val UPDATE_SCHEMA =
        """{"type":"object","properties":{"eventId":{"type":"string","description":"Calendar event ID"},"title":{"type":"string","description":"New event title"},"begin":{"type":"string","description":"New start time"},"end":{"type":"string","description":"New end time"}},"required":["eventId"]}"""

    private const val DELETE_SCHEMA =
        """{"type":"object","properties":{"eventId":{"type":"string","description":"Calendar event ID to delete"}},"required":["eventId"]}"""

    private const val FREE_SLOTS_SCHEMA =
        """{"type":"object","properties":{"date":{"type":"string","description":"Day to inspect: today, tomorrow, yyyy-MM-dd, or ISO date"},"durationMinutes":{"type":"integer","minimum":1,"description":"Required free-slot length in minutes"},"workdayStart":{"type":"string","description":"Workday start such as 09:00"},"workdayEnd":{"type":"string","description":"Workday end such as 18:00"}},"required":["date","durationMinutes"]}"""

    private val CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "create_event",
            title = "Create Calendar Event",
            description = "Open the calendar editor with a new event pre-filled",
            keywords = listOf("create event", "add event", "calendar", "schedule", "meeting", "appointment"),
            parametersJson = CREATE_SCHEMA,
            execute = { context, args -> createEvent(context, args) },
        ),
        AssistantTool(
            id = "update_event",
            title = "Update Calendar Event",
            description = "Open the system calendar editor for an existing event",
            keywords = listOf("update event", "edit event", "change event", "reschedule", "calendar"),
            parametersJson = UPDATE_SCHEMA,
            execute = { context, args -> updateEvent(context, args) },
        ),
        AssistantTool(
            id = "delete_event",
            title = "Delete Calendar Event",
            description = "Delete a calendar event by its ID",
            keywords = listOf("delete event", "remove event", "cancel event", "calendar"),
            parametersJson = DELETE_SCHEMA,
            execute = { context, args -> deleteEvent(context, args) },
        ),
        AssistantTool(
            id = "find_free_slots",
            title = "Find Free Slots",
            description = "Find free calendar gaps during a workday",
            keywords = listOf("free slots", "free time", "availability", "calendar", "open slots", "gap"),
            parametersJson = FREE_SLOTS_SCHEMA,
            execute = { context, args -> findFreeSlots(context, args) },
        ),
    )

    private fun createEvent(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        calendarPermissionError(context)?.let { return it }

        val title = (args["title"] ?: args["name"] ?: "").trim()
        if (title.isEmpty()) return ToolResult(false, "An event title is required.")

        val beginRaw = (args["begin"] ?: args["start"] ?: args["from"] ?: "").trim()
        if (beginRaw.isEmpty()) return ToolResult(false, "An event start time is required.")
        val begin = AssistantTimeParser.parseDateTimeMillis(beginRaw)
            ?: return ToolResult(false, "Could not parse the start time '$beginRaw'.")

        val endRaw = (args["end"] ?: args["stop"] ?: args["to"] ?: "").trim()
        val end = if (endRaw.isEmpty()) {
            begin + 60L * 60L * 1000L
        } else {
            AssistantTimeParser.parseDateTimeMillis(endRaw)
                ?: return ToolResult(false, "Could not parse the end time '$endRaw'.")
        }
        if (end < begin) {
            return ToolResult(false, "The event end time must be after the start time.")
        }

        val allDay = parseBoolean(args["allDay"] ?: args["all_day"] ?: args["allday"])
        val location = (args["location"] ?: "").trim()
        val description = (args["description"] ?: args["notes"] ?: "").trim()

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            if (location.isNotEmpty()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            if (description.isNotEmpty()) putExtra(CalendarContract.Events.DESCRIPTION, description)
        }
        return launch(context, intent, "Opening the calendar editor for '$title'.")
    }

    private fun updateEvent(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        calendarPermissionError(context)?.let { return it }

        val eventId = (args["eventId"] ?: args["event_id"] ?: args["id"] ?: "").trim()
        if (eventId.isEmpty()) return ToolResult(false, "An event ID is required.")

        val intent = Intent(Intent.ACTION_EDIT).apply {
            data = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val title = (args["title"] ?: args["name"])?.trim()
        if (!title.isNullOrEmpty()) intent.putExtra(CalendarContract.Events.TITLE, title)

        val beginRaw = (args["begin"] ?: args["start"] ?: "").trim()
        if (beginRaw.isNotEmpty()) {
            val begin = AssistantTimeParser.parseDateTimeMillis(beginRaw)
                ?: return ToolResult(false, "Could not parse the start time '$beginRaw'.")
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        }

        val endRaw = (args["end"] ?: args["stop"] ?: "").trim()
        if (endRaw.isNotEmpty()) {
            val end = AssistantTimeParser.parseDateTimeMillis(endRaw)
                ?: return ToolResult(false, "Could not parse the end time '$endRaw'.")
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }

        return launch(context, intent, "Opening the calendar editor for event $eventId.")
    }

    private fun deleteEvent(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        if (!hasWritePermission(context)) {
            return ToolResult(false, "WRITE_CALENDAR permission is required to delete events.")
        }
        val eventId = (args["eventId"] ?: args["event_id"] ?: args["id"] ?: "").trim()
        if (eventId.isEmpty()) return ToolResult(false, "An event ID is required.")
        val id = eventId.toLongOrNull()
            ?: return ToolResult(false, "Event ID '$eventId' is not numeric.")
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return try {
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                ToolResult(true, "Deleted calendar event $eventId.")
            } else {
                ToolResult(false, "No calendar event was deleted for ID $eventId.")
            }
        } catch (e: SecurityException) {
            ToolResult(false, "WRITE_CALENDAR permission is required to delete events.")
        } catch (e: Exception) {
            ToolResult(false, "Could not delete event $eventId: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun findFreeSlots(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        if (!hasReadPermission(context)) {
            return ToolResult(false, "READ_CALENDAR permission is required to find free slots.")
        }

        val dateRaw = (args["date"] ?: args["day"] ?: "today").trim()
        val date = AssistantTimeParser.parseLocalDate(dateRaw)
            ?: return ToolResult(false, "Could not parse the date '$dateRaw'.")

        val durationMinutes = (args["durationMinutes"] ?: args["duration"] ?: args["minutes"])
            ?.trim()?.toIntOrNull() ?: 30
        if (durationMinutes <= 0) {
            return ToolResult(false, "The free-slot duration must be a positive number of minutes.")
        }

        val workdayStart = AssistantTimeParser.parseLocalTime(args["workdayStart"] ?: "09:00")
            ?: LocalTime.of(9, 0)
        val workdayEnd = AssistantTimeParser.parseLocalTime(args["workdayEnd"] ?: "18:00")
            ?: LocalTime.of(18, 0)
        if (!workdayEnd.isAfter(workdayStart)) {
            return ToolResult(false, "The workday end must be after the workday start.")
        }

        val zone = ZoneId.systemDefault()
        val dayStart = date.atTime(workdayStart).atZone(zone).toInstant().toEpochMilli()
        val dayEnd = date.atTime(workdayEnd).atZone(zone).toInstant().toEpochMilli()

        val busy = mutableListOf<Pair<Long, Long>>()
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, dayStart)
            ContentUris.appendId(this, dayEnd)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        try {
            context.contentResolver.query(
                instancesUri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val startIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                if (startIndex == -1 || endIndex == -1 || allDayIndex == -1) return@use
                while (cursor.moveToNext()) {
                    if (cursor.getInt(allDayIndex) != 0) {
                        busy += dayStart to dayEnd
                        continue
                    }
                    val eventStart = cursor.getLong(startIndex)
                    val eventEnd = cursor.getLong(endIndex)
                    val clippedStart = max(eventStart, dayStart)
                    val clippedEnd = min(eventEnd, dayEnd)
                    if (clippedEnd > clippedStart) {
                        busy += clippedStart to clippedEnd
                    }
                }
            }
        } catch (e: SecurityException) {
            return ToolResult(false, "READ_CALENDAR permission is required to find free slots.")
        } catch (e: Exception) {
            return ToolResult(false, "Could not read the calendar: ${e.message ?: e.javaClass.simpleName}")
        }

        val merged = mergeIntervals(busy)
        val minimumMillis = durationMinutes.toLong() * 60L * 1000L
        val free = mutableListOf<Pair<Long, Long>>()
        var cursorMillis = dayStart
        for ((busyStart, busyEnd) in merged) {
            if (busyStart > cursorMillis && busyStart - cursorMillis >= minimumMillis) {
                free += cursorMillis to busyStart
            }
            if (busyEnd > cursorMillis) cursorMillis = busyEnd
        }
        if (cursorMillis < dayEnd && dayEnd - cursorMillis >= minimumMillis) {
            free += cursorMillis to dayEnd
        }

        val lines = mutableListOf<String>()
        lines += "Free slots on $date (${durationMinutes} min, " +
            "${workdayStart.format(CLOCK_FORMATTER)}-${workdayEnd.format(CLOCK_FORMATTER)}):"
        if (free.isEmpty()) {
            lines += "  none"
        } else {
            free.forEach { (start, end) ->
                lines += "  ${formatMillis(start, zone)}-${formatMillis(end, zone)}"
            }
        }
        val data = lines.joinToString("\n")
        val message = if (free.isEmpty()) {
            "No free slots of at least $durationMinutes minutes were found on $date."
        } else {
            "Found ${free.size} free slot(s) of at least $durationMinutes minutes on $date."
        }
        return ToolResult(true, message, data)
    }

    private fun mergeIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        for ((start, end) in sorted) {
            val last = merged.lastOrNull()
            if (last == null || start > last.second) {
                merged += start to end
            } else if (last != null && end > last.second) {
                merged[merged.lastIndex] = last.first to end
            }
        }
        return merged
    }

    private fun formatMillis(
        millis: Long,
        zone: ZoneId,
    ): String = Instant.ofEpochMilli(millis).atZone(zone).format(CLOCK_FORMATTER)

    private fun parseBoolean(raw: String?): Boolean {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return false
        return value in setOf("1", "true", "yes", "y", "on", "all_day", "allday")
    }

    private fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun calendarPermissionError(context: Context): ToolResult? {
        val read = hasReadPermission(context)
        val write = hasWritePermission(context)
        if (read && write) return null
        val missing = buildList {
            if (!read) add("READ_CALENDAR")
            if (!write) add("WRITE_CALENDAR")
        }.joinToString(" and ")
        return ToolResult(false, "$missing permission is required to use the calendar.")
    }

    private fun launch(
        context: Context,
        intent: Intent,
        successMessage: String,
    ): ToolResult = try {
        context.startActivity(intent)
        ToolResult(true, successMessage)
    } catch (_: ActivityNotFoundException) {
        ToolResult(false, "No calendar app can handle ${intent.action} on this device.")
    } catch (e: Exception) {
        ToolResult(false, "Could not open the calendar app: ${e.message ?: e.javaClass.simpleName}")
    }
}
