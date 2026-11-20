/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Raycast / Vicinae Runtime Security
 * File: ExtensionPermissionManager.kt
 * Description: Per-extension capability permissions (NETWORK, CLIPBOARD, FILES, CLI_BRIDGE)
 *              enforced at the QuickJS native bridge boundary.
 */

package com.sameerasw.essentials.raycast

import android.content.Context

enum class ExtensionCapability(
    val key: String,
    val title: String,
    val description: String,
    val defaultAllowed: Boolean,
) {
    NETWORK(
        key = "network",
        title = "Network Access",
        description = "Allow HTTP/HTTPS requests to external servers",
        defaultAllowed = true,
    ),
    CLIPBOARD(
        key = "clipboard",
        title = "Clipboard Access",
        description = "Allow reading, copying, and pasting clipboard text",
        defaultAllowed = true,
    ),
    FILES(
        key = "files",
        title = "Sandboxed Files",
        description = "Allow reading and writing files inside extension storage",
        defaultAllowed = true,
    ),
    CLI_BRIDGE(
        key = "cli_bridge",
        title = "CLI / Shell Bridge",
        description = "Allow executing local shell commands via the CLI bridge",
        defaultAllowed = false,
    ),
}

object ExtensionPermissionManager {
    private const val PREFS_NAME = "komorebi_extension_permissions_v1"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prefKey(extensionId: String, capability: ExtensionCapability): String =
        "${extensionId.trim()}::${capability.key}"

    fun isAllowed(
        context: Context,
        extensionId: String,
        capability: ExtensionCapability,
    ): Boolean {
        val p = prefs(context)
        val key = prefKey(extensionId, capability)
        if (!p.contains(key)) return capability.defaultAllowed
        return p.getBoolean(key, capability.defaultAllowed)
    }

    fun setAllowed(
        context: Context,
        extensionId: String,
        capability: ExtensionCapability,
        allowed: Boolean,
    ) {
        prefs(context).edit().putBoolean(prefKey(extensionId, capability), allowed).apply()
    }

    fun getAll(context: Context, extensionId: String): Map<ExtensionCapability, Boolean> =
        ExtensionCapability.entries.associateWith { cap -> isAllowed(context, extensionId, cap) }

    fun clearForExtension(context: Context, extensionId: String) {
        val p = prefs(context)
        val prefix = "${extensionId.trim()}::"
        val editor = p.edit()
        p.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * Pure policy helper for unit testing without Android SharedPreferences.
     */
    internal fun evaluatePolicy(
        overrides: Map<ExtensionCapability, Boolean>,
        capability: ExtensionCapability,
    ): Boolean = overrides[capability] ?: capability.defaultAllowed
}
