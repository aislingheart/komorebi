/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Core Components
 * File: FeatureCustomHelp.kt
 * Description: Registry of feature specific help sheets and open-sheet tracking for shake to help.
 */

package com.sameerasw.essentials.ui.core.sheets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

class FeatureCustomHelp(
    @StringRes val label: Int,
    @DrawableRes val iconRes: Int,
    val sheet: @Composable (onDismissRequest: () -> Unit) -> Unit,
) {
    companion object {
        private val entries = emptyMap<String, FeatureCustomHelp>()

        fun forFeature(featureId: String): FeatureCustomHelp? = entries[featureId]
    }
}

object HelpSheetTracker {
    private var openCount = 0

    val isAnyOpen: Boolean get() = openCount > 0

    @Composable
    fun TrackOpen() {
        DisposableEffect(Unit) {
            openCount++
            onDispose { openCount-- }
        }
    }
}
