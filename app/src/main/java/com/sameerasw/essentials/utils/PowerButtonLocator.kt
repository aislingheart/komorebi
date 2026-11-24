/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities - Hardware Geometry
 * File: PowerButtonLocator.kt
 * Description: Resolves the physical power button screen coordinate (via SystemUI RRO,
 *              verified hardware overlays, and cached online device overlay lookup) and
 *              normalizes launch origins (power button, accessibility bottom, launcher/widget bounds).
 */

package com.sameerasw.essentials.utils

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

enum class SearchLaunchOriginKind {
    POWER_BUTTON,
    BOTTOM_ACCESSIBILITY,
    POINT,
    DEFAULT,
}

data class SearchLaunchOrigin(
    val kind: SearchLaunchOriginKind = SearchLaunchOriginKind.DEFAULT,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.94f,
) {
    fun writeToIntent(intent: Intent): Intent =
        intent.apply {
            putExtra(EXTRA_LAUNCH_ORIGIN_KIND, kind.name)
            putExtra(EXTRA_LAUNCH_ORIGIN_X, xFraction)
            putExtra(EXTRA_LAUNCH_ORIGIN_Y, yFraction)
        }

    fun resolveNormalizedOffset(context: Context): Offset =
        when (kind) {
            SearchLaunchOriginKind.POWER_BUTTON -> {
                PowerButtonLocator.prefetchOnlineIfNeeded(context)
                PowerButtonLocator.resolvePowerButtonScreenOffset(context)
            }

            SearchLaunchOriginKind.BOTTOM_ACCESSIBILITY -> {
                Offset(0.5f, 0.985f)
            }

            SearchLaunchOriginKind.POINT -> {
                Offset(
                    x = xFraction.coerceIn(0.02f, 0.98f),
                    y = yFraction.coerceIn(0.02f, 0.98f),
                )
            }

            SearchLaunchOriginKind.DEFAULT -> {
                Offset(0.5f, 0.94f)
            }
        }

    companion object {
        const val EXTRA_LAUNCH_ORIGIN_KIND = "com.sameerasw.essentials.EXTRA_LAUNCH_ORIGIN_KIND"
        const val EXTRA_LAUNCH_ORIGIN_X = "com.sameerasw.essentials.EXTRA_LAUNCH_ORIGIN_X"
        const val EXTRA_LAUNCH_ORIGIN_Y = "com.sameerasw.essentials.EXTRA_LAUNCH_ORIGIN_Y"

        fun powerButton(): SearchLaunchOrigin =
            SearchLaunchOrigin(
                kind = SearchLaunchOriginKind.POWER_BUTTON,
                xFraction = 1f,
                yFraction = 0.34f,
            )

        fun accessibilityBottom(): SearchLaunchOrigin =
            SearchLaunchOrigin(
                kind = SearchLaunchOriginKind.BOTTOM_ACCESSIBILITY,
                xFraction = 0.5f,
                yFraction = 0.985f,
            )

        fun fromPoint(
            xFraction: Float,
            yFraction: Float,
        ): SearchLaunchOrigin =
            SearchLaunchOrigin(
                kind = SearchLaunchOriginKind.POINT,
                xFraction = xFraction.coerceIn(0.02f, 0.98f),
                yFraction = yFraction.coerceIn(0.02f, 0.98f),
            )

        fun fromSourceBounds(
            context: Context,
            bounds: Rect?,
        ): SearchLaunchOrigin? {
            if (bounds == null || bounds.isEmpty) return null
            val (widthPx, heightPx) = PowerButtonLocator.getCurrentDisplaySizePx(context)
            if (widthPx <= 0 || heightPx <= 0) return null
            val cx = bounds.exactCenterX() / widthPx.toFloat()
            val cy = bounds.exactCenterY() / heightPx.toFloat()
            if (!cx.isFinite() || !cy.isFinite()) return null
            return fromPoint(cx, cy)
        }

        fun fromIntent(
            context: Context,
            intent: Intent?,
        ): SearchLaunchOrigin {
            if (intent == null) return SearchLaunchOrigin()
            val rawKind = intent.getStringExtra(EXTRA_LAUNCH_ORIGIN_KIND)
            if (!rawKind.isNullOrBlank()) {
                val parsed = runCatching { SearchLaunchOriginKind.valueOf(rawKind) }.getOrNull()
                if (parsed != null) {
                    val x = intent.getFloatExtra(EXTRA_LAUNCH_ORIGIN_X, 0.5f)
                    val y = intent.getFloatExtra(EXTRA_LAUNCH_ORIGIN_Y, 0.94f)
                    return SearchLaunchOrigin(parsed, x, y)
                }
            }
            fromSourceBounds(context, intent.sourceBounds)?.let { return it }
            return SearchLaunchOrigin()
        }
    }
}

object PowerButtonLocator {
    private const val TAG = "PowerButtonLocator"
    private const val PREFS_NAME = "komorebi_power_button_locator"
    private const val KEY_DEVICE_SIGNATURE = "device_signature"
    private const val KEY_CACHED_Y_RATIO = "cached_y_ratio"
    private const val KEY_CACHED_EDGE = "cached_edge"
    private const val KEY_LAST_LOOKUP_MS = "last_lookup_ms"
    private const val LOOKUP_RETRY_BACKOFF_MS = 14L * 24L * 60L * 60L * 1000L // 14 days
    private const val AOSP_DEFAULT_POWER_BUTTON_PX = 620f

    enum class PhysicalEdge {
        RIGHT,
        LEFT,
    }

    data class PortraitPowerButtonSpec(
        val yRatio: Float,
        val edge: PhysicalEdge = PhysicalEdge.RIGHT,
        val isExactMatch: Boolean = true,
    )

    private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lookupInFlight = AtomicBoolean(false)

    @Volatile
    private var cachedOnlineSpec: PortraitPowerButtonSpec? = null

    @Volatile
    private var cachedSystemUiRatio: Float? = null

    @Volatile
    private var systemUiProbed: Boolean = false

    /**
     * Exact hardware-calibrated power button Y ratios (in natural portrait orientation, 0 = top, 1 = bottom)
     * sourced from official Google/OEM SystemUI RRO overlays (`physical_power_button_center_screen_location_y`)
     * and verified hardware dimensional schematics.
     */
    private val EXACT_CODENAME_SPECS: Map<String, PortraitPowerButtonSpec> =
        mapOf(
            // Google Pixel (Power button sits ABOVE volume rocker on right edge)
            "flame" to PortraitPowerButtonSpec(480f / 2280f), // Pixel 4
            "coral" to PortraitPowerButtonSpec(705f / 3040f), // Pixel 4 XL
            "sunfish" to PortraitPowerButtonSpec(640f / 2340f), // Pixel 4a
            "bramble" to PortraitPowerButtonSpec(575f / 2340f), // Pixel 4a 5G
            "redfin" to PortraitPowerButtonSpec(620f / 2340f), // Pixel 5
            "barbet" to PortraitPowerButtonSpec(595f / 2400f), // Pixel 5a
            "oriole" to PortraitPowerButtonSpec(650f / 2400f), // Pixel 6
            "raven" to PortraitPowerButtonSpec(940f / 3120f), // Pixel 6 Pro
            "bluejay" to PortraitPowerButtonSpec(650f / 2400f), // Pixel 6a
            "panther" to PortraitPowerButtonSpec(776f / 2400f), // Pixel 7
            "cheetah" to PortraitPowerButtonSpec(828f / 3120f), // Pixel 7 Pro
            "lynx" to PortraitPowerButtonSpec(730f / 2400f), // Pixel 7a
            "felix" to PortraitPowerButtonSpec(706f / 2092f), // Pixel Fold
            "shiba" to PortraitPowerButtonSpec(698f / 2400f), // Pixel 8
            "husky" to PortraitPowerButtonSpec(774f / 2992f), // Pixel 8 Pro
            "akita" to PortraitPowerButtonSpec(710f / 2400f), // Pixel 8a
            "tokay" to PortraitPowerButtonSpec(728f / 2424f), // Pixel 9
            "caiman" to PortraitPowerButtonSpec(856f / 2856f), // Pixel 9 Pro
            "komodo" to PortraitPowerButtonSpec(882f / 2992f), // Pixel 9 Pro XL
            "comet" to PortraitPowerButtonSpec(765f / 2424f), // Pixel 9 Pro Fold
            "tegu" to PortraitPowerButtonSpec(730f / 2424f), // Pixel 9a
            "frankel" to PortraitPowerButtonSpec(0.300f), // Pixel 10
            "blazer" to PortraitPowerButtonSpec(0.300f), // Pixel 10 Pro
            "mustang" to PortraitPowerButtonSpec(0.295f), // Pixel 10 Pro XL
            "rango" to PortraitPowerButtonSpec(0.315f), // Pixel 10 Pro Fold
            // Samsung Galaxy (Power button sits BELOW volume rocker on right edge)
            "x1s" to PortraitPowerButtonSpec(990f / 2400f), // S20
            "x1q" to PortraitPowerButtonSpec(990f / 2400f),
            "y2s" to PortraitPowerButtonSpec(990f / 2400f), // S20+
            "y2q" to PortraitPowerButtonSpec(990f / 2400f),
            "z3s" to PortraitPowerButtonSpec(990f / 2400f), // S20 Ultra
            "z3q" to PortraitPowerButtonSpec(990f / 2400f),
            "r8q" to PortraitPowerButtonSpec(975f / 2400f), // S20 FE
            "r8s" to PortraitPowerButtonSpec(975f / 2400f),
            "c1s" to PortraitPowerButtonSpec(950f / 2400f), // Note 20
            "c2s" to PortraitPowerButtonSpec(1250f / 3088f), // Note 20 Ultra
            "o1s" to PortraitPowerButtonSpec(0.445f), // S21
            "t2s" to PortraitPowerButtonSpec(0.448f), // S21+
            "p3s" to PortraitPowerButtonSpec(0.452f), // S21 Ultra
            "r9q" to PortraitPowerButtonSpec(0.442f), // S21 FE
            "r0s" to PortraitPowerButtonSpec(0.450f), // S22
            "r0q" to PortraitPowerButtonSpec(0.450f),
            "g0s" to PortraitPowerButtonSpec(1060f / 2340f), // S22+
            "g0q" to PortraitPowerButtonSpec(1060f / 2340f),
            "b0s" to PortraitPowerButtonSpec(0.455f), // S22 Ultra
            "b0q" to PortraitPowerButtonSpec(0.455f),
            "dm1q" to PortraitPowerButtonSpec(0.452f), // S23
            "dm2q" to PortraitPowerButtonSpec(0.454f), // S23+
            "dm3q" to PortraitPowerButtonSpec(0.456f), // S23 Ultra
            "r11q" to PortraitPowerButtonSpec(0.445f), // S23 FE
            "e1q" to PortraitPowerButtonSpec(0.450f), // S24
            "e1s" to PortraitPowerButtonSpec(0.450f),
            "e2q" to PortraitPowerButtonSpec(0.452f), // S24+
            "e2s" to PortraitPowerButtonSpec(0.452f),
            "e3q" to PortraitPowerButtonSpec(0.455f), // S24 Ultra
            "r12s" to PortraitPowerButtonSpec(0.445f), // S24 FE
            "pa1q" to PortraitPowerButtonSpec(0.450f), // S25
            "pa2q" to PortraitPowerButtonSpec(0.452f), // S25+
            "pa3q" to PortraitPowerButtonSpec(0.456f), // S25 Ultra
            "a52sxq" to PortraitPowerButtonSpec(1020f / 2400f), // A52s
            "a73xq" to PortraitPowerButtonSpec(1020f / 2400f), // A73
            "a71" to PortraitPowerButtonSpec(980f / 2400f), // A71
            // OnePlus (Right edge)
            "guacamole" to PortraitPowerButtonSpec(1250f / 3120f), // OnePlus 7 Pro
            "hotdog" to PortraitPowerButtonSpec(1250f / 3120f), // OnePlus 7T Pro
            "instantnoodle" to PortraitPowerButtonSpec(810f / 2400f), // OnePlus 8
            "instantnoodlep" to PortraitPowerButtonSpec(1210f / 3168f), // OnePlus 8 Pro
            "kebab" to PortraitPowerButtonSpec(900f / 2400f), // OnePlus 8T
            "lemonade" to PortraitPowerButtonSpec(900f / 2400f), // OnePlus 9
            "lemonadep" to PortraitPowerButtonSpec(1160f / 3216f), // OnePlus 9 Pro
            "salami" to PortraitPowerButtonSpec(0.415f), // OnePlus 11
            "waffle" to PortraitPowerButtonSpec(0.415f), // OnePlus 12
            "aston" to PortraitPowerButtonSpec(1200f / 2780f), // OnePlus 12R
            "astonc" to PortraitPowerButtonSpec(1200f / 2780f),
            "dodge" to PortraitPowerButtonSpec(0.415f), // OnePlus 13
            "giuliac" to PortraitPowerButtonSpec(1130f / 2780f), // OnePlus 13R
            // Nothing (Right edge, volume on left)
            "spacewar" to PortraitPowerButtonSpec(965f / 2400f), // Nothing Phone (1)
            "pong" to PortraitPowerButtonSpec(1009f / 2412f), // Nothing Phone (2)
            "pacman" to PortraitPowerButtonSpec(0.418f), // Nothing Phone (2a)
            "pacmanpro" to PortraitPowerButtonSpec(0.418f), // Nothing Phone (2a) Plus
            "asteroids" to PortraitPowerButtonSpec(0.398f), // Nothing Phone (3a)
            "tetris" to PortraitPowerButtonSpec(0.422f), // CMF Phone 1
            // Xiaomi / Redmi / POCO
            "fuxi" to PortraitPowerButtonSpec(1040f / 2400f), // Xiaomi 13
            "nuwa" to PortraitPowerButtonSpec(1460f / 3200f), // Xiaomi 13 Pro
            "houji" to PortraitPowerButtonSpec(0.435f), // Xiaomi 14
            "aurora" to PortraitPowerButtonSpec(0.445f), // Xiaomi 14 Ultra
            "marble" to PortraitPowerButtonSpec(1025f / 2400f), // POCO F5
            "peridot" to PortraitPowerButtonSpec(1210f / 2712f), // POCO F6
            "vermeer" to PortraitPowerButtonSpec(1460f / 3200f), // POCO F6 Pro
            "garnet" to PortraitPowerButtonSpec(1220f / 2712f), // Redmi Note 13 Pro
            "mondrian" to PortraitPowerButtonSpec(1385f / 3200f), // POCO F5 Pro
            "socrates" to PortraitPowerButtonSpec(1375f / 3200f), // Redmi K60 Pro
            "diting" to PortraitPowerButtonSpec(1170f / 2712f), // Xiaomi 12T Pro
            "cupid" to PortraitPowerButtonSpec(995f / 2400f), // Xiaomi 12
            "zeus" to PortraitPowerButtonSpec(1400f / 3200f), // Xiaomi 12 Pro
            // Sony Xperia (Side fingerprint / power button)
            "pdx203" to PortraitPowerButtonSpec(1685f / 3840f), // Xperia 1 II
            "pdx206" to PortraitPowerButtonSpec(1060f / 2520f), // Xperia 5 II
            "pdx215" to PortraitPowerButtonSpec(1685f / 3840f), // Xperia 1 III
            "pdx234" to PortraitPowerButtonSpec(1685f / 3840f), // Xperia 1 V
            "pdx237" to PortraitPowerButtonSpec(1150f / 2520f), // Xperia 5 V
            "pdx245" to PortraitPowerButtonSpec(1685f / 3840f), // Xperia 1 VI
            // Fairphone
            "fp4" to PortraitPowerButtonSpec(940f / 2340f),
            "fp5" to PortraitPowerButtonSpec(1105f / 2700f),
            "fp6" to PortraitPowerButtonSpec(1095f / 2484f),
            // Asus
            "sake" to PortraitPowerButtonSpec(1111f / 2400f), // Zenfone 8
            "ai2202" to PortraitPowerButtonSpec(0.455f), // Zenfone 9
            "ai2302" to PortraitPowerButtonSpec(0.455f), // Zenfone 10
            // Motorola
            "dubai" to PortraitPowerButtonSpec(1000f / 2400f), // Edge 30
            "rtwo" to PortraitPowerButtonSpec(990f / 2400f), // Edge 40 Pro
            "eqs" to PortraitPowerButtonSpec(920f / 2400f), // Edge 30 Ultra
            "lyriq" to PortraitPowerButtonSpec(930f / 2400f), // Edge 40
            "berlin" to PortraitPowerButtonSpec(810f / 2400f), // Edge 20
        )

    private val MODEL_PREFIX_SPECS: List<Pair<String, PortraitPowerButtonSpec>> =
        listOf(
            // Pixel marketing names (for custom ROMs / spoofing where Build.DEVICE may vary)
            "pixel 10 pro fold" to PortraitPowerButtonSpec(0.315f),
            "pixel 10 pro xl" to PortraitPowerButtonSpec(0.295f),
            "pixel 10 pro" to PortraitPowerButtonSpec(0.300f),
            "pixel 10" to PortraitPowerButtonSpec(0.300f),
            "pixel 9 pro fold" to PortraitPowerButtonSpec(765f / 2424f),
            "pixel 9 pro xl" to PortraitPowerButtonSpec(882f / 2992f),
            "pixel 9 pro" to PortraitPowerButtonSpec(856f / 2856f),
            "pixel 9a" to PortraitPowerButtonSpec(730f / 2424f),
            "pixel 9" to PortraitPowerButtonSpec(728f / 2424f),
            "pixel 8 pro" to PortraitPowerButtonSpec(774f / 2992f),
            "pixel 8a" to PortraitPowerButtonSpec(710f / 2400f),
            "pixel 8" to PortraitPowerButtonSpec(698f / 2400f),
            "pixel fold" to PortraitPowerButtonSpec(706f / 2092f),
            "pixel 7 pro" to PortraitPowerButtonSpec(828f / 3120f),
            "pixel 7a" to PortraitPowerButtonSpec(730f / 2400f),
            "pixel 7" to PortraitPowerButtonSpec(776f / 2400f),
            "pixel 6 pro" to PortraitPowerButtonSpec(940f / 3120f),
            "pixel 6a" to PortraitPowerButtonSpec(650f / 2400f),
            "pixel 6" to PortraitPowerButtonSpec(650f / 2400f),
            "pixel 5" to PortraitPowerButtonSpec(620f / 2340f),
            // Samsung SM- model numbers
            "sm-s938" to PortraitPowerButtonSpec(0.456f), // S25 Ultra
            "sm-s936" to PortraitPowerButtonSpec(0.452f), // S25+
            "sm-s931" to PortraitPowerButtonSpec(0.450f), // S25
            "sm-s928" to PortraitPowerButtonSpec(0.455f), // S24 Ultra
            "sm-s926" to PortraitPowerButtonSpec(0.452f), // S24+
            "sm-s921" to PortraitPowerButtonSpec(0.450f), // S24
            "sm-s918" to PortraitPowerButtonSpec(0.456f), // S23 Ultra
            "sm-s916" to PortraitPowerButtonSpec(0.454f), // S23+
            "sm-s911" to PortraitPowerButtonSpec(0.452f), // S23
            "sm-s908" to PortraitPowerButtonSpec(0.455f), // S22 Ultra
            "sm-s906" to PortraitPowerButtonSpec(1060f / 2340f), // S22+
            "sm-s901" to PortraitPowerButtonSpec(0.450f), // S22
            "sm-f956" to PortraitPowerButtonSpec(0.450f), // Z Fold 6
            "sm-f946" to PortraitPowerButtonSpec(0.450f), // Z Fold 5
            "sm-f741" to PortraitPowerButtonSpec(0.435f), // Z Flip 6
            "sm-f731" to PortraitPowerButtonSpec(0.435f), // Z Flip 5
            "sm-a556" to PortraitPowerButtonSpec(0.430f), // A55
            "sm-a546" to PortraitPowerButtonSpec(0.430f), // A54
            // OnePlus model prefixes
            "cph265" to PortraitPowerButtonSpec(0.415f), // OnePlus 13
            "pjz110" to PortraitPowerButtonSpec(0.415f), // OnePlus 13 CN
            "cph258" to PortraitPowerButtonSpec(0.415f), // OnePlus 12
            "cph257" to PortraitPowerButtonSpec(0.415f), // OnePlus 12
            "cph261" to PortraitPowerButtonSpec(1200f / 2780f), // OnePlus 12R
            "cph244" to PortraitPowerButtonSpec(0.415f), // OnePlus 11
            // Nothing models
            "a063" to PortraitPowerButtonSpec(965f / 2400f), // Phone (1)
            "a065" to PortraitPowerButtonSpec(1009f / 2412f), // Phone (2)
            "a142" to PortraitPowerButtonSpec(0.418f), // Phone (2a)
            "a059" to PortraitPowerButtonSpec(0.398f), // Phone (3a)
        )

    fun resolvePowerButtonScreenOffset(context: Context): Offset {
        val (widthPx, heightPx) = getCurrentDisplaySizePx(context)
        val portraitHeightPx = max(widthPx, heightPx).coerceAtLeast(1)
        val rotation = getCurrentDisplayRotation(context)

        // Tier 1: Live on-device SystemUI RRO calibration (pixel-exact OEM/ROM hardware value)
        val systemUiRatio = resolveSystemUiPowerButtonRatio(context, portraitHeightPx)
        if (systemUiRatio != null) {
            return mapPortraitEdgeToCurrentRotation(
                spec = PortraitPowerButtonSpec(yRatio = systemUiRatio, edge = PhysicalEdge.RIGHT),
                rotation = rotation,
            )
        }

        // Tier 2: Cached online measurement for this exact device
        val cached = getCachedOnlineSpec(context)
        if (cached != null) {
            return mapPortraitEdgeToCurrentRotation(cached, rotation)
        }

        // Tier 3: Verified hardware overlay database + OEM family fallback
        val spec = resolveFromDeviceMetadata(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            model = Build.MODEL.orEmpty(),
        )
        return mapPortraitEdgeToCurrentRotation(spec, rotation)
    }

    /**
     * Pure lookup helper suitable for unit testing and deterministic fallback resolution.
     */
    fun resolveFromDeviceMetadata(
        manufacturer: String,
        device: String,
        model: String,
    ): PortraitPowerButtonSpec {
        val normDevice = device.trim().lowercase(Locale.ROOT)
        val normModel = model.trim().lowercase(Locale.ROOT)
        val normMfg = manufacturer.trim().lowercase(Locale.ROOT)

        EXACT_CODENAME_SPECS[normDevice]?.let { return it }

        for ((prefix, spec) in MODEL_PREFIX_SPECS) {
            if (matchesModelPrefix(normModel, prefix)) {
                return spec
            }
        }

        val familyY =
            when {
                normMfg.contains("google") || normModel.contains("pixel") -> 0.295f
                normMfg.contains("samsung") -> 0.450f
                normMfg.contains("oneplus") || normMfg.contains("oppo") || normMfg.contains("realme") -> 0.418f
                normMfg.contains("nothing") -> 0.412f
                normMfg.contains("xiaomi") || normMfg.contains("redmi") || normMfg.contains("poco") -> 0.438f
                normMfg.contains("sony") -> 0.445f
                normMfg.contains("motorola") -> 0.405f
                normMfg.contains("asus") -> 0.455f
                normMfg.contains("fairphone") -> 0.415f
                else -> 0.380f
            }
        return PortraitPowerButtonSpec(
            yRatio = familyY,
            edge = PhysicalEdge.RIGHT,
            isExactMatch = false,
        )
    }

    /**
     * Maps a portrait-orientation physical edge coordinate onto the current display rotation
     * so landscape / reverse-portrait triggers still pulse from the true physical power button.
     */
    fun mapPortraitEdgeToCurrentRotation(
        spec: PortraitPowerButtonSpec,
        rotation: Int,
    ): Offset {
        val y = spec.yRatio.coerceIn(0.08f, 0.92f)
        val isRight = spec.edge == PhysicalEdge.RIGHT
        return when (rotation) {
            Surface.ROTATION_90 -> {
                // Device rotated 90° CCW: portrait right edge becomes TOP edge (x = y, y = 0)
                if (isRight) Offset(y, 0.01f) else Offset(y, 0.99f)
            }

            Surface.ROTATION_180 -> {
                // Upside-down portrait
                if (isRight) Offset(0.01f, 1f - y) else Offset(0.99f, 1f - y)
            }

            Surface.ROTATION_270 -> {
                // Device rotated 90° CW: portrait right edge becomes BOTTOM edge (x = 1 - y, y = 1)
                if (isRight) Offset(1f - y, 0.99f) else Offset(1f - y, 0.01f)
            }

            else -> {
                // Natural portrait (ROTATION_0)
                if (isRight) Offset(0.99f, y) else Offset(0.01f, y)
            }
        }
    }

    /**
     * For unrecognized devices that aren't calibrated by SystemUI or our built-in exact table,
     * asynchronously fetches the exact `physical_power_button_center_screen_location_y` measurement
     * from public LineageOS / AOSP device overlays online and caches it permanently.
     */
    fun prefetchOnlineIfNeeded(context: Context) {
        val appContext = context.applicationContext ?: context
        val (w, h) = getCurrentDisplaySizePx(appContext)
        val portraitHeightPx = max(w, h)
        if (portraitHeightPx <= 0) return

        // Skip network lookup if SystemUI RRO already gave an exact hardware value
        if (resolveSystemUiPowerButtonRatio(appContext, portraitHeightPx) != null) return

        // Skip if we already have an exact built-in measurement
        val localSpec = resolveFromDeviceMetadata(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            model = Build.MODEL.orEmpty(),
        )
        if (localSpec.isExactMatch) return

        // Skip if already cached or recently attempted
        val signature = currentDeviceSignature()
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DEVICE_SIGNATURE, null) == signature) {
            val cachedY = prefs.getFloat(KEY_CACHED_Y_RATIO, -1f)
            if (cachedY in 0.08f..0.92f) return
            val lastAttempt = prefs.getLong(KEY_LAST_LOOKUP_MS, 0L)
            if (System.currentTimeMillis() - lastAttempt < LOOKUP_RETRY_BACKOFF_MS) return
        }

        if (!lookupInFlight.compareAndSet(false, true)) return

        lookupScope.launch {
            try {
                prefs.edit()
                    .putString(KEY_DEVICE_SIGNATURE, signature)
                    .putLong(KEY_LAST_LOOKUP_MS, System.currentTimeMillis())
                    .apply()

                val fetchedRatio = fetchOnlinePowerButtonRatio(
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    device = Build.DEVICE.orEmpty(),
                    portraitHeightPx = portraitHeightPx,
                )
                if (fetchedRatio != null && fetchedRatio in 0.08f..0.92f) {
                    val spec = PortraitPowerButtonSpec(
                        yRatio = fetchedRatio,
                        edge = PhysicalEdge.RIGHT,
                        isExactMatch = true,
                    )
                    cachedOnlineSpec = spec
                    prefs.edit()
                        .putString(KEY_DEVICE_SIGNATURE, signature)
                        .putFloat(KEY_CACHED_Y_RATIO, fetchedRatio)
                        .putString(KEY_CACHED_EDGE, PhysicalEdge.RIGHT.name)
                        .apply()
                    Log.i(TAG, "Cached online power button Y ratio=$fetchedRatio for $signature")
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Online power button lookup skipped/failed: ${t.message}")
            } finally {
                lookupInFlight.set(false)
            }
        }
    }

    private fun resolveSystemUiPowerButtonRatio(
        context: Context,
        portraitHeightPx: Int,
    ): Float? {
        if (systemUiProbed) return cachedSystemUiRatio
        systemUiProbed = true
        val ratio =
            runCatching {
                val res = context.packageManager.getResourcesForApplication("com.android.systemui")
                val id = res.getIdentifier(
                    "physical_power_button_center_screen_location_y",
                    "dimen",
                    "com.android.systemui",
                )
                if (id == 0) return@runCatching null
                val px = res.getDimension(id)
                if (px <= 0f) return@runCatching null
                // AOSP leaves an uncalibrated 620px default on devices that don't ship a SystemUI RRO.
                // Only trust 620px verbatim on Pixel 5 (redfin), where 620px is genuinely calibrated.
                val isGenericDefault = kotlin.math.abs(px - AOSP_DEFAULT_POWER_BUTTON_PX) < 0.5f
                val isRedfin = Build.DEVICE.equals("redfin", ignoreCase = true)
                if (isGenericDefault && !isRedfin) return@runCatching null
                val normalized = px / portraitHeightPx.toFloat()
                if (normalized in 0.08f..0.92f) normalized else null
            }.getOrNull()
        cachedSystemUiRatio = ratio
        return ratio
    }

    private fun getCachedOnlineSpec(context: Context): PortraitPowerButtonSpec? {
        cachedOnlineSpec?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DEVICE_SIGNATURE, null) != currentDeviceSignature()) return null
        val y = prefs.getFloat(KEY_CACHED_Y_RATIO, -1f)
        if (y !in 0.08f..0.92f) return null
        val edge = runCatching {
            PhysicalEdge.valueOf(prefs.getString(KEY_CACHED_EDGE, PhysicalEdge.RIGHT.name) ?: PhysicalEdge.RIGHT.name)
        }.getOrDefault(PhysicalEdge.RIGHT)
        return PortraitPowerButtonSpec(yRatio = y, edge = edge, isExactMatch = true).also {
            cachedOnlineSpec = it
        }
    }

    private fun fetchOnlinePowerButtonRatio(
        manufacturer: String,
        device: String,
        portraitHeightPx: Int,
    ): Float? {
        val mfgSlug = normalizeRepoSlug(manufacturer)
        val devSlug = normalizeRepoSlug(device)
        if (mfgSlug.isBlank() || devSlug.isBlank()) return null

        val repo = "LineageOS/android_device_${mfgSlug}_${devSlug}"
        val repoInfoText = httpGetText("https://api.github.com/repos/$repo") ?: return null
        val defaultBranch = JSONObject(repoInfoText).optString("default_branch", "").ifBlank { return null }

        val treeUrl = "https://api.github.com/repos/$repo/git/trees/${URLEncoder.encode(defaultBranch, "UTF-8")}?recursive=1"
        val treeText = httpGetText(treeUrl) ?: return null
        val treeArray = JSONObject(treeText).optJSONArray("tree") ?: return null

        val candidatePaths = mutableListOf<String>()
        for (i in 0 until treeArray.length()) {
            val path = treeArray.optJSONObject(i)?.optString("path").orEmpty()
            val lower = path.lowercase(Locale.ROOT)
            if ((lower.endsWith("dimens.xml") || lower.endsWith("config.xml")) &&
                (lower.contains("systemui") || lower.contains("overlay"))
            ) {
                candidatePaths.add(path)
            }
        }

        val regex = Regex("""physical_power_button_center_screen_location_y"[^>]*>\s*([0-9]+(?:\.[0-9]+)?)\s*px\s*<""")
        for (path in candidatePaths.take(6)) {
            val rawUrl = "https://raw.githubusercontent.com/$repo/$defaultBranch/$path"
            val xml = httpGetText(rawUrl) ?: continue
            val match = regex.find(xml) ?: continue
            val px = match.groupValues[1].toFloatOrNull() ?: continue
            if (px > 0f && kotlin.math.abs(px - AOSP_DEFAULT_POWER_BUTTON_PX) >= 0.5f) {
                val ratio = px / portraitHeightPx.toFloat()
                if (ratio in 0.08f..0.92f) return ratio
            }
        }
        return null
    }

    private fun httpGetText(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Komorebi-Android")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun matchesModelPrefix(
        normModel: String,
        prefix: String,
    ): Boolean {
        if (normModel.isEmpty() || prefix.isEmpty()) return false
        val isHardwareSkuPrefix =
            prefix.startsWith("sm-") ||
                prefix.startsWith("cph") ||
                prefix.startsWith("pjz") ||
                (prefix.startsWith("a0") || prefix.startsWith("a1"))
        if (isHardwareSkuPrefix) {
            return normModel.startsWith(prefix)
        }
        val idx = normModel.indexOf(prefix)
        if (idx < 0) return false
        val beforeOk = idx == 0 || !normModel[idx - 1].isLetterOrDigit()
        val endIdx = idx + prefix.length
        val afterOk = endIdx == normModel.length || !normModel[endIdx].isLetterOrDigit()
        return beforeOk && afterOk
    }

    private fun normalizeRepoSlug(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "")

    private fun currentDeviceSignature(): String =
        "${Build.MANUFACTURER.orEmpty()}|${Build.DEVICE.orEmpty()}|${Build.MODEL.orEmpty()}"

    fun getCurrentDisplaySizePx(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // WindowManager.getCurrentWindowMetrics / getMaximumWindowMetrics throw
            // UnsupportedOperationException on non-visual contexts (e.g., AccessibilityService,
            // Application, or ContextThemeWrapper without a display/window context).
            val bounds =
                runCatching { wm.maximumWindowMetrics.bounds }.getOrNull()
                    ?: runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val display =
            runCatching {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                dm?.getDisplay(Display.DEFAULT_DISPLAY)
            }.getOrNull()
        if (display != null) {
            val realSize = Point()
            @Suppress("DEPRECATION")
            val gotReal = runCatching { display.getRealSize(realSize) }.isSuccess
            if (gotReal && realSize.x > 0 && realSize.y > 0) {
                return realSize.x to realSize.y
            }
            val mode = runCatching { display.mode }.getOrNull()
            if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                val rot = runCatching { display.rotation }.getOrDefault(Surface.ROTATION_0)
                return if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                    mode.physicalHeight to mode.physicalWidth
                } else {
                    mode.physicalWidth to mode.physicalHeight
                }
            }
        }
        val dm = context.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }

    fun getCurrentDisplayRotation(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display.rotation }.getOrNull()?.let { return it }
        }
        runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }.getOrNull()?.let { return it }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return runCatching { wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0 }
            .getOrDefault(Surface.ROTATION_0)
    }

    /**
     * Returns the smallest display dimension in portrait orientation (width, height).
     */
    fun getPortraitDimensionsPx(context: Context): Pair<Int, Int> {
        val (w, h) = getCurrentDisplaySizePx(context)
        return min(w, h) to max(w, h)
    }
}
