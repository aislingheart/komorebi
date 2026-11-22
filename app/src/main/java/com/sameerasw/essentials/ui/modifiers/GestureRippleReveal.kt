/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Modifiers & Animations
 * File: GestureRippleReveal.kt
 * Description: Dynamic, interactive multi-wave liquid ripple & refraction animation for Komorebi
 * (API 33+ multi-wave AGSL fluid glass refraction + all-API organic luminous rings & light motes).
 */

package com.sameerasw.essentials.ui.modifiers

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.utils.DeviceUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val GESTURE_RIPPLE_LAUNCH_DURATION_MS = 1480L
private const val GESTURE_RIPPLE_ECHO_DURATION_MS = 1620L
private const val GESTURE_RIPPLE_ECHO_DELAY_MS = 175L
private const val GESTURE_RIPPLE_TAP_DURATION_MS = 1180L
private const val GESTURE_RIPPLE_DRAG_DURATION_MS = 860L
private const val GESTURE_RIPPLE_BURST_DURATION_MS = 960L
private const val MAX_ACTIVE_WAVES = 6
private const val DRAG_SPAWN_MIN_INTERVAL_MS = 72L
private const val DRAG_SPAWN_MIN_DISTANCE = 0.032f

// Slow, silky ease-out curve so the ripple breathes across the screen gracefully.
private val GestureRippleEasing = CubicBezierEasing(0.12f, 0.92f, 0.22f, 1.0f)

private const val GESTURE_RIPPLE_MULTI_AGSL = """
    uniform shader content;
    uniform float2 resolution;
    uniform float uTime;
    uniform float4 uWave0;
    uniform float4 uWave1;
    uniform float4 uWave2;
    uniform float4 uWave3;

    void accumWave(
        float2 fragCoord,
        float2 resolution,
        float maxR,
        float uTime,
        float4 wave,
        float phaseSeed,
        inout float2 totalDisp,
        inout float totalChroma,
        inout float totalHighlight
    ) {
        float p = wave.z;
        float intensity = wave.w;
        if (p <= 0.001 || p >= 0.999 || intensity <= 0.001) {
            return;
        }

        float2 center = wave.xy * resolution;
        float2 delta = fragCoord - center;
        float dist = length(delta);
        float ringWidth = mix(82.0, 215.0, p);
        float approxR = p * maxR;
        if (abs(dist - approxR) > (ringWidth * 2.6 + 190.0)) {
            return;
        }
        float2 dir = dist > 0.001 ? (delta / dist) : float2(0.0, -1.0);
        float angle = atan(dir.y, dir.x);

        // Gentle organic liquid undulation along the wavefront
        float wobble =
            sin(angle * 5.0 + uTime * 2.2 + phaseSeed) * 10.0 +
            cos(angle * 3.0 - uTime * 1.7 + phaseSeed * 1.6) * 7.0;
        float waveR = approxR + wobble * (1.0 - p * 0.45);
        float d1 = dist - waveR;
        float primaryEnv = exp(-(d1 * d1) / (2.0 * ringWidth * ringWidth));

        // Secondary trailing harmonic crest for fluid multi-ring interference
        float trailR = max(0.0, waveR - mix(78.0, 170.0, p));
        float d2 = dist - trailR;
        float trailWidth = ringWidth * 0.72;
        float trailEnv = exp(-(d2 * d2) / (2.0 * trailWidth * trailWidth)) * 0.48 * smoothstep(0.06, 0.22, p);

        float fadeIn = smoothstep(0.0, 0.08, p);
        float fadeOut = pow(1.0 - p, 1.45);
        float strength = (primaryEnv + trailEnv) * fadeIn * fadeOut * intensity;

        // Tangential swirl nuance makes intersecting waves feel playful and alive
        float2 tangent = float2(-dir.y, dir.x);
        float swirl = sin(angle * 4.0 + uTime * 2.8 + phaseSeed) * strength * 5.5;

        totalDisp += (-dir * (strength * 38.0)) + tangent * swirl;
        totalChroma += strength * 3.4;
        totalHighlight += strength * 0.11;
    }

    half4 main(float2 fragCoord) {
        float maxR = length(resolution) * 1.16;
        float2 totalDisp = float2(0.0);
        float totalChroma = 0.0;
        float totalHighlight = 0.0;

        accumWave(fragCoord, resolution, maxR, uTime, uWave0, 0.4, totalDisp, totalChroma, totalHighlight);
        accumWave(fragCoord, resolution, maxR, uTime, uWave1, 2.1, totalDisp, totalChroma, totalHighlight);
        accumWave(fragCoord, resolution, maxR, uTime, uWave2, 3.8, totalDisp, totalChroma, totalHighlight);
        accumWave(fragCoord, resolution, maxR, uTime, uWave3, 5.5, totalDisp, totalChroma, totalHighlight);

        if (length(totalDisp) < 0.01 && totalHighlight < 0.001) {
            return content.eval(fragCoord);
        }

        float2 dispDir = length(totalDisp) > 0.001 ? normalize(totalDisp) : float2(0.0, -1.0);
        float2 baseUv = fragCoord + totalDisp;
        float2 maxCoord = resolution - 1.0;

        half4 rSample = content.eval(clamp(baseUv + dispDir * totalChroma, float2(0.0), maxCoord));
        half4 gSample = content.eval(clamp(baseUv, float2(0.0), maxCoord));
        half4 bSample = content.eval(clamp(baseUv - dispDir * totalChroma, float2(0.0), maxCoord));

        half hl = half(min(totalHighlight, 0.22));
        return half4(
            clamp(rSample.r + hl * half(1.06), half(0.0), half(1.0)),
            clamp(gSample.g + hl, half(0.0), half(1.0)),
            clamp(bSample.b + hl * half(1.12), half(0.0), half(1.0)),
            gSample.a
        );
    }
"""

@Immutable
data class RippleWave(
    val id: Long,
    val originNormalized: Offset,
    val startTimeNanos: Long,
    val durationMs: Long,
    val intensity: Float,
    val radiusScale: Float,
    val colorPhase: Float,
    val sparkleCount: Int,
)

@Stable
class GestureRippleController {
    val waves = mutableStateListOf<RippleWave>()
    var frameTimeNanos by mutableLongStateOf(0L)
        internal set

    private var nextId = 1L
    private var lastDragSpawnMs = 0L
    private var lastDragOrigin: Offset? = null

    val hasActiveWaves: Boolean
        get() = waves.isNotEmpty()

    fun spawnLaunch(originNormalized: Offset = Offset(0.5f, 0.96f)) {
        val now = System.nanoTime()
        enqueueWave(
            RippleWave(
                id = nextId++,
                originNormalized = originNormalized,
                startTimeNanos = now,
                durationMs = GESTURE_RIPPLE_LAUNCH_DURATION_MS,
                intensity = 1.0f,
                radiusScale = 1.14f,
                colorPhase = (nextId % 5) * 0.2f,
                sparkleCount = 14,
            ),
        )
        // Lush staggered harmonic echo right behind the opening wave
        enqueueWave(
            RippleWave(
                id = nextId++,
                originNormalized = originNormalized,
                startTimeNanos = now + GESTURE_RIPPLE_ECHO_DELAY_MS * 1_000_000L,
                durationMs = GESTURE_RIPPLE_ECHO_DURATION_MS,
                intensity = 0.62f,
                radiusScale = 1.02f,
                colorPhase = ((nextId + 2) % 5) * 0.2f,
                sparkleCount = 8,
            ),
        )
    }

    fun spawnTap(
        originNormalized: Offset,
        intensity: Float = 0.82f,
    ) {
        val now = System.nanoTime()
        lastDragOrigin = originNormalized
        lastDragSpawnMs = now / 1_000_000L
        enqueueWave(
            RippleWave(
                id = nextId++,
                originNormalized = Offset(
                    originNormalized.x.coerceIn(0f, 1f),
                    originNormalized.y.coerceIn(0f, 1f),
                ),
                startTimeNanos = now,
                durationMs = GESTURE_RIPPLE_TAP_DURATION_MS,
                intensity = intensity,
                radiusScale = 0.88f,
                colorPhase = (nextId * 0.31f) % 1f,
                sparkleCount = 10,
            ),
        )
    }

    fun spawnDrag(originNormalized: Offset) {
        val clamped = Offset(
            originNormalized.x.coerceIn(0f, 1f),
            originNormalized.y.coerceIn(0f, 1f),
        )
        val nowNanos = System.nanoTime()
        val nowMs = nowNanos / 1_000_000L
        if (nowMs - lastDragSpawnMs < DRAG_SPAWN_MIN_INTERVAL_MS) return
        val prev = lastDragOrigin
        if (prev != null) {
            val dist = hypot(
                (clamped.x - prev.x).toDouble(),
                (clamped.y - prev.y).toDouble(),
            ).toFloat()
            if (dist < DRAG_SPAWN_MIN_DISTANCE) return
        }
        lastDragOrigin = clamped
        lastDragSpawnMs = nowMs
        enqueueWave(
            RippleWave(
                id = nextId++,
                originNormalized = clamped,
                startTimeNanos = nowNanos,
                durationMs = GESTURE_RIPPLE_DRAG_DURATION_MS,
                intensity = 0.56f,
                radiusScale = 0.54f,
                colorPhase = (nextId * 0.23f) % 1f,
                sparkleCount = 6,
            ),
        )
    }

    fun spawnBurst(originNormalized: Offset = Offset(0.5f, 0.55f)) {
        val now = System.nanoTime()
        enqueueWave(
            RippleWave(
                id = nextId++,
                originNormalized = originNormalized,
                startTimeNanos = now,
                durationMs = GESTURE_RIPPLE_BURST_DURATION_MS,
                intensity = 0.78f,
                radiusScale = 0.76f,
                colorPhase = (nextId * 0.37f) % 1f,
                sparkleCount = 12,
            ),
        )
    }

    internal fun pruneExpired(nowNanos: Long) {
        var i = waves.lastIndex
        while (i >= 0) {
            val wave = waves[i]
            val endNanos = wave.startTimeNanos + wave.durationMs * 1_000_000L
            if (nowNanos >= endNanos) {
                waves.removeAt(i)
            }
            i--
        }
    }

    internal fun rawProgress(wave: RippleWave, nowNanos: Long): Float {
        val elapsedNanos = nowNanos - wave.startTimeNanos
        if (elapsedNanos <= 0L) return 0f
        val totalNanos = wave.durationMs * 1_000_000L
        return (elapsedNanos.toFloat() / totalNanos.toFloat()).coerceIn(0f, 1f)
    }

    private val wakeChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    internal suspend fun awaitWave() {
        wakeChannel.receive()
    }

    private fun enqueueWave(wave: RippleWave) {
        while (waves.size >= MAX_ACTIVE_WAVES) {
            waves.removeAt(0)
        }
        waves.add(wave)
        if (frameTimeNanos == 0L) {
            frameTimeNanos = System.nanoTime()
        }
        wakeChannel.trySend(Unit)
    }
}

val LocalGestureRippleController = compositionLocalOf<GestureRippleController?> { null }

@Composable
fun rememberGestureRippleController(): GestureRippleController {
    val controller = remember { GestureRippleController() }
    LaunchedEffect(controller) {
        while (true) {
            if (controller.waves.isEmpty()) {
                controller.awaitWave()
            }
            while (controller.waves.isNotEmpty()) {
                withFrameNanos {
                    val wallNow = System.nanoTime()
                    controller.frameTimeNanos = wallNow
                    controller.pruneExpired(wallNow)
                }
            }
        }
    }
    return controller
}

fun isGestureRippleEnabled(context: Context): Boolean {
    val repo = SettingsRepository(context.applicationContext)
    return repo.getBoolean(SettingsRepository.KEY_USE_RIPPLE, true) &&
        !DeviceUtils.isPowerSaveMode(context)
}

/**
 * Observes taps and finger drags on [PointerEventPass.Initial] without consuming pointer events,
 * spawning playful interactive ripples right beneath the user's touch.
 */
fun Modifier.interactiveGestureRipple(
    controller: GestureRippleController?,
    enabled: Boolean,
): Modifier {
    if (!enabled || controller == null) return this
    return this.pointerInput(controller, enabled) {
        awaitPointerEventScope {
            var isPressed = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w <= 1f || h <= 1f) continue

                val firstChange = event.changes.firstOrNull() ?: continue
                val norm = Offset(
                    x = (firstChange.position.x / w).coerceIn(0f, 1f),
                    y = (firstChange.position.y / h).coerceIn(0f, 1f),
                )

                when (event.type) {
                    PointerEventType.Press -> {
                        isPressed = true
                        controller.spawnTap(norm)
                    }
                    PointerEventType.Move -> {
                        if (isPressed || event.changes.any { it.pressed }) {
                            controller.spawnDrag(norm)
                        }
                    }
                    PointerEventType.Release -> {
                        isPressed = event.changes.any { it.pressed }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Applies multi-wave AGSL fluid glass refraction across the backdrop (API 33+).
 * Runs entirely in the graphicsLayer phase (zero recompositions) and clears [RenderEffect]
 * automatically as soon as all waves settle so idle GPU cost is zero.
 */
fun Modifier.gestureRippleRefraction(
    triggerKey: Int = 0,
    enabled: Boolean,
    originNormalized: Offset = Offset(0.5f, 0.96f),
    controller: GestureRippleController? = null,
): Modifier = composed {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this
    }

    val activeController = controller ?: rememberGestureRippleController()
    LaunchedEffect(triggerKey) {
        if (triggerKey > 0 && controller == null) {
            activeController.spawnLaunch(originNormalized)
        }
    }

    val shader = remember { runCatching { RuntimeShader(GESTURE_RIPPLE_MULTI_AGSL) }.getOrNull() }
        ?: return@composed this
    val uniformValues = remember { FloatArray(16) }

    this.graphicsLayer {
        val waves = activeController.waves
        if (waves.isEmpty()) {
            renderEffect = null
            return@graphicsLayer
        }
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) {
            renderEffect = null
            return@graphicsLayer
        }

        val nowNanos = activeController.frameTimeNanos.takeIf { it > 0L } ?: System.nanoTime()
        uniformValues.fill(0f)

        val startIdx = (waves.size - 4).coerceAtLeast(0)
        var slot = 0
        var anyVisible = false
        for (i in startIdx until waves.size) {
            val wave = waves[i]
            val raw = activeController.rawProgress(wave, nowNanos)
            if (raw in 0.001f..0.999f) {
                anyVisible = true
                val eased = GestureRippleEasing.transform(raw)
                val base = slot * 4
                uniformValues[base] = wave.originNormalized.x
                uniformValues[base + 1] = wave.originNormalized.y
                uniformValues[base + 2] = eased * wave.radiusScale
                uniformValues[base + 3] = wave.intensity
                slot++
            }
        }

        if (!anyVisible) {
            renderEffect = null
            return@graphicsLayer
        }

        val timeSeconds = ((nowNanos / 1_000_000L) % 100_000L) / 1000f
        shader.setFloatUniform("resolution", w, h)
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform(
            "uWave0",
            uniformValues[0],
            uniformValues[1],
            uniformValues[2],
            uniformValues[3],
        )
        shader.setFloatUniform(
            "uWave1",
            uniformValues[4],
            uniformValues[5],
            uniformValues[6],
            uniformValues[7],
        )
        shader.setFloatUniform(
            "uWave2",
            uniformValues[8],
            uniformValues[9],
            uniformValues[10],
            uniformValues[11],
        )
        shader.setFloatUniform(
            "uWave3",
            uniformValues[12],
            uniformValues[13],
            uniformValues[14],
            uniformValues[15],
        )
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

/**
 * Dynamic, luminous multi-wave ripple overlay with organic liquid wavefronts, iridescent
 * Material You color blending, and twinkling Komorebi light motes.
 * Runs purely in the draw phase (zero recompositions) and works across all API levels
 * with zero touch interception.
 */
@Composable
fun GestureRippleWaveOverlay(
    triggerKey: Int = 0,
    enabled: Boolean,
    originNormalized: Offset = Offset(0.5f, 0.96f),
    controller: GestureRippleController? = null,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val activeController = controller ?: rememberGestureRippleController()
    LaunchedEffect(triggerKey) {
        if (triggerKey > 0 && controller == null) {
            activeController.spawnLaunch(originNormalized)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val highlightColor = Color.White
    val organicPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val waves = activeController.waves
        if (waves.isEmpty()) return@Canvas

        val nowNanos = activeController.frameTimeNanos.takeIf { it > 0L } ?: System.nanoTime()
        val timeSec = ((nowNanos / 1_000_000L) % 100_000L) / 1000f
        val diagonal = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()

        for (idx in 0 until waves.size) {
            val wave = waves[idx]
            val raw = activeController.rawProgress(wave, nowNanos)
            if (raw <= 0.001f || raw >= 0.999f) continue

            val p = GestureRippleEasing.transform(raw)
            val center = Offset(
                size.width * wave.originNormalized.x,
                size.height * wave.originNormalized.y,
            )
            val maxRadius = diagonal * wave.radiusScale
            val radius = max(14f, p * maxRadius)

            val fadeIn = (raw / 0.09f).coerceIn(0f, 1f)
            val fadeOut = (1f - raw).pow(1.48f)
            val alpha = (fadeIn * fadeOut * wave.intensity).coerceIn(0f, 1f)
            if (alpha <= 0.003f) continue

            // Iridescent tint shifting smoothly across Material You palette per wave
            val leadColor = when {
                wave.colorPhase < 0.33f -> lerp(primaryColor, tertiaryColor, wave.colorPhase * 3f)
                wave.colorPhase < 0.66f -> lerp(tertiaryColor, secondaryColor, (wave.colorPhase - 0.33f) * 3f)
                else -> lerp(secondaryColor, primaryColor, (wave.colorPhase - 0.66f) * 3f)
            }
            val echoColor = lerp(tertiaryColor, primaryColor, (wave.colorPhase + 0.45f) % 1f)

            // 1. Soft luminous atmospheric wash band sweeping outward
            val bandWidth = (150f + 120f * p) * wave.radiusScale.coerceIn(0.6f, 1.15f)
            val innerStop = ((radius - bandWidth) / radius).coerceIn(0f, 0.90f)
            val peakStop = ((radius - bandWidth * 0.24f) / radius).coerceIn(innerStop + 0.02f, 0.98f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        innerStop to Color.Transparent,
                        peakStop to leadColor.copy(alpha = 0.26f * alpha),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )

            // 2. Organic gently undulating primary liquid ring
            val waveSeed = wave.id * 1.37f
            val wobbleAmp = (7.5f * (1f - p * 0.45f) * wave.intensity).coerceAtLeast(1.5f)
            buildOrganicRingPath(
                path = organicPath,
                center = center,
                baseRadius = radius,
                wobbleAmp = wobbleAmp,
                phase = timeSec * 2.1f + waveSeed,
                lobes = 5,
            )
            drawPath(
                path = organicPath,
                color = leadColor.copy(alpha = 0.54f * alpha),
                style = Stroke(
                    width = (3.4f + 8.5f * (1f - p)).coerceAtLeast(2.2f),
                    cap = StrokeCap.Round,
                ),
            )

            // 3. Crisp specular glass shimmer right inside the leading crest
            buildOrganicRingPath(
                path = organicPath,
                center = center,
                baseRadius = max(6f, radius - 5f),
                wobbleAmp = wobbleAmp * 0.82f,
                phase = timeSec * 2.1f + waveSeed + 0.18f,
                lobes = 5,
            )
            drawPath(
                path = organicPath,
                color = highlightColor.copy(alpha = 0.34f * alpha),
                style = Stroke(width = 2.2f, cap = StrokeCap.Round),
            )

            // 4. Trailing harmonic echo ring
            val secondaryP = ((p - 0.11f) / 0.89f).coerceIn(0f, 1f)
            if (secondaryP > 0.004f) {
                val secRadius = max(10f, secondaryP * maxRadius * 0.88f)
                val secAlpha = (alpha * 0.62f * (1f - secondaryP * 0.25f)).coerceIn(0f, 1f)
                buildOrganicRingPath(
                    path = organicPath,
                    center = center,
                    baseRadius = secRadius,
                    wobbleAmp = wobbleAmp * 0.65f,
                    phase = -timeSec * 1.7f + waveSeed,
                    lobes = 4,
                )
                drawPath(
                    path = organicPath,
                    color = echoColor.copy(alpha = 0.42f * secAlpha),
                    style = Stroke(
                        width = (2.4f + 4.5f * (1f - secondaryP)).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            // 5. Playful Komorebi light motes twinkling along the expanding wave
            if (wave.sparkleCount > 0 && raw in 0.04f..0.94f) {
                val moteFade = ((raw - 0.04f) / 0.14f).coerceIn(0f, 1f) * (1f - raw).pow(1.25f)
                for (i in 0 until wave.sparkleCount) {
                    val idxFloat = i.toFloat()
                    val baseAngle = (2.0 * PI * i / wave.sparkleCount) + (waveSeed * 0.7f)
                    val angularDrift = sin(timeSec * 1.6f + idxFloat * 2.1f + waveSeed) * 0.08f
                    val angle = baseAngle + angularDrift
                    val radialOffset = ((i % 3) - 1) * 18f * (0.4f + p * 0.8f)
                    val r = (radius + radialOffset).coerceAtLeast(8f)

                    val px = center.x + (cos(angle) * r).toFloat()
                    val py = center.y + (sin(angle) * r).toFloat()
                    if (px < -24f || px > size.width + 24f || py < -24f || py > size.height + 24f) {
                        continue
                    }

                    val twinkle = 0.45f + 0.55f * sin(timeSec * 6.5f + idxFloat * 1.9f + waveSeed).coerceAtLeast(-1f)
                    val moteAlpha = (moteFade * twinkle * wave.intensity * 0.75f).coerceIn(0f, 1f)
                    val moteRadius = (2.8f + (i % 3) * 1.6f) * (1f - raw * 0.35f)
                    val moteTint = if (i % 2 == 0) highlightColor else leadColor

                    // Soft halo
                    drawCircle(
                        color = moteTint.copy(alpha = moteAlpha * 0.28f),
                        radius = moteRadius * 2.6f,
                        center = Offset(px, py),
                    )
                    // Bright core
                    drawCircle(
                        color = highlightColor.copy(alpha = moteAlpha * 0.88f),
                        radius = moteRadius,
                        center = Offset(px, py),
                    )
                }
            }
        }
    }
}

private fun buildOrganicRingPath(
    path: Path,
    center: Offset,
    baseRadius: Float,
    wobbleAmp: Float,
    phase: Float,
    lobes: Int,
    segments: Int = 56,
) {
    path.reset()
    val twoPi = (2.0 * PI).toFloat()
    for (i in 0..segments) {
        val theta = twoPi * i / segments
        val wobble =
            sin(theta * lobes + phase) * wobbleAmp +
                cos(theta * (lobes - 2) - phase * 0.8f) * (wobbleAmp * 0.55f)
        val r = max(4f, baseRadius + wobble)
        val x = center.x + cos(theta) * r
        val y = center.y + sin(theta) * r
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
}
