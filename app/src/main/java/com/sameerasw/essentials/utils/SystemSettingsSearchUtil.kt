/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Utilities
 * File: SystemSettingsSearchUtil.kt
 * Description: Static Android settings index for universal search.
 */

package com.sameerasw.essentials.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.PixelSearchResultItem

object SystemSettingsSearchUtil {

    fun all(context: Context): List<PixelSearchResultItem.SystemSettingItem> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.island_network_wifi),
                subtitle = context.getString(R.string.pixel_search_setting_network),
                iconRes = R.drawable.rounded_android_wifi_3_bar_24,
                intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.icon_bluetooth),
                subtitle = context.getString(R.string.pixel_search_setting_bluetooth),
                iconRes = R.drawable.rounded_bluetooth_24,
                intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.cat_display),
                subtitle = context.getString(R.string.pixel_search_setting_display),
                iconRes = R.drawable.rounded_mobile_text_2_24,
                intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.pixel_search_setting_sound_title),
                subtitle = context.getString(R.string.pixel_search_setting_sound),
                iconRes = R.drawable.rounded_volume_up_24,
                intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.icon_battery),
                subtitle = context.getString(R.string.pixel_search_setting_battery),
                iconRes = R.drawable.rounded_battery_charging_60_24,
                intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.tab_apps),
                subtitle = context.getString(R.string.pixel_search_setting_apps),
                iconRes = R.drawable.rounded_apps_24,
                intent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.option_notifications),
                subtitle = context.getString(R.string.pixel_search_setting_notifications),
                iconRes = R.drawable.rounded_notifications_unread_24,
                intent = Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.label_device_storage),
                subtitle = context.getString(R.string.pixel_search_setting_storage),
                iconRes = R.drawable.rounded_save_24,
                intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply { addFlags(flags) },
            ),
            PixelSearchResultItem.SystemSettingItem(
                title = context.getString(R.string.pixel_search_setting_security_title),
                subtitle = context.getString(R.string.pixel_search_setting_security),
                iconRes = R.drawable.rounded_security_24,
                intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(flags) },
            ),
        )
    }

    fun search(
        context: Context,
        settings: List<PixelSearchResultItem.SystemSettingItem>,
        query: String,
        limit: Int = 4,
    ): List<PixelSearchResultItem.SystemSettingItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return settings.take(limit)
        return settings.mapNotNull { item ->
            val baseScore = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = item.title,
                secondary = listOf(item.subtitle),
            )
            if (baseScore <= 0) {
                null
            } else {
                val usageBoost = SearchUsageTracker.boost(
                    context,
                    SearchUsageTracker.suggestionKey("syssetting", item.title),
                )
                item to (baseScore + usageBoost)
            }
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
