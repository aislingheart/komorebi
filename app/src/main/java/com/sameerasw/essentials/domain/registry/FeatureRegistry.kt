/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Domain Layer Registries
 * File: FeatureRegistry.kt
 * Description: Central registry defining retained core search and utility features.
 */

package com.sameerasw.essentials.domain.registry

import android.content.Context
import android.content.Intent
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.Feature
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.viewmodels.MainViewModel

object FeatureRegistry {
    const val BACK_GESTURE_FEATURE_ID = "Back gesture"

    fun getFilteredFeatures(
        context: Context,
        includeUnsupported: Boolean,
        includeLegacy: Boolean = true,
    ): List<Feature> {
        val featureMap = ALL_FEATURES.associateBy { it.id }
        val supportCache = mutableMapOf<String, Boolean>()

        fun isVisible(feature: Feature): Boolean {
            supportCache[feature.id]?.let { return it }

            val supported = includeUnsupported || feature.isDeviceSupported(context)
            val visible =
                supported &&
                    (includeLegacy || !feature.isLegacy) &&
                    (
                        feature.parentFeatureId?.let { parentId ->
                            featureMap[parentId]?.let { parent -> isVisible(parent) } ?: true
                        } ?: true
                    )

            supportCache[feature.id] = visible
            return visible
        }

        return ALL_FEATURES.filter { isVisible(it) }
    }

    fun getUnsupportedFeatures(context: Context): List<Feature> =
        ALL_FEATURES.filter { !it.isDeviceSupported(context) }

    val ALL_FEATURES: List<Feature> =
        listOf(
            object : Feature(
                id = "Pixel Searchbar",
                title = R.string.feat_pixel_searchbar_title,
                iconRes = R.drawable.rounded_search_24,
                category = R.string.cat_interface,
                description = R.string.feat_pixel_searchbar_desc,
                aboutDescription = R.string.about_desc_pixel_searchbar,
                permissionKeys = listOf("WRITE_SECURE_SETTINGS"),
                showToggle = true,
                hasMoreSettings = true,
                isBeta = true,
                animationRes = R.raw.searchbar_motion,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = viewModel.isPixelSearchbarEnabled.value

                override fun isToggleEnabled(
                    viewModel: MainViewModel,
                    context: Context,
                ) = viewModel.isWriteSecureSettingsEnabled.value ||
                    viewModel.isShizukuPermissionGranted.value ||
                    viewModel.isRootPermissionGranted.value

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {
                    viewModel.setPixelSearchbarEnabled(enabled, context)
                }

                override fun onClick(
                    context: Context,
                    viewModel: MainViewModel,
                ) {
                    context.startActivity(Intent(context, PixelSearchbarSettingsActivity::class.java))
                }
            },
            object : Feature(
                id = "Activity launcher",
                contributors = listOf("jawzf"),
                title = R.string.feat_activity_launcher_title,
                iconRes = R.drawable.rounded_app_registration_24,
                category = R.string.cat_interaction,
                description = R.string.feat_activity_launcher_desc,
                aboutDescription = R.string.about_desc_activity_launcher,
                showToggle = false,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = false

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {}
            },
            object : Feature(
                id = "Link actions",
                title = R.string.feat_link_actions_title,
                iconRes = R.drawable.rounded_link_24,
                category = R.string.cat_interaction,
                description = R.string.feat_link_actions_desc,
                aboutDescription = R.string.about_desc_link_actions,
                showToggle = false,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = false

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {}
            },
            object : Feature(
                id = "Standby apps",
                title = R.string.feat_standby_apps_title,
                iconRes = R.drawable.rounded_app_registration_24,
                category = R.string.cat_system,
                description = R.string.feat_standby_apps_desc,
                aboutDescription = R.string.about_desc_standby_apps,
                permissionKeys = listOf("SHIZUKU"),
                showToggle = false,
                hasMoreSettings = true,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = true

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {}
            },
            object : Feature(
                id = "Calendar Sync",
                title = R.string.feat_calendar_sync_title,
                iconRes = R.drawable.rounded_calendar_today_24,
                category = R.string.cat_tools,
                description = R.string.feat_calendar_sync_desc,
                aboutDescription = R.string.about_desc_calendar_sync,
                permissionKeys = listOf("READ_CALENDAR"),
            ) {
                override fun isEnabled(viewModel: MainViewModel) = viewModel.isCalendarSyncEnabled.value

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) = viewModel.setCalendarSyncEnabled(enabled, context)
            },
        )
}
