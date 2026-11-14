/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities - Screenshots
 * File: ScreenshotCleanupNotifier.kt
 * Description: Quiet notification for a newly scheduled screenshot with Keep and Trash now actions.
 */

package com.sameerasw.essentials.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R

object ScreenshotCleanupNotifier {
    private const val CHANNEL_ID = "screenshot_cleanup"
    private const val RESULT_CHANNEL_ID = "screenshot_cleanup_result"
    private const val NOTIFICATION_TAG = "screenshot_cleanup"
    private const val GROUP_KEY = "com.sameerasw.essentials.SCREENSHOT_CLEANUP"
    private const val TIMEOUT_MILLIS = 60 * 60 * 1000L
    private const val RESULT_TIMEOUT_MILLIS = 5_000L
    private const val ACTION_KEEP = "com.sameerasw.essentials.SCREENSHOT_KEEP"
    private const val ACTION_TRASH_NOW = "com.sameerasw.essentials.SCREENSHOT_TRASH_NOW"
    private const val ACTION_UNDO = "com.sameerasw.essentials.SCREENSHOT_UNDO"
    private const val EXTRA_MEDIA_ID = "media_id"
    private const val EXTRA_DAYS = "days"
    private const val EXTRA_TRASHED_BYTES = "trashed_bytes"

    fun show(
        context: Context,
        mediaId: Long,
        days: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannels(context)
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val openIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val preview = loadThumbnail(context, mediaId)
        val daysText = context.resources.getQuantityString(R.plurals.screenshot_auto_clean_notification_title, days, days)
        val body = context.getString(R.string.screenshot_auto_clean_notification_text)

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.rounded_screenshot_region_24)
                .setContentTitle(daysText)
                .setContentText(body)
                .setContentIntent(
                    PendingIntent.getActivity(context, requestCode(mediaId, 0), openIntent, PendingIntent.FLAG_IMMUTABLE),
                ).addAction(
                    R.drawable.rounded_bookmark_24,
                    context.getString(R.string.screenshot_action_keep),
                    actionIntent(context, mediaId, ACTION_KEEP, 1, days),
                ).addAction(
                    R.drawable.rounded_delete_24,
                    context.getString(R.string.screenshot_action_trash_now),
                    actionIntent(context, mediaId, ACTION_TRASH_NOW, 2, days),
                ).setSilent(true)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setTimeoutAfter(TIMEOUT_MILLIS)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        if (preview != null) {
            builder
                .setLargeIcon(preview)
                .setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(preview)
                        .bigLargeIcon(null as Bitmap?),
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, mediaId.toInt(), builder.build())
    }

    fun showResult(
        context: Context,
        mediaId: Long,
        message: Int,
        days: Int,
        trashedBytes: Long,
    ) {
        showResultNotification(context, mediaId, days, trashedBytes, message)
    }

    fun showResultNotification(
        context: Context,
        mediaId: Long,
        days: Int,
        trashedBytes: Long,
        @StringRes message: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannels(context)
        val undo =
            actionIntent(context, mediaId, ACTION_UNDO, 3, days) { putExtra(EXTRA_TRASHED_BYTES, trashedBytes) }
        val notification =
            NotificationCompat
                .Builder(context, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.rounded_screenshot_region_24)
                .setContentTitle(context.getString(message))
                .addAction(R.drawable.ic_undo, context.getString(R.string.screenshot_action_undo), undo)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter(RESULT_TIMEOUT_MILLIS)
                .setGroup(GROUP_KEY)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, mediaId.toInt(), notification)
    }

    fun dismiss(
        context: Context,
        mediaId: Long,
    ) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, mediaId.toInt())
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.screenshot_cleanup_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(RESULT_CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    context.getString(R.string.screenshot_cleanup_result_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                }
            manager.createNotificationChannel(channel)
        }
    }

    private fun actionIntent(
        context: Context,
        mediaId: Long,
        action: String,
        slot: Int,
        days: Int,
        extras: Intent.() -> Unit = {},
    ): PendingIntent {
        val intent =
            Intent(action).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_MEDIA_ID, mediaId)
                putExtra(EXTRA_DAYS, days)
                extras()
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode(mediaId, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(
        mediaId: Long,
        slot: Int,
    ) = (mediaId * 4 + slot).toInt()

    private fun loadThumbnail(
        context: Context,
        mediaId: Long,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        return runCatching { context.contentResolver.loadThumbnail(uri, Size(720, 720), null) }.getOrNull()
    }
}
