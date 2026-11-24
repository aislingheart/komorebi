/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI - Search Components
 * File: InSearchUpdateComponents.kt
 * Description: Inline update banner and overlay-safe update detail/installer sheet rendered
 *              directly inside Komorebi Search (works in both Accessibility overlay and Activity).
 */

package com.sameerasw.essentials.ui.components.search

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.UpdateInfo
import com.sameerasw.essentials.ui.components.text.SimpleMarkdown
import com.sameerasw.essentials.utils.AutoUpdateManagerHelper
import com.sameerasw.essentials.utils.HapticUtil
import kotlinx.coroutines.launch

@Composable
fun InSearchUpdateBannerCard(
    updateInfo: UpdateInfo,
    onOpenUpdateSheet: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val summaryLine = remember(updateInfo.releaseNotes) {
        updateInfo.releaseNotes
            .lineSequence()
            .map { it.trim().removePrefix("#").removePrefix("*").removePrefix("-").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("http") }
            ?: "Tap to view release notes and install"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable {
                HapticUtil.performUIHaptic(view)
                onOpenUpdateSheet()
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_mobile_arrow_down_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Komorebi ${updateInfo.versionName} available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FilledTonalButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onOpenUpdateSheet()
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Update",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            IconButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onDismiss()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_close_24),
                    contentDescription = "Dismiss update banner",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InSearchUpdateOverlay(
    updateInfo: UpdateInfo?,
    isChecking: Boolean,
    errorMessage: String?,
    checkCompleted: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDismissSearchForInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
    }

    BackHandler(enabled = true) {
        if (!isDownloading) onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { if (!isDownloading) onDismiss() },
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (updateInfo?.isUpdateAvailable == true) {
                                    R.drawable.rounded_mobile_arrow_down_24
                                } else {
                                    R.drawable.rounded_mobile_check_24
                                },
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isChecking -> "Checking for updates..."
                                updateInfo?.isUpdateAvailable == true -> "Update Available"
                                errorMessage != null -> "Update Check Failed"
                                else -> "Komorebi Updates"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = when {
                                updateInfo?.isUpdateAvailable == true ->
                                    "Current v$currentVersion → ${updateInfo.versionName}"
                                else -> "Installed version v$currentVersion"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            onDismiss()
                        },
                        enabled = !isDownloading,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_close_24),
                            contentDescription = "Close update panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    isChecking -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }

                    updateInfo != null && updateInfo.isUpdateAvailable -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 280.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.label_release_notes),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                val notes = updateInfo.releaseNotes.trim()
                                if (notes.isNotEmpty()) {
                                    SimpleMarkdown(content = notes)
                                } else {
                                    Text(
                                        text = "New release ${updateInfo.versionName} is ready to install.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (isDownloading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.downloading_update_progress, downloadProgress),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                LinearWavyProgressIndicator(
                                    progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (!downloadError.isNullOrBlank()) {
                            Text(
                                text = downloadError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (updateInfo.releaseUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        HapticUtil.performUIHaptic(view)
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, updateInfo.releaseUrl.toUri()).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                            )
                                            onDismissSearchForInstall()
                                        }
                                    },
                                    enabled = !isDownloading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("GitHub", maxLines = 1)
                                }
                            }

                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    val url = updateInfo.downloadUrl
                                    if (url.isBlank()) {
                                        if (updateInfo.releaseUrl.isNotBlank()) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, updateInfo.releaseUrl.toUri()).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    },
                                                )
                                                onDismissSearchForInstall()
                                            }
                                        }
                                        return@Button
                                    }
                                    downloadError = null
                                    isDownloading = true
                                    downloadProgress = 0
                                    scope.launch {
                                        val helper = AutoUpdateManagerHelper(context.applicationContext)
                                        val result = helper.downloadAndInstallApk(
                                            apkUrl = url,
                                            apkName = "Komorebi_${updateInfo.versionName}",
                                            autoInstall = true,
                                            onProgressUpdate = { progress -> downloadProgress = progress },
                                            onError = { err -> downloadError = err },
                                        )
                                        isDownloading = false
                                        when (result) {
                                            is AutoUpdateManagerHelper.DownloadResult.Success -> {
                                                onDismissSearchForInstall()
                                            }
                                            is AutoUpdateManagerHelper.DownloadResult.Failed -> {
                                                downloadError = result.reason
                                                Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                modifier = Modifier.weight(1.4f),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_mobile_arrow_down_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isDownloading) "Downloading..." else "Install Update",
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = when {
                                    errorMessage != null -> errorMessage
                                    checkCompleted -> stringResource(R.string.status_up_to_date)
                                    else -> "Check GitHub for the latest Komorebi release."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (errorMessage != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                            )

                            OutlinedButton(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    onRetry()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (errorMessage != null) {
                                        stringResource(R.string.action_retry)
                                    } else {
                                        "Check for Updates"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
