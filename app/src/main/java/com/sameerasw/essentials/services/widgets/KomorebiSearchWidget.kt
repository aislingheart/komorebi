/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: KomorebiSearchWidget.kt
 * Description: Home screen search widget that opens the Komorebi search surface.
 */

package com.sameerasw.essentials.services.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.ColorFilter
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sameerasw.essentials.R
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity

class KomorebiSearchWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            GlanceTheme {
                Row(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(28.dp)
                            .clickable(actionStartActivity(PixelSearchResultsActivity::class.java))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.rounded_search_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier.size(24.dp),
                    )
                    Spacer(modifier = GlanceModifier.size(12.dp))
                    Text(
                        text = context.getString(R.string.search_widget_placeholder),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Image(
                        provider = ImageProvider(R.drawable.rounded_mic_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.size(22.dp),
                    )
                }
            }
        }
    }
}
