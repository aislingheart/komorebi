/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher & System Utilities
 * File: AppLaunchUtil.kt
 * Description: Reliable app and activity launching from overlays, accessibility services, and assistant sessions.
 */

package com.sameerasw.essentials.utils

import android.app.Activity
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.sameerasw.essentials.services.SearchGestureAccessibilityService
import com.sameerasw.essentials.ui.activities.PixelSearchResultsActivity

object AppLaunchUtil {
    private const val TAG = "AppLaunchUtil"

    /**
     * Launches a target app reliably whether invoked from a foreground Activity,
     * an Accessibility overlay WindowContext, or a VoiceInteractionSession.
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val host = resolveHostContext(context)
        val options = buildLaunchOptions()

        // 1. Prefer LauncherApps.startMainActivity (same system path used by Pixel Launcher;
        //    handles task restoration, secondary profiles, and Android 14-16 BAL cleanly).
        val launchedViaLauncherApps =
            runCatching {
                val launcherApps = host.getSystemService(LauncherApps::class.java) ?: return@runCatching false
                val primaryUser = Process.myUserHandle()
                val primaryMatch = launcherApps.getActivityList(packageName, primaryUser).firstOrNull()
                val targetInfo =
                    primaryMatch ?: launcherApps.profiles
                        .asSequence()
                        .filter { it != primaryUser }
                        .mapNotNull { profile -> launcherApps.getActivityList(packageName, profile).firstOrNull() }
                        .firstOrNull()
                if (targetInfo != null) {
                    launcherApps.startMainActivity(targetInfo.componentName, targetInfo.user, null, options)
                    true
                } else {
                    false
                }
            }.onFailure { error ->
                Log.w(TAG, "LauncherApps.startMainActivity failed for $packageName", error)
            }.getOrDefault(false)
        if (launchedViaLauncherApps) return true

        // 2. Fallback to PackageManager.getLaunchIntentForPackage with task-reset flags.
        val launchIntent =
            runCatching { host.packageManager.getLaunchIntentForPackage(packageName) }
                .getOrNull()
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                    )
                }
        if (launchIntent != null && startActivityReliably(host, launchIntent, options)) {
            return true
        }

        // 3. Final fallback: resolve explicit ACTION_MAIN + CATEGORY_LAUNCHER component.
        val resolvedIntent =
            runCatching {
                val queryIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(packageName)
                    }
                val resolveInfo = host.packageManager.queryIntentActivities(queryIntent, 0).firstOrNull()
                resolveInfo?.activityInfo?.let { info ->
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = ComponentName(info.packageName, info.name)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                        )
                    }
                }
            }.getOrNull()

        if (resolvedIntent != null && startActivityReliably(host, resolvedIntent, options)) {
            return true
        }

        Log.w(TAG, "Unable to launch package: $packageName")
        return false
    }

    /**
     * Starts any Intent reliably from an overlay, accessibility service, or Activity.
     * Prefers an active AccessibilityService host when called from a non-Activity overlay
     * so Android 14+ Background Activity Launch restrictions don't silently drop the launch.
     */
    fun startActivityReliably(
        context: Context,
        intent: Intent,
        options: Bundle? = buildLaunchOptions(),
    ): Boolean {
        val host = resolveHostContext(context)
        val prepared =
            Intent(intent).apply {
                if (host !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        val primaryOk =
            runCatching {
                if (options != null) {
                    host.startActivity(prepared, options)
                } else {
                    host.startActivity(prepared)
                }
                true
            }.onFailure { error ->
                Log.w(TAG, "Primary startActivity failed via ${host.javaClass.simpleName} for ${prepared.component ?: prepared.action}", error)
            }.getOrDefault(false)
        if (primaryOk) return true

        if (host !== context) {
            return runCatching {
                val fallback = Intent(prepared).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(fallback, options)
                true
            }.onFailure { error ->
                Log.w(TAG, "Fallback startActivity failed for ${prepared.component ?: prepared.action}", error)
            }.getOrDefault(false)
        }
        return false
    }

    /**
     * Builds a sanitized intent to open PixelSearchResultsActivity without forwarding
     * unsafe system Parcelables/Binders from VoiceInteractionSession bundles.
     */
    fun buildSearchResultsIntent(
        context: Context,
        query: String = "",
    ): Intent =
        Intent(context.applicationContext, PixelSearchResultsActivity::class.java).apply {
            if (query.isNotEmpty()) {
                putExtra(SearchManager.QUERY, query)
            }
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        }

    fun extractSafeQuery(bundle: Bundle?): String {
        if (bundle == null) return ""
        return runCatching {
            bundle.getString(SearchManager.QUERY)
                ?: bundle.getString("query")
                ?: ""
        }.getOrDefault("")
    }

    private fun resolveHostContext(context: Context): Context {
        findActivity(context)?.let { return it }
        SearchGestureAccessibilityService.instance?.let { return it }
        return context
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    @Suppress("DEPRECATION")
    fun defaultLaunchOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            val opts = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 36) {
                opts.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                )
            } else {
                opts.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            opts.toBundle()
        }.getOrNull()
    }

    private fun buildLaunchOptions(): Bundle? = defaultLaunchOptions()
}
