/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Health
 * File: HealthPermissionsRationaleActivity.kt
 * Description: No-display target for Health Connect's "App permissions" entry point.
 */

package com.sameerasw.essentials.ui.activities

import android.app.Activity
import android.os.Bundle

class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
