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
import com.sameerasw.essentials.raycast.InstalledRaycastExtension
import com.sameerasw.essentials.raycast.RaycastCommandManifest
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.search.semantic.PhotoIndexWorker
import com.sameerasw.essentials.search.semantic.PhotoSemanticEngine
import com.sameerasw.essentials.shizuku.ShizukuProcessHelper
import com.sameerasw.essentials.ui.activities.ExtensionRunnerActivity
import com.sameerasw.essentials.ui.activities.ExtensionsActivity
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.utils.FileSearchUtil
import com.sameerasw.essentials.utils.FuzzySearchUtil
import com.sameerasw.essentials.utils.SearchContextRanker
import com.sameerasw.essentials.utils.SearchUsageTracker
import com.sameerasw.essentials.utils.ShizukuUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SearchProviderDiscovery {

    private const val PHOTOS_PACKAGE = "com.google.android.apps.photos"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val REDDIT_PACKAGE = "com.reddit.frontpage"
    private const val GITHUB_PACKAGE = "com.github.android"
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val TWITTER_PACKAGE = "com.twitter.android"
    private const val DISCORD_PACKAGE = "com.discord"
    private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val ACTION_APP_FUNCTION_SERVICE = "android.app.appfunctions.AppFunctionService"
    private const val SHORTCUT_CACHE_TTL_MS = 60_000L
    private const val APP_FUNCTION_CACHE_TTL_MS = 60_000L

    private val TELEGRAM_USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,32}$")
    private val CURATED_PACKAGES = setOf(
        YOUTUBE_PACKAGE,
        SPOTIFY_PACKAGE,
        REDDIT_PACKAGE,
        GITHUB_PACKAGE,
        MAPS_PACKAGE,
        PLAY_STORE_PACKAGE,
        TWITTER_PACKAGE,
        DISCORD_PACKAGE,
        TELEGRAM_PACKAGE,
        INSTAGRAM_PACKAGE,
    )

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    @Volatile
    private var cachedProviders: List<SearchProvider>? = null

    @Volatile
    private var cachedShortcuts: List<DiscoveredShortcut> = emptyList()

    @Volatile
    private var lastShortcutsFetchMs: Long = 0L

    @Volatile
    private var cachedAppFunctions: List<AppFunctionItem> = emptyList()

    @Volatile
    private var lastAppFunctionsFetchMs: Long = 0L

    fun invalidateCaches() {
        cachedProviders = null
        lastShortcutsFetchMs = 0L
        lastAppFunctionsFetchMs = 0L
    }

    suspend fun discoverProviders(context: Context): List<SearchProvider> = withContext(Dispatchers.IO) {
        cachedProviders?.let { return@withContext it }

        val pm = context.packageManager
        val photosAppInstalled = isPackageInstalled(pm, PHOTOS_PACKAGE)
        val photosIcon = if (photosAppInstalled) loadAppIcon(pm, PHOTOS_PACKAGE) else null

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
                category = "System",
            ),
            SearchProvider(
                id = "provider:apps",
                title = "Apps",
                subtitle = "Installed applications",
                kind = SearchProviderKind.APPS,
                aliases = listOf("apps", "applications", "launch"),
                iconRes = R.drawable.rounded_apps_24,
                priority = 80,
                category = "System",
            ),
            SearchProvider(
                id = "provider:contacts",
                title = "Contacts",
                subtitle = "People & phone numbers",
                kind = SearchProviderKind.CONTACTS,
                aliases = listOf("contacts", "people", "call", "dial"),
                iconRes = R.drawable.rounded_call_24,
                priority = 80,
                category = "System",
            ),
            SearchProvider(
                id = "provider:files",
                title = "Files",
                subtitle = "Local documents & downloads",
                kind = SearchProviderKind.FILES,
                aliases = listOf("files", "documents", "docs", "downloads", "pdf"),
                iconRes = R.drawable.rounded_description_24,
                priority = 80,
                category = "System",
            ),
            SearchProvider(
                id = "provider:settings",
                title = "Settings",
                subtitle = "System & Komorebi settings",
                kind = SearchProviderKind.SETTINGS,
                aliases = listOf("settings", "preferences", "config"),
                iconRes = R.drawable.rounded_settings_24,
                priority = 75,
                category = "System",
            ),
            SearchProvider(
                id = "provider:web",
                title = "Web",
                subtitle = "Search web or open URL",
                kind = SearchProviderKind.WEB,
                aliases = listOf("web", "browser", "search"),
                iconRes = R.drawable.rounded_language_24,
                priority = 70,
                category = "Web",
            ),
            SearchProvider(
                id = "provider:extensions",
                title = "Extensions",
                subtitle = "Installed Raycast & Vicinae extensions",
                kind = SearchProviderKind.EXTENSION,
                aliases = listOf("extensions", "plugins", "addons", "raycast", "vicinae"),
                iconRes = R.drawable.rounded_extension_24,
                priority = 78,
                category = "Extensions",
            ),
            SearchProvider(
                id = "provider:store",
                title = "Extension Store",
                subtitle = "Browse & install Raycast & Vicinae extensions",
                kind = SearchProviderKind.EXTENSION,
                aliases = listOf("store", "extension store", "marketplace"),
                iconRes = R.drawable.rounded_storefront_24,
                priority = 74,
                category = "Extensions",
            ),
        )

        fun curatedAppProvider(
            id: String,
            title: String,
            subtitleInstalled: String,
            subtitleFallback: String,
            aliases: List<String>,
            packageName: String,
            iconRes: Int,
            priority: Int,
            deepLinkTemplate: String?,
            webUrlTemplate: String?,
            category: String,
            fallbackSchemes: List<String> = emptyList(),
        ): SearchProvider {
            val installed = isPackageInstalled(pm, packageName)
            return SearchProvider(
                id = id,
                title = title,
                subtitle = if (installed) subtitleInstalled else subtitleFallback,
                kind = SearchProviderKind.APP_INTENT,
                aliases = aliases,
                packageName = packageName,
                appIcon = if (installed) loadAppIcon(pm, packageName) else null,
                iconRes = iconRes,
                targets = discoveredMap[packageName].orEmpty(),
                priority = priority,
                deepLinkTemplate = deepLinkTemplate,
                webUrlTemplate = webUrlTemplate,
                category = category,
                fallbackSchemes = fallbackSchemes,
            )
        }

        val curatedProviders = listOf(
            curatedAppProvider(
                id = "provider:youtube",
                title = "YouTube",
                subtitleInstalled = "Search videos in YouTube app",
                subtitleFallback = "Search YouTube on the web",
                aliases = listOf("yt", "youtube"),
                packageName = YOUTUBE_PACKAGE,
                iconRes = R.drawable.rounded_play_arrow_24,
                priority = 86,
                deepLinkTemplate = "https://www.youtube.com/results?search_query={query}",
                webUrlTemplate = "https://www.youtube.com/results?search_query={query}",
                category = "Media",
                fallbackSchemes = listOf("youtube://", "vnd.youtube://"),
            ),
            curatedAppProvider(
                id = "provider:spotify",
                title = "Spotify",
                subtitleInstalled = "Search tracks & artists in Spotify",
                subtitleFallback = "Search Spotify on the web",
                aliases = listOf("sp", "spotify"),
                packageName = SPOTIFY_PACKAGE,
                iconRes = R.drawable.rounded_music_note_24,
                priority = 86,
                deepLinkTemplate = "spotify:search:{query}",
                webUrlTemplate = "https://open.spotify.com/search/{query}",
                category = "Media",
                fallbackSchemes = listOf("spotify:"),
            ),
            curatedAppProvider(
                id = "provider:maps",
                title = "Google Maps",
                subtitleInstalled = "Search places & directions in Maps",
                subtitleFallback = "Search Google Maps on the web",
                aliases = listOf("map", "maps", "google maps"),
                packageName = MAPS_PACKAGE,
                iconRes = R.drawable.rounded_map_search_24,
                priority = 85,
                deepLinkTemplate = "geo:0,0?q={query}",
                webUrlTemplate = "https://www.google.com/maps/search/?api=1&query={query}",
                category = "Navigation",
            ),
            SearchProvider(
                id = "provider:wikipedia",
                title = "Wikipedia",
                subtitle = "Search Wikipedia encyclopedia",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("w", "wiki", "wikipedia"),
                iconRes = R.drawable.rounded_menu_book_24,
                priority = 85,
                webUrlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
                category = "Knowledge",
            ),
            curatedAppProvider(
                id = "provider:github",
                title = "GitHub",
                subtitleInstalled = "Search repositories & code in GitHub",
                subtitleFallback = "Search GitHub on the web",
                aliases = listOf("gh", "github"),
                packageName = GITHUB_PACKAGE,
                iconRes = R.drawable.rounded_code_24,
                priority = 85,
                deepLinkTemplate = "https://github.com/search?q={query}",
                webUrlTemplate = "https://github.com/search?q={query}",
                category = "Developer",
                fallbackSchemes = listOf("github://"),
            ),
            SearchProvider(
                id = "provider:google",
                title = "Google Search",
                subtitle = "Search Google on the web",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("g", "google"),
                iconRes = R.drawable.rounded_search_24,
                priority = 85,
                webUrlTemplate = "https://www.google.com/search?q={query}",
                category = "Web",
            ),
            SearchProvider(
                id = "provider:duckduckgo",
                title = "DuckDuckGo",
                subtitle = "Private web search",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("ddg", "duckduckgo"),
                iconRes = R.drawable.rounded_travel_explore_24,
                priority = 84,
                webUrlTemplate = "https://duckduckgo.com/?q={query}",
                category = "Web",
            ),
            SearchProvider(
                id = "provider:perplexity",
                title = "Perplexity",
                subtitle = "AI-powered answer engine",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("px", "perplexity"),
                iconRes = R.drawable.rounded_auto_awesome_24,
                priority = 84,
                webUrlTemplate = "https://www.perplexity.ai/search?q={query}",
                category = "AI",
            ),
            curatedAppProvider(
                id = "provider:reddit",
                title = "Reddit",
                subtitleInstalled = "Search posts & communities in Reddit",
                subtitleFallback = "Search Reddit on the web",
                aliases = listOf("r", "reddit"),
                packageName = REDDIT_PACKAGE,
                iconRes = R.drawable.rounded_forum_24,
                priority = 85,
                deepLinkTemplate = "https://www.reddit.com/search/?q={query}",
                webUrlTemplate = "https://www.reddit.com/search/?q={query}",
                category = "Social",
                fallbackSchemes = listOf("reddit://"),
            ),
            curatedAppProvider(
                id = "provider:twitter",
                title = "X / Twitter",
                subtitleInstalled = "Search posts in X app",
                subtitleFallback = "Search X on the web",
                aliases = listOf("x", "tw", "twitter"),
                packageName = TWITTER_PACKAGE,
                iconRes = R.drawable.rounded_chat_bubble_24,
                priority = 84,
                deepLinkTemplate = "https://x.com/search?q={query}",
                webUrlTemplate = "https://x.com/search?q={query}",
                category = "Social",
                fallbackSchemes = listOf("twitter://"),
            ),
            curatedAppProvider(
                id = "provider:playstore",
                title = "Play Store",
                subtitleInstalled = "Search apps in Google Play",
                subtitleFallback = "Search Google Play on the web",
                aliases = listOf("play", "play store", "playstore"),
                packageName = PLAY_STORE_PACKAGE,
                iconRes = R.drawable.rounded_storefront_24,
                priority = 84,
                deepLinkTemplate = "market://search?q={query}&c=apps",
                webUrlTemplate = "https://play.google.com/store/search?q={query}&c=apps",
                category = "Apps",
            ),
            curatedAppProvider(
                id = "provider:discord",
                title = "Discord",
                subtitleInstalled = "Open Discord app",
                subtitleFallback = "Open Discord on the web",
                aliases = listOf("dc", "discord"),
                packageName = DISCORD_PACKAGE,
                iconRes = R.drawable.rounded_forum_24,
                priority = 84,
                deepLinkTemplate = "discord://",
                webUrlTemplate = "https://discord.com/app",
                category = "Social",
                fallbackSchemes = listOf("discord://"),
            ),
            curatedAppProvider(
                id = "provider:telegram",
                title = "Telegram",
                subtitleInstalled = "Open @username or Telegram",
                subtitleFallback = "Open @username or Telegram Web",
                aliases = listOf("tg", "telegram"),
                packageName = TELEGRAM_PACKAGE,
                iconRes = R.drawable.rounded_chat_bubble_24,
                priority = 84,
                deepLinkTemplate = "tg://resolve?domain={query}",
                webUrlTemplate = "https://web.telegram.org/",
                category = "Social",
                fallbackSchemes = listOf("tg://"),
            ),
            curatedAppProvider(
                id = "provider:instagram",
                title = "Instagram",
                subtitleInstalled = "Open @user, #tag, or Explore",
                subtitleFallback = "Open Instagram on the web",
                aliases = listOf("ig", "instagram", "insta"),
                packageName = INSTAGRAM_PACKAGE,
                iconRes = R.drawable.rounded_photo_camera_24,
                priority = 84,
                deepLinkTemplate = "instagram://explore",
                webUrlTemplate = "https://www.instagram.com/",
                category = "Social",
                fallbackSchemes = listOf("instagram://explore"),
            ),
            SearchProvider(
                id = "provider:amazon",
                title = "Amazon",
                subtitle = "Search products on Amazon",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("az", "amazon"),
                iconRes = R.drawable.rounded_shopping_cart_24,
                priority = 83,
                webUrlTemplate = "https://www.amazon.com/s?k={query}",
                category = "Shopping",
            ),
            SearchProvider(
                id = "provider:stackoverflow",
                title = "Stack Overflow",
                subtitle = "Search programming Q&A",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("so", "stack", "stackoverflow"),
                iconRes = R.drawable.rounded_code_24,
                priority = 83,
                webUrlTemplate = "https://stackoverflow.com/search?q={query}",
                category = "Developer",
            ),
            SearchProvider(
                id = "provider:npm",
                title = "NPM",
                subtitle = "Search JavaScript & Node packages",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("npm"),
                iconRes = R.drawable.rounded_mobile_code_24,
                priority = 83,
                webUrlTemplate = "https://www.npmjs.com/search?q={query}",
                category = "Developer",
            ),
            SearchProvider(
                id = "provider:imdb",
                title = "IMDb",
                subtitle = "Search movies, TV shows & cast",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("imdb"),
                iconRes = R.drawable.rounded_movie_24,
                priority = 83,
                webUrlTemplate = "https://www.imdb.com/find/?q={query}",
                category = "Media",
            ),
            SearchProvider(
                id = "provider:mdn",
                title = "MDN Web Docs",
                subtitle = "Search web APIs, HTML, CSS & JS docs",
                kind = SearchProviderKind.APP_INTENT,
                aliases = listOf("mdn"),
                iconRes = R.drawable.rounded_library_books_24,
                priority = 83,
                webUrlTemplate = "https://developer.mozilla.org/en-US/search?q={query}",
                category = "Developer",
            ),
        )

        val appProviders = discoveredMap.entries
            .filterNot { (pkg, _) -> pkg in CURATED_PACKAGES }
            .map { (pkg, targets) ->
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
                    category = "Installed Apps",
                )
            }.sortedBy { it.title.lowercase(Locale.getDefault()) }

        val combined = builtIn + curatedProviders + appProviders
        cachedProviders = combined
        combined
    }

    fun matchPrefixSuggestion(
        query: String,
        providers: List<SearchProvider>,
    ): PrefixSuggestion? {
        val rawLower = query.lowercase(Locale.getDefault())
        val trimmed = rawLower.trimStart()
        if (trimmed.isEmpty()) return null

        var best: PrefixSuggestion? = null
        for (provider in providers) {
            for ((aliasIdx, alias) in provider.aliases.withIndex()) {
                val lowerAlias = alias.lowercase(Locale.getDefault())
                val matches = if (trimmed.length == 1) {
                    lowerAlias == trimmed
                } else {
                    lowerAlias.startsWith(trimmed)
                }
                if (matches) {
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
        onRunExtension: ((InstalledRaycastExtension, RaycastCommandManifest) -> Unit)? = null,
    ): List<InternalCommandItem> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) return emptyList()

        val commands = listOf(
            InternalCommandItem(
                id = "cmd:settings",
                title = "App Settings & Configuration",
                description = "Open Komorebi configuration, themes, permissions, and backup",
                keywords = listOf("settings", "config", "configure", "preferences", "komorebi"),
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
                closesSearchUi = onOpenExtensions == null,
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
                closesSearchUi = onOpenStore == null,
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
                        closesSearchUi = onRunExtension == null,
                        onExecute = { ctx ->
                            if (onRunExtension != null) {
                                onRunExtension(ext, cmd)
                            } else {
                                ctx.startActivity(
                                    ExtensionRunnerActivity.createIntent(ctx, ext.id, cmd.name).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }.getOrDefault(emptyList())

        val trimmed = query.trim()
        return (commands + extensionCommands).mapNotNull { cmd ->
            val score = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = cmd.title,
                secondary = cmd.keywords,
                tertiary = listOf(cmd.description),
            )
            if (score > 0) {
                cmd.copy(score = score + SearchUsageTracker.boost(context, SearchUsageTracker.suggestionKey("command", cmd.id)))
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<InternalCommandItem> { it.score }
                .thenBy { it.title.lowercase(Locale.getDefault()) },
        )
    }

    suspend fun getAllShortcuts(context: Context): List<DiscoveredShortcut> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastShortcutsFetchMs < SHORTCUT_CACHE_TTL_MS && cachedShortcuts.isNotEmpty()) {
            cachedShortcuts
        } else {
            val loaded = fetchAllShortcuts(context)
            cachedShortcuts = loaded
            lastShortcutsFetchMs = now
            loaded
        }
    }

    fun searchShortcutsIn(
        context: Context,
        all: List<DiscoveredShortcut>,
        query: String,
        packageFilter: String? = null,
    ): List<DiscoveredShortcut> {
        val trimmed = query.trim()
        val rankContext = SearchContextRanker.snapshot(context)
        return all.mapNotNull { item ->
            val pkgOk = packageFilter == null || item.packageName == packageFilter
            if (!pkgOk) return@mapNotNull null
            if (trimmed.isEmpty()) return@mapNotNull item.copy(score = 1000)

            val score = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = item.shortLabel,
                secondary = listOfNotNull(item.longLabel.takeIf { it.isNotBlank() && it != item.shortLabel }),
                tertiary = listOf(item.appName),
            )
            if (score > 0) {
                item.copy(
                    score = score +
                        SearchUsageTracker.boost(context, SearchUsageTracker.suggestionKey("shortcut", item.id)) +
                        SearchContextRanker.boostForShortcut(rankContext, item.packageName),
                )
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<DiscoveredShortcut> { it.score }
                .thenBy { it.shortLabel.lowercase(Locale.getDefault()) },
        ).take(12)
    }

    suspend fun discoverShortcuts(
        context: Context,
        query: String,
        packageFilter: String? = null,
    ): List<DiscoveredShortcut> =
        searchShortcutsIn(context, getAllShortcuts(context), query, packageFilter)

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

    suspend fun indexAppFunctions(context: Context): List<AppFunctionItem> = withContext(Dispatchers.IO) {
        if (!AppFunctionAvailabilityManager.isSupported(context)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        if (now - lastAppFunctionsFetchMs < APP_FUNCTION_CACHE_TTL_MS && cachedAppFunctions.isNotEmpty()) {
            return@withContext cachedAppFunctions
        }

        val pm = context.packageManager
        val services = runCatching {
            pm.queryIntentServices(Intent(ACTION_APP_FUNCTION_SERVICE), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val loaded = services.mapNotNull { ri ->
            val si = ri.serviceInfo ?: return@mapNotNull null
            val pkg = si.packageName ?: return@mapNotNull null
            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val svcLabel = runCatching { ri.loadLabel(pm).toString() }.getOrDefault("App Function")
            val finalLabel = svcLabel.ifBlank { "App Function" }
            AppFunctionItem(
                id = "appfn:$pkg:${si.name}",
                packageName = pkg,
                appName = appName,
                functionLabel = finalLabel,
                icon = loadAppIcon(pm, pkg),
                launchIntent = pm.getLaunchIntentForPackage(pkg),
                score = 0,
            )
        }.distinctBy { it.id }
        cachedAppFunctions = loaded
        lastAppFunctionsFetchMs = now
        loaded
    }

    fun searchAppFunctionsIn(
        context: Context,
        functions: List<AppFunctionItem>,
        query: String,
    ): List<AppFunctionItem> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return functions.mapNotNull { item ->
            val baseScore = FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = item.functionLabel,
                secondary = listOf(item.appName),
            )
            if (baseScore <= 0) {
                null
            } else {
                item.copy(
                    score = baseScore + SearchUsageTracker.boost(
                        context,
                        SearchUsageTracker.suggestionKey("app_function", item.id),
                    ),
                )
            }
        }.sortedByDescending { it.score }
            .take(5)
    }

    suspend fun discoverAppFunctions(
        context: Context,
        query: String,
    ): List<AppFunctionItem> =
        searchAppFunctionsIn(context, indexAppFunctions(context), query)

    suspend fun resolveEntities(
        context: Context,
        query: String,
        discoveredShortcuts: List<DiscoveredShortcut>,
    ): List<EntityRouteItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val q = trimmed.lowercase(Locale.getDefault())
        if (q.length < 2) return@withContext emptyList()

        val navVerbPrefixes = listOf(
            "navigate to ", "navigate ", "directions to ", "directions ",
            "route to ", "route ", "drive to ", "go to ", "map ",
        )
        val matchedVerb = navVerbPrefixes.firstOrNull { q.startsWith(it) }
        val entityQuery = if (matchedVerb != null) {
            trimmed.substring(matchedVerb.length).trim()
        } else {
            trimmed
        }
        if (entityQuery.length < 2) return@withContext emptyList()
        val eqLower = entityQuery.lowercase(Locale.getDefault())

        val results = mutableListOf<EntityRouteItem>()
        val seenAddresses = HashSet<String>()

        fun addRoute(id: String, label: String, address: String, sourceLabel: String, score: Int) {
            val clean = address.trim()
            if (clean.isEmpty() || !seenAddresses.add(clean.lowercase(Locale.getDefault()))) return
            val navOptions = NavigationApps.routeOptions(context, clean)
            val navIntent = navOptions.firstOrNull()?.intent
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(clean)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val baseScore = if (matchedVerb != null) (score + 120).coerceAtMost(1000) else score
            val finalScore = baseScore +
                SearchContextRanker.boostForNavigation(SearchContextRanker.snapshot(context))
            results.add(
                EntityRouteItem(
                    id = id,
                    label = label,
                    address = clean,
                    sourceLabel = sourceLabel,
                    navigationIntent = navIntent,
                    mapIntent = mapIntent,
                    navigationOptions = navOptions,
                    score = finalScore,
                ),
            )
        }

        val homeScore = FuzzySearchUtil.score(entityQuery, "home")
        val workScore = maxOf(
            FuzzySearchUtil.score(entityQuery, "work"),
            FuzzySearchUtil.score(entityQuery, "office"),
        )
        val isHomeQuery = homeScore >= 600
        val isWorkQuery = workScore >= 600

        // 1. Saved Places in SettingsRepository (if configured)
        val repo = SettingsRepository(context)
        if (isHomeQuery) {
            repo.getHomeAddress()?.takeIf { it.isNotBlank() }?.let { addr ->
                addRoute("saved:home", "Home", addr, "Saved Place", homeScore)
            }
        }
        if (isWorkQuery) {
            repo.getWorkAddress()?.takeIf { it.isNotBlank() }?.let { addr ->
                addRoute("saved:work", "Work", addr, "Saved Place", workScore)
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
                        while (cursor.moveToNext() && results.size < 10) {
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
                            val fuzzyScore = FuzzySearchUtil.scoreFields(
                                query = entityQuery,
                                primary = name.ifBlank { typeName },
                                secondary = listOf(typeName),
                                tertiary = listOf(address),
                            )
                            val typeBoost = when {
                                isHomeQuery && type == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> homeScore
                                isWorkQuery && type == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> workScore
                                else -> 0
                            }
                            val bestScore = maxOf(fuzzyScore, typeBoost)
                            if (bestScore >= 450) {
                                val title = if (name.isNotBlank()) "$name ($typeName)" else typeName
                                addRoute("contact:$title:$address", title, address, source, bestScore)
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
                    while (cursor.moveToNext() && results.size < 10) {
                        val location = if (locIdx >= 0) cursor.getString(locIdx) else null
                        if (location.isNullOrBlank()) continue
                        val eventTitle = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "Event"
                        val fuzzyScore = FuzzySearchUtil.scoreFields(
                            query = entityQuery,
                            primary = eventTitle,
                            secondary = listOf(location),
                        )
                        val workFallback = if (isWorkQuery && (eventTitle.contains("work", ignoreCase = true) || eventTitle.contains("office", ignoreCase = true))) workScore else 0
                        val bestScore = maxOf(fuzzyScore, workFallback)
                        if (bestScore >= 450) {
                            addRoute("cal:$eventTitle:$location", eventTitle, location, "Calendar", bestScore)
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
                addRoute(
                    "sc:${sc.id}",
                    "${sc.appName}: $placeLabel",
                    placeLabel,
                    "Shortcut",
                    if (isHomeQuery) homeScore else workScore,
                )
            }
        }

        results.sortedByDescending { it.score }.take(4)
    }

    suspend fun searchPhotos(
        context: Context,
        query: String,
        limit: Int = 36,
    ): List<PixelSearchResultItem.FileItem> = withContext(Dispatchers.IO) {
        val rawTrimmed = query.trim()
        val trimmed = rawTrimmed.lowercase(Locale.getDefault())
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
        val fuzzyPhotos = mutableListOf<Pair<PixelSearchResultItem.FileItem, Int>>()

        runCatching {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                val maxScan = if (trimmed.isEmpty()) limit else 600
                var scanned = 0
                while (cursor.moveToNext() && scanned < maxScan) {
                    scanned++
                    val id = cursor.getLong(idCol)
                    if (seenIds.contains(id)) continue

                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L || name.startsWith(".")) continue

                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else ""
                    val dateSec = cursor.getLong(dateCol)
                    val dateLabel = DateFormat.format("MMMM yyyy MMM", Date(dateSec * 1000L)).toString()

                    val score = if (rawTrimmed.isEmpty()) {
                        1
                    } else {
                        val stem = name.substringBeforeLast(".")
                        FuzzySearchUtil.scoreFields(
                            query = rawTrimmed,
                            primary = stem.ifBlank { name },
                            secondary = listOf(bucket, name),
                            tertiary = listOf(dateLabel),
                        )
                    }
                    if (score <= 0) continue

                    seenIds.add(id)
                    val mime = cursor.getString(mimeCol) ?: "image/*"
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    fuzzyPhotos.add(
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
                        ) to score,
                    )
                    if (rawTrimmed.isEmpty() && list.size + fuzzyPhotos.size >= limit) break
                }
            }
        }
        val sortedFuzzy = fuzzyPhotos.sortedWith(
            compareByDescending<Pair<PixelSearchResultItem.FileItem, Int>> { it.second }
                .thenByDescending { it.first.dateModified },
        ).map { it.first }
        (list + sortedFuzzy).take(limit)
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
        val pm = context.packageManager
        val pkg = provider.packageName
        val installedPackage = pkg?.takeIf { isPackageInstalled(pm, it) }
        val trimmed = subQuery.trim()
        val encoded = Uri.encode(trimmed)

        fun tryStart(intent: Intent?): Boolean {
            if (intent == null) return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        fun tryViewUri(uriString: String, targetPackage: String? = null, requireResolved: Boolean = false): Boolean {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                if (targetPackage != null) setPackage(targetPackage)
            }
            if (requireResolved && intent.resolveActivity(pm) == null) return false
            return tryStart(intent)
        }

        val (specialDeepLink, specialWebFallback) = resolveSpecialProviderUrls(provider, trimmed, encoded)

        if (trimmed.isEmpty()) {
            if (installedPackage != null) {
                if (specialDeepLink != null && tryViewUri(specialDeepLink, targetPackage = installedPackage)) {
                    return true
                }
                for (scheme in provider.fallbackSchemes) {
                    if (tryViewUri(scheme, targetPackage = installedPackage)) {
                        return true
                    }
                }
                if (tryStart(pm.getLaunchIntentForPackage(installedPackage))) {
                    return true
                }
            }
            if (specialDeepLink != null && tryViewUri(specialDeepLink, requireResolved = true)) {
                return true
            }
            for (scheme in provider.fallbackSchemes) {
                if (tryViewUri(scheme, requireResolved = true)) {
                    return true
                }
            }
            val homeUrl = specialWebFallback ?: resolveHomeUrl(provider.webUrlTemplate)
            if (homeUrl != null && tryViewUri(homeUrl)) {
                return true
            }
            return false
        }

        // 1) App-specific deep-link / package-targeted ACTION_VIEW
        val effectiveDeepLink = specialDeepLink ?: provider.deepLinkTemplate?.replace("{query}", encoded)
        val effectiveWebFallback = specialWebFallback ?: provider.webUrlTemplate?.replace("{query}", encoded)

        if (effectiveDeepLink != null) {
            val parsed = runCatching { Uri.parse(effectiveDeepLink) }.getOrNull()
            val isHttp = parsed?.scheme.equals("http", ignoreCase = true) ||
                parsed?.scheme.equals("https", ignoreCase = true)
            if (isHttp) {
                if (installedPackage != null && tryViewUri(effectiveDeepLink, targetPackage = installedPackage)) {
                    return true
                }
            } else {
                if (installedPackage != null && tryViewUri(effectiveDeepLink, targetPackage = installedPackage)) {
                    return true
                }
                if (tryViewUri(effectiveDeepLink, requireResolved = true)) {
                    return true
                }
            }
        }

        // 2) Existing ACTION_SEARCH / MEDIA_PLAY_FROM_SEARCH if app is installed
        if (installedPackage != null) {
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
                    "geo" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                    "google.navigation" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                    else -> Intent(target.action).apply {
                        putExtra(SearchManager.QUERY, trimmed)
                        putExtra("query", trimmed)
                    }
                }.apply {
                    setPackage(installedPackage)
                    target.componentName?.let { component = it }
                }

                if (tryStart(intent)) {
                    return true
                }
            }

            val genericPkgSearch = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(installedPackage)
                putExtra(SearchManager.QUERY, trimmed)
            }
            if (tryStart(genericPkgSearch)) {
                return true
            }
        }

        // 3) Browser URL fallback (https://...) so tapping any provider never crashes
        if (effectiveWebFallback != null && tryViewUri(effectiveWebFallback)) {
            return true
        }

        // Final fallback for installed apps without web fallback (e.g. Discord or dynamic device apps)
        if (installedPackage != null) {
            for (scheme in provider.fallbackSchemes) {
                if (tryViewUri(scheme, targetPackage = installedPackage)) {
                    return true
                }
            }
            if (tryStart(pm.getLaunchIntentForPackage(installedPackage))) {
                return true
            }
        }
        return false
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun resolveSpecialProviderUrls(
        provider: SearchProvider,
        trimmedQuery: String,
        encodedQuery: String,
    ): Pair<String?, String?> =
        when (provider.id) {
            "provider:telegram" -> {
                if (trimmedQuery.isNotEmpty()) {
                    val username = trimmedQuery.removePrefix("@").trim()
                    if ((trimmedQuery.startsWith("@") && username.isNotEmpty()) ||
                        TELEGRAM_USERNAME_REGEX.matches(trimmedQuery)
                    ) {
                        val encUser = Uri.encode(username)
                        "tg://resolve?domain=$encUser" to "https://t.me/$encUser"
                    } else {
                        "tg://" to "https://web.telegram.org/"
                    }
                } else {
                    "tg://" to "https://web.telegram.org/"
                }
            }
            "provider:instagram" -> {
                when {
                    trimmedQuery.startsWith("#") -> {
                        val tag = trimmedQuery.removePrefix("#").trim()
                        if (tag.isNotEmpty()) {
                            val encTag = Uri.encode(tag)
                            "instagram://tag?name=$encTag" to "https://www.instagram.com/explore/tags/$encTag/"
                        } else {
                            "instagram://explore" to "https://www.instagram.com/"
                        }
                    }
                    trimmedQuery.startsWith("@") -> {
                        val user = trimmedQuery.removePrefix("@").trim()
                        if (user.isNotEmpty()) {
                            val encUser = Uri.encode(user)
                            "instagram://user?username=$encUser" to "https://www.instagram.com/$encUser/"
                        } else {
                            "instagram://explore" to "https://www.instagram.com/"
                        }
                    }
                    else -> {
                        "instagram://explore" to "https://www.instagram.com/"
                    }
                }
            }
            "provider:twitter" -> {
                if (trimmedQuery.isNotEmpty()) {
                    "https://x.com/search?q=$encodedQuery" to "https://x.com/search?q=$encodedQuery"
                } else {
                    "twitter://" to "https://x.com/"
                }
            }
            else -> null to null
        }

    private fun resolveHomeUrl(webUrlTemplate: String?): String? {
        val raw = webUrlTemplate ?: return null
        val uri = runCatching { Uri.parse(raw.replace("{query}", "")) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val authority = uri.authority ?: return null
        return "$scheme://$authority/"
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
