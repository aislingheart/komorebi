package com.sameerasw.essentials.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class TextSnippet(
    val id: String,
    val name: String,
    val keyword: String,
    val content: String,
)

object SnippetsRepository {
    private const val PREF_NAME = "komorebi_snippets"
    private const val KEY_SNIPPETS = "snippets_json"
    private const val KEY_SEEDED = "seeded_v1"

    private val gson = Gson()
    private val listType = object : TypeToken<List<TextSnippet>>() {}.type
    @Volatile private var cache: List<TextSnippet>? = null

    private val BUILT_IN_DEFAULTS = listOf(
        TextSnippet(
            id = "builtin_date",
            name = "Today's Date",
            keyword = "!date",
            content = "{date}",
        ),
        TextSnippet(
            id = "builtin_time",
            name = "Current Time",
            keyword = "!time",
            content = "{time}",
        ),
        TextSnippet(
            id = "builtin_iso",
            name = "ISO Timestamp",
            keyword = "!iso",
            content = "{datetime}",
        ),
        TextSnippet(
            id = "builtin_uuid",
            name = "Random UUID v4",
            keyword = "!uuid",
            content = "{uuid}",
        ),
        TextSnippet(
            id = "builtin_signoff",
            name = "Email Sign-off",
            keyword = "!sig",
            content = "Best regards,\n",
        ),
    )

    private fun ensureSeeded(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit()
                .putString(KEY_SNIPPETS, gson.toJson(BUILT_IN_DEFAULTS))
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
    }

    fun list(context: Context): List<TextSnippet> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            return loadFromPrefs(context).also { cache = it }
        }
    }

    private fun loadFromPrefs(context: Context): List<TextSnippet> {
        val appContext = context.applicationContext
        ensureSeeded(appContext)
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SNIPPETS, null) ?: return BUILT_IN_DEFAULTS
        return runCatching {
            val parsed: List<TextSnippet>? = gson.fromJson(raw, listType)
            parsed ?: BUILT_IN_DEFAULTS
        }.getOrDefault(BUILT_IN_DEFAULTS)
    }

    fun save(context: Context, snippet: TextSnippet) {
        val appContext = context.applicationContext
        val current = list(appContext).toMutableList()
        val finalId = snippet.id.ifBlank { UUID.randomUUID().toString() }
        val clean = snippet.copy(
            id = finalId,
            name = snippet.name.trim(),
            keyword = snippet.keyword.trim(),
            content = snippet.content,
        )
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) {
            current[idx] = clean
        } else {
            current.add(0, clean)
        }
        persist(appContext, current)
    }

    fun delete(context: Context, id: String) {
        val appContext = context.applicationContext
        val current = list(appContext).filterNot { it.id == id }
        persist(appContext, current)
    }

    fun search(context: Context, query: String): List<TextSnippet> =
        searchScored(context, query).map { it.first }

    fun searchScored(context: Context, query: String): List<Pair<TextSnippet, Int>> {
        val all = list(context)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all.map { it to 1000 }
        return all.mapNotNull { snippet ->
            val kw = snippet.keyword.trim()
            val exactKw = kw.isNotEmpty() && kw.equals(trimmed, ignoreCase = true)
            val score = if (exactKw) {
                1000
            } else {
                com.sameerasw.essentials.utils.FuzzySearchUtil.scoreFields(
                    query = trimmed,
                    primary = snippet.name,
                    secondary = listOf(kw),
                    tertiary = listOf(snippet.content),
                )
            }
            if (score > 0) {
                snippet to score + com.sameerasw.essentials.utils.SearchUsageTracker.boost(
                    context,
                    com.sameerasw.essentials.utils.SearchUsageTracker.suggestionKey("snippet", snippet.id),
                )
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Pair<TextSnippet, Int>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.US) },
        )
    }

    fun expandContent(content: String, clipboardText: String = ""): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        var result = content
            .replace("{date}", today)
            .replace("{time}", nowTime)
            .replace("{datetime}", nowIso)
            .replace("{clipboard}", clipboardText)

        while (result.contains("{uuid}")) {
            result = result.replaceFirst("{uuid}", UUID.randomUUID().toString())
        }
        return result
    }

    fun expandAndCopy(context: Context, snippet: TextSnippet): String {
        val appContext = context.applicationContext
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java)
        val clipText = runCatching {
            cm?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(appContext)
                ?.toString()
                .orEmpty()
        }.getOrDefault("")

        val expanded = expandContent(snippet.content, clipText)
        cm?.setPrimaryClip(ClipData.newPlainText(snippet.name.ifBlank { "Snippet" }, expanded))
        ClipboardHistoryManager.recordCopiedText(appContext, expanded)
        return expanded
    }

    private fun persist(context: Context, list: List<TextSnippet>) {
        cache = list
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNIPPETS, gson.toJson(list))
            .putBoolean(KEY_SEEDED, true)
            .apply()
    }
}
