package com.sameerasw.essentials

import com.sameerasw.essentials.assistant.LocalIntentDecisionEngine
import com.sameerasw.essentials.assistant.tools.ScreenTools
import com.sameerasw.essentials.data.local.KomorebiDatabase
import com.sameerasw.essentials.external.SettingsExternalHandler
import com.sameerasw.essentials.raycast.ExtensionCapability
import com.sameerasw.essentials.raycast.ExtensionPermissionManager
import com.sameerasw.essentials.raycast.RaycastStoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecurityAndAssistantRegressionTest {
    @Test
    fun settingsExternalHandlerBlocksSensitiveKeys() {
        val handler = SettingsExternalHandler()
        assertTrue(handler.isSensitiveKey("external_control_token"))
        assertTrue(handler.isSensitiveKey("byok_api_key"))
        assertTrue(handler.isSensitiveKey("oauth_refresh_secret"))
        assertTrue(handler.isSensitiveKey("github_pat_value"))
        assertTrue(handler.isSensitiveKey("user_password"))
        assertFalse(handler.isSensitiveKey("pixel_search_engine"))
        assertFalse(handler.isSensitiveKey("pitch_black_theme"))
    }

    @Test
    fun raycastStoreClientRejectsZipSlipAndSanitizesFolderNames() {
        val tempDir = Files.createTempDirectory("raycast-zipslip-test").toFile()
        try {
            assertNull(RaycastStoreClient.safeChildFile(tempDir, "../escape.txt"))
            assertNull(RaycastStoreClient.safeChildFile(tempDir, "subdir/../../escape.txt"))
            assertNull(RaycastStoreClient.safeChildFile(tempDir, ""))
            val safe = RaycastStoreClient.safeChildFile(tempDir, "nested/index.js")
            assertNotNull(safe)
            assertTrue(safe!!.canonicalPath.startsWith(tempDir.canonicalPath + File.separator))

            assertFalse(RaycastStoreClient.sanitizeExtensionFolder("../../evil").contains(".."))
            assertFalse(RaycastStoreClient.sanitizeExtensionFolder("a/b\\c").contains("/"))
            assertFalse(RaycastStoreClient.sanitizeExtensionFolder("a/b\\c").contains("\\"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun localDecisionEngineMatchesNaturalLanguageToToolsAndExtractsSlots() {
        val torch = LocalIntentDecisionEngine.decide("turn on my torch light")
        assertTrue("Expected flashlight match", torch.isNotEmpty())
        assertEquals("flashlight", torch.first().tool.id)
        assertEquals("on", torch.first().extractedArgs["state"])

        val brightness = LocalIntentDecisionEngine.decide("set screen brightness to 35%")
        assertTrue("Expected brightness match", brightness.isNotEmpty())
        assertEquals("brightness", brightness.first().tool.id)
        assertEquals("set", brightness.first().extractedArgs["action"])
        assertEquals("35", brightness.first().extractedArgs["value"])

        val timer = LocalIntentDecisionEngine.decide("start a countdown timer for 10 minutes")
        assertTrue("Expected timer match", timer.isNotEmpty())
        assertEquals("timer", timer.first().tool.id)
        assertEquals("10 minutes", timer.first().extractedArgs["duration"])

        val alarm = LocalIntentDecisionEngine.decide("wake me up at 7:30 am")
        assertTrue("Expected alarm match", alarm.isNotEmpty())
        assertEquals("alarm", alarm.first().tool.id)
        assertEquals("7:30 am", alarm.first().extractedArgs["time"])
    }

    @Test
    fun localDecisionEngineHandlesTyposConfirmationAndFeedbackBoost() {
        val typoMatch = LocalIntentDecisionEngine.decide("flshlight on")
        assertTrue("Expected typo-tolerant flashlight match", typoMatch.isNotEmpty())
        assertEquals("flashlight", typoMatch.first().tool.id)

        val lock = LocalIntentDecisionEngine.decide("lock my phone screen now")
        assertTrue("Expected lock_screen match", lock.isNotEmpty())
        assertEquals("lock_screen", lock.first().tool.id)
        assertTrue("lock_screen must require explicit user confirmation", lock.first().requiresConfirmation)

        val boosted = LocalIntentDecisionEngine.decide(
            query = "check battery specs",
            feedbackWeights = mapOf("battery->device_info" to 0.35f),
        )
        assertTrue(boosted.isNotEmpty())
        assertEquals("device_info", boosted.first().tool.id)
    }

    @Test
    fun localScreenSummarizerExtractsKeyHighlightsWithoutLlm() {
        val screenDump = """
            Back
            OK
            Your flight BA249 to Tokyo Haneda departs from Terminal 5 at 14:35 today.
            Gate information will be announced 45 minutes before boarding starts.
            Please keep your boarding pass and passport ready at security checkpoint B.
            Menu
        """.trimIndent()

        val highlights = ScreenTools.extractHighlights(screenDump, maxLines = 2)
        assertEquals(2, highlights.size)
        assertTrue(highlights.any { it.contains("BA249") || it.contains("boarding") })
    }

    @Test
    fun ftsPrefixQuerySanitizesSpecialCharacters() {
        assertEquals("hello* world*", KomorebiDatabase.toFtsPrefixQuery("hello, world!"))
        assertEquals("", KomorebiDatabase.toFtsPrefixQuery("   ---   "))
        assertEquals("api* key*", KomorebiDatabase.toFtsPrefixQuery("API:key"))
    }

    @Test
    fun extensionPermissionManagerDefaultsCliBridgeToDenied() {
        assertFalse(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.CLI_BRIDGE))
        assertTrue(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.NETWORK))
        assertTrue(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.CLIPBOARD))
        assertTrue(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.FILES))

        val overrides = mapOf(
            ExtensionCapability.CLI_BRIDGE to true,
            ExtensionCapability.NETWORK to false,
        )
        assertTrue(ExtensionPermissionManager.evaluatePolicy(overrides, ExtensionCapability.CLI_BRIDGE))
        assertFalse(ExtensionPermissionManager.evaluatePolicy(overrides, ExtensionCapability.NETWORK))
    }
}
