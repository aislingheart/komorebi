/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities - General
 * File: UpdateNotificationHelper.kt
 * Description: Utility helper for UpdateNotificationHelper.kt.
 */

package com.sameerasw.essentials.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.SettingsActivity

object UpdateNotificationHelper {
    const val ACTION_DOWNLOAD_UPDATE = "com.sameerasw.essentials.ACTION_DOWNLOAD_UPDATE"
    const val EXTRA_SHOW_UPDATE_SHEET = "show_update_sheet"
    const val EXTRA_DOWNLOAD_URL = "download_url"
    const val EXTRA_VERSION = "version"

    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1001

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notifySafely(
        context: Context,
        notification: android.app.Notification,
    ) {
        if (!canPostNotifications(context)) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildSearchUpdatePendingIntent(context: Context): PendingIntent {
        val searchIntent =
            AppLaunchUtil.buildSearchResultsIntent(
                context = context,
                showUpdateSheet = true,
            )
        return PendingIntent.getActivity(
            context,
            0,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.update_channel_name)
            val descriptionText = context.getString(R.string.update_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(
        context: Context,
        version: String,
        downloadUrl: String?,
    ) {
        createNotificationChannel(context)

        val contentPendingIntent = buildSearchUpdatePendingIntent(context)

        val downloadIntent =
            Intent(ACTION_DOWNLOAD_UPDATE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                putExtra(EXTRA_VERSION, version)
            }
        val downloadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                1,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(context.getString(R.string.notification_update_available))
                .setContentText(context.getString(R.string.notification_update_subtext, version))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.rounded_mobile_arrow_down_24,
                    context.getString(R.string.action_download),
                    downloadPendingIntent,
                )

        notifySafely(context, builder.build())
    }

    fun showDownloadProgressNotification(
        context: Context,
        version: String,
        progress: Int,
    ) {
        if (progress >= 100) {
            cancelNotification(context)
            return
        }
        createNotificationChannel(context)

        val contentPendingIntent = buildSearchUpdatePendingIntent(context)

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(context.getString(R.string.notification_update_available))
                .setContentText(context.getString(R.string.downloading_update_progress, progress))
                .setProgress(100, progress, progress <= 0)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)

        notifySafely(context, builder.build())
    }

    fun showReadyToInstallNotification(
        context: Context,
        version: String,
        installIntent: Intent,
    ) {
        createNotificationChannel(context)

        val installPendingIntent =
            PendingIntent.getActivity(
                context,
                2,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(context.getString(R.string.update_ready_to_install))
                .setContentText(context.getString(R.string.update_ready_to_install_desc, version))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(installPendingIntent)

        notifySafely(context, builder.build())
    }

    fun showDownloadFailedNotification(context: Context) {
        createNotificationChannel(context)

        val contentPendingIntent = buildSearchUpdatePendingIntent(context)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(context.getString(R.string.update_download_failed))
                .setContentText(context.getString(R.string.notification_update_available))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)

        notifySafely(context, builder.build())
    }

    fun cancelNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
