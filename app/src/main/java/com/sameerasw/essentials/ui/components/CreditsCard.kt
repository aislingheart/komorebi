/*
 * License: MIT License
 *
 * Feature Module: UI Module
 * File: CreditsCard.kt
 * Description: In-app credits card highlighting projects and ecosystems Komorebi builds upon.
 */

package com.sameerasw.essentials.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sameerasw.essentials.R
import com.sameerasw.essentials.translation.TranslatableText
import com.sameerasw.essentials.ui.core.sheets.LicensesBottomSheet
import com.sameerasw.essentials.ui.theme.GoogleSansFlexRounded
import com.sameerasw.essentials.utils.HapticUtil

/**
 * Credits card acknowledging upstream projects and open-source inspirations
 * including Essentials, Vicinae, and Raycast Extensions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    var showLicensesSheet by remember { mutableStateOf(false) }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    HapticUtil.performUIHaptic(view)
                    showLicensesSheet = true
                },
        color = com.sameerasw.essentials.ui.core.komorebi.pixelSettingsCardColor(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_heart_smile_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Credits & Inspirations",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontFamily = GoogleSansFlexRounded,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Komorebi is built with gratitude on top of open-source projects and ecosystems, including Essentials by Sameera Wijerathna, Vicinae, and Raycast Extensions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "Essentials" to "https://github.com/sameerasw/essentials",
                    "Vicinae" to "https://github.com/vicinaehq/vicinae",
                    "Raycast Extensions" to "https://github.com/raycast/extensions",
                ).forEach { (label, url) ->
                    AssistChip(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.brand_github),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    showLicensesSheet = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_code_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TranslatableText(R.string.licenses_credits_title)
            }
        }
    }

    if (showLicensesSheet) {
        LicensesBottomSheet(
            onDismissRequest = { showLicensesSheet = false },
        )
    }
}
