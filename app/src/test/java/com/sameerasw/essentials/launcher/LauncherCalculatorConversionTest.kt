package com.sameerasw.essentials.launcher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LauncherCalculatorConversionTest {

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

    @Test
    fun implicitImperialLengthConvertsToMetric() {
        val value = result("5ft")
        assertEquals("Unit Conversion", value.category)
        assertEquals(1.524, value.copyValue.toDouble(), 0.0001)
        assertTrue(value.formattedResult.endsWith(" m"))
    }

    @Test
    fun unitWordsAreDetected() {
        assertEquals(1.524, result("5 feet").copyValue.toDouble(), 0.0001)
        assertEquals(1.2427, result("2 kilometres").copyValue.toDouble(), 0.0001)
        assertEquals(11.0231, result("5 kilograms").copyValue.toDouble(), 0.0001)
    }

    @Test
    fun metricLengthConvertsToImperial() {
        val value = result("5m")
        assertEquals(16.4042, value.copyValue.toDouble(), 0.0001)
        assertTrue(value.formattedResult.endsWith(" ft"))
    }

    @Test
    fun currencySymbolCanBePrefixOrSuffix() {
        val prefix = result("£5")
        val suffix = result("5£")
        assertEquals("Currency", prefix.category)
        assertEquals(prefix.copyValue, suffix.copyValue)
        assertTrue(prefix.subtitle.contains("GBP"))
    }

    @Test
    fun currencyWordsAreDetected() {
        val value = result("5 euros")
        assertEquals("Currency", value.category)
        assertTrue(value.subtitle.contains("EUR"))
    }

    @Test
    fun currencyWordsAndExplicitTarget() {
        val value = result("5 dollars to euros")
        assertEquals("Currency", value.category)
        assertTrue(value.subtitle.contains("USD"))
        assertTrue(value.subtitle.contains("EUR"))
    }

    @Test
    fun poundsRemainMassUnlessCurrencyIsRequested() {
        val mass = result("5 pounds")
        assertEquals("Unit Conversion", mass.category)
        assertEquals(2.268, mass.copyValue.toDouble(), 0.001)
    }

    @Test
    fun temperatureConvertsBothWays() {
        assertEquals(37.7778, result("100f").copyValue.toDouble(), 0.0001)
        assertEquals(68.0, result("20c").copyValue.toDouble(), 0.0001)
    }

    @Test
    fun massConvertsBothWays() {
        assertEquals(11.0231, result("5kg").copyValue.toDouble(), 0.0001)
        assertEquals(2.268, result("5lb").copyValue.toDouble(), 0.001)
    }

    @Test
    fun volumeConvertsToMetric() {
        assertEquals(3.7854, result("1 gal").copyValue.toDouble(), 0.0001)
    }
}
