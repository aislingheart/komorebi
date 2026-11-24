/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: NotificationListener.kt
 * Description: Notification listener component used for notification access detection, active notification search, and media session controls.
 */

package com.sameerasw.essentials.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sameerasw.essentials.launcher.ActiveNotificationStore

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ActiveNotificationStore.refresh(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ActiveNotificationStore.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        ActiveNotificationStore.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        ActiveNotificationStore.onRemoved(sbn.key)
    }
}
