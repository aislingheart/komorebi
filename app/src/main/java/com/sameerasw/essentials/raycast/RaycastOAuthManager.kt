package com.sameerasw.essentials.raycast

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object RaycastOAuthManager {
    private const val AUTH_TIMEOUT_MS = 180_000L
    private val ALLOWED_REDIRECT_SCHEMES = setOf("komorebi", "raycast")
    private const val ALLOWED_REDIRECT_HOST = "oauth"

    private val pendingStates = ConcurrentHashMap<String, Long>()
    private val pendingAuthFlows = ConcurrentHashMap<String, CompletableDeferred<Map<String, String>>>()

    internal fun parseValidAuthUri(authUrl: String): Uri? {
        val trimmed = authUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    internal fun isAllowedRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return scheme in ALLOWED_REDIRECT_SCHEMES && host == ALLOWED_REDIRECT_HOST
    }

    private fun pruneExpiredStates(nowMs: Long = System.currentTimeMillis()) {
        val iterator = pendingStates.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value > AUTH_TIMEOUT_MS) {
                iterator.remove()
                pendingAuthFlows.remove(entry.key)?.cancel()
            }
        }
    }

    suspend fun launchAuthorizeFlow(context: Context, authUrl: String): Map<String, String> {
        val parsedUri = parseValidAuthUri(authUrl)
            ?: return mapOf("error" to "invalid_auth_url")
        val state = parsedUri.getQueryParameter("state")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapOf("error" to "missing_oauth_state")

        val now = System.currentTimeMillis()
        pruneExpiredStates(now)

        val deferred = CompletableDeferred<Map<String, String>>()
        pendingStates[state] = now
        pendingAuthFlows.put(state, deferred)?.cancel()

        val appContext = context.applicationContext
        val launched = withContext(Dispatchers.Main) {
            runCatching {
                val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                customTabs.launchUrl(appContext, parsedUri)
                true
            }.getOrElse {
                runCatching {
                    val browserIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(browserIntent)
                    true
                }.getOrDefault(false)
            }
        }

        if (!launched) {
            pendingStates.remove(state)
            pendingAuthFlows.remove(state, deferred)
            deferred.cancel()
            return mapOf("error" to "no_browser_available")
        }

        return try {
            withTimeoutOrNull(AUTH_TIMEOUT_MS) { deferred.await() } ?: emptyMap()
        } finally {
            pendingStates.remove(state)
            pendingAuthFlows.remove(state, deferred)
        }
    }

    fun handleRedirectUri(uri: Uri?): Boolean {
        if (!isAllowedRedirectUri(uri)) return false
        val safeUri = uri ?: return false
        val code = safeUri.getQueryParameter("code")
        val state = safeUri.getQueryParameter("state")?.trim()?.takeIf { it.isNotEmpty() }
        val error = safeUri.getQueryParameter("error")
        if ((code == null && error == null) || state == null) return false

        val now = System.currentTimeMillis()
        pruneExpiredStates(now)

        val issuedAt = pendingStates.remove(state) ?: return false
        if (now - issuedAt > AUTH_TIMEOUT_MS) {
            pendingAuthFlows.remove(state)?.cancel()
            return false
        }

        val deferred = pendingAuthFlows.remove(state) ?: return false
        val map = buildMap {
            code?.let { put("authorizationCode", it) }
            put("state", state)
            error?.let { put("error", it) }
        }
        return deferred.complete(map)
    }
}
