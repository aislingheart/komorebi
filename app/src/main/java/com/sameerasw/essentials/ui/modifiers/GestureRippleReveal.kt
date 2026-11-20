/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Modifiers & Animations
 * File: GestureRippleReveal.kt
 * Description: Gesture-triggered expanding ripple wave reveal animation for Search
 * (API 33+ AGSL refraction wave on backdrop + all-API luminous dual-ring radial wave).
 */

package com.sameerasw.essentials.ui.modifiers

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.utils.DeviceUtils
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

private const val GESTURE_RIPPLE_DURATION_MS = 680
private val GestureRippleEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)

private const val GESTURE_RIPPLE_AGSL = """
    uniform shader content;
    uniform float2 resolution;
    uniform float2 origin;
    uniform float progress;

    half4 main(float2 fragCoord) {
        if (progress <= 0.001 || progress >= 0.999) {
            return content.eval(fragCoord);
        }

        float2 center = origin * resolution;
        float2 delta = fragCoord - center;
        float dist = length(delta);

        float maxR = length(resolution) * 1.15;
        float waveR = progress * maxR;

        // Smooth Gaussian ring envelope that widens slightly and fades out cleanly
        float ringWidth = mix(95.0, 210.0, progress);
        float d = dist - waveR;
        float envelope = exp(-(d * d) / (2.0 * ringWidth * ringWidth));

        // Fade in very quickly at start, smoothly decay to 0 before completion
        float fadeIn = smoothstep(0.0, 0.06, progress);
        float fadeOut = pow(1.0 - progress, 1.35);
        float strength = envelope * fadeIn * fadeOut;

        float2 dir = dist > 0.001 ? (delta / dist) : float2(0.0, -1.0);
        float displacement = -strength * 34.0;
        float chroma = strength * 2.4;

        float2 baseUv = fragCoord + dir * displacement;
        half4 rSample = content.eval(clamp(baseUv + dir * chroma, float2(0.0), resolution - 1.0));
        half4 gSample = content.eval(clamp(baseUv, float2(0.0), resolution - 1.0));
        half4 bSample = content.eval(clamp(baseUv - dir * chroma, float2(0.0), resolution - 1.0));

        // Subtle glass highlight along the wave crest
        half highlight = half(strength * 0.09);
        return half4(
            clamp(rSample.r + highlight, 0.0h, 1.0h),
            clamp(gSample.g + highlight, 0.0h, 1.0h),
            clamp(bSample.b + highlight, 0.0h, 1.0h),
            gSample.a
        );
    }
"""

fun isGestureRippleEnabled(context: Context): Boolean {
    val repo = SettingsRepository(context.applicationContext)
    return repo.getBoolean(SettingsRepository.KEY_USE_RIPPLE, true) &&
        !DeviceUtils.isPowerSaveMode(context)
}

/**
 * Applies a single crisp AGSL shockwave refraction across the backdrop when search is opened via gesture.
 * Automatically clears [RenderEffect] as soon as the wave finishes so idle GPU cost is zero.
 */
fun Modifier.gestureRippleRefraction(
    triggerKey: Int,
    enabled: Boolean,
    originNormalized: Offset = Offset(0.5f, 0.96f),
): Modifier = composed {
    if (!enabled || triggerKey <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this
    }

    val progress = remember { Animatable(0f) }
    val shader = remember { RuntimeShader(GESTURE_RIPPLE_AGSL) }

    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = GESTURE_RIPPLE_DURATION_MS,
                easing = GestureRippleEasing,
            ),
        )
    }

    val p = progress.value
    if (p <= 0.001f || p >= 0.999f) {
        this
    } else {
        this.graphicsLayer {
            val w = size.width
            val h = size.height
            if (w > 1f && h > 1f) {
                shader.setFloatUniform("resolution", w, h)
                shader.setFloatUniform("origin", originNormalized.x, originNormalized.y)
                shader.setFloatUniform("progress", p)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            }
        }
    }
}

/**
 * Luminous expanding dual-ring radial ripple wave originating from the gesture zone.
 * Works across all API levels (overlay mode & window mode) with zero touch interception.
 */
@Composable
fun GestureRippleWaveOverlay(
    triggerKey: Int,
    enabled: Boolean,
    originNormalized: Offset = Offset(0.5f, 0.96f),
    modifier: Modifier = Modifier,
) {
    if (!enabled || triggerKey <= 0) return

    val progress = remember { Animatable(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val highlightColor = Color.White

    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = GESTURE_RIPPLE_DURATION_MS,
                easing = GestureRippleEasing,
            ),
        )
    }

    val p = progress.value
    if (p <= 0.001f || p >= 0.999f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * originNormalized.x, size.height * originNormalized.y)
        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 1.12f
        val radius = max(12f, p * maxRadius)

        val fadeIn = (p / 0.08f).coerceIn(0f, 1f)
        val fadeOut = (1f - p).pow(1.4f)
        val alpha = (fadeIn * fadeOut).coerceIn(0f, 1f)

        // 1. Soft atmospheric radial band sweeping outward from the gesture edge
        val innerStop = ((radius - 190f) / radius).coerceIn(0f, 0.92f)
        val peakStop = ((radius - 42f) / radius).coerceIn(innerStop + 0.02f, 0.98f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    innerStop to Color.Transparent,
                    peakStop to primaryColor.copy(alpha = 0.24f * alpha),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // 2. Crisp luminous leading wave ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.48f * alpha),
            radius = radius,
            center = center,
            style = Stroke(width = (3.5f + 8f * (1f - p)).coerceAtLeast(2f)),
        )

        // 3. Subtle specular shimmer crest right inside the leading edge
        drawCircle(
            color = highlightColor.copy(alpha = 0.30f * alpha),
            radius = max(4f, radius - 4f),
            center = center,
            style = Stroke(width = 2.2f),
        )

        // 4. Trailing secondary harmonic ring tinted with Material You tertiary
        val secondaryP = ((p - 0.10f) / 0.90f).coerceIn(0f, 1f)
        if (secondaryP > 0.001f) {
            val secRadius = max(8f, secondaryP * maxRadius * 0.92f)
            val secAlpha = (1f - secondaryP).pow(1.6f) * fadeIn
            drawCircle(
                color = tertiaryColor.copy(alpha = 0.32f * secAlpha),
                radius = secRadius,
                center = center,
                style = Stroke(width = (2.5f + 5f * (1f - secondaryP)).coerceAtLeast(1.5f)),
            )
        }
    }
}
