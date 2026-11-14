/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Core Components
 * File: UnsupportedFeaturesConfirmationSheet.kt
 * Description: Reusable core UI component for UnsupportedFeaturesConfirmationSheet.kt.
 */

package com.sameerasw.essentials.ui.core.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.translation.TranslatableText
import com.sameerasw.essentials.ui.core.containers.RoundedCardContainer
import com.sameerasw.essentials.utils.DeviceUtils

@Composable
fun UnsupportedFeaturesConfirmationSheet(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    featureTitleResIds: List<Int>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    EssentialsBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TranslatableText(
                stringResId = R.string.unsupported_sheet_title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TranslatableText(
                stringResId = R.string.unsupported_sheet_body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RoundedCardContainer(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    (capabilities + if (DeviceUtils.isTorchRestrictedDevice()) torchCapabilities else emptyList()).forEach { (titleRes, descriptionRes) ->
                        Column {
                            TranslatableText(
                                stringResId = titleRes,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TranslatableText(
                                stringResId = descriptionRes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    featureTitleResIds.forEach { titleRes ->
                        TranslatableText(
                            stringResId = titleRes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            ) {
                Text(stringResource(R.string.unsupported_hold_to_enable))
            }
            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TranslatableText(R.string.action_cancel)
            }
        }
    }
}

private val capabilities =
    listOf(
        R.string.unsupported_cap_hidden_title to R.string.unsupported_cap_hidden_desc,
        R.string.unsupported_cap_tiles_title to R.string.unsupported_cap_tiles_desc,
        R.string.unsupported_cap_search_title to R.string.unsupported_cap_search_desc,
        R.string.unsupported_cap_freeze_title to R.string.unsupported_cap_freeze_desc,
        R.string.unsupported_cap_duo_title to R.string.unsupported_cap_duo_desc,
    )

private val torchCapabilities =
    listOf(
        R.string.unsupported_cap_flashlight_title to R.string.unsupported_cap_flashlight_desc,
    )
