package com.sameerasw.essentials.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.essentials.services.SearchGestureAccessibilityService

class DebugOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = SearchGestureAccessibilityService.instance
        if (service == null) {
            Log.e("DebugOverlayReceiver", "Search gesture service is not connected")
            return
        }
        service.openSearch()
    }
}
