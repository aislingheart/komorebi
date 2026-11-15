/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Module
 * File: AboutSection.kt
 * Description: UI layout element for AboutSection.kt.
 */

package com.sameerasw.essentials.ui.components.dialogs

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.material3.toShape
import com.sameerasw.essentials.R
import com.sameerasw.essentials.translation.TranslatableText
import com.sameerasw.essentials.ui.core.sheets.LicensesBottomSheet
import com.sameerasw.essentials.utils.HapticUtil

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
fun AboutSection(
    modifier: Modifier = Modifier,
    appName: String = "Komorebi",
    developerName: String = stringResource(R.string.app_developer_name),
    description: String = stringResource(R.string.app_description),
    onAvatarLongClick: () -> Unit = {},
    onAvatarLongClickWithPosition: ((Offset) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showLicensesSheet by remember { mutableStateOf(false) }
    val versionName =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "Unknown"
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "$appName v$versionName", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            var avatarCenterOffset by remember { mutableStateOf(Offset.Zero) }

            val infiniteTransition = rememberInfiniteTransition(label = "avatar_cookie_rotation")
            val rotationDegrees by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 30000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "cookie_rotation",
            )

            Box(
                modifier =
                    Modifier
                        .size(180.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            val size = coords.size
                            avatarCenterOffset = Offset(
                                x = pos.x + (size.width / 2f),
                                y = pos.y + (size.height / 2f)
                            )
                        }
                        .graphicsLayer { rotationZ = rotationDegrees }
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                onAvatarLongClick()
                                onAvatarLongClickWithPosition?.invoke(avatarCenterOffset)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_komorebi),
                    contentDescription = stringResource(R.string.content_desc_essentials_icon),
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .size(160.dp)
                            .graphicsLayer { rotationZ = -rotationDegrees },
                )
            }

            TranslatableText(
                stringResId = R.string.developed_by_format,
                formatArgs = listOf(developerName),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Button(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            val websiteUrl = "https://github.com/aislingheart/komorebi"
                            val intent = Intent(Intent.ACTION_VIEW, websiteUrl.toUri())
                            context.startActivity(intent)
                        },
                        shape =
                            RoundedCornerShape(
                                topStart = 20.dp,
                                bottomStart = 20.dp,
                                topEnd = 6.dp,
                                bottomEnd = 6.dp,
                            ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.brand_github),
                            contentDescription = stringResource(R.string.action_view_on_github),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Button(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            val websiteUrl = "https://github.com/aislingheart/komorebi/releases"
                            val intent = Intent(Intent.ACTION_VIEW, websiteUrl.toUri())
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_download_24),
                            contentDescription = stringResource(R.string.action_source_code),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Button(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            val websiteUrl = "https://github.com/aislingheart/komorebi/issues"
                            val intent = Intent(Intent.ACTION_VIEW, websiteUrl.toUri())
                            context.startActivity(intent)
                        },
                        shape =
                            RoundedCornerShape(
                                topStart = 6.dp,
                                bottomStart = 6.dp,
                                topEnd = 20.dp,
                                bottomEnd = 20.dp,
                            ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_bug_report_24),
                            contentDescription = stringResource(R.string.action_report_github),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    val portfolioUrl = "https://aislingheart.github.io/Portfolio/"
                    val intent = Intent(Intent.ACTION_VIEW, portfolioUrl.toUri())
                    context.startActivity(intent)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_language_24),
                    contentDescription = "Portfolio & Website",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Portfolio & Website")
            }

            Spacer(modifier = Modifier.height(2.dp))

            RepoDetailsRow(repoUrl = "https://github.com/aislingheart/komorebi")

            Spacer(modifier = Modifier.height(2.dp))

            ContributorsCarousel()

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    showLicensesSheet = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_code_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TranslatableText(R.string.action_licenses_and_credits)
            }
        }
    }

    if (showLicensesSheet) {
        LicensesBottomSheet(
            onDismissRequest = { showLicensesSheet = false },
        )
    }
}
