package com.sameerasw.essentials.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class LauncherCalculatorAndSnippetsTest {

    @Test
    fun arithmeticExpressionsEvaluateAccurately() {
        val pctAdd = LauncherCalculator.evaluate("200 + 15%")
        assertNotNull(pctAdd)
        assertEquals("230", pctAdd?.copyValue)
        assertEquals("Calculator", pctAdd?.category)

        val pctOf = LauncherCalculator.evaluate("15% of 240")
        assertNotNull(pctOf)
        assertEquals("36", pctOf?.copyValue)

        val sqrtRes = LauncherCalculator.evaluate("sqrt(144)")
        assertNotNull(sqrtRes)
        assertEquals("12", sqrtRes?.copyValue)

        val powRes = LauncherCalculator.evaluate("2^10")
        assertNotNull(powRes)
        assertEquals("1024", powRes?.copyValue)

        val parenRes = LauncherCalculator.evaluate("(12 + 8) * 3")
        assertNotNull(parenRes)
        assertEquals("60", parenRes?.copyValue)
    }

    @Test
    fun unitConversionsEvaluateAccurately() {
        val kmToMi = LauncherCalculator.evaluate("10 km to mi")
        assertNotNull(kmToMi)
        assertEquals("Unit Conversion", kmToMi?.category)
        val miVal = kmToMi!!.copyValue.toDouble()
        assertEquals(6.2137, miVal, 0.01)

        val fToC = LauncherCalculator.evaluate("100 f to c")
        assertNotNull(fToC)
        assertEquals(37.777, fToC!!.copyValue.toDouble(), 0.01)

        val cToF = LauncherCalculator.evaluate("0 c to f")
        assertNotNull(cToF)
        assertEquals(32.0, cToF!!.copyValue.toDouble(), 0.001)

        val gbToMb = LauncherCalculator.evaluate("2 gb to mb")
        assertNotNull(gbToMb)
        assertEquals("2048", gbToMb?.copyValue)
        assertTrue(gbToMb!!.formattedResult.contains("MB"))

        val mbToGb = LauncherCalculator.evaluate("512 mb to gb")
        assertNotNull(mbToGb)
        assertEquals("0.5", mbToGb?.copyValue)
    }

    @Test
    fun currencyFallbackEvaluatesWithoutContext() {
        val usdToEur = LauncherCalculator.evaluate("100 usd to eur", context = null)
        assertNotNull(usdToEur)
        assertEquals("Currency", usdToEur?.category)
        assertTrue(usdToEur!!.formattedResult.contains("EUR"))
        assertTrue(usdToEur.copyValue.toDouble() > 50.0)

        val dollarToJpy = LauncherCalculator.evaluate("\$50 to jpy", context = null)
        assertNotNull(dollarToJpy)
        assertEquals("Currency", dollarToJpy?.category)
        assertTrue(dollarToJpy!!.copyValue.toDouble() > 5000.0)
    }

    @Test
    fun programmerAndColorConversionsWork() {
        val hexToDec = LauncherCalculator.evaluate("0xff to dec")
        assertNotNull(hexToDec)
        assertEquals("255", hexToDec?.copyValue)
        assertEquals("Programmer", hexToDec?.category)

        val decToHex = LauncherCalculator.evaluate("255 to hex")
        assertNotNull(decToHex)
        assertEquals("0xFF", decToHex?.copyValue)

        val decToBin = LauncherCalculator.evaluate("42 to bin")
        assertNotNull(decToBin)
        assertEquals("0b101010", decToBin?.copyValue)

        val hexToRgb = LauncherCalculator.evaluate("#ff5500 to rgb")
        assertNotNull(hexToRgb)
        assertEquals("rgb(255, 85, 0)", hexToRgb?.copyValue)
        assertEquals("Color", hexToRgb?.category)
    }

    @Test
    fun dateAndUnixTimeQueriesWork() {
        val nextWeek = LauncherCalculator.evaluate("today + 7 days")
        assertNotNull(nextWeek)
        assertEquals("Date & Time", nextWeek?.category)
        val expected = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, nextWeek?.copyValue)
        assertTrue(nextWeek!!.subtitle.contains(expected))

        val unixNow = LauncherCalculator.evaluate("now unix")
        assertNotNull(unixNow)
        assertTrue(unixNow!!.copyValue.toLong() > 1_700_000_000L)
    }

    @Test
    fun nonMathQueriesReturnNull() {
        assertNull(LauncherCalculator.evaluate("wifi settings"))
        assertNull(LauncherCalculator.evaluate("komorebi"))
        assertNull(LauncherCalculator.evaluate("42"))
    }

    @Test
    fun snippetsExpandDynamicPlaceholders() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val expanded = SnippetsRepository.expandContent(
            content = "Date={date} Clip={clipboard} Id1={uuid} Id2={uuid}",
            clipboardText = "hello-world",
        )
        assertTrue(expanded.contains("Date=$today"))
        assertTrue(expanded.contains("Clip=hello-world"))
        val id1 = expanded.substringAfter("Id1=").substringBefore(" Id2=")
        val id2 = expanded.substringAfter("Id2=")
        assertNotEquals(id1, id2)
        // Verify valid UUID format
        UUID.fromString(id1)
        UUID.fromString(id2)
    }

    @Test
    fun quicklinksResolveEncodedQueryAndDefaultScheme() {
        val resolved = QuicklinksRepository.resolveUrl(
            template = "github.com/search?q={query}",
            argument = "jetpack compose",
        )
        assertEquals("https://github.com/search?q=jetpack+compose", resolved)
    }

    @Test
    fun emojiCatalogAndBuiltInToolsSearchMatchExpectedItems() {
        val fireMatches = EmojiCatalog.search(":fire:")
        assertTrue(fireMatches.any { it.emoji == "🔥" })

        val speedMatches = BuiltInLauncherTools.search("speedtest")
        assertTrue(speedMatches.any { it.id == "speed_test" && it.opensModal == LauncherToolModal.SPEED_TEST })
    }
}
