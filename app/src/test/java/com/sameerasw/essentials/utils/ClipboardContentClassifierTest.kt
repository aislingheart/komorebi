/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Unit Tests - Utilities
 * File: ClipboardContentClassifierTest.kt
 * Description: Unit tests for ClipboardContentClassifier clipboard text classification.
 */

package com.sameerasw.essentials.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardContentClassifierTest {

    @Test
    fun classify_httpsUrl_returnsUrlWithHost() {
        val result = ClipboardContentClassifier.classify("https://example.com/some/page?x=1")

        assertTrue(result is ClipboardContent.Url)
        result as ClipboardContent.Url
        assertEquals("https://example.com/some/page?x=1", result.url)
        assertEquals("example.com", result.host)
    }

    @Test
    fun classify_httpUrlWithoutPath_returnsUrlWithHost() {
        val result = ClipboardContentClassifier.classify("http://www.wikipedia.org")

        assertTrue(result is ClipboardContent.Url)
        result as ClipboardContent.Url
        assertEquals("http://www.wikipedia.org", result.url)
        assertEquals("www.wikipedia.org", result.host)
    }

    @Test
    fun classify_internationalPhone_returnsPhone() {
        val result = ClipboardContentClassifier.classify("+44 20 7946 0958")

        assertTrue(result is ClipboardContent.Phone)
        result as ClipboardContent.Phone
        assertEquals("+44 20 7946 0958", result.number)
    }

    @Test
    fun classify_formattedUsPhone_returnsPhone() {
        val result = ClipboardContentClassifier.classify("(555) 123-4567")

        assertTrue(result is ClipboardContent.Phone)
        result as ClipboardContent.Phone
        assertEquals("(555) 123-4567", result.number)
    }

    @Test
    fun classify_email_returnsEmail() {
        val result = ClipboardContentClassifier.classify("someone.name@example.co.uk")

        assertTrue(result is ClipboardContent.Email)
        result as ClipboardContent.Email
        assertEquals("someone.name@example.co.uk", result.address)
    }

    @Test
    fun classify_sixDigitHexColor_returnsHexColor() {
        val result = ClipboardContentClassifier.classify("#a1b2c3")

        assertTrue(result is ClipboardContent.HexColor)
        result as ClipboardContent.HexColor
        assertEquals("#a1b2c3", result.code)
    }

    @Test
    fun classify_shortHexColor_returnsHexColor() {
        val result = ClipboardContentClassifier.classify("#abc")

        assertTrue(result is ClipboardContent.HexColor)
        result as ClipboardContent.HexColor
        assertEquals("#abc", result.code)
    }

    @Test
    fun classify_eightDigitHexColor_returnsHexColor() {
        val result = ClipboardContentClassifier.classify("#80FFAABB")

        assertTrue(result is ClipboardContent.HexColor)
        result as ClipboardContent.HexColor
        assertEquals("#80FFAABB", result.code)
    }

    @Test
    fun classify_plainText_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("hello world"))
    }

    @Test
    fun classify_fourDigitYearLikeNumber_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("1234"))
    }

    @Test
    fun classify_priceLikeNumber_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("55.50"))
    }

    @Test
    fun classify_emptyString_returnsNull() {
        assertNull(ClipboardContentClassifier.classify(""))
    }

    @Test
    fun classify_nullInput_returnsNull() {
        assertNull(ClipboardContentClassifier.classify(null))
    }

    @Test
    fun classify_shortInput_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("abc"))
        assertNull(ClipboardContentClassifier.classify("#zz"))
    }

    @Test
    fun classify_trimsSurroundingWhitespace() {
        val result = ClipboardContentClassifier.classify("  https://example.com  \n")

        assertTrue(result is ClipboardContent.Url)
        result as ClipboardContent.Url
        assertEquals("https://example.com", result.url)
        assertEquals("example.com", result.host)
    }

    @Test
    fun classify_phoneWithTooFewDigits_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("123-45"))
        assertNull(ClipboardContentClassifier.classify("+12 345"))
    }

    @Test
    fun classify_nonHttpUrl_returnsNull() {
        assertNull(ClipboardContentClassifier.classify("ftp://example.com/file"))
        assertNull(ClipboardContentClassifier.classify("example.com"))
    }
}
