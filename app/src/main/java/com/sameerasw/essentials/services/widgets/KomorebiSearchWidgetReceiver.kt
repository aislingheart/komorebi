/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: KomorebiSearchWidgetReceiver.kt
 * Description: Glance receiver for the Komorebi home screen search widget.
 */

package com.sameerasw.essentials.services.widgets

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class KomorebiSearchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KomorebiSearchWidget()
}
