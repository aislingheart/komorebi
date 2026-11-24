/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI - Modifiers
 * File: GestureRippleReveal.kt
 * Description: Featherlight liquid-light wave pulse, origin-aware radial expansion reveal,
 *              and lightweight AGSL glass refraction for Komorebi search launches.
 */

package com.sameerasw.essentials.ui.modifiers

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.utils.DeviceUtils
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private const val MAX_ACTIVE_WAVES = 3
private const val LAUNCH_PRIMARY_DURATION_MS = 560L
private const val LAUNCH_WHISPER_DURATION_MS = 460L
private const val LAUNCH_WHISPER_DELAY_MS = 55L
private const val TAP_WAVE_DURATION_MS = 420L
private const val BURST_WAVE_DURATION_MS = 480L
private const val EXPAND_REVEAL_DURATION_MS = 360

private val SlickWaveEasing = CubicBezierEasing(0.16f, 0.96f, 0.28f, 1.0f)
private val OriginRevealEasing = CubicBezierEasing(0.18f, 0.98f, 0.28f, 1.0f)

/**
 * Featherlight AGSL optical-refraction shader (API 33+):
 * - Capped at 2 waves with fast ring-band early-out (zero trig per pixel outside the thin rim).
 * - Clean analytical Gaussian ridge bend + whisper-subtle chromatic edge.
 */
private const val GESTURE_RIPPLE_SLICK_AGSL = """
uniform shader content;
uniform float2 uResolution;
uniform int uCount;
uniform float4 uW0; // x, y, radiusPx, strength
uniform float4 uW1;
uniform float2 uB0; // bandPx, edgeWeight
uniform float2 uB1;

float2 sampleWave(float2 coord, float4 w, float2 b, out float chromatic) {
    chromatic = 0.0;
    float2 delta = coord - w.xy;
    float dist = length(delta);
    float band = max(b.x, 12.0);
    float signedDist = dist - w.z;
    if (abs(signedDist) > band * 2.1 || dist < 1.0) {
        return float2(0.0);
    }
    float norm = signedDist / band;
    float env = exp(-norm * norm * 2.2) * w.w;
    float slope = -norm * env;
    chromatic = env * b.y;
    return (delta / dist) * (slope * 13.0);
}

half4 main(float2 coord) {
    float2 disp = float2(0.0);
    float chroma = 0.0;
    float c = 0.0;
    if (uCount > 0) {
        disp += sampleWave(coord, uW0, uB0, c);
        chroma += c;
    }
    if (uCount > 1) {
        disp += sampleWave(coord, uW1, uB1, c);
        chroma += c;
    }
    if (abs(disp.x) + abs(disp.y) < 0.05 && chroma < 0.01) {
        return content.eval(coord);
    }
    float2 uv = clamp(coord + disp, float2(0.0), uResolution - float2(1.0));
    float2 ca = disp * (0.14 * chroma);
    half r = content.eval(clamp(uv + ca, float2(0.0), uResolution - float2(1.0))).r;
    half g = content.eval(uv).g;
    half b = content.eval(clamp(uv - ca, float2(0.0), uResolution - float2(1.0))).b;
    half a = content.eval(uv).a;
    return half4(r, g, b, a);
}
"""

internal data class RippleWave(
    val id: Long,
    val originNormalized: Offset,
    val startTimeMs: Long,
    val durationMs: Long,
    val maxRadiusFraction: Float,
    val intensity: Float,
    val bandWidthDp: Float,
    val isPrimaryLaunch: Boolean,
)

@Stable
class GestureRippleController {
    private var nextId = 1L
    internal val waves = mutableStateListOf<RippleWave>()
    internal var frameClockMs by mutableLongStateOf(0L)

    val hasActiveWaves: Boolean
        get() = waves.isNotEmpty()

    /**
     * Spawns a fast, sleek liquid-light pulse originating from [originNormalized].
     */
    fun spawnLaunch(originNormalized: Offset = Offset(0.5f, 0.94f)) {
        val now = nowMs()
        val clamped = Offset(
            originNormalized.x.coerceIn(0f, 1f),
            originNormalized.y.coerceIn(0f, 1f),
        )
        // Clear stale launch waves so rapid re-triggers stay crisp instead of stacking mud
        waves.clear()
        enqueue(
            RippleWave(
                id = nextId++,
                originNormalized = clamped,
                startTimeMs = now,
                durationMs = LAUNCH_PRIMARY_DURATION_MS,
                maxRadiusFraction = 1.08f,
                intensity = 0.86f,
                bandWidthDp = 42f,
                isPrimaryLaunch = true,
            ),
        )
        enqueue(
            RippleWave(
                id = nextId++,
                originNormalized = clamped,
                startTimeMs = now + LAUNCH_WHISPER_DELAY_MS,
                durationMs = LAUNCH_WHISPER_DURATION_MS,
                maxRadiusFraction = 0.78f,
                intensity = 0.36f,
                bandWidthDp = 24f,
                isPrimaryLaunch = false,
            ),
        )
        frameClockMs = now
    }

    /**
     * Spawns a tight, responsive ripple on user tap.
     */
    fun spawnTap(originNormalized: Offset) {
        val now = nowMs()
        enqueue(
            RippleWave(
                id = nextId++,
                originNormalized = Offset(
                    originNormalized.x.coerceIn(0f, 1f),
                    originNormalized.y.coerceIn(0f, 1f),
                ),
                startTimeMs = now,
                durationMs = TAP_WAVE_DURATION_MS,
                maxRadiusFraction = 0.42f,
                intensity = 0.40f,
                bandWidthDp = 22f,
                isPrimaryLaunch = false,
            ),
        )
        frameClockMs = now
    }

    /**
     * Spawns a subtle pulse when an inline setting or action triggers.
     */
    fun spawnBurst(originNormalized: Offset = Offset(0.5f, 0.55f)) {
        val now = nowMs()
        enqueue(
            RippleWave(
                id = nextId++,
                originNormalized = Offset(
                    originNormalized.x.coerceIn(0f, 1f),
                    originNormalized.y.coerceIn(0f, 1f),
                ),
                startTimeMs = now,
                durationMs = BURST_WAVE_DURATION_MS,
                maxRadiusFraction = 0.62f,
                intensity = 0.48f,
                bandWidthDp = 28f,
                isPrimaryLaunch = false,
            ),
        )
        frameClockMs = now
    }

    internal fun tick(nowMs: Long) {
        frameClockMs = nowMs
        if (waves.isEmpty()) return
        val iterator = waves.listIterator()
        while (iterator.hasNext()) {
            val w = iterator.next()
            if (nowMs >= w.startTimeMs + w.durationMs) {
                iterator.remove()
            }
        }
    }

    private fun enqueue(wave: RippleWave) {
        while (waves.size >= MAX_ACTIVE_WAVES) {
            waves.removeAt(0)
        }
        waves.add(wave)
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

val LocalGestureRippleController = compositionLocalOf<GestureRippleController?> { null }

@Composable
fun rememberGestureRippleController(): GestureRippleController {
    val controller = remember { GestureRippleController() }
    val hasActive = controller.hasActiveWaves
    LaunchedEffect(hasActive) {
        if (!hasActive) return@LaunchedEffect
        while (controller.hasActiveWaves) {
            withFrameNanos { frameNanos ->
                controller.tick(frameNanos / 1_000_000L)
            }
        }
    }
    return controller
}

fun isGestureRippleEnabled(context: Context): Boolean {
    val repository = SettingsRepository(context)
    return repository.getBoolean(
        SettingsRepository.KEY_USE_RIPPLE,
        !DeviceUtils.isBlurProblematicDevice(),
    )
}

private fun areSystemAnimationsEnabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }.getOrDefault(true)

/**
 * Smoothly expands Komorebi radially out from [originNormalized] (power button, bottom edge,
 * tapped widget, or launcher icon) with a subtle origin-anchored scale & alpha bloom.
 * Short-circuits with zero clip/layer cost the moment the expansion settles.
 */
fun Modifier.originExpansionReveal(
    triggerKey: Int,
    originNormalized: Offset,
    enabled: Boolean = true,
    durationMs: Int = EXPAND_REVEAL_DURATION_MS,
): Modifier =
    composed {
        val context = LocalContext.current
        val animationsAllowed = remember(context) { areSystemAnimationsEnabled(context) }
        val active = enabled && animationsAllowed && triggerKey > 0
        val progress = remember { Animatable(if (active) 0f else 1f) }
        val clipPath = remember { Path() }

        LaunchedEffect(triggerKey, active) {
            if (!active) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = OriginRevealEasing,
                    ),
                )
            }
        }

        val clampedOrigin = Offset(
            originNormalized.x.coerceIn(0f, 1f),
            originNormalized.y.coerceIn(0f, 1f),
        )

        this
            .graphicsLayer {
                val p = progress.value
                if (p < 0.999f) {
                    val s = 0.945f + (0.055f * p)
                    scaleX = s
                    scaleY = s
                    alpha = (p / 0.24f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(clampedOrigin.x, clampedOrigin.y)
                }
            }
            .drawWithContent {
                val p = progress.value
                if (p >= 0.999f) {
                    drawContent()
                    return@drawWithContent
                }
                val originPx = Offset(size.width * clampedOrigin.x, size.height * clampedOrigin.y)
                val maxRadius = maxCornerDistance(originPx, size.width, size.height) * 1.06f
                val seedRadius = 28.dp.toPx()
                val currentRadius = seedRadius + (maxRadius - seedRadius) * p

                clipPath.reset()
                clipPath.addOval(
                    Rect(
                        left = originPx.x - currentRadius,
                        top = originPx.y - currentRadius,
                        right = originPx.x + currentRadius,
                        bottom = originPx.y + currentRadius,
                    ),
                )
                clipPath(clipPath) {
                    this@drawWithContent.drawContent()
                }
            }
    }

/**
 * Lightweight tap ripple detector (no heavy drag-trail wave spam).
 */
fun Modifier.interactiveGestureRipple(
    controller: GestureRippleController?,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || controller == null) return this
    return this.pointerInput(controller, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            controller.spawnTap(Offset(down.position.x / w, down.position.y / h))
        }
    }
}

/**
 * Featherlight AGSL optical refraction modifier (API 33+) applied to the blurred backdrop.
 */
fun Modifier.gestureRippleRefraction(
    enabled: Boolean,
    triggerKey: Int = 0,
    originNormalized: Offset = Offset(0.5f, 0.94f),
    controller: GestureRippleController? = null,
): Modifier =
    composed {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return@composed this
        }
        val activeController = controller ?: rememberGestureRippleController()
        LaunchedEffect(triggerKey, enabled, originNormalized) {
            if (controller == null && enabled && triggerKey > 0) {
                activeController.spawnLaunch(originNormalized)
            }
        }
        val shader = remember {
            runCatching { RuntimeShader(GESTURE_RIPPLE_SLICK_AGSL) }.getOrNull()
        } ?: return@composed this

        this.graphicsLayer {
            val now = activeController.frameClockMs
            val activeWaves = activeController.waves
            if (activeWaves.isEmpty() || size.width <= 0f || size.height <= 0f) {
                renderEffect = null
                return@graphicsLayer
            }
            var count = 0
            val wData = Array(2) { FloatArray(4) }
            val bData = Array(2) { FloatArray(2) }

            for (i in activeWaves.indices) {
                if (count >= 2) break
                val wave = activeWaves[i]
                if (now < wave.startTimeMs) continue
                val raw = ((now - wave.startTimeMs).toFloat() / wave.durationMs.toFloat()).coerceIn(0f, 1f)
                if (raw >= 1f) continue

                val eased = SlickWaveEasing.transform(raw)
                val originPx = Offset(
                    size.width * wave.originNormalized.x,
                    size.height * wave.originNormalized.y,
                )
                val maxDist = maxCornerDistance(originPx, size.width, size.height) * wave.maxRadiusFraction
                val radiusPx = (18.dp.toPx() + maxDist * eased)
                val fadeIn = (raw / 0.10f).coerceIn(0f, 1f)
                val fadeOut = (1f - raw) * (1f - raw)
                val strength = wave.intensity * fadeIn * fadeOut
                if (strength < 0.01f) continue

                val bandPx = wave.bandWidthDp.dp.toPx() * (1.05f - 0.35f * eased)
                wData[count][0] = originPx.x
                wData[count][1] = originPx.y
                wData[count][2] = radiusPx
                wData[count][3] = strength
                bData[count][0] = bandPx
                bData[count][1] = if (wave.isPrimaryLaunch) 1.0f else 0.45f
                count++
            }

            if (count == 0) {
                renderEffect = null
                return@graphicsLayer
            }

            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setIntUniform("uCount", count)
            shader.setFloatUniform("uW0", wData[0][0], wData[0][1], wData[0][2], wData[0][3])
            shader.setFloatUniform("uW1", wData[1][0], wData[1][1], wData[1][2], wData[1][3])
            shader.setFloatUniform("uB0", bData[0][0], bData[0][1])
            shader.setFloatUniform("uB1", bData[1][0], bData[1][1])
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    }

/**
 * Ultra-slick, GPU-lightweight luminous wave overlay:
 * - Compact edge bloom right at the opening point (power button / bottom / widget / icon)
 * - Clean liquid-light caustic ring with a crisp specular crest
 * - Zero Path allocations, zero polygon trig, zero sparkle clutter
 */
@Composable
fun GestureRippleWaveOverlay(
    enabled: Boolean,
    triggerKey: Int = 0,
    originNormalized: Offset = Offset(0.5f, 0.94f),
    controller: GestureRippleController? = null,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val activeController = controller ?: rememberGestureRippleController()
    LaunchedEffect(triggerKey, enabled, originNormalized) {
        if (controller == null && enabled && triggerKey > 0) {
            activeController.spawnLaunch(originNormalized)
        }
    }
    if (!activeController.hasActiveWaves) return

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val highlight = Color.White

    Canvas(modifier = modifier.fillMaxSize()) {
        val now = activeController.frameClockMs
        val activeWaves = activeController.waves
        if (activeWaves.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        for (i in activeWaves.indices) {
            val wave = activeWaves[i]
            if (now < wave.startTimeMs) continue
            val raw = ((now - wave.startTimeMs).toFloat() / wave.durationMs.toFloat()).coerceIn(0f, 1f)
            if (raw >= 1f) continue

            val p = SlickWaveEasing.transform(raw)
            val originPx = Offset(
                size.width * wave.originNormalized.x,
                size.height * wave.originNormalized.y,
            )
            val maxDist = maxCornerDistance(originPx, size.width, size.height) * wave.maxRadiusFraction
            val radius = (16.dp.toPx() + maxDist * p).coerceAtLeast(1f)
            val fadeIn = (raw / 0.08f).coerceIn(0f, 1f)
            val fadeOut = (1f - raw) * (1f - raw)
            val alpha = (fadeIn * fadeOut * wave.intensity).coerceIn(0f, 1f)
            if (alpha <= 0.005f) continue

            // 1. Crisp origin pulse bloom (highlights the exact power button / bottom / icon point)
            if (wave.isPrimaryLaunch && raw < 0.48f) {
                val bloomT = (raw / 0.48f).coerceIn(0f, 1f)
                val bloomAlpha = sin(bloomT * Math.PI).toFloat() * 0.34f * (1f - bloomT * 0.45f)
                val bloomRadius = (54.dp.toPx() + 92.dp.toPx() * bloomT).coerceAtLeast(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to highlight.copy(alpha = bloomAlpha * 0.85f),
                            0.35f to primary.copy(alpha = bloomAlpha * 0.72f),
                            0.72f to tertiary.copy(alpha = bloomAlpha * 0.24f),
                            1.0f to Color.Transparent,
                        ),
                        center = originPx,
                        radius = bloomRadius,
                    ),
                    radius = bloomRadius,
                    center = originPx,
                    blendMode = BlendMode.Screen,
                )
            }

            // 2. Tight translucent caustic ribbon riding the wave front
            val bandPx = (wave.bandWidthDp.dp.toPx() * (1.05f - 0.45f * p)).coerceAtLeast(8f)
            val outerEdge = (radius + bandPx * 0.55f).coerceAtLeast(2f)
            val innerStop = ((radius - bandPx * 1.15f) / outerEdge).coerceIn(0f, 0.94f)
            val peakStop = (radius / outerEdge).coerceIn(innerStop + 0.02f, 0.985f)

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        innerStop to Color.Transparent,
                        ((innerStop + peakStop) * 0.5f) to tertiary.copy(alpha = alpha * 0.14f),
                        peakStop to primary.copy(alpha = alpha * 0.30f),
                        1.0f to Color.Transparent,
                    ),
                    center = originPx,
                    radius = outerEdge,
                ),
                radius = outerEdge,
                center = originPx,
                blendMode = BlendMode.Screen,
            )

            // 3. Razor-clean specular hairline crest
            val strokePx = (if (wave.isPrimaryLaunch) 1.9f else 1.25f).dp.toPx() * (1f - 0.55f * p)
            drawCircle(
                color = highlight.copy(alpha = alpha * (if (wave.isPrimaryLaunch) 0.44f else 0.24f)),
                radius = radius,
                center = originPx,
                style = Stroke(width = strokePx.coerceAtLeast(0.75f)),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

private fun maxCornerDistance(
    origin: Offset,
    width: Float,
    height: Float,
): Float {
    val d0 = hypot(origin.x, origin.y)
    val d1 = hypot(width - origin.x, origin.y)
    val d2 = hypot(origin.x, height - origin.y)
    val d3 = hypot(width - origin.x, height - origin.y)
    return max(max(d0, d1), max(d2, d3))
}
