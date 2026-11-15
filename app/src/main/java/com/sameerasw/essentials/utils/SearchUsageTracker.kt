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

    private val lock = Any()
    private val counts = HashMap<String, Int>()
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
        val count = synchronized(lock) {
            val next = (counts[key] ?: 0) + 1
            counts[key] = next
            next
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, count)
            .apply()
    }

    fun boost(context: Context, key: String): Int {
        if (!isRankingEnabled(context)) return 0
        ensureLoaded(context.applicationContext)
        val count = synchronized(lock) { counts[key] ?: 0 }
        if (count <= 0) return 0
        return (count * BOOST_PER_USE).coerceAtMost(MAX_BOOST)
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

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            for ((key, value) in prefs.all) {
                if (value is Int) counts[key] = value
            }
            loaded = true
        }
    }
}
