/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchbarTapActivity.kt
 * Description: Activity component for PixelSearchbarTapActivity.kt.
 */

package com.sameerasw.essentials.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class PixelSearchbarTapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultsIntent =
            Intent(this, PixelSearchResultsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        startActivity(resultsIntent)
        finish()
    }
}
