/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: FeatureSettingsActivity.kt
 * Description: Compatibility trampoline routing feature settings intents into Komorebi settings screens.
 */

package com.sameerasw.essentials

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity

class FeatureSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val featureId = intent.getStringExtra("feature") ?: ""
        val target =
            if (featureId == "Pixel Searchbar") {
                PixelSearchbarSettingsActivity::class.java
            } else {
                SettingsActivity::class.java
            }
        startActivity(Intent(this, target))
        finish()
    }
}
