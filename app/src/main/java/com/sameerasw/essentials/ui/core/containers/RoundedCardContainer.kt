/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Core UI Components
 * File: RoundedCardContainer.kt
 * Description: Material 3 Expressive 24.dp rounded card container wrapper for grouping settings items.
 */

package com.sameerasw.essentials.ui.core.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RoundedCardContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = 1.dp,
    cornerRadius: Dp = 20.dp,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val resolvedContainer =
        if (containerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        } else {
            containerColor
        }
    val resolvedBorder =
        if (borderColor == Color.Unspecified) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        } else {
            borderColor
        }

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(resolvedContainer)
                .border(1.dp, resolvedBorder, shape),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
