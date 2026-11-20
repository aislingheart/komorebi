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
                title = R.string.pixel_searchbar_settings_title,
                iconRes = R.drawable.rounded_search_24,
                category = R.string.cat_interface,
                description = R.string.feat_pixel_searchbar_desc,
                aboutDescription = R.string.about_desc_pixel_searchbar,
                showToggle = false,
                hasMoreSettings = true,
            ) {
                override fun isEnabled(viewModel: MainViewModel) = false

                override fun onToggle(
                    viewModel: MainViewModel,
                    context: Context,
                    enabled: Boolean,
                ) {}

                override fun onClick(
                    context: Context,
                    viewModel: MainViewModel,
                ) {
                    context.startActivity(Intent(context, PixelSearchbarSettingsActivity::class.java))
                }
            },
        )
}
