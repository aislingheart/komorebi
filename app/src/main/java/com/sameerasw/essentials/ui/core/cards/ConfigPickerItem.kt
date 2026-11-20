/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Core Components
 * File: ConfigPickerItem.kt
 * Description: Reusable core UI component for ConfigPickerItem.kt.
 */

package com.sameerasw.essentials.ui.core.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.ui.components.menus.LocalDropdownMenuDismiss
import com.sameerasw.essentials.ui.components.menus.SegmentedDropdownMenu
import com.sameerasw.essentials.ui.core.komorebi.KomorebiIconBadge
import com.sameerasw.essentials.utils.HapticUtil

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigPickerItem(
    title: String,
    selectedValue: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconRes: Int? = null,
    isEnabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    options: List<Any> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = LocalView.current

    var isMenuExpanded by remember { mutableStateOf(false) }

    ListItem(
        onClick = {
            if (isEnabled) {
                HapticUtil.performVirtualKeyHaptic(view)
                isMenuExpanded = true
            } else if (onDisabledClick != null) {
                HapticUtil.performVirtualKeyHaptic(view)
                onDisabledClick()
            }
        },
        enabled = isEnabled,
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent =
            if (iconRes != null && iconRes != 0) {
                {
                    KomorebiIconBadge(
                        iconRes = iconRes,
                        contentDescription = title,
                        active = isEnabled,
                    )
                }
            } else {
                null
            },
        contentPadding =
            PaddingValues(
                horizontal = 16.dp,
                vertical = 14.dp,
            ),
        supportingContent =
            if (description != null) {
                {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        trailingContent = {
            Box {
                Surface(
                    onClick = {
                        if (isEnabled) {
                            HapticUtil.performVirtualKeyHaptic(view)
                            isMenuExpanded = true
                        }
                    },
                    enabled = isEnabled,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = selectedValue,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                SegmentedDropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                ) {
                    CompositionLocalProvider(
                        LocalDropdownMenuDismiss provides { isMenuExpanded = false },
                    ) {
                        content()
                    }
                }
            }
        },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
