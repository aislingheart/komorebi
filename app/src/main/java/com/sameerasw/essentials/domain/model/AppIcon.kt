/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Domain Model
 * File: AppIcon.kt
 * Description: Model enum representing the app launcher icon.
 */

package com.sameerasw.essentials.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sameerasw.essentials.R

enum class AppIcon(
    val key: String,
    val aliasName: String,
    @StringRes val titleRes: Int,
    @DrawableRes val foregroundRes: Int,
) {
    DEFAULT(
        key = "default",
        aliasName = "",
        titleRes = R.string.app_icon_default,
        foregroundRes = R.drawable.ic_launcher_foreground_komorebi,
    );

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromKey(key: String?): AppIcon = DEFAULT
    }
}
