/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search
 * File: InteractiveSettingControl.kt
 * Description: Unified interactive setting controls (toggle, choice, slider) and Material 3
 * inline search result card for toggling/changing internal and system settings directly in search.
 */

package com.sameerasw.essentials.search

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.ui.modifiers.LocalGestureRippleController
import com.sameerasw.essentials.utils.HapticUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private val cachedToggleStates = ConcurrentHashMap<String, Boolean>()
private val cachedChoiceStates = ConcurrentHashMap<String, String>()
private val cachedSliderStates = ConcurrentHashMap<String, Float>()

data class SettingChoiceOption(
    val id: String,
    val label: String,
)

data class SettingActionOutcome(
    val applied: Boolean,
    val message: String? = null,
    val requiresFallbackIntent: Boolean = false,
)

sealed interface InteractiveSettingControl {
    val fallbackIntent: ((Context) -> Intent?)?
    val runOffMainThread: Boolean

    data class Toggle(
        val readState: (Context) -> Boolean,
        val writeState: (Context, Boolean) -> SettingActionOutcome,
        override val fallbackIntent: ((Context) -> Intent?)? = null,
        override val runOffMainThread: Boolean = false,
    ) : InteractiveSettingControl

    data class Choice(
        val options: List<SettingChoiceOption>,
        val readSelectedId: (Context) -> String,
        val writeSelectedId: (Context, String) -> SettingActionOutcome,
        override val fallbackIntent: ((Context) -> Intent?)? = null,
        override val runOffMainThread: Boolean = false,
    ) : InteractiveSettingControl

    data class Slider(
        val readPercent: (Context) -> Int,
        val writePercent: (Context, Int) -> SettingActionOutcome,
        val stepPercent: Int = 5,
        override val fallbackIntent: ((Context) -> Intent?)? = null,
        override val runOffMainThread: Boolean = false,
    ) : InteractiveSettingControl
}

/**
 * Triggers the primary action for an [InteractiveSettingControl] (e.g. pressing Enter on the
 * top-highlighted search result). Returns true if the control handled the action inline.
 */
fun triggerInteractiveSettingPrimaryAction(
    context: Context,
    control: InteractiveSettingControl,
    onFallbackOpen: (Intent) -> Unit,
    onUpdated: () -> Unit = {},
) {
    when (control) {
        is InteractiveSettingControl.Toggle -> {
            val current = runCatching { control.readState(context) }.getOrDefault(false)
            val outcome = runCatching { control.writeState(context, !current) }
                .getOrElse { SettingActionOutcome(false, it.message, requiresFallbackIntent = true) }
            if (outcome.requiresFallbackIntent) {
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                control.fallbackIntent?.invoke(context)?.let(onFallbackOpen)
            } else {
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                onUpdated()
            }
        }
        is InteractiveSettingControl.Choice -> {
            if (control.options.isEmpty()) return
            val current = runCatching { control.readSelectedId(context) }.getOrDefault(control.options.first().id)
            val currentIdx = control.options.indexOfFirst { it.id == current }.coerceAtLeast(0)
            val next = control.options[(currentIdx + 1) % control.options.size]
            val outcome = runCatching { control.writeSelectedId(context, next.id) }
                .getOrElse { SettingActionOutcome(false, it.message, requiresFallbackIntent = true) }
            if (outcome.requiresFallbackIntent) {
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                control.fallbackIntent?.invoke(context)?.let(onFallbackOpen)
            } else {
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                onUpdated()
            }
        }
        is InteractiveSettingControl.Slider -> {
            control.fallbackIntent?.invoke(context)?.let(onFallbackOpen)
        }
    }
}

@Composable
fun InteractiveSearchSettingCard(
    id: String,
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    control: InteractiveSettingControl,
    containerColor: Color,
    refreshNonce: Int,
    onSettingChanged: () -> Unit,
    onOpenFallback: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val rippleController = LocalGestureRippleController.current

    var localRefresh by remember(id) { mutableIntStateOf(0) }
    val stateKey = refreshNonce + localRefresh

    var toggleChecked by remember(id) {
        mutableStateOf(
            if (control is InteractiveSettingControl.Toggle) {
                if (control.runOffMainThread) {
                    cachedToggleStates[id] ?: false
                } else {
                    runCatching { control.readState(context) }.getOrDefault(false).also {
                        cachedToggleStates[id] = it
                    }
                }
            } else {
                false
            },
        )
    }
    var selectedChoiceId by remember(id) {
        mutableStateOf(
            if (control is InteractiveSettingControl.Choice) {
                if (control.runOffMainThread) {
                    cachedChoiceStates[id] ?: control.options.firstOrNull()?.id.orEmpty()
                } else {
                    runCatching { control.readSelectedId(context) }
                        .getOrDefault(control.options.firstOrNull()?.id.orEmpty())
                        .also { cachedChoiceStates[id] = it }
                }
            } else {
                ""
            },
        )
    }
    var sliderPercent by remember(id) {
        mutableFloatStateOf(
            if (control is InteractiveSettingControl.Slider) {
                if (control.runOffMainThread) {
                    cachedSliderStates[id] ?: 50f
                } else {
                    runCatching { control.readPercent(context) }
                        .getOrDefault(50)
                        .coerceIn(0, 100)
                        .toFloat()
                        .also { cachedSliderStates[id] = it }
                }
            } else {
                50f
            },
        )
    }

    LaunchedEffect(id, stateKey) {
        when (control) {
            is InteractiveSettingControl.Toggle -> {
                val fresh = withContext(if (control.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                    runCatching { control.readState(context) }.getOrDefault(toggleChecked)
                }
                cachedToggleStates[id] = fresh
                if (fresh != toggleChecked) {
                    toggleChecked = fresh
                }
            }
            is InteractiveSettingControl.Choice -> {
                val fresh = withContext(if (control.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                    runCatching { control.readSelectedId(context) }.getOrDefault(selectedChoiceId)
                }
                cachedChoiceStates[id] = fresh
                if (fresh != selectedChoiceId) {
                    selectedChoiceId = fresh
                }
            }
            is InteractiveSettingControl.Slider -> {
                val fresh = withContext(if (control.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                    runCatching { control.readPercent(context) }.getOrDefault(sliderPercent.roundToInt()).coerceIn(0, 100).toFloat()
                }
                cachedSliderStates[id] = fresh
                if (fresh != sliderPercent) {
                    sliderPercent = fresh
                }
            }
        }
    }

    fun applyToggle(target: Boolean) {
        val toggle = control as? InteractiveSettingControl.Toggle ?: return
        HapticUtil.performVirtualKeyHaptic(view)
        rippleController?.spawnBurst()
        val previous = toggleChecked
        toggleChecked = target
        cachedToggleStates[id] = target
        scope.launch {
            val outcome = withContext(if (toggle.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                runCatching { toggle.writeState(context, target) }
                    .getOrElse { SettingActionOutcome(false, it.message, requiresFallbackIntent = true) }
            }
            if (outcome.requiresFallbackIntent) {
                toggleChecked = previous
                cachedToggleStates[id] = previous
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                toggle.fallbackIntent?.invoke(context)?.let(onOpenFallback)
            } else if (!outcome.applied) {
                toggleChecked = previous
                cachedToggleStates[id] = previous
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            } else {
                val actual = withContext(if (toggle.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                    runCatching { toggle.readState(context) }.getOrDefault(target)
                }
                toggleChecked = actual
                cachedToggleStates[id] = actual
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                localRefresh++
                onSettingChanged()
            }
        }
    }

    fun applyChoice(optionId: String) {
        val choice = control as? InteractiveSettingControl.Choice ?: return
        if (optionId == selectedChoiceId) return
        HapticUtil.performVirtualKeyHaptic(view)
        rippleController?.spawnBurst()
        val previous = selectedChoiceId
        selectedChoiceId = optionId
        cachedChoiceStates[id] = optionId
        scope.launch {
            val outcome = withContext(if (choice.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                runCatching { choice.writeSelectedId(context, optionId) }
                    .getOrElse { SettingActionOutcome(false, it.message, requiresFallbackIntent = true) }
            }
            if (outcome.requiresFallbackIntent) {
                selectedChoiceId = previous
                cachedChoiceStates[id] = previous
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                choice.fallbackIntent?.invoke(context)?.let(onOpenFallback)
            } else if (!outcome.applied) {
                selectedChoiceId = previous
                cachedChoiceStates[id] = previous
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            } else {
                val actual = withContext(if (choice.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                    runCatching { choice.readSelectedId(context) }.getOrDefault(optionId)
                }
                selectedChoiceId = actual
                cachedChoiceStates[id] = actual
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                localRefresh++
                onSettingChanged()
            }
        }
    }

    fun applySlider(percent: Int) {
        val slider = control as? InteractiveSettingControl.Slider ?: return
        cachedSliderStates[id] = percent.coerceIn(0, 100).toFloat()
        scope.launch {
            val outcome = withContext(if (slider.runOffMainThread) Dispatchers.IO else Dispatchers.Main.immediate) {
                runCatching { slider.writePercent(context, percent) }
                    .getOrElse { SettingActionOutcome(false, it.message, requiresFallbackIntent = true) }
            }
            if (outcome.requiresFallbackIntent) {
                outcome.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                slider.fallbackIntent?.invoke(context)?.let(onOpenFallback)
            } else if (outcome.applied) {
                onSettingChanged()
            }
        }
    }

    val isActiveVisual = when (control) {
        is InteractiveSettingControl.Toggle -> toggleChecked
        is InteractiveSettingControl.Choice -> true
        is InteractiveSettingControl.Slider -> sliderPercent > 0f
    }

    val iconBg = if (isActiveVisual) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = if (isActiveVisual) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (control) {
        is InteractiveSettingControl.Toggle -> if (toggleChecked) "On" else "Off"
        is InteractiveSettingControl.Choice ->
            control.options.firstOrNull { it.id == selectedChoiceId }?.label ?: ""
        is InteractiveSettingControl.Slider -> "${sliderPercent.roundToInt()}%"
    }
    val resolvedSubtitle = if (statusText.isNotEmpty() && subtitle.isNotEmpty()) {
        "$statusText • $subtitle"
    } else {
        subtitle.ifEmpty { statusText }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when (control) {
                    is InteractiveSettingControl.Toggle -> Modifier.clickable { applyToggle(!toggleChecked) }
                    else -> Modifier
                },
            ),
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (resolvedSubtitle.isNotBlank()) {
                        Text(
                            text = resolvedSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val hasFallback = control.fallbackIntent != null
                when (control) {
                    is InteractiveSettingControl.Toggle -> {
                        if (hasFallback) {
                            IconButton(
                                onClick = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    control.fallbackIntent?.invoke(context)?.let(onOpenFallback)
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_arrows_outward_24),
                                    contentDescription = "Open settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Switch(
                            checked = toggleChecked,
                            onCheckedChange = { applyToggle(it) },
                        )
                    }

                    is InteractiveSettingControl.Choice,
                    is InteractiveSettingControl.Slider,
                    -> {
                        if (hasFallback) {
                            IconButton(
                                onClick = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    control.fallbackIntent?.invoke(context)?.let(onOpenFallback)
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_arrows_outward_24),
                                    contentDescription = "Open settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            when (control) {
                is InteractiveSettingControl.Choice -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        control.options.forEach { option ->
                            val selected = option.id == selectedChoiceId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { applyChoice(option.id) },
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                is InteractiveSettingControl.Slider -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = sliderPercent,
                        onValueChange = { raw ->
                            val snapped = ((raw / control.stepPercent).roundToInt() * control.stepPercent)
                                .coerceIn(0, 100)
                            if (snapped != sliderPercent.roundToInt()) {
                                sliderPercent = snapped.toFloat()
                                applySlider(snapped)
                            } else {
                                sliderPercent = raw
                            }
                        },
                        onValueChangeFinished = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            applySlider(sliderPercent.roundToInt().coerceIn(0, 100))
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is InteractiveSettingControl.Toggle -> Unit
            }
        }
    }
}
