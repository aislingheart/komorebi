/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Domain Layer Registries
 * File: PermissionRegistry.kt
 * Description: Registry containing system permission declarations, intent actions, and guidance strings.
 */

package com.sameerasw.essentials.domain.registry

import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.AppPermission

object PermissionRegistry {
    private val registry = mutableMapOf<String, MutableList<Int>>()

    fun register(
        permission: AppPermission,
        featureTitleRes: Int,
    ) {
        register(permission.name, featureTitleRes)
    }

    fun register(
        permissionKey: String,
        featureTitleRes: Int,
    ) {
        val list = registry.getOrPut(permissionKey) { mutableListOf() }
        if (!list.contains(featureTitleRes)) list.add(featureTitleRes)
    }

    fun getFeatures(permission: AppPermission): List<Int> {
        val direct = registry[permission.name] ?: emptyList()
        val aliasMatches = permission.aliases.flatMap { registry[it] ?: emptyList() }
        return (direct + aliasMatches).distinct()
    }

    fun getFeatures(permissionKey: String): List<Int> {
        val perm = AppPermission.fromKey(permissionKey)
        return if (perm != null) {
            getFeatures(perm)
        } else {
            registry[permissionKey]?.toList() ?: emptyList()
        }
    }
}

// Register active Komorebi feature permission dependencies
fun initPermissionRegistry() {
    PermissionRegistry.register("ACCESSIBILITY", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("DRAW_OVER_OTHER_APPS", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("USAGE_STATS", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("NOTIFICATION_LISTENER", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("POST_NOTIFICATIONS", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("READ_CALENDAR", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("READ_PHONE_STATE", R.string.search_smart_data_title)
    PermissionRegistry.register("SHIZUKU", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("ROOT", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("WRITE_SECURE_SETTINGS", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("WRITE_SETTINGS", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("NOTIFICATION_POLICY", R.string.pixel_searchbar_settings_title)
    PermissionRegistry.register("STORAGE", R.string.pixel_search_results_files_title)
    PermissionRegistry.register("READ_CONTACTS", R.string.pixel_search_results_contacts_title)
    PermissionRegistry.register("NOTIFICATION_BUBBLES", R.string.preview_web_title)
}
