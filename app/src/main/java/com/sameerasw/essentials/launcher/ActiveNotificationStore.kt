/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Search
 * File: ActiveNotificationStore.kt
 * Description: In-memory cache of active notifications for launcher search.
 */

package com.sameerasw.essentials.launcher

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.sameerasw.essentials.utils.FuzzySearchUtil
import java.util.concurrent.ConcurrentHashMap

data class ActiveNotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val contentIntent: android.app.PendingIntent?,
    val actions: List<Notification.Action>,
)

object ActiveNotificationStore {

    @Volatile
    private var items: List<ActiveNotificationItem> = emptyList()
    private val labelCache = ConcurrentHashMap<String, String>()

    fun hasAccess(context: Context): Boolean =
        runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)

    fun refresh(service: NotificationListenerService) {
        val notifications = runCatching { service.activeNotifications }.getOrNull() ?: return
        items = notifications
            .mapNotNull { toItem(service, it) }
            .sortedByDescending { it.postedAt }
    }

    fun onPosted(service: NotificationListenerService, notification: StatusBarNotification) {
        val item = toItem(service, notification) ?: return
        items = (items.filterNot { it.key == item.key } + item).sortedByDescending { it.postedAt }
    }

    fun onRemoved(key: String) {
        items = items.filterNot { it.key == key }
    }

    fun all(): List<ActiveNotificationItem> = items

    fun reply(context: Context, key: String, message: String): Boolean {
        val item = items.firstOrNull { it.key == key } ?: return false
        for (action in item.actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                val intent = Intent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val results = android.os.Bundle().apply {
                        putCharSequence(remoteInput.resultKey, message)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, this, results)
                }
                val sent = runCatching { action.actionIntent.send(context, 0, intent) }.isSuccess
                if (sent) return true
            }
        }
        return false
    }

    fun search(context: Context, query: String, limit: Int): List<ActiveNotificationItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return items.asSequence()
            .mapNotNull { item ->
                val score = FuzzySearchUtil.scoreFields(
                    query = trimmed,
                    primary = item.title,
                    secondary = listOf(item.text, item.appLabel),
                )
                if (score > 0) item to score else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun open(context: Context, item: ActiveNotificationItem): Boolean {
        val pending = item.contentIntent
        if (pending != null) {
            val sent = runCatching {
                pending.send()
                true
            }.getOrDefault(false)
            if (sent) return true
        }
        return runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun toItem(
        service: NotificationListenerService,
        notification: StatusBarNotification,
    ): ActiveNotificationItem? {
        val posted = notification.notification ?: return null
        if (posted.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = posted.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = sequenceOf(
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
        ).mapNotNull { extras.getCharSequence(it)?.toString()?.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val label = labelCache.getOrPut(notification.packageName) {
            runCatching {
                val info = service.packageManager.getApplicationInfo(notification.packageName, 0)
                service.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(notification.packageName)
        }
        return ActiveNotificationItem(
            key = notification.key,
            packageName = notification.packageName,
            appLabel = label,
            title = title.ifBlank { label },
            text = text,
            postedAt = notification.postTime,
            contentIntent = posted.contentIntent,
            actions = posted.actions?.toList().orEmpty(),
        )
    }
}
