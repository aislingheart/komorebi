/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Engine
 * File: SearchProvider.kt
 * Description: Models for two-stage scoped search providers, prefix hints, entity routes, and dynamic shortcuts.
 */

package com.sameerasw.essentials.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap

enum class SearchProviderKind {
    PHOTOS,
    APPS,
    CONTACTS,
    FILES,
    SETTINGS,
    WEB,
    APP_INTENT,
    EXTENSION,
}

data class IntentSearchTarget(
    val action: String,
    val scheme: String? = null,
    val componentName: ComponentName? = null,
)

data class SearchProvider(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: SearchProviderKind,
    val aliases: List<String>,
    val packageName: String? = null,
    val appIcon: ImageBitmap? = null,
    @DrawableRes val iconRes: Int,
    val targets: List<IntentSearchTarget> = emptyList(),
    val priority: Int = 0,
    val deepLinkTemplate: String? = null,
    val webUrlTemplate: String? = null,
    val category: String = "Provider",
    val fallbackSchemes: List<String> = emptyList(),
)

data class PrefixSuggestion(
    val provider: SearchProvider,
    val matchedAlias: String,
    val ghostSuffix: String,
    val score: Int,
)

data class EntityNavigationOption(
    val id: String,
    val label: String,
    val intent: Intent,
)

data class EntityRouteItem(
    val id: String,
    val label: String,
    val address: String,
    val sourceLabel: String,
    val navigationIntent: Intent,
    val mapIntent: Intent,
    val navigationOptions: List<EntityNavigationOption> = emptyList(),
    val score: Int = 0,
)

data class InternalCommandItem(
    val id: String,
    val title: String,
    val description: String,
    val keywords: List<String>,
    @DrawableRes val iconRes: Int,
    val closesSearchUi: Boolean = true,
    val onExecute: (Context) -> Unit,
    val score: Int = 0,
)

data class DiscoveredShortcut(
    val id: String,
    val packageName: String,
    val appName: String,
    val shortLabel: String,
    val longLabel: String,
    val intent: Intent?,
    val icon: ImageBitmap? = null,
    @DrawableRes val fallbackIconRes: Int,
    val fromShizuku: Boolean = false,
    val score: Int = 0,
)

data class AppFunctionItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val functionLabel: String,
    val icon: ImageBitmap?,
    val launchIntent: Intent?,
    val score: Int = 0,
)
