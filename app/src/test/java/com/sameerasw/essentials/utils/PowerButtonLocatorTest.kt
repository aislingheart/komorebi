package com.sameerasw.essentials.utils

import android.view.Surface
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerButtonLocatorTest {

    @Test
    fun verifiedPixelModelsResolveToExactCalibratedCoordinates() {
        // Pixel 9 Pro (caiman): 856px / 2856px
        val caiman = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Google",
            device = "caiman",
            model = "Pixel 9 Pro",
        )
        assertTrue(caiman.isExactMatch)
        assertEquals(PowerButtonLocator.PhysicalEdge.RIGHT, caiman.edge)
        assertEquals(856f / 2856f, caiman.yRatio, 0.002f)

        // Pixel 8 Pro (husky): 774px / 2992px
        val husky = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Google",
            device = "husky",
            model = "Pixel 8 Pro",
        )
        assertTrue(husky.isExactMatch)
        assertEquals(774f / 2992f, husky.yRatio, 0.002f)

        // Pixel 7 (panther): 776px / 2400px
        val panther = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Google",
            device = "panther",
            model = "Pixel 7",
        )
        assertTrue(panther.isExactMatch)
        assertEquals(776f / 2400f, panther.yRatio, 0.002f)
    }

    @Test
    fun verifiedNonPixelModelsResolveByCodenameOrModelName() {
        val s24Ultra = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Samsung",
            device = "e3q",
            model = "SM-S928B",
        )
        assertTrue(s24Ultra.isExactMatch)
        assertEquals(PowerButtonLocator.PhysicalEdge.RIGHT, s24Ultra.edge)
        assertEquals(0.455f, s24Ultra.yRatio, 0.005f)

        val onePlus12 = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "OnePlus",
            device = "waffle",
            model = "CPH2581",
        )
        assertTrue(onePlus12.isExactMatch)
        assertEquals(0.415f, onePlus12.yRatio, 0.005f)

        val nothing2 = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Nothing",
            device = "Pong",
            model = "A065",
        )
        assertTrue(nothing2.isExactMatch)
        assertEquals(1009f / 2412f, nothing2.yRatio, 0.002f)
    }

    @Test
    fun unknownDeviceFallsBackToManufacturerFamilyAndFlagsNonExact() {
        val unknownGoogle = PowerButtonLocator.resolveFromDeviceMetadata(
            manufacturer = "Google",
            device = "future_pixel_99",
            model = "Pixel 99",
        )
        assertFalse(unknownGoogle.isExactMatch)
        assertTrue(unknownGoogle.yRatio in 0.25f..0.45f)
    }

    @Test
    fun rotationMappingTransformsRightEdgePowerButtonAcrossAllFourOrientations() {
        val spec = PowerButtonLocator.PortraitPowerButtonSpec(
            yRatio = 0.35f,
            edge = PowerButtonLocator.PhysicalEdge.RIGHT,
            isExactMatch = true,
        )

        val rot0 = PowerButtonLocator.mapPortraitEdgeToCurrentRotation(spec, Surface.ROTATION_0)
        assertEquals(0.99f, rot0.x, 0.001f)
        assertEquals(0.35f, rot0.y, 0.001f)

        // ROTATION_90 (CCW 90°): right edge moves to top edge.
        val rot90 = PowerButtonLocator.mapPortraitEdgeToCurrentRotation(spec, Surface.ROTATION_90)
        assertEquals(0.35f, rot90.x, 0.001f)
        assertEquals(0.01f, rot90.y, 0.001f)

        // ROTATION_180 (upside down): right edge moves to left edge, inverted Y.
        val rot180 = PowerButtonLocator.mapPortraitEdgeToCurrentRotation(spec, Surface.ROTATION_180)
        assertEquals(0.01f, rot180.x, 0.001f)
        assertEquals(0.65f, rot180.y, 0.001f)

        // ROTATION_270 (CW 90°): right edge moves to bottom edge, inverted X.
        val rot270 = PowerButtonLocator.mapPortraitEdgeToCurrentRotation(spec, Surface.ROTATION_270)
        assertEquals(0.65f, rot270.x, 0.001f)
        assertEquals(0.99f, rot270.y, 0.001f)
    }

    @Test
    fun searchLaunchOriginClampsPointsAndResolvesBottomAccessibility() {
        val clamped = SearchLaunchOrigin.fromPoint(1.5f, -0.3f)
        assertEquals(SearchLaunchOriginKind.POINT, clamped.kind)
        assertTrue(clamped.xFraction in 0.02f..0.98f)
        assertTrue(clamped.yFraction in 0.02f..0.98f)

        val bottom = SearchLaunchOrigin.accessibilityBottom()
        assertEquals(SearchLaunchOriginKind.BOTTOM_ACCESSIBILITY, bottom.kind)
        assertEquals(Offset(0.5f, 0.985f), Offset(bottom.xFraction, bottom.yFraction))

        val power = SearchLaunchOrigin.powerButton()
        assertEquals(SearchLaunchOriginKind.POWER_BUTTON, power.kind)
    }
}
