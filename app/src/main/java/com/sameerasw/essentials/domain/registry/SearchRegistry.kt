/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Domain Layer Registries
 * File: SearchRegistry.kt
 * Description: Registry indexing searchable features, settings items, and in-app action shortcuts.
 */

package com.sameerasw.essentials.domain.registry

import android.content.Context
import com.sameerasw.essentials.R
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.StatusBarIconRegistry
import com.sameerasw.essentials.domain.model.SearchableItem
import com.sameerasw.essentials.utils.FuzzySearchUtil
import com.sameerasw.essentials.utils.SearchUsageTracker

object SearchRegistry {
    fun search(
        context: Context,
        query: String,
    ): List<SearchableItem> =
        search(
            context,
            query,
            SettingsRepository(context).isEnableUnsupportedFeatures(),
            SettingsRepository(context).isShowLegacyFeatures(),
        )

    fun search(
        context: Context,
        query: String,
        includeUnsupportedFeatures: Boolean = false,
        includeLegacyFeatures: Boolean = true,
    ): List<SearchableItem> =
        searchScored(context, query, includeUnsupportedFeatures, includeLegacyFeatures).map { it.first }

    fun searchScored(
        context: Context,
        query: String,
        includeUnsupportedFeatures: Boolean = false,
        includeLegacyFeatures: Boolean = true,
    ): List<Pair<SearchableItem, Int>> =
        searchScored(
            context = context,
            items = indexItems(context, includeUnsupportedFeatures, includeLegacyFeatures),
            query = query,
        )

    fun searchScored(
        context: Context,
        items: List<SearchableItem>,
        query: String,
    ): List<Pair<SearchableItem, Int>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return items
            .mapNotNull { item ->
                val baseScore = FuzzySearchUtil.scoreFields(
                    query = trimmed,
                    primary = item.title,
                    secondary = item.keywords,
                    tertiary = listOfNotNull(item.parentFeature, item.category, item.description),
                )
                if (baseScore > 0) {
                    val score = baseScore + SearchUsageTracker.boost(
                        context,
                        SearchUsageTracker.settingKey(item.featureKey, item.title),
                    )
                    item to score
                } else {
                    null
                }
            }
            .sortedWith(compareByDescending<Pair<SearchableItem, Int>> { it.second }.thenBy { it.first.title.lowercase() })
    }

    fun indexItems(
        context: Context,
        includeUnsupportedFeatures: Boolean = false,
        includeLegacyFeatures: Boolean = true,
    ): List<SearchableItem> {
        val allItems = mutableListOf<SearchableItem>()

        // --- Index Features and Sub-settings ---
        FeatureRegistry
            .getFilteredFeatures(context, includeUnsupportedFeatures, includeLegacyFeatures)
            .forEach { feature ->
                val featureTitle = context.getString(feature.title)
                val featureCategory = context.getString(feature.category)
                val isUnsupported = !feature.isDeviceSupported(context)

                // Index the feature itself
                allItems.add(
                    SearchableItem(
                        title = featureTitle,
                        description = context.getString(feature.description),
                        category = featureCategory,
                        icon = feature.iconRes,
                        featureKey = feature.id,
                        keywords =
                            listOf(
                                context.getString(R.string.keyword_feature),
                                context.getString(R.string.keyword_settings),
                            ),
                        isBeta = feature.isBeta,
                        isLegacy = feature.isLegacy,
                        isUnsupported = isUnsupported,
                    ),
                )

                // Index sub-settings
                feature.searchableSettings.forEach { setting ->
                    allItems.add(
                        SearchableItem(
                            title = context.getString(setting.title),
                            description = context.getString(setting.description),
                            category =
                                setting.category?.let { context.getString(it) }
                                    ?: featureCategory,
                            icon = feature.iconRes,
                            featureKey = feature.id,
                            parentFeature = featureTitle,
                            targetSettingHighlightKey = setting.targetSettingHighlightKey,
                            keywords =
                                if (setting.keywordRes != 0) {
                                    context.resources
                                        .getStringArray(
                                            setting.keywordRes,
                                        ).toList()
                                } else {
                                    emptyList()
                                },
                            isBeta = feature.isBeta,
                            isLegacy = feature.isLegacy,
                            isUnsupported = isUnsupported,
                        ),
                    )
                }
            }

        // --- Dynamic Status Bar Icons ---
        StatusBarIconRegistry.ALL_ICONS.forEach { icon ->
            val title = context.getString(icon.displayNameRes)
            allItems.add(
                SearchableItem(
                    title = title,
                    description = context.getString(R.string.search_desc_toggle_visibility, title),
                    category = context.getString(R.string.feat_statusbar_icons_title),
                    icon = icon.iconRes,
                    featureKey = "Statusbar icons",
                    parentFeature = context.getString(R.string.feat_statusbar_icons_title),
                    targetSettingHighlightKey = title,
                    keywords =
                        icon.blacklistNames +
                            listOf(
                                context.getString(R.string.keyword_hide),
                                context.getString(R.string.keyword_show),
                                context.getString(R.string.keyword_visibility),
                            ),
                ),
            )
        }

        return allItems
    }
}
