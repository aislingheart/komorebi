/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search Engine
 * File: SearchProviderDiscovery.kt
 * Description: Dynamic Android package/intent/shortcut/AppFunction discovery and contextual entity resolver.
 */

package com.sameerasw.essentials.search

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.appfunctions.AppFunctionAvailabilityManager
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.search.semantic.PhotoIndexWorker
import com.sameerasw.essentials.search.semantic.PhotoSemanticEngine
import com.sameerasw.essentials.shizuku.ShizukuProcessHelper
import com.sameerasw.essentials.ui.activities.ExtensionRunnerActivity
import com.sameerasw.essentials.ui.activities.ExtensionsActivity
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.ShizukuUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SearchProviderDiscovery {

    private const val PHOTOS_PACKAGE = "com.google.android.apps.photos"
    private const val ACTION_APP_FUNCTION_SERVICE = "android.app.appfunctions.AppFunctionService"
    private const val SHORTCUT_CACHE_TTL_MS = 60_000L

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    @Volatile
    private var cachedProviders: List<SearchProvider>? = null

    @Volatile
    private var cachedShortcuts: List<DiscoveredShortcut> = emptyList()

    @Volatile
    private var lastShortcutsFetchMs: Long = 0L

    fun invalidateCaches() {
        cachedProviders = null
        lastShortcutsFetchMs = 0L
    }

    suspend fun discoverProviders(context: Context): List<SearchProvider> = withContext(Dispatchers.IO) {
        cachedProviders?.let { return@withContext it }

        val pm = context.packageManager
        val photosAppInstalled = runCatching { pm.getPackageInfo(PHOTOS_PACKAGE, 0) }.isSuccess
        val photosIcon = if (photosAppInstalled) loadAppIcon(pm, PHOTOS_PACKAGE) else null

        val builtIn = listOf(
            SearchProvider(
                id = "provider:photos",
                title = "Photos",
                subtitle = if (photosAppInstalled) "Google Photos & Gallery" else "Device Photos",
                kind = SearchProviderKind.PHOTOS,
                aliases = listOf("photos", "google photos", "gallery", "images", "pictures", "screenshots"),
                packageName = if (photosAppInstalled) PHOTOS_PACKAGE else null,
                appIcon = photosIcon,
                iconRes = R.drawable.rounded_image_24,
                priority = 100,
            ),
            SearchProvider(
                id = "provider:apps",
                title = "Apps",
                subtitle = "Installed applications",
                kind = SearchProviderKind.APPS,
                aliases = listOf("apps", "applications", "launch"),
                iconRes = R.drawable.rounded_apps_24,
                priority = 80,
            ),
            SearchProvider(
                id = "provider:contacts",
                title = "Contacts",
                subtitle = "People & phone numbers",
                kind = SearchProviderKind.CONTACTS,
                aliases = listOf("contacts", "people", "call", "dial"),
                iconRes = R.drawable.rounded_call_24,
                priority = 80,
            ),
            SearchProvider(
                id = "provider:files",
                title = "Files",
                subtitle = "Local documents & downloads",
                kind = SearchProviderKind.FILES,
                aliases = listOf("files", "documents", "docs", "downloads", "pdf"),
                iconRes = R.drawable.rounded_description_24,
                priority = 80,
            ),
            SearchProvider(
                id = "provider:settings",
                title = "Settings",
                subtitle = "System & Komorebi settings",
                kind = SearchProviderKind.SETTINGS,
                aliases = listOf("settings", "preferences", "config"),
                iconRes = R.drawable.rounded_settings_24,
                priority = 75,
            ),
            SearchProvider(
                id = "provider:web",
                title = "Web",
                subtitle = "Search web or open URL",
                kind = SearchProviderKind.WEB,
                aliases = listOf("web", "google", "browser", "search"),
                iconRes = R.drawable.rounded_language_24,
                priority = 70,
            ),
        )

        val discoveredMap = LinkedHashMap<String, MutableList<IntentSearchTarget>>()
        val labelMap = HashMap<String, String>()

        val probeIntents = listOf(
            Intent(Intent.ACTION_SEARCH) to null,
            Intent(Intent.ACTION_WEB_SEARCH) to null,
            Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH) to null,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=coffee")) to "geo",
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=coffee")) to "google.navigation",
        )

        val flags = PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        for ((probe, scheme) in probeIntents) {
            val matches: List<ResolveInfo> = runCatching {
                pm.queryIntentActivities(probe, flags)
            }.getOrDefault(emptyList())

            for (ri in matches) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName ?: continue
                if (pkg == context.packageName || pkg == PHOTOS_PACKAGE) continue

                val label = runCatching {
                    ri.loadLabel(pm).toString().ifBlank {
                        ai.applicationInfo.loadLabel(pm).toString()
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue

                labelMap.putIfAbsent(pkg, label)
                val list = discoveredMap.getOrPut(pkg) { mutableListOf() }
                val action = probe.action ?: Intent.ACTION_VIEW
                val component = ComponentName(pkg, ai.name)
                if (list.none { it.action == action && it.scheme == scheme }) {
                    list.add(
                        IntentSearchTarget(
                            action = action,
                            scheme = scheme,
                            componentName = component,
                        ),
                    )
                }
            }
        }

        val appProviders = discoveredMap.entries.map { (pkg, targets) ->
            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: labelMap[pkg] ?: pkg

            val targetSummary = targets.joinToString(" • ") { target ->
                when {
                    target.scheme == "google.navigation" -> "Navigate"
                    target.scheme == "geo" -> "Maps"
                    target.action == MediaStore.INTENT_ACTION_MEDIA_SEARCH -> "Media"
                    target.action == Intent.ACTION_WEB_SEARCH -> "Web"
                    else -> "Search"
                }
            }

            val aliasSet = linkedSetOf(appName.lowercase(Locale.getDefault()))
            // Add common compact aliases for multi-word apps (e.g. "YouTube Music", "Google Maps")
            appName.lowercase(Locale.getDefault())
                .split(" ", "-", "_")
                .filter { it.length >= 3 }
                .forEach { aliasSet.add(it) }

            SearchProvider(
                id = "app:$pkg",
                title = appName,
                subtitle = targetSummary,
                kind = SearchProviderKind.APP_INTENT,
                aliases = aliasSet.toList(),
                packageName = pkg,
                appIcon = loadAppIcon(pm, pkg),
                iconRes = R.drawable.rounded_search_24,
                targets = targets,
                priority = 60,
            )
        }.sortedBy { it.title.lowercase(Locale.getDefault()) }

        val combined = builtIn + appProviders
        cachedProviders = combined
        combined
    }

    fun matchPrefixSuggestion(
        query: String,
        providers: List<SearchProvider>,
    ): PrefixSuggestion? {
        val rawLower = query.lowercase(Locale.getDefault())
        val trimmed = rawLower.trimStart()
        if (trimmed.length < 2) return null

        var best: PrefixSuggestion? = null
        for (provider in providers) {
            for ((aliasIdx, alias) in provider.aliases.withIndex()) {
                val lowerAlias = alias.lowercase(Locale.getDefault())
                if (lowerAlias.startsWith(trimmed)) {
                    val suffix = alias.substring(trimmed.length)
                    val exactBonus = if (lowerAlias == trimmed) 40 else 0
                    val primaryBonus = if (aliasIdx == 0) 20 else 0
                    val lengthRatio = (trimmed.length * 30) / lowerAlias.length.coerceAtLeast(1)
                    val score = provider.priority + exactBonus + primaryBonus + lengthRatio
                    if (best == null || score > best.score) {
                        best = PrefixSuggestion(
                            provider = provider,
                            matchedAlias = alias,
                            ghostSuffix = suffix,
                            score = score,
                        )
                    }
                }
            }
        }
        return best
    }

    fun matchInternalCommands(
        context: Context,
        query: String,
        onOpenExtensions: (() -> Unit)? = null,
        onOpenStore: (() -> Unit)? = null,
    ): List<InternalCommandItem> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) return emptyList()

        val commands = listOf(
            InternalCommandItem(
                id = "cmd:settings",
                title = "App Settings & Configuration",
                description = "Open Komorebi configuration, themes, permissions, and backup",
                keywords = listOf("settings", "config", "configure", "preferences", "komorebi", "essentials"),
                iconRes = R.drawable.rounded_settings_24,
                onExecute = { ctx ->
                    ctx.startActivity(
                        Intent().setClassName(ctx, "com.sameerasw.essentials.SettingsActivity").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
            ),
            InternalCommandItem(
                id = "cmd:search_settings",
                title = "Search Overlay & Bar Settings",
                description = "Configure search tabs, web engine, bubbles, and searchbar appearance",
                keywords = listOf("search settings", "searchbar", "config search", "pixel search"),
                iconRes = R.drawable.rounded_search_24,
                onExecute = { ctx ->
                    ctx.startActivity(
                        Intent(ctx, PixelSearchbarSettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
            ),
            InternalCommandItem(
                id = "cmd:permissions",
                title = "Permissions & Shizuku Bridge",
                description = "Manage Contacts, Media, Calendar, Shizuku, and privileged access",
                keywords = listOf("permissions", "shizuku", "root", "access", "config"),
                iconRes = R.drawable.rounded_shield_24,
                onExecute = { ctx ->
                    ctx.startActivity(
                        Intent().setClassName(ctx, "com.sameerasw.essentials.SettingsActivity").apply {
                            putExtra("expand_permissions", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
            ),
            InternalCommandItem(
                id = "cmd:extensions",
                title = "Extensions & Plugins",
                description = "Manage installed Raycast & Vicinae extensions",
                keywords = listOf("extensions", "plugins", "raycast", "vicinae", "addons"),
                iconRes = R.drawable.rounded_extension_24,
                onExecute = { ctx ->
                    if (onOpenExtensions != null) {
                        onOpenExtensions()
                    } else {
                        ctx.startActivity(
                            ExtensionsActivity.createIntent(ctx, openStore = false).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                },
            ),
            InternalCommandItem(
                id = "cmd:store",
                title = "Extension Store",
                description = "Browse and install Raycast & Vicinae extensions",
                keywords = listOf("store", "marketplace", "install", "raycast store", "extensions"),
                iconRes = R.drawable.rounded_storefront_24,
                onExecute = { ctx ->
                    if (onOpenStore != null) {
                        onOpenStore()
                    } else {
                        ctx.startActivity(
                            ExtensionsActivity.createIntent(ctx, openStore = true).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                },
            ),
        )

        val extensionCommands = runCatching {
            RaycastExtensionRepository.listInstalledExtensions(context).flatMap { ext ->
                ext.manifest.commands.map { cmd ->
                    val cmdTitle = cmd.title.ifBlank { cmd.name }
                    InternalCommandItem(
                        id = "ext:${ext.id}:${cmd.name}",
                        title = cmdTitle,
                        description = "${ext.displayTitle} — ${cmd.description.ifBlank { "Run extension command" }}",
                        keywords = listOfNotNull(
                            ext.displayTitle.lowercase(Locale.getDefault()),
                            ext.manifest.name.lowercase(Locale.getDefault()),
                            cmdTitle.lowercase(Locale.getDefault()),
                            cmd.name.lowercase(Locale.getDefault()),
                        ) + cmd.keywords.orEmpty().map { it.lowercase(Locale.getDefault()) },
                        iconRes = R.drawable.rounded_extension_24,
                        onExecute = { ctx ->
                            ctx.startActivity(
                                ExtensionRunnerActivity.createIntent(ctx, ext.id, cmd.name).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        },
                    )
                }
            }
        }.getOrDefault(emptyList())

        return (commands + extensionCommands).filter { cmd ->
            cmd.title.lowercase(Locale.getDefault()).contains(q) ||
                cmd.keywords.any { it.contains(q) || q.contains(it) }
        }
    }

    suspend fun discoverShortcuts(
        context: Context,
        query: String,
        packageFilter: String? = null,
    ): List<DiscoveredShortcut> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val all = if (now - lastShortcutsFetchMs < SHORTCUT_CACHE_TTL_MS && cachedShortcuts.isNotEmpty()) {
            cachedShortcuts
        } else {
            val loaded = fetchAllShortcuts(context)
            cachedShortcuts = loaded
            lastShortcutsFetchMs = now
            loaded
        }

        val q = query.trim().lowercase(Locale.getDefault())
        all.filter { item ->
            val pkgOk = packageFilter == null || item.packageName == packageFilter
            val queryOk = q.isEmpty() ||
                item.shortLabel.lowercase(Locale.getDefault()).contains(q) ||
                item.longLabel.lowercase(Locale.getDefault()).contains(q) ||
                item.appName.lowercase(Locale.getDefault()).contains(q)
            pkgOk && queryOk
        }.take(12)
    }

    private fun fetchAllShortcuts(context: Context): List<DiscoveredShortcut> {
        val pm = context.packageManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            }
            val infos = runCatching {
                launcherApps.getShortcuts(query, Process.myUserHandle())
            }.getOrNull().orEmpty()

            if (infos.isNotEmpty()) {
                return infos.mapNotNull { info ->
                    val shortLabel = info.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val longLabel = info.longLabel?.toString()?.takeIf { it.isNotBlank() } ?: shortLabel
                    val pkg = info.`package`
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    val iconBmp = runCatching {
                        launcherApps.getShortcutIconDrawable(info, 0)?.let { drawableToBitmap(it).asImageBitmap() }
                    }.getOrNull() ?: loadAppIcon(pm, pkg)

                    DiscoveredShortcut(
                        id = "${pkg}:${info.id}",
                        packageName = pkg,
                        appName = appName,
                        shortLabel = shortLabel,
                        longLabel = longLabel,
                        intent = info.intent,
                        icon = iconBmp,
                        fallbackIconRes = R.drawable.rounded_action_key_24,
                        fromShizuku = false,
                    )
                }
            }
        }

        // Shizuku fallback when app is not set as default launcher
        if (ShizukuUtils.hasPermission()) {
            return parseShortcutsViaShizuku(pm)
        }
        return emptyList()
    }

    private fun parseShortcutsViaShizuku(pm: PackageManager): List<DiscoveredShortcut> {
        val output = runCatching {
            val proc = ShizukuProcessHelper.newProcess(arrayOf("sh", "-c", "dumpsys shortcut")) ?: return emptyList()
            val text = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            text
        }.getOrNull() ?: return emptyList()

        val results = mutableListOf<DiscoveredShortcut>()
        val seen = HashSet<String>()
        var currentPkg = ""
        var currentId = ""
        var shortLabel = ""
        var longLabel = ""
        var componentStr = ""

        val pkgRegex = Regex("""Package:\s+([a-zA-Z0-9._]+)""")
        val shortcutRegex = Regex("""ShortcutInfo\s+\{id=([^,]+),\s*flags=.*""")
        val pkgAttrRegex = Regex("""packageName=([a-zA-Z0-9._]+)""")
        val shortRegex = Regex("""shortLabel=([^,\}]+)""")
        val longRegex = Regex("""longLabel=([^,\}]+)""")
        val activityRegex = Regex("""activity=ComponentInfo\{([^\}]+)\}""")

        fun flush() {
            if (currentPkg.isNotBlank() && currentId.isNotBlank() && shortLabel.isNotBlank()) {
                val key = "$currentPkg:$currentId"
                if (seen.add(key)) {
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(currentPkg, 0)).toString()
                    }.getOrDefault(currentPkg)
                    val launchIntent = componentStr.takeIf { it.contains("/") }?.let { comp ->
                        ComponentName.unflattenFromString(comp)?.let { cn ->
                            Intent(Intent.ACTION_MAIN).apply {
                                component = cn
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                    } ?: pm.getLaunchIntentForPackage(currentPkg)

                    results.add(
                        DiscoveredShortcut(
                            id = key,
                            packageName = currentPkg,
                            appName = appName,
                            shortLabel = shortLabel.trim(),
                            longLabel = longLabel.ifBlank { shortLabel }.trim(),
                            intent = launchIntent,
                            icon = loadAppIcon(pm, currentPkg),
                            fallbackIconRes = R.drawable.rounded_action_key_24,
                            fromShizuku = true,
                        ),
                    )
                }
            }
            currentId = ""
            shortLabel = ""
            longLabel = ""
            componentStr = ""
        }

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            pkgRegex.find(line)?.let {
                flush()
                currentPkg = it.groupValues[1]
            }
            if (line.startsWith("ShortcutInfo {")) {
                flush()
                shortcutRegex.find(line)?.let { currentId = it.groupValues[1].trim() }
                pkgAttrRegex.find(line)?.let { currentPkg = it.groupValues[1].trim() }
            }
            shortRegex.find(line)?.let { shortLabel = it.groupValues[1] }
            longRegex.find(line)?.let { longLabel = it.groupValues[1] }
            activityRegex.find(line)?.let { componentStr = it.groupValues[1] }
            if (results.size >= 60) break
        }
        flush()
        return results
    }

    fun launchShortcut(context: Context, shortcut: DiscoveredShortcut): Boolean {
        val rawId = shortcut.id.substringAfter(":", shortcut.id)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (!shortcut.fromShizuku && launcherApps != null && launcherApps.hasShortcutHostPermission()) {
            val ok = runCatching {
                launcherApps.startShortcut(
                    shortcut.packageName,
                    rawId,
                    null,
                    null,
                    Process.myUserHandle(),
                )
                true
            }.getOrDefault(false)
            if (ok) return true
        }

        shortcut.intent?.let { intent ->
            val ok = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }

        if (ShizukuUtils.hasPermission()) {
            ShizukuUtils.runCommand("cmd shortcut launch-shortcut ${shortcut.packageName} $rawId --user 0")
            return true
        }

        val fallback = context.packageManager.getLaunchIntentForPackage(shortcut.packageName)
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            return true
        }
        return false
    }

    suspend fun discoverAppFunctions(
        context: Context,
        query: String,
    ): List<AppFunctionItem> = withContext(Dispatchers.IO) {
        if (!AppFunctionAvailabilityManager.isSupported(context)) return@withContext emptyList()
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) return@withContext emptyList()

        val pm = context.packageManager
        val services = runCatching {
            pm.queryIntentServices(Intent(ACTION_APP_FUNCTION_SERVICE), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        services.mapNotNull { ri ->
            val si = ri.serviceInfo ?: return@mapNotNull null
            val pkg = si.packageName ?: return@mapNotNull null
            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val svcLabel = runCatching { ri.loadLabel(pm).toString() }.getOrDefault("App Function")
            if (!appName.lowercase(Locale.getDefault()).contains(q) &&
                !svcLabel.lowercase(Locale.getDefault()).contains(q)
            ) {
                return@mapNotNull null
            }

            AppFunctionItem(
                id = "appfn:$pkg:${si.name}",
                packageName = pkg,
                appName = appName,
                functionLabel = svcLabel.ifBlank { "App Function" },
                icon = loadAppIcon(pm, pkg),
                launchIntent = pm.getLaunchIntentForPackage(pkg),
            )
        }.distinctBy { it.id }.take(5)
    }

    suspend fun resolveEntities(
        context: Context,
        query: String,
        discoveredShortcuts: List<DiscoveredShortcut>,
    ): List<EntityRouteItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) return@withContext emptyList()

        val results = mutableListOf<EntityRouteItem>()
        val seenAddresses = HashSet<String>()

        fun addRoute(id: String, label: String, address: String, sourceLabel: String) {
            val clean = address.trim()
            if (clean.isEmpty() || !seenAddresses.add(clean.lowercase(Locale.getDefault()))) return
            val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            results.add(
                EntityRouteItem(
                    id = id,
                    label = label,
                    address = clean,
                    sourceLabel = sourceLabel,
                    navigationIntent = navIntent,
                    mapIntent = mapIntent,
                ),
            )
        }

        val isHomeQuery = "home".startsWith(q) || q == "home"
        val isWorkQuery = "work".startsWith(q) || "office".startsWith(q) || q == "work"

        // 1. Saved Places in SettingsRepository (if configured)
        val repo = SettingsRepository(context)
        if (isHomeQuery) {
            repo.getHomeAddress()?.takeIf { it.isNotBlank() }?.let { addr ->
                addRoute("saved:home", "Home", addr, "Saved Place")
            }
        }
        if (isWorkQuery) {
            repo.getWorkAddress()?.takeIf { it.isNotBlank() }?.let { addr ->
                addRoute("saved:work", "Work", addr, "Saved Place")
            }
        }

        // 2. Contacts Postal Addresses + Me Profile
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val urisToQuery = listOf(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI to "Contacts",
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, "data") to "My Profile",
            )
            for ((uri, source) in urisToQuery) {
                runCatching {
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.LABEL,
                    )
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME)
                        val addrIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                        val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.TYPE)
                        val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.LABEL)
                        while (cursor.moveToNext() && results.size < 6) {
                            val address = if (addrIdx >= 0) cursor.getString(addrIdx) else null
                            if (address.isNullOrBlank()) continue
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                            val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
                            val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx).orEmpty() else ""

                            val typeName = when (type) {
                                ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "Home"
                                ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "Work"
                                else -> customLabel.ifBlank { "Address" }
                            }
                            val matches = (isHomeQuery && type == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME) ||
                                (isWorkQuery && type == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK) ||
                                name.lowercase(Locale.getDefault()).contains(q) ||
                                typeName.lowercase(Locale.getDefault()).contains(q) ||
                                address.lowercase(Locale.getDefault()).contains(q)

                            if (matches) {
                                val title = if (name.isNotBlank()) "$name ($typeName)" else typeName
                                addRoute("contact:$title:$address", title, address, source)
                            }
                        }
                    }
                }
            }
        }

        // 3. Calendar Event Locations
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val now = System.currentTimeMillis()
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                )
                val selection = "${CalendarContract.Events.EVENT_LOCATION} IS NOT NULL AND ${CalendarContract.Events.EVENT_LOCATION} != '' AND ${CalendarContract.Events.DTSTART} >= ?"
                val selectionArgs = arrayOf((now - 86_400_000L).toString())
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC",
                )?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    while (cursor.moveToNext() && results.size < 6) {
                        val location = if (locIdx >= 0) cursor.getString(locIdx) else null
                        if (location.isNullOrBlank()) continue
                        val eventTitle = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "Event"
                        val matches = eventTitle.lowercase(Locale.getDefault()).contains(q) ||
                            location.lowercase(Locale.getDefault()).contains(q) ||
                            (isWorkQuery && (eventTitle.lowercase(Locale.getDefault()).contains("work") || eventTitle.lowercase(Locale.getDefault()).contains("office")))
                        if (matches) {
                            addRoute("cal:$eventTitle:$location", eventTitle, location, "Calendar")
                        }
                    }
                }
            }
        }

        // 4. Discovered Navigation / Place Shortcuts (e.g. Google Maps "Home" / "Work")
        if (isHomeQuery || isWorkQuery) {
            discoveredShortcuts.filter { sc ->
                val lbl = sc.shortLabel.lowercase(Locale.getDefault())
                (isHomeQuery && lbl.contains("home")) || (isWorkQuery && lbl.contains("work"))
            }.forEach { sc ->
                val placeLabel = sc.longLabel.ifBlank { sc.shortLabel }
                addRoute("sc:${sc.id}", "${sc.appName}: $placeLabel", placeLabel, "Shortcut")
            }
        }

        results.take(4)
    }

    suspend fun searchPhotos(
        context: Context,
        query: String,
        limit: Int = 36,
    ): List<PixelSearchResultItem.FileItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        val resolver = context.contentResolver
        val list = mutableListOf<PixelSearchResultItem.FileItem>()
        val seenIds = HashSet<Long>()

        if (PhotoSemanticEngine.getIndexedCount(context) == 0) {
            PhotoIndexWorker.enqueue(context)
        }

        if (trimmed.isNotEmpty()) {
            val semanticHits = runCatching {
                PhotoSemanticEngine.searchSemanticPhotos(context, trimmed, limit)
            }.getOrDefault(emptyList())
            for (hit in semanticHits) {
                if (seenIds.add(hit.id)) {
                    list.add(
                        PixelSearchResultItem.FileItem(
                            id = hit.id,
                            uri = Uri.parse(hit.contentUri),
                            displayName = hit.displayName,
                            mimeType = "image/*",
                            sizeBytes = 0L,
                            dateModified = hit.dateTaken / 1000L,
                            isMedia = true,
                            isImage = true,
                            isGif = hit.displayName.endsWith(".gif", ignoreCase = true),
                            path = hit.bucketName.ifBlank { null },
                            iconRes = FileSearchUtil.getIconForMimeType("image/*", hit.displayName),
                        ),
                    )
                }
            }
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        runCatching {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext() && list.size < limit) {
                    val id = cursor.getLong(idCol)
                    if (seenIds.contains(id)) continue

                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L || name.startsWith(".")) continue

                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else ""
                    val dateSec = cursor.getLong(dateCol)
                    val dateLabel = DateFormat.format("MMMM yyyy MMM", Date(dateSec * 1000L)).toString()

                    val matches = trimmed.isEmpty() ||
                        name.lowercase(Locale.getDefault()).contains(trimmed) ||
                        bucket.lowercase(Locale.getDefault()).contains(trimmed) ||
                        dateLabel.lowercase(Locale.getDefault()).contains(trimmed)

                    if (!matches) continue

                    seenIds.add(id)
                    val mime = cursor.getString(mimeCol) ?: "image/*"
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    list.add(
                        PixelSearchResultItem.FileItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            dateModified = dateSec,
                            isMedia = true,
                            isImage = true,
                            isGif = mime.equals("image/gif", ignoreCase = true) || name.endsWith(".gif", ignoreCase = true),
                            path = bucket.ifBlank { null },
                            iconRes = FileSearchUtil.getIconForMimeType(mime, name),
                        ),
                    )
                }
            }
        }
        list.take(limit)
    }

    fun launchPhotoInGallery(context: Context, item: PixelSearchResultItem.FileItem) {
        val photosIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            setPackage(PHOTOS_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedPhotos = runCatching {
            context.startActivity(photosIntent)
            true
        }.getOrDefault(false)
        if (launchedPhotos) return

        val genericIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedGeneric = runCatching {
            context.startActivity(genericIntent)
            true
        }.getOrDefault(false)
        if (!launchedGeneric) {
            Toast.makeText(context, item.displayName, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchScopedProviderSearch(
        context: Context,
        provider: SearchProvider,
        subQuery: String,
    ): Boolean {
        val pkg = provider.packageName ?: return false
        val trimmed = subQuery.trim()
        if (trimmed.isEmpty()) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            return true
        }

        // Prioritize explicit search targets discovered from PackageManager
        val orderedTargets = provider.targets.sortedBy {
            when {
                it.action == Intent.ACTION_SEARCH -> 0
                it.action == MediaStore.INTENT_ACTION_MEDIA_SEARCH -> 1
                it.scheme == "geo" -> 2
                it.scheme == "google.navigation" -> 3
                it.action == Intent.ACTION_WEB_SEARCH -> 4
                else -> 5
            }
        }

        for (target in orderedTargets) {
            val intent = when (target.scheme) {
                "geo" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(trimmed)}"))
                "google.navigation" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(trimmed)}"))
                else -> Intent(target.action).apply {
                    putExtra(SearchManager.QUERY, trimmed)
                    putExtra("query", trimmed)
                }
            }.apply {
                setPackage(pkg)
                target.componentName?.let { component = it }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return true
            }
        }

        // Retry without fixed componentName in case a different exported activity handles it
        val genericPkgSearch = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(pkg)
            putExtra(SearchManager.QUERY, trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(genericPkgSearch); true }.getOrDefault(false)) {
            return true
        }

        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    private fun loadAppIcon(pm: PackageManager, packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return runCatching {
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable).asImageBitmap()
            iconCache[packageName] = bmp
            bmp
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(144)
        val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(144)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
