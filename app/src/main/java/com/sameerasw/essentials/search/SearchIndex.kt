/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Engine
 * File: SearchIndex.kt
 * Description: Unified in-memory index for universal search sources.
 */

package com.sameerasw.essentials.search

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.domain.model.NotificationApp
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.domain.model.SearchableItem
import com.sameerasw.essentials.domain.registry.SearchRegistry
import com.sameerasw.essentials.utils.AppUtil
import com.sameerasw.essentials.utils.ContactsSearchUtil
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.PermissionUtils
import com.sameerasw.essentials.utils.SystemSettingsSearchUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SearchIndex {

    data class Snapshot(
        val apps: List<NotificationApp>,
        val contacts: List<PixelSearchResultItem.ContactItem>,
        val files: FileSearchUtil.FileIndex,
        val settings: List<SearchableItem>,
        val systemSettings: List<PixelSearchResultItem.SystemSettingItem>,
        val shortcuts: List<DiscoveredShortcut>,
        val appFunctions: List<AppFunctionItem>,
        val providers: List<SearchProvider>,
        val includeUnsupportedFeatures: Boolean,
        val includeLegacyFeatures: Boolean,
        val hadContactsPermission: Boolean,
        val hadFilesPermission: Boolean,
        val builtAtMs: Long,
    ) {
        fun isFresh(
            now: Long,
            unsupported: Boolean,
            legacy: Boolean,
            contactsPerm: Boolean,
            filesPerm: Boolean,
        ): Boolean =
            now - builtAtMs <= TTL_MS &&
                includeUnsupportedFeatures == unsupported &&
                includeLegacyFeatures == legacy &&
                hadContactsPermission == contactsPerm &&
                hadFilesPermission == filesPerm
    }

    private const val TTL_MS = 5 * 60_000L
    private val buildMutex = Mutex()

    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate()
        }
    }

    suspend fun get(
        context: Context,
        includeUnsupportedFeatures: Boolean,
        includeLegacyFeatures: Boolean,
    ): Snapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        ensureInvalidationReceiver(appContext)
        val contactsPerm = hasContactsPermission(appContext)
        val filesPerm = hasFilesPermission(appContext)
        val now = System.currentTimeMillis()
        snapshot?.let {
            if (it.isFresh(now, includeUnsupportedFeatures, includeLegacyFeatures, contactsPerm, filesPerm)) {
                return@withContext it
            }
        }
        buildMutex.withLock {
            val recheckedNow = System.currentTimeMillis()
            val recheckedContactsPerm = hasContactsPermission(appContext)
            val recheckedFilesPerm = hasFilesPermission(appContext)
            snapshot?.let {
                if (it.isFresh(
                        recheckedNow,
                        includeUnsupportedFeatures,
                        includeLegacyFeatures,
                        recheckedContactsPerm,
                        recheckedFilesPerm,
                    )
                ) {
                    return@withContext it
                }
            }
            val built = build(
                context = appContext,
                includeUnsupportedFeatures = includeUnsupportedFeatures,
                includeLegacyFeatures = includeLegacyFeatures,
                contactsPerm = recheckedContactsPerm,
                filesPerm = recheckedFilesPerm,
                builtAtMs = recheckedNow,
            )
            snapshot = built
            built
        }
    }

    fun invalidate() {
        snapshot = null
        SearchProviderDiscovery.invalidateCaches()
    }

    fun peek(): Snapshot? = snapshot

    private fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFilesPermission(context: Context): Boolean =
        PermissionUtils.hasMediaPermissions(context) ||
            PermissionUtils.hasManageExternalStoragePermission(context) ||
            PermissionUtils.hasStoragePermission(context)

    private fun ensureInvalidationReceiver(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    packageReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                receiverRegistered = true
            }
        }
    }

    private suspend fun build(
        context: Context,
        includeUnsupportedFeatures: Boolean,
        includeLegacyFeatures: Boolean,
        contactsPerm: Boolean,
        filesPerm: Boolean,
        builtAtMs: Long,
    ): Snapshot = coroutineScope {
        val appContext = context.applicationContext
        val appsDeferred = async { AppUtil.getInstalledApps(appContext, includeSelf = true) }
        val contactsDeferred = async {
            if (contactsPerm) ContactsSearchUtil.loadAll(appContext) else emptyList()
        }
        val filesDeferred = async {
            if (filesPerm) FileSearchUtil.buildFileIndex(appContext) else FileSearchUtil.FileIndex()
        }
        val settingsDeferred = async {
            SearchRegistry.indexItems(
                appContext,
                includeUnsupportedFeatures,
                includeLegacyFeatures,
            )
        }
        val systemSettingsDeferred = async { SystemSettingsSearchUtil.all(appContext) }
        val shortcutsDeferred = async { SearchProviderDiscovery.getAllShortcuts(appContext) }
        val appFunctionsDeferred = async { SearchProviderDiscovery.indexAppFunctions(appContext) }
        val providersDeferred = async { SearchProviderDiscovery.discoverProviders(appContext) }
        Snapshot(
            apps = appsDeferred.await(),
            contacts = contactsDeferred.await(),
            files = filesDeferred.await(),
            settings = settingsDeferred.await(),
            systemSettings = systemSettingsDeferred.await(),
            shortcuts = shortcutsDeferred.await(),
            appFunctions = appFunctionsDeferred.await(),
            providers = providersDeferred.await(),
            includeUnsupportedFeatures = includeUnsupportedFeatures,
            includeLegacyFeatures = includeLegacyFeatures,
            hadContactsPermission = contactsPerm,
            hadFilesPermission = filesPerm,
            builtAtMs = builtAtMs,
        )
    }
}
