package com.sameerasw.essentials.launcher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class LauncherCalculatorProductivityTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun result(query: String): CalcResult {
        val value = LauncherCalculator.evaluate(query, null)
        assertNotNull("Expected a result for \"$query\"", value)
        return value!!
    }

    private fun countOccurrences(text: String, token: String): Int = text.split(token).size - 1

    // --- Tip ---

    @Test
    fun tipWithoutSplit() {
        val value = result("tip 100 15%")
        assertEquals("Tip", value.category)
        assertTrue(value.formattedResult.contains("$15.00 tip on $100.00"))
        assertTrue(value.subtitle.contains("Total $115.00"))
        assertEquals("115", value.copyValue)
    }

    @Test
    fun tipWithSplit() {
        val value = result("tip 45.50 18% split 4")
        assertEquals("Tip", value.category)
        assertTrue(value.formattedResult.contains("$8.19 tip on $45.50"))
        assertEquals("53.69", value.copyValue)
        assertTrue(value.subtitle.contains("each (4 people)"))
    }

    @Test
    fun tipPhraseVariantsAllParse() {
        assertNotNull(LauncherCalculator.evaluate("tip 45.50 18", null))
        assertNotNull(LauncherCalculator.evaluate("tip 45.50 18 split between 4", null))
        val value = result("tip 45.50 at 18% for 4")
        assertEquals("Tip", value.category)
        assertEquals("53.69", value.copyValue)
    }

    // --- Age ---

    @Test
    fun flexibleDateParsing() {
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("1994-06-15"))
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("15/06/1994"))
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("06/15/1994"))
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("june 15 1994"))
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("June 15, 1994"))
        assertEquals(LocalDate.of(1994, 6, 15), LauncherCalculator.parseFlexibleDate("15 June 1994"))
        assertNull(LauncherCalculator.parseFlexibleDate("not a date"))
        assertNull(LauncherCalculator.parseFlexibleDate("1994-13-45"))
    }

    @Test
    fun ageResultIsPresentForIsoBirthDate() {
        val value = result("age 1994-06-15")
        assertEquals("Age", value.category)
        assertTrue(value.formattedResult.contains("years"))
        assertTrue(value.formattedResult.contains("months"))
        assertTrue(value.subtitle.contains("next birthday"))
        assertTrue(value.subtitle.contains("Jun 15, 1994"))
    }

    @Test
    fun ageResultIsPresentForMonthNameBirthDate() {
        val value = result("age june 15 1994")
        assertEquals("Age", value.category)
        assertTrue(value.formattedResult.contains("years"))
    }

    // --- Days between / days until ---

    @Test
    fun daysFromToIsExact() {
        val value = result("days from 2026-01-01 to 2026-03-01")
        assertEquals("Date Math", value.category)
        assertEquals("59 days", value.formattedResult)
        assertEquals("59", value.copyValue)
        assertTrue(value.subtitle.contains("8 weeks, 3 days"))
    }

    @Test
    fun existingDaysBetweenStillHandledByOriginalEvaluator() {
        val value = result("days between 2026-01-01 and 2026-03-01")
        assertEquals("Date & Time", value.category)
        assertEquals("59 days", value.formattedResult)
    }

    @Test
    fun daysUntilChristmas() {
        val value = result("days until christmas")
        assertEquals("Date Math", value.category)
        assertTrue(value.formattedResult.contains("days"))
        assertTrue(value.subtitle.contains("Dec 25"))
    }

    @Test
    fun daysTillHalloween() {
        val value = result("days till halloween")
        assertEquals("Date Math", value.category)
        assertTrue(value.subtitle.contains("Oct 31"))
    }

    @Test
    fun daysUntilNewYear() {
        val value = result("days until new year")
        assertEquals("Date Math", value.category)
        assertTrue(value.subtitle.contains("Jan 1"))
    }

    // --- Epoch ---

    @Test
    fun bareEpochSeconds() {
        val value = result("1731129600")
        assertEquals("Epoch", value.category)
        assertEquals("1731129600", value.copyValue)
        assertTrue(value.subtitle.contains("UTC"))
    }

    @Test
    fun bareEpochMilliseconds() {
        val value = result("1731129600000")
        assertEquals("Epoch", value.category)
        assertEquals("1731129600000", value.copyValue)
    }

    @Test
    fun currentEpochVariants() {
        for (query in listOf("epoch", "epoch now", "now to epoch", "current epoch")) {
            val value = result(query)
            assertEquals("Epoch", value.category)
            assertTrue(value.copyValue.toLong() > 1_700_000_000L)
        }
    }

    // --- Sleep cycles ---

    @Test
    fun wakeAtSevenAmSuggestsFourBedtimes() {
        val value = result("wake at 7")
        assertEquals("Sleep", value.category)
        assertTrue(value.formattedResult.contains("7:00 AM"))
        assertEquals("9:46 PM · 11:16 PM · 12:46 AM · 2:16 AM", value.subtitle)
        assertEquals("9:46 PM", value.copyValue)
    }

    @Test
    fun wakeAtHalfPastSevenCrossesMidnightCorrectly() {
        val value = result("wake up at 7:30am")
        assertEquals("Sleep", value.category)
        assertTrue(value.formattedResult.contains("7:30 AM"))
        assertEquals(3, countOccurrences(value.subtitle, "·"))
    }

    @Test
    fun sleepNowGivesWakeTimes() {
        val value = result("sleep now")
        assertEquals("Sleep", value.category)
        assertTrue(value.formattedResult.contains("wake"))
        assertEquals(2, countOccurrences(value.subtitle, "·"))
    }

    // --- Random suite ---

    @Test
    fun coinFlip() {
        repeat(20) {
            val value = result("flip a coin")
            assertEquals("Random", value.category)
            assertTrue(value.formattedResult == "Heads" || value.formattedResult == "Tails")
            assertTrue(value.formattedResult == value.copyValue)
        }
        assertTrue(result("coin flip").category == "Random")
    }

    @Test
    fun rollDice() {
        repeat(20) {
            val value = result("roll 2d6")
            assertEquals("Random", value.category)
            val total = value.copyValue.toIntOrNull()
            assertNotNull(total)
            assertTrue(total!! in 2..12)
            assertTrue(value.subtitle.contains("2d6:"))
        }
        repeat(20) {
            assertTrue(result("roll d20").copyValue.toInt() in 1..20)
        }
        repeat(20) {
            assertTrue(result("roll dice").copyValue.toInt() in 1..6)
        }
    }

    @Test
    fun randomNumberInRange() {
        repeat(20) {
            val value = result("random 1-100")
            assertEquals("Random", value.category)
            val n = value.copyValue.toIntOrNull()
            assertNotNull(n)
            assertTrue(n!! in 1..100)
        }
        assertTrue(result("random number 1 to 100").copyValue.toInt() in 1..100)
        assertTrue(result("random 1 and 100").copyValue.toInt() in 1..100)
    }

    // --- Defensive behavior ---

    @Test
    fun malformedQueriesReturnNull() {
        assertNull(LauncherCalculator.evaluate("tip", null))
        assertNull(LauncherCalculator.evaluate("tip 100", null))
        assertNull(LauncherCalculator.evaluate("tip 100 15% split 0", null))
        assertNull(LauncherCalculator.evaluate("age", null))
        assertNull(LauncherCalculator.evaluate("age not a date", null))
        assertNull(LauncherCalculator.evaluate("wake at 25", null))
        assertNull(LauncherCalculator.evaluate("roll 0d6", null))
        assertNull(LauncherCalculator.evaluate("random", null))
        assertNull(LauncherCalculator.evaluate("123456789", null))
    }

    // --- Existing evaluators remain intact ---

    @Test
    fun existingEvaluatorBehaviorUnchanged() {
        assertEquals("Unit Conversion", result("5ft").category)
        assertEquals("Currency", result("5 dollars to euros").category)
        assertEquals("Calculator", result("2 + 2").category)
        assertEquals("Color", result("#ff0000 to rgb").category)
    }
}
