/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search
 * File: SearchTopTargetResolver.kt
 * Description: Single source of truth for resolving the ONE highlighted most-likely search result
 *              so UI highlight state and Enter/Search key execution stay 100% in sync.
 */

package com.sameerasw.essentials.search

enum class TopSearchTarget {
    NONE,
    LOCAL_DECISION_CONFIRMATION,
    SCOPED_PHOTOS_PERMISSION,
    SCOPED_PHOTO,
    SCOPED_APP_INTENT,
    CALCULATOR,
    INTERNAL_COMMAND,
    TOOL_ACTION,
    SNIPPET,
    QUICKLINK,
    ENTITY_ROUTE,
    APP,
    PREFIX_SCOPE,
    CONTACT,
    NOTIFICATION,
    MEDIA,
    FILE,
    SYSTEM_SETTING,
    ESSENTIAL_SETTING,
    SHORTCUT,
    APP_FUNCTION,
    MATCHING_PROVIDER,
    LOCAL_DECISION,
    WEB_SEARCH,
}

data class SearchTopTargetInput(
    val trimmedQuery: String,
    val activeScopeKind: SearchProviderKind?,
    val hasMediaPermission: Boolean,
    val hasPendingDecisionConfirmation: Boolean,
    val hasScopedPhotos: Boolean,
    val hasCalc: Boolean,
    val hasCommands: Boolean,
    val hasTools: Boolean,
    val hasSnippets: Boolean,
    val hasExactQuicklink: Boolean,
    val hasQuicklinks: Boolean,
    val hasRoutes: Boolean,
    val hasApps: Boolean,
    val hasPrefixSuggestion: Boolean,
    val hasExactPrefixSuggestion: Boolean,
    val hasContacts: Boolean,
    val hasNotifications: Boolean,
    val hasMedia: Boolean,
    val hasFiles: Boolean,
    val hasSystemSettings: Boolean,
    val hasEssentialSettings: Boolean,
    val hasShortcuts: Boolean,
    val hasAppFunctions: Boolean,
    val hasMatchingProviders: Boolean,
    val hasLocalDecisionMatches: Boolean,
    val isWebEnabled: Boolean,
)

object SearchTopTargetResolver {
    fun resolve(input: SearchTopTargetInput): TopSearchTarget {
        if (input.hasPendingDecisionConfirmation) {
            return TopSearchTarget.LOCAL_DECISION_CONFIRMATION
        }

        val scopeKind = input.activeScopeKind
        if (scopeKind != null) {
            return when (scopeKind) {
                SearchProviderKind.PHOTOS -> when {
                    !input.hasMediaPermission -> TopSearchTarget.SCOPED_PHOTOS_PERMISSION
                    input.hasScopedPhotos -> TopSearchTarget.SCOPED_PHOTO
                    else -> TopSearchTarget.NONE
                }
                SearchProviderKind.APP_INTENT -> TopSearchTarget.SCOPED_APP_INTENT
                SearchProviderKind.APPS -> if (input.hasApps) TopSearchTarget.APP else TopSearchTarget.NONE
                SearchProviderKind.CONTACTS -> if (input.hasContacts) TopSearchTarget.CONTACT else TopSearchTarget.NONE
                SearchProviderKind.FILES -> if (input.hasFiles) TopSearchTarget.FILE else TopSearchTarget.NONE
                SearchProviderKind.SETTINGS -> when {
                    input.hasCommands -> TopSearchTarget.INTERNAL_COMMAND
                    input.hasSystemSettings -> TopSearchTarget.SYSTEM_SETTING
                    input.hasEssentialSettings -> TopSearchTarget.ESSENTIAL_SETTING
                    else -> TopSearchTarget.NONE
                }
                SearchProviderKind.WEB -> {
                    if (input.trimmedQuery.isNotEmpty() && input.isWebEnabled) {
                        TopSearchTarget.WEB_SEARCH
                    } else {
                        TopSearchTarget.NONE
                    }
                }
                SearchProviderKind.EXTENSION -> TopSearchTarget.NONE
            }
        }

        if (input.trimmedQuery.isEmpty()) {
            return TopSearchTarget.NONE
        }

        return when {
            input.hasCalc -> TopSearchTarget.CALCULATOR
            input.hasCommands -> TopSearchTarget.INTERNAL_COMMAND
            input.hasTools -> TopSearchTarget.TOOL_ACTION
            input.hasSnippets -> TopSearchTarget.SNIPPET
            input.hasExactQuicklink -> TopSearchTarget.QUICKLINK
            input.hasRoutes -> TopSearchTarget.ENTITY_ROUTE
            input.hasApps -> TopSearchTarget.APP
            input.hasExactPrefixSuggestion -> TopSearchTarget.PREFIX_SCOPE
            input.hasQuicklinks -> TopSearchTarget.QUICKLINK
            input.hasContacts -> TopSearchTarget.CONTACT
            input.hasNotifications -> TopSearchTarget.NOTIFICATION
            input.hasMedia -> TopSearchTarget.MEDIA
            input.hasFiles -> TopSearchTarget.FILE
            input.hasSystemSettings -> TopSearchTarget.SYSTEM_SETTING
            input.hasEssentialSettings -> TopSearchTarget.ESSENTIAL_SETTING
            input.hasShortcuts -> TopSearchTarget.SHORTCUT
            input.hasAppFunctions -> TopSearchTarget.APP_FUNCTION
            input.hasPrefixSuggestion -> TopSearchTarget.PREFIX_SCOPE
            input.hasMatchingProviders -> TopSearchTarget.MATCHING_PROVIDER
            input.hasLocalDecisionMatches -> TopSearchTarget.LOCAL_DECISION
            input.isWebEnabled -> TopSearchTarget.WEB_SEARCH
            else -> TopSearchTarget.NONE
        }
    }
}
