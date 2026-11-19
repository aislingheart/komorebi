package com.sameerasw.essentials.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

data class Quicklink(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val iconName: String = "link",
    val keyword: String = "",
    val isBuiltIn: Boolean = false,
)

object QuicklinksRepository {
    private const val PREF_NAME = "komorebi_quicklinks"
    private const val KEY_LINKS = "quicklinks_json"
    private const val KEY_SEEDED = "seeded_v1"

    private val gson = Gson()
    private val listType = object : TypeToken<List<Quicklink>>() {}.type
    @Volatile private var cache: List<Quicklink>? = null

    private val BUILT_IN_DEFAULTS = listOf(
        Quicklink(
            id = "builtin_google",
            name = "Google",
            urlTemplate = "https://www.google.com/search?q={query}",
            iconName = "search",
            keyword = "g",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_github",
            name = "GitHub",
            urlTemplate = "https://github.com/search?q={query}",
            iconName = "code",
            keyword = "gh",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_youtube",
            name = "YouTube",
            urlTemplate = "https://www.youtube.com/results?search_query={query}",
            iconName = "play",
            keyword = "yt",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_wikipedia",
            name = "Wikipedia",
            urlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
            iconName = "book",
            keyword = "wiki",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_reddit",
            name = "Reddit",
            urlTemplate = "https://www.reddit.com/search/?q={query}",
            iconName = "forum",
            keyword = "r",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_maps",
            name = "Maps",
            urlTemplate = "https://maps.google.com/?q={query}",
            iconName = "map",
            keyword = "maps",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_npm",
            name = "NPM",
            urlTemplate = "https://www.npmjs.com/search?q={query}",
            iconName = "terminal",
            keyword = "npm",
            isBuiltIn = true,
        ),
        Quicklink(
            id = "builtin_stackoverflow",
            name = "StackOverflow",
            urlTemplate = "https://stackoverflow.com/search?q={query}",
            iconName = "help",
            keyword = "so",
            isBuiltIn = true,
        ),
    )

    private fun ensureSeeded(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit()
                .putString(KEY_LINKS, gson.toJson(BUILT_IN_DEFAULTS))
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
    }

    fun list(context: Context): List<Quicklink> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            return loadFromPrefs(context).also { cache = it }
        }
    }

    private fun loadFromPrefs(context: Context): List<Quicklink> {
        val appContext = context.applicationContext
        ensureSeeded(appContext)
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LINKS, null) ?: return BUILT_IN_DEFAULTS
        return runCatching {
            val parsed: List<Quicklink>? = gson.fromJson(raw, listType)
            parsed ?: BUILT_IN_DEFAULTS
        }.getOrDefault(BUILT_IN_DEFAULTS)
    }

    fun save(context: Context, link: Quicklink) {
        val appContext = context.applicationContext
        val current = list(appContext).toMutableList()
        val finalId = link.id.ifBlank { UUID.randomUUID().toString() }
        val cleanLink = link.copy(
            id = finalId,
            name = link.name.trim(),
            urlTemplate = link.urlTemplate.trim(),
            keyword = link.keyword.trim(),
        )
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) {
            current[idx] = cleanLink
        } else {
            current.add(0, cleanLink)
        }
        persist(appContext, current)
    }

    fun delete(context: Context, id: String) {
        val appContext = context.applicationContext
        val current = list(appContext).filterNot { it.id == id }
        persist(appContext, current)
    }

    fun search(context: Context, query: String): List<Quicklink> =
        searchScored(context, query).map { it.first }

    fun searchScored(context: Context, query: String): List<Pair<Quicklink, Int>> {
        val all = list(context)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all.map { it to 1000 }
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val head = parts[0]
        val hasArg = parts.size > 1

        return all.mapNotNull { link ->
            val kw = link.keyword.trim()
            val exactKwMatch = kw.isNotEmpty() && kw.equals(head, ignoreCase = true)
            val score = if (exactKwMatch) {
                if (hasArg) 1000 else 960
            } else {
                val fullScore = com.sameerasw.essentials.utils.FuzzySearchUtil.scoreFields(
                    query = trimmed,
                    primary = link.name,
                    secondary = listOf(kw),
                    tertiary = listOf(link.urlTemplate),
                )
                val headKwScore = if (kw.isNotEmpty() && hasArg) {
                    (com.sameerasw.essentials.utils.FuzzySearchUtil.score(head, kw) * 0.90f).toInt()
                } else {
                    0
                }
                maxOf(fullScore, headKwScore)
            }
            if (score > 0) {
                link to score + com.sameerasw.essentials.utils.SearchUsageTracker.boost(
                    context,
                    com.sameerasw.essentials.utils.SearchUsageTracker.suggestionKey("quicklink", link.id),
                )
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Pair<Quicklink, Int>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.US) },
        )
    }

    fun resolveUrl(template: String, argument: String): String {
        val cleanTemplate = template.trim()
        val cleanArg = argument.trim()
        val encoded = URLEncoder.encode(cleanArg, "UTF-8")
        var resolved = cleanTemplate
            .replace("{query}", encoded, ignoreCase = true)
            .replace("{argument}", encoded, ignoreCase = true)
            .replace("{raw}", cleanArg, ignoreCase = true)

        if (!resolved.startsWith("http://", ignoreCase = true) &&
            !resolved.startsWith("https://", ignoreCase = true) &&
            !resolved.contains("://")
        ) {
            resolved = "https://$resolved"
        }
        return resolved
    }

    fun launch(context: Context, link: Quicklink, argument: String = ""): Boolean {
        return runCatching {
            val url = resolveUrl(link.urlTemplate, argument)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun persist(context: Context, list: List<Quicklink>) {
        cache = list
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LINKS, gson.toJson(list))
            .putBoolean(KEY_SEEDED, true)
            .apply()
    }
}
