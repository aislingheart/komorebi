/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher
 * File: CalendarIntentLauncher.kt
 * Description: Safe calendar event creation with editor and app fallbacks.
 */

package com.sameerasw.essentials.launcher

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.TimeZone

object CalendarIntentLauncher {

    fun createEvent(
        context: Context,
        title: String,
        beginMillis: Long? = null,
        endMillis: Long? = null,
    ): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (beginMillis != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis ?: (beginMillis + 60L * 60L * 1000L))
            }
        }
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0)
        }.getOrNull()
        if (resolved != null) {
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (started) return true
        }
        return openCalendar(context)
    }

    fun openCalendar(context: Context): Boolean {
        val knownCalendarPackages = listOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.sec.android.calendar",
            "com.coloros.calendar",
            "com.oplus.calendar",
            "com.miui.calendar",
        )
        for (packageName in knownCalendarPackages) {
            val launch = runCatching {
                context.packageManager.getLaunchIntentForPackage(packageName)
            }.getOrNull() ?: continue
            if (runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
                return true
            }
        }
        val fallback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }
}
