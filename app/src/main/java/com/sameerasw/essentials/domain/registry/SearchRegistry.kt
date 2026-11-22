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
                    val interactiveBonus = if (item.control != null) 4 else 0
                    val score = baseScore + interactiveBonus + SearchUsageTracker.boost(
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
        val seenKeys = LinkedHashSet<String>()

        // --- Index all interactive Komorebi internal toggles, choices & settings screens ---
        com.sameerasw.essentials.search.InternalSettingsCatalog.all(context).forEach { item ->
            val dedupeKey = "${item.featureKey}:${item.targetSettingHighlightKey ?: item.title.lowercase()}"
            if (seenKeys.add(dedupeKey)) {
                allItems.add(item)
            }
        }

        // --- Index Features and Sub-settings ---
        FeatureRegistry
            .getFilteredFeatures(context, includeUnsupportedFeatures, includeLegacyFeatures)
            .forEach { feature ->
                val featureTitle = context.getString(feature.title)
                val featureCategory = context.getString(feature.category)
                val isUnsupported = !feature.isDeviceSupported(context)

                // Index the feature itself
                val featureDedupe = "${feature.id}:${featureTitle.lowercase()}"
                if (seenKeys.add(featureDedupe)) {
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
                }

                // Index sub-settings
                feature.searchableSettings.forEach { setting ->
                    val settingTitle = context.getString(setting.title)
                    val settingDedupe = "${feature.id}:${setting.targetSettingHighlightKey ?: settingTitle.lowercase()}"
                    if (seenKeys.add(settingDedupe)) {
                        allItems.add(
                            SearchableItem(
                                title = settingTitle,
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
            }

        return allItems
    }
}
