package com.sameerasw.essentials.raycast

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object RaycastOAuthManager {
    @Volatile
    private var pendingAuth: CompletableDeferred<Map<String, String>>? = null

    suspend fun launchAuthorizeFlow(context: Context, authUrl: String): Map<String, String> {
        val deferred = CompletableDeferred<Map<String, String>>()
        pendingAuth?.cancel()
        pendingAuth = deferred

        val launched = withContext(Dispatchers.Main) {
            runCatching {
                val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                customTabs.launchUrl(context, Uri.parse(authUrl))
                true
            }.getOrElse {
                runCatching {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    true
                }.getOrDefault(false)
            }
        }

        if (!launched) {
            pendingAuth = null
            deferred.cancel()
            return mapOf("error" to "no_browser_available")
        }

        return withTimeoutOrNull(180_000L) { deferred.await() } ?: emptyMap()
    }

    fun handleRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        if (code == null && error == null) return false
        val map = buildMap {
            code?.let { put("authorizationCode", it) }
            state?.let { put("state", it) }
            error?.let { put("error", it) }
        }
        pendingAuth?.complete(map)
        pendingAuth = null
        return true
    }
}
