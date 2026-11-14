/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: ActionShortcutActivity.kt
 * Description: Activity component for ActionShortcutActivity.kt.
 */

package com.sameerasw.essentials.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sameerasw.essentials.R

class ActionShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast
            .makeText(this, getString(R.string.action_shortcut_not_configured), Toast.LENGTH_SHORT)
            .show()
        finish()
    }
}
