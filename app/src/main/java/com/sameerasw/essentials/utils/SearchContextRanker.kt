/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: SearchContextRanker.kt
 * Description: Lightweight foreground and time context signals for launcher ranking.
 */

package com.sameerasw.essentials.utils

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Locale

object SearchContextRanker {

    data class Snapshot(
        val foregroundPackage: String?,
        val communicationApp: Boolean,
        val mediaApp: Boolean,
        val navigationApp: Boolean,
        val rankingEnabled: Boolean,
    )

    private val communicationPackages = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.discord",
        "com.instagram.android",
        "com.facebook.orca",
        "com.microsoft.teams",
        "com.slack",
        "com.google.android.gm",
    )

    private val mediaPackages = setOf(
        "com.google.android.apps.photos",
        "com.google.android.gallery3d",
        "com.spotify.music",
        "com.google.android.youtube",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.soundcloud.android",
        "org.videolan.vlc",
    )

    private val navigationPackages = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.here.app.maps",
        "com.sygic.aura",
    )

    @Volatile private var cached: Snapshot? = null
    @Volatile private var cachedAtMs: Long = 0L
    private const val CACHE_MS = 15_000L

    fun snapshot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAtMs < CACHE_MS) return it }
        val foreground = runCatching { lastForegroundPackage(context) }.getOrNull()
            ?.takeIf { it != context.packageName }
        val value = Snapshot(
            foregroundPackage = foreground,
            communicationApp = foreground in communicationPackages,
            mediaApp = foreground in mediaPackages,
            navigationApp = foreground in navigationPackages,
            rankingEnabled = SearchUsageTracker.isRankingEnabled(context),
        )
        cached = value
        cachedAtMs = now
        return value
    }

    fun boostForApp(snapshot: Snapshot, packageName: String): Int {
        if (!snapshot.rankingEnabled || snapshot.foregroundPackage == null) return 0
        return when {
            snapshot.foregroundPackage == packageName -> 28
            else -> 0
        }
    }

    fun boostForContact(snapshot: Snapshot): Int =
        if (snapshot.rankingEnabled && snapshot.communicationApp) 22 else 0

    fun boostForFile(snapshot: Snapshot, isMedia: Boolean): Int =
        if (snapshot.rankingEnabled && isMedia && snapshot.mediaApp) 16 else 0

    fun boostForNavigation(snapshot: Snapshot): Int =
        if (snapshot.rankingEnabled && snapshot.navigationApp) 18 else 0

    fun boostForShortcut(snapshot: Snapshot, packageName: String): Int =
        if (snapshot.rankingEnabled && snapshot.foregroundPackage == packageName) 18 else 0

    fun invalidate() {
        cached = null
        cachedAtMs = 0L
    }

    private fun lastForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 5 * 60_000L, now) ?: return null
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val packageName = event.packageName ?: continue
            if (packageName == context.packageName) continue
            val lower = packageName.lowercase(Locale.getDefault())
            if (lower.contains("launcher") || lower.contains("systemui")) continue
            lastPackage = packageName
        }
        return lastPackage
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        }.getOrDefault(AppOpsManager.MODE_DEFAULT)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
