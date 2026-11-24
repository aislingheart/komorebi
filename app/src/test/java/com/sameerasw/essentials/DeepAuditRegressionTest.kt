package com.sameerasw.essentials

import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import com.sameerasw.essentials.launcher.LauncherCalculator
import com.sameerasw.essentials.launcher.QuicklinksRepository
import com.sameerasw.essentials.raycast.ExtensionCapability
import com.sameerasw.essentials.raycast.ExtensionPermissionManager
import com.sameerasw.essentials.utils.AutoUpdateManagerHelper
import com.sameerasw.essentials.utils.RootUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepAuditRegressionTest {
    @Test
    fun extensionPermissionManagerDeniesHealthAndScreenContextByDefault() {
        assertFalse(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.HEALTH_DATA))
        assertFalse(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.SCREEN_CONTEXT))
        assertFalse(ExtensionPermissionManager.evaluatePolicy(emptyMap(), ExtensionCapability.CLI_BRIDGE))

        val allowed = mapOf(
            ExtensionCapability.HEALTH_DATA to true,
            ExtensionCapability.SCREEN_CONTEXT to true,
        )
        assertTrue(ExtensionPermissionManager.evaluatePolicy(allowed, ExtensionCapability.HEALTH_DATA))
        assertTrue(ExtensionPermissionManager.evaluatePolicy(allowed, ExtensionCapability.SCREEN_CONTEXT))
    }

    @Test
    fun autoUpdateManagerValidatesTrustedGithubHttpsUrlsOnly() {
        assertTrue(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://github.com/aislingheart/komorebi/releases/download/v0.12.5/app-release.apk",
            ),
        )
        assertTrue(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://release-assets.githubusercontent.com/github-production-release-asset/123/app-release.apk",
            ),
        )
        assertTrue(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://objects.githubusercontent.com/some/path/app-release.apk",
            ),
        )
        assertFalse(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "http://github.com/aislingheart/komorebi/releases/download/v0.12.5/app-release.apk",
            ),
        )
        assertFalse(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://evil.example.com/github.com/app-release.apk",
            ),
        )
        assertFalse(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://github.com@evil.example.com/app-release.apk",
            ),
        )
        assertFalse(
            AutoUpdateManagerHelper.isAllowedUpdateUrl(
                "https://github.com:8443/aislingheart/komorebi/releases/download/v0.12.5/app-release.apk",
            ),
        )
        assertFalse(AutoUpdateManagerHelper.isAllowedUpdateUrl("file:///sdcard/app.apk"))
    }

    @Test
    fun rootUtilsShellQuoteSafelyEscapesArguments() {
        assertEquals("'hello'", RootUtils.shellQuote("hello"))
        assertEquals("'a b; rm -rf /'", RootUtils.shellQuote("a b; rm -rf /"))
        assertEquals("'it'\\''s safe'", RootUtils.shellQuote("it's safe"))
    }

    @Test
    fun destructiveAssistantToolsRequireConfirmationAndMediaPlayExcludesPause() {
        val lock = AssistantToolRegistry.find("lock_screen")
        val freeze = AssistantToolRegistry.find("freeze_app")
        val deleteEvent = AssistantToolRegistry.find("delete_event")
        val replyLast = AssistantToolRegistry.find("reply_last")
        val mediaPlay = AssistantToolRegistry.find("media_play")

        assertNotNull(lock)
        assertNotNull(freeze)
        assertNotNull(deleteEvent)
        assertNotNull(replyLast)
        assertNotNull(mediaPlay)

        assertTrue("lock_screen must require confirmation", lock!!.requiresConfirmation)
        assertTrue("freeze_app must require confirmation", freeze!!.requiresConfirmation)
        assertTrue("delete_event must require confirmation", deleteEvent!!.requiresConfirmation)
        assertTrue("reply_last must require confirmation", replyLast!!.requiresConfirmation)
        assertFalse("media_play keywords must not contain pause", mediaPlay!!.keywords.contains("pause"))
    }

    @Test
    fun calculatorSupportsNegativeTempsImplicitMulPercentagesAndStandaloneColors() {
        val negForty = LauncherCalculator.evaluate("-40c to f")
        assertNotNull(negForty)
        assertEquals("-40", negForty!!.copyValue)

        val negTen = LauncherCalculator.evaluate("-10°C to F")
        assertNotNull(negTen)
        assertEquals("14", negTen!!.copyValue)

        val implicit1 = LauncherCalculator.evaluate("2(3+4)")
        assertNotNull(implicit1)
        assertEquals("14", implicit1!!.copyValue)

        val implicit2 = LauncherCalculator.evaluate("(2)(3)")
        assertNotNull(implicit2)
        assertEquals("6", implicit2!!.copyValue)

        val pctOf = LauncherCalculator.evaluate("15% of 240")
        assertNotNull(pctOf)
        assertEquals("36", pctOf!!.copyValue)

        val pctAdd = LauncherCalculator.evaluate("200 + 10%")
        assertNotNull(pctAdd)
        assertEquals("220", pctAdd!!.copyValue)

        val pctSub = LauncherCalculator.evaluate("200 - 15%")
        assertNotNull(pctSub)
        assertEquals("170", pctSub!!.copyValue)

        val hexToDec = LauncherCalculator.evaluate("0xff to dec")
        assertNotNull(hexToDec)
        assertEquals("255", hexToDec!!.copyValue)

        val hexColor = LauncherCalculator.evaluate("#ff6600")
        assertNotNull(hexColor)
        assertEquals("rgb(255, 102, 0)", hexColor!!.copyValue)
        assertTrue(hexColor.formattedResult.contains("hsl("))

        val rgbColor = LauncherCalculator.evaluate("rgb(255, 102, 0)")
        assertNotNull(rgbColor)
        assertEquals("#FF6600", rgbColor!!.copyValue)

        assertNull(LauncherCalculator.evaluate("rgb(300, 0, 0)"))
    }

    @Test
    fun quicklinksResolveUrlSubstitutesQueryAndHandlesClipboardGracefullyWithoutContext() {
        assertEquals(
            "https://example.com/search?q=hello+world",
            QuicklinksRepository.resolveUrl("https://example.com/search?q={query}", "hello world"),
        )
        assertEquals(
            "https://example.com/clip?v=",
            QuicklinksRepository.resolveUrl("https://example.com/clip?v={clipboard}", "", null),
        )
    }
}
