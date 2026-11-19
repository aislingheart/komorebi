package com.sameerasw.essentials

import com.sameerasw.essentials.assistant.AiMessage
import com.sameerasw.essentials.assistant.AssistantAiSettings
import com.sameerasw.essentials.assistant.ByokAiProvider
import com.sameerasw.essentials.external.SettingsExternalHandler
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
    fun byokParseSchemaAlwaysReturnsValidObjectSchema() {
        val empty = ByokAiProvider.parseSchema("{}").asJsonObject
        assertEquals("object", empty.get("type").asString)
        assertTrue(empty.get("properties").isJsonObject)

        val invalid = ByokAiProvider.parseSchema("not-json").asJsonObject
        assertEquals("object", invalid.get("type").asString)
        assertTrue(invalid.get("properties").isJsonObject)

        val valid = ByokAiProvider.parseSchema(
            """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
        ).asJsonObject
        assertEquals("object", valid.get("type").asString)
        assertNotNull(valid.getAsJsonObject("properties").get("query"))
    }

    @Test
    fun byokNormalizeAlternatingTurnsMergesConsecutiveAndDropsBlanks() {
        val turns = ByokAiProvider.normalizeAlternatingTurns(
            messages = listOf(
                AiMessage("system", "ignored system"),
                AiMessage("user", "hello"),
                AiMessage("user", "world"),
                AiMessage("assistant", "   "),
                AiMessage("assistant", "reply 1"),
                AiMessage("assistant", "reply 2"),
                AiMessage("user", "follow-up"),
            ),
            assistantRole = "model",
            userRole = "user",
        )
        assertEquals(3, turns.size)
        assertEquals("user" to "hello\n\nworld", turns[0])
        assertEquals("model" to "reply 1\n\nreply 2", turns[1])
        assertEquals("user" to "follow-up", turns[2])
    }

    @Test
    fun isLocalEndpointRecognizesLoopbackAndPrivateLanHosts() {
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://localhost:11434/v1"))
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://127.0.0.1:8080/v1"))
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://192.168.1.42:11434/v1"))
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://10.0.0.5:8000/v1"))
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://172.16.4.10:11434/v1"))
        assertTrue(AssistantAiSettings.isLocalEndpoint("http://ollama.local:11434/v1"))
        assertFalse(AssistantAiSettings.isLocalEndpoint("https://api.openai.com/v1"))
        assertFalse(AssistantAiSettings.isLocalEndpoint("https://api.anthropic.com/v1"))
        assertFalse(AssistantAiSettings.isLocalEndpoint("https://172.32.0.1/v1"))
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
}
