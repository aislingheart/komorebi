/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: PixelSearchActivity.kt
 * Description: Invisible trampoline that opens the search surface.
 */

package com.sameerasw.essentials

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity

class PixelSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra("query")
            ?: ""

        try {
            startActivity(
                Intent(this, PixelSearchResultsActivity::class.java).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        } catch (t: Throwable) {
            Log.e("PixelSearchActivity", "Failed to open search results", t)
            runCatching {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        finish()
    }
}
