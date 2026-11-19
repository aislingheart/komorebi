/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: TaskReminderReceiver.kt
 * Description: BroadcastReceiver that posts task due notifications.
 */

package com.sameerasw.essentials.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.PermissionUtils

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != TasksRepository.ACTION_TASK_REMINDER) return
        if (!PermissionUtils.isPostNotificationsEnabled(context)) return

        val taskId = intent.getStringExtra(TasksRepository.EXTRA_TASK_ID).orEmpty()
        val title = intent.getStringExtra(TasksRepository.EXTRA_TASK_TITLE)
            .orEmpty()
            .ifBlank { "Task reminder" }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannel(manager)

        val builder = NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText("Task is due")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        val notificationId = taskId.hashCode().takeIf { it != 0 } ?: title.hashCode()
        manager.notify(notificationId, builder.build())
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Task reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Reminders for assistant tasks"
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "assistant_tasks"
    }
}
