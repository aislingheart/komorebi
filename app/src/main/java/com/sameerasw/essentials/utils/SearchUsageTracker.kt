/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: SearchUsageTracker.kt
 * Description: Lightweight background usage ranking for launcher search results.
 */

package com.sameerasw.essentials.utils

import android.content.Context

object SearchUsageTracker {

    private const val PREFS_NAME = "komorebi_search_usage"
    private const val SETTINGS_PREFS = "essentials_prefs"
    private const val KEY_USAGE_RANKING_ENABLED = "pixel_search_usage_ranking"
    private const val MAX_BOOST = 90
    private const val BOOST_PER_USE = 6
    private const val MAX_RECENCY_BOOST = 24
    private const val MAX_TIME_BOOST = 12
    private const val LAST_USED_SUFFIX = "#last"
    private const val HOUR_BUCKET_SUFFIX = "#bucket"

    private val lock = Any()
    private val counts = HashMap<String, Int>()
    private val lastUsed = HashMap<String, Long>()
    private val hourCounts = HashMap<String, Int>()
    @Volatile private var loaded = false
    @Volatile private var rankingEnabled: Boolean? = null

    fun appKey(packageName: String): String = "app:$packageName"

    fun fileKey(uri: String): String = "file:$uri"

    fun settingKey(featureKey: String, title: String): String = "setting:$featureKey:$title"

    fun actionKey(id: String): String = "action:$id"

    fun suggestionKey(type: String, id: String): String = "$type:$id"

    fun record(context: Context, key: String) {
        val appContext = context.applicationContext
        ensureLoaded(appContext)
        val now = System.currentTimeMillis()
        val bucketKey = hourBucketKey(key, now)
        val (count, bucketCount) = synchronized(lock) {
            val next = (counts[key] ?: 0) + 1
            val nextBucket = (hourCounts[bucketKey] ?: 0) + 1
            counts[key] = next
            lastUsed[key] = now
            hourCounts[bucketKey] = nextBucket
            next to nextBucket
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, count)
            .putLong(key + LAST_USED_SUFFIX, now)
            .putInt(bucketKey, bucketCount)
            .apply()
    }

    fun boost(context: Context, key: String): Int {
        if (!isRankingEnabled(context)) return 0
        ensureLoaded(context.applicationContext)
        val now = System.currentTimeMillis()
        val (count, last, bucketCount) = synchronized(lock) {
            Triple(
                counts[key] ?: 0,
                lastUsed[key] ?: 0L,
                hourCounts[hourBucketKey(key, now)] ?: 0,
            )
        }
        if (count <= 0) return 0
        val usageBoost = (count * BOOST_PER_USE).coerceAtMost(MAX_BOOST)
        return usageBoost + recencyBoost(now, last) + timeBoost(bucketCount)
    }

    fun boostFor(context: Context, vararg keys: String): Int {
        if (!isRankingEnabled(context)) return 0
        var best = 0
        for (key in keys) {
            val value = boost(context, key)
            if (value > best) best = value
        }
        return best
    }

    fun isRankingEnabled(context: Context): Boolean {
        rankingEnabled?.let { return it }
        val enabled = context.applicationContext
            .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USAGE_RANKING_ENABLED, true)
        rankingEnabled = enabled
        return enabled
    }

    fun invalidateRankingSetting() {
        rankingEnabled = null
    }

    private fun recencyBoost(now: Long, lastUsedAt: Long): Int {
        if (lastUsedAt <= 0L) return 0
        val age = (now - lastUsedAt).coerceAtLeast(0L)
        return when {
            age <= 10 * 60_000L -> MAX_RECENCY_BOOST
            age <= 60 * 60_000L -> 16
            age <= 24 * 60 * 60_000L -> 10
            age <= 7 * 24 * 60 * 60_000L -> 4
            else -> 0
        }
    }

    private fun timeBoost(bucketCount: Int): Int =
        (bucketCount * 2).coerceAtMost(MAX_TIME_BOOST)

    private fun hourBucketKey(key: String, now: Long): String {
        val hour = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault())
            .hour
        return key + HOUR_BUCKET_SUFFIX + (hour / 3)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            for ((storedKey, value) in prefs.all) {
                when {
                    storedKey.endsWith(LAST_USED_SUFFIX) && value is Long -> {
                        lastUsed[storedKey.removeSuffix(LAST_USED_SUFFIX)] = value
                    }
                    storedKey.contains(HOUR_BUCKET_SUFFIX) && value is Int -> {
                        hourCounts[storedKey] = value
                    }
                    value is Int -> counts[storedKey] = value
                }
            }
            loaded = true
        }
    }
}
