/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 */

package com.sameerasw.essentials.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTopTargetResolverTest {

    private fun baseInput(
        trimmedQuery: String = "test",
        activeScopeKind: SearchProviderKind? = null,
        hasMediaPermission: Boolean = true,
        hasPendingDecisionConfirmation: Boolean = false,
        hasScopedPhotos: Boolean = false,
        hasCalc: Boolean = false,
        hasCommands: Boolean = false,
        hasTools: Boolean = false,
        hasSnippets: Boolean = false,
        hasExactQuicklink: Boolean = false,
        hasQuicklinks: Boolean = false,
        hasRoutes: Boolean = false,
        hasApps: Boolean = false,
        hasPrefixSuggestion: Boolean = false,
        hasExactPrefixSuggestion: Boolean = false,
        hasContacts: Boolean = false,
        hasNotifications: Boolean = false,
        hasMedia: Boolean = false,
        hasFiles: Boolean = false,
        hasSystemSettings: Boolean = false,
        hasEssentialSettings: Boolean = false,
        hasShortcuts: Boolean = false,
        hasAppFunctions: Boolean = false,
        hasMatchingProviders: Boolean = false,
        hasLocalDecisionMatches: Boolean = false,
        isWebEnabled: Boolean = true,
    ) = SearchTopTargetInput(
        trimmedQuery = trimmedQuery,
        activeScopeKind = activeScopeKind,
        hasMediaPermission = hasMediaPermission,
        hasPendingDecisionConfirmation = hasPendingDecisionConfirmation,
        hasScopedPhotos = hasScopedPhotos,
        hasCalc = hasCalc,
        hasCommands = hasCommands,
        hasTools = hasTools,
        hasSnippets = hasSnippets,
        hasExactQuicklink = hasExactQuicklink,
        hasQuicklinks = hasQuicklinks,
        hasRoutes = hasRoutes,
        hasApps = hasApps,
        hasPrefixSuggestion = hasPrefixSuggestion,
        hasExactPrefixSuggestion = hasExactPrefixSuggestion,
        hasContacts = hasContacts,
        hasNotifications = hasNotifications,
        hasMedia = hasMedia,
        hasFiles = hasFiles,
        hasSystemSettings = hasSystemSettings,
        hasEssentialSettings = hasEssentialSettings,
        hasShortcuts = hasShortcuts,
        hasAppFunctions = hasAppFunctions,
        hasMatchingProviders = hasMatchingProviders,
        hasLocalDecisionMatches = hasLocalDecisionMatches,
        isWebEnabled = isWebEnabled,
    )

    @Test
    fun `blank query without active scope highlights nothing`() {
        val result = SearchTopTargetResolver.resolve(
            baseInput(
                trimmedQuery = "",
                hasApps = true,
                hasLocalDecisionMatches = true,
                isWebEnabled = true,
            ),
        )
        assertEquals(TopSearchTarget.NONE, result)
    }

    @Test
    fun `pending decision confirmation always takes top priority`() {
        val globalResult = SearchTopTargetResolver.resolve(
            baseInput(
                hasPendingDecisionConfirmation = true,
                hasCalc = true,
                hasApps = true,
                hasLocalDecisionMatches = true,
            ),
        )
        assertEquals(TopSearchTarget.LOCAL_DECISION_CONFIRMATION, globalResult)

        val scopedResult = SearchTopTargetResolver.resolve(
            baseInput(
                activeScopeKind = SearchProviderKind.APP_INTENT,
                hasPendingDecisionConfirmation = true,
            ),
        )
        assertEquals(TopSearchTarget.LOCAL_DECISION_CONFIRMATION, scopedResult)
    }

    @Test
    fun `partial prefix suggestion does not hijack matched apps`() {
        // e.g. typing "ca" matches Camera app AND partial prefix "call" (Contacts scope)
        val result = SearchTopTargetResolver.resolve(
            baseInput(
                trimmedQuery = "ca",
                hasApps = true,
                hasPrefixSuggestion = true,
                hasExactPrefixSuggestion = false,
            ),
        )
        assertEquals(TopSearchTarget.APP, result)
    }

    @Test
    fun `exact prefix suggestion beats lower fallbacks when no app matches`() {
        val result = SearchTopTargetResolver.resolve(
            baseInput(
                trimmedQuery = "photos",
                hasApps = false,
                hasPrefixSuggestion = true,
                hasExactPrefixSuggestion = true,
                hasContacts = true,
            ),
        )
        assertEquals(TopSearchTarget.PREFIX_SCOPE, result)
    }

    @Test
    fun `partial prefix suggestion wins when no direct item matches`() {
        val result = SearchTopTargetResolver.resolve(
            baseInput(
                trimmedQuery = "spot",
                hasPrefixSuggestion = true,
                hasExactPrefixSuggestion = false,
                hasMatchingProviders = true,
            ),
        )
        assertEquals(TopSearchTarget.PREFIX_SCOPE, result)
    }

    @Test
    fun `local decision on search miss displaces web search`() {
        val result = SearchTopTargetResolver.resolve(
            baseInput(
                trimmedQuery = "turn on flashlight please",
                hasLocalDecisionMatches = true,
                isWebEnabled = true,
            ),
        )
        assertEquals(TopSearchTarget.LOCAL_DECISION, result)
    }

    @Test
    fun `web search is highlighted only when nothing else matches`() {
        assertEquals(
            TopSearchTarget.WEB_SEARCH,
            SearchTopTargetResolver.resolve(baseInput(trimmedQuery = "weather tomorrow", isWebEnabled = true)),
        )
        assertEquals(
            TopSearchTarget.NONE,
            SearchTopTargetResolver.resolve(baseInput(trimmedQuery = "weather tomorrow", isWebEnabled = false)),
        )
    }

    @Test
    fun `global priority order resolves deterministically`() {
        assertEquals(
            TopSearchTarget.CALCULATOR,
            SearchTopTargetResolver.resolve(baseInput(hasCalc = true, hasCommands = true, hasTools = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.INTERNAL_COMMAND,
            SearchTopTargetResolver.resolve(baseInput(hasCommands = true, hasTools = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.TOOL_ACTION,
            SearchTopTargetResolver.resolve(baseInput(hasTools = true, hasSnippets = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.SNIPPET,
            SearchTopTargetResolver.resolve(baseInput(hasSnippets = true, hasExactQuicklink = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.QUICKLINK,
            SearchTopTargetResolver.resolve(baseInput(hasExactQuicklink = true, hasQuicklinks = true, hasRoutes = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.ENTITY_ROUTE,
            SearchTopTargetResolver.resolve(baseInput(hasRoutes = true, hasApps = true)),
        )
        assertEquals(
            TopSearchTarget.APP,
            SearchTopTargetResolver.resolve(baseInput(hasApps = true, hasQuicklinks = true, hasContacts = true)),
        )
        assertEquals(
            TopSearchTarget.QUICKLINK,
            SearchTopTargetResolver.resolve(baseInput(hasQuicklinks = true, hasContacts = true)),
        )
        assertEquals(
            TopSearchTarget.CONTACT,
            SearchTopTargetResolver.resolve(baseInput(hasContacts = true, hasNotifications = true)),
        )
        assertEquals(
            TopSearchTarget.NOTIFICATION,
            SearchTopTargetResolver.resolve(baseInput(hasNotifications = true, hasMedia = true)),
        )
        assertEquals(
            TopSearchTarget.MEDIA,
            SearchTopTargetResolver.resolve(baseInput(hasMedia = true, hasFiles = true)),
        )
        assertEquals(
            TopSearchTarget.FILE,
            SearchTopTargetResolver.resolve(baseInput(hasFiles = true, hasSystemSettings = true)),
        )
        assertEquals(
            TopSearchTarget.SYSTEM_SETTING,
            SearchTopTargetResolver.resolve(baseInput(hasSystemSettings = true, hasEssentialSettings = true)),
        )
        assertEquals(
            TopSearchTarget.ESSENTIAL_SETTING,
            SearchTopTargetResolver.resolve(baseInput(hasEssentialSettings = true, hasShortcuts = true)),
        )
        assertEquals(
            TopSearchTarget.SHORTCUT,
            SearchTopTargetResolver.resolve(baseInput(hasShortcuts = true, hasAppFunctions = true)),
        )
        assertEquals(
            TopSearchTarget.APP_FUNCTION,
            SearchTopTargetResolver.resolve(baseInput(hasAppFunctions = true, hasMatchingProviders = true)),
        )
        assertEquals(
            TopSearchTarget.MATCHING_PROVIDER,
            SearchTopTargetResolver.resolve(baseInput(hasMatchingProviders = true, hasLocalDecisionMatches = true)),
        )
    }

    @Test
    fun `scoped modes highlight only the active scope top item`() {
        assertEquals(
            TopSearchTarget.SCOPED_PHOTOS_PERMISSION,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "",
                    activeScopeKind = SearchProviderKind.PHOTOS,
                    hasMediaPermission = false,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.SCOPED_PHOTO,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "",
                    activeScopeKind = SearchProviderKind.PHOTOS,
                    hasMediaPermission = true,
                    hasScopedPhotos = true,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.SCOPED_APP_INTENT,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "daft punk",
                    activeScopeKind = SearchProviderKind.APP_INTENT,
                    hasShortcuts = true,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.INTERNAL_COMMAND,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "bluetooth",
                    activeScopeKind = SearchProviderKind.SETTINGS,
                    hasCommands = true,
                    hasSystemSettings = true,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.SYSTEM_SETTING,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "bluetooth",
                    activeScopeKind = SearchProviderKind.SETTINGS,
                    hasSystemSettings = true,
                    hasEssentialSettings = true,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.ESSENTIAL_SETTING,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "searchbar",
                    activeScopeKind = SearchProviderKind.SETTINGS,
                    hasEssentialSettings = true,
                ),
            ),
        )
        assertEquals(
            TopSearchTarget.WEB_SEARCH,
            SearchTopTargetResolver.resolve(
                baseInput(
                    trimmedQuery = "kotlin",
                    activeScopeKind = SearchProviderKind.WEB,
                    isWebEnabled = true,
                ),
            ),
        )
    }
}
