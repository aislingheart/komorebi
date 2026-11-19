package com.sameerasw.essentials.search

import com.sameerasw.essentials.ui.activities.inReachabilityOrder
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.SearchContextRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexAndContextRankerTest {

    private fun emptySnapshot(
        builtAtMs: Long = 1_000_000L,
        unsupported: Boolean = false,
        legacy: Boolean = false,
        contactsPerm: Boolean = true,
        filesPerm: Boolean = true,
    ): SearchIndex.Snapshot = SearchIndex.Snapshot(
        apps = emptyList(),
        contacts = emptyList(),
        files = FileSearchUtil.FileIndex(),
        settings = emptyList(),
        systemSettings = emptyList(),
        shortcuts = emptyList(),
        appFunctions = emptyList(),
        providers = emptyList(),
        includeUnsupportedFeatures = unsupported,
        includeLegacyFeatures = legacy,
        hadContactsPermission = contactsPerm,
        hadFilesPermission = filesPerm,
        builtAtMs = builtAtMs,
    )

    @Test
    fun snapshotIsFreshWithinTtlWhenFlagsAndPermissionsMatch() {
        val snap = emptySnapshot(builtAtMs = 1_000_000L)
        assertTrue(
            snap.isFresh(
                now = 1_000_000L + 4 * 60_000L,
                unsupported = false,
                legacy = false,
                contactsPerm = true,
                filesPerm = true,
            ),
        )
    }

    @Test
    fun snapshotExpiresAfterTtl() {
        val snap = emptySnapshot(builtAtMs = 1_000_000L)
        assertFalse(
            snap.isFresh(
                now = 1_000_000L + 5 * 60_000L + 1L,
                unsupported = false,
                legacy = false,
                contactsPerm = true,
                filesPerm = true,
            ),
        )
    }

    @Test
    fun snapshotInvalidatesWhenFeatureFlagsOrPermissionsChange() {
        val snap = emptySnapshot(
            unsupported = false,
            legacy = false,
            contactsPerm = false,
            filesPerm = false,
        )
        assertFalse(snap.isFresh(1_000_500L, unsupported = true, legacy = false, contactsPerm = false, filesPerm = false))
        assertFalse(snap.isFresh(1_000_500L, unsupported = false, legacy = true, contactsPerm = false, filesPerm = false))
        assertFalse(snap.isFresh(1_000_500L, unsupported = false, legacy = false, contactsPerm = true, filesPerm = false))
        assertFalse(snap.isFresh(1_000_500L, unsupported = false, legacy = false, contactsPerm = false, filesPerm = true))
    }

    @Test
    fun contextRankerAppliesBoostsOnlyWhenEnabledAndMatching() {
        val commSnapshot = SearchContextRanker.Snapshot(
            foregroundPackage = "com.whatsapp",
            communicationApp = true,
            mediaApp = false,
            navigationApp = false,
            rankingEnabled = true,
        )
        assertEquals(28, SearchContextRanker.boostForApp(commSnapshot, "com.whatsapp"))
        assertEquals(0, SearchContextRanker.boostForApp(commSnapshot, "com.android.settings"))
        assertEquals(22, SearchContextRanker.boostForContact(commSnapshot))
        assertEquals(18, SearchContextRanker.boostForShortcut(commSnapshot, "com.whatsapp"))
        assertEquals(0, SearchContextRanker.boostForShortcut(commSnapshot, "com.spotify.music"))
        assertEquals(0, SearchContextRanker.boostForFile(commSnapshot, isMedia = true))
        assertEquals(0, SearchContextRanker.boostForNavigation(commSnapshot))

        val mediaAndNavSnapshot = SearchContextRanker.Snapshot(
            foregroundPackage = "com.google.android.apps.maps",
            communicationApp = false,
            mediaApp = true,
            navigationApp = true,
            rankingEnabled = true,
        )
        assertEquals(16, SearchContextRanker.boostForFile(mediaAndNavSnapshot, isMedia = true))
        assertEquals(0, SearchContextRanker.boostForFile(mediaAndNavSnapshot, isMedia = false))
        assertEquals(18, SearchContextRanker.boostForNavigation(mediaAndNavSnapshot))

        val disabledSnapshot = mediaAndNavSnapshot.copy(rankingEnabled = false)
        assertEquals(0, SearchContextRanker.boostForApp(disabledSnapshot, "com.google.android.apps.maps"))
        assertEquals(0, SearchContextRanker.boostForContact(commSnapshot.copy(rankingEnabled = false)))
        assertEquals(0, SearchContextRanker.boostForFile(disabledSnapshot, isMedia = true))
        assertEquals(0, SearchContextRanker.boostForNavigation(disabledSnapshot))
        assertEquals(0, SearchContextRanker.boostForShortcut(disabledSnapshot, "com.google.android.apps.maps"))
    }

    @Test
    fun reachabilityOrderReversesItemsWhilePreservingTopHitIndex() {
        val ranked = listOf("top_hit", "second", "third")
        assertEquals(ranked, ranked.inReachabilityOrder(reachabilityLayout = false))
        val reachability = ranked.withIndex().toList().inReachabilityOrder(reachabilityLayout = true)
        assertEquals(listOf("third", "second", "top_hit"), reachability.map { it.value })
        // origIndex == 0 ends up at the bottom slot (closest to keyboard)
        assertEquals(0, reachability.last().index)
    }
}
