/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search UI Components
 * File: LocalDecisionCards.kt
 * Description: Modular Material 3 Expressive UI cards for search-miss local decision matches,
 *              in-app local model selector dropdown, on-device model download prompt,
 *              and explicit Approve/Cancel confirmation cards for sensitive local tools.
 */

package com.sameerasw.essentials.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.assistant.LocalIntentDecision
import com.sameerasw.essentials.assistant.decision.LocalDecisionModel
import com.sameerasw.essentials.assistant.decision.ModelDownloadState

@Composable
fun LocalDecisionModelHeader(
    activeModel: LocalDecisionModel,
    isEvaluating: Boolean,
    latencyMs: Long,
    downloadStates: Map<LocalDecisionModel, ModelDownloadState>,
    onSelectModel: (LocalDecisionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Local Decision",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isEvaluating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (latencyMs > 0L) {
                Text(
                    text = "${latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }

        Box {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = activeModel.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_keyboard_arrow_down_24),
                        contentDescription = "Choose local decision model",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                LocalDecisionModel.entries.forEach { model ->
                    val dlState = downloadStates[model]
                    val badge = when {
                        model == activeModel -> "Active"
                        dlState is ModelDownloadState.Ready -> "Downloaded"
                        dlState is ModelDownloadState.Downloading -> "${(dlState.progress * 100).toInt()}%"
                        else -> model.sizeBadge
                    }
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = model.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (model == activeModel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (model == activeModel) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = "$badge · ${model.description}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectModel(model)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun LocalDecisionDownloadPromptCard(
    model: LocalDecisionModel,
    downloadState: ModelDownloadState,
    containerColor: Color,
    onDownloadClick: () -> Unit,
    onUseFastLocalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${model.label} runs 100% on-device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (downloadState) {
                    is ModelDownloadState.Downloading ->
                        "Downloading local weights… ${(downloadState.progress * 100).toInt()}%"
                    is ModelDownloadState.Error ->
                        "Download error: ${downloadState.message}"
                    else ->
                        "One-time ${model.sizeBadge} download to app-private storage (Apache-2.0, zero cloud calls)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadState is ModelDownloadState.Downloading) {
                LinearProgressIndicator(
                    progress = { downloadState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onUseFastLocalClick) {
                        Text("Use Fast Local")
                    }
                    Button(onClick = onDownloadClick) {
                        Text("Download (${model.sizeBadge})")
                    }
                }
            }
        }
    }
}

@Composable
fun LocalDecisionMatchList(
    decisions: List<LocalIntentDecision>,
    containerColor: Color,
    highlightColor: Color,
    highlightTopMatch: Boolean,
    onRunDecision: (LocalIntentDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (decisions.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        decisions.forEachIndexed { index, decision ->
            val bg = if (index == 0 && highlightTopMatch) highlightColor else containerColor
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = bg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRunDecision(decision) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (decision.requiresConfirmation) {
                                R.drawable.rounded_shield_lock_24
                            } else {
                                R.drawable.rounded_bolt_24
                            },
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = decision.tool.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = decision.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = if (decision.requiresConfirmation) "Review" else "Run",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalToolConfirmationCard(
    decision: LocalIntentDecision,
    containerColor: Color,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_shield_lock_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm ${decision.tool.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = decision.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (decision.extractedArgs.isNotEmpty()) {
                val summary = decision.extractedArgs.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm) {
                    Text("Run Action")
                }
            }
        }
    }
}
