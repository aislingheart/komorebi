/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Application Activities
 * File: ShortcutHandlerActivity.kt
 * Description: Activity component for ShortcutHandlerActivity.kt.
 */

package com.sameerasw.essentials

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class ShortcutHandlerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }

        val packageName = intent.getStringExtra("package_name")

        if (packageName != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Toast
                    .makeText(
                        this,
                        R.string.error_app_uninstalled,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
        finish()
    }
}
