package com.sameerasw.essentials.assistant

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sameerasw.essentials.utils.FuzzySearchUtil
import java.util.Locale

data class MemoryEntry(
    val key: String,
    val value: String,
    val kind: String,
    val updatedAt: Long,
)

object AssistantMemoryRepository {
    private const val PREFS_NAME = "komorebi_assistant_memory"
    private const val KEY_ENTRIES = "entries"

    private val gson = Gson()
    private val entriesType = object : TypeToken<MutableMap<String, MemoryEntry>>() {}.type
    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun remember(
        context: Context,
        key: String,
        value: String,
        kind: String = "fact",
    ): MemoryEntry {
        val entry = MemoryEntry(
            key = key.trim(),
            value = value.trim(),
            kind = kind.trim().ifEmpty { "fact" },
            updatedAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            val entries = load(context)
            entries[entry.key.lowercase(Locale.ROOT)] = entry
            save(context, entries)
        }
        return entry
    }

    fun recall(
        context: Context,
        query: String,
        limit: Int = 5,
    ): List<MemoryEntry> {
        val max = limit.coerceAtLeast(0)
        val entries = all(context)
        if (query.isBlank()) return entries.take(max)
        return entries
            .map { entry ->
                entry to FuzzySearchUtil.scoreFields(
                    query = query,
                    primary = entry.key,
                    secondary = listOf(entry.value),
                    tertiary = listOf(entry.kind),
                )
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEntry, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .take(max)
            .map { (entry, _) -> entry }
    }

    fun forget(context: Context, key: String): Boolean {
        val normalized = key.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        synchronized(lock) {
            val entries = load(context)
            if (!entries.containsKey(normalized)) return false
            entries.remove(normalized)
            save(context, entries)
        }
        return true
    }

    fun all(context: Context): List<MemoryEntry> =
        synchronized(lock) {
            load(context).values.sortedByDescending { it.updatedAt }
        }

    private fun load(context: Context): MutableMap<String, MemoryEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return mutableMapOf()
        return runCatching {
            gson.fromJson<MutableMap<String, MemoryEntry>>(raw, entriesType) ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())
    }

    private fun save(
        context: Context,
        entries: Map<String, MemoryEntry>,
    ) {
        prefs(context).edit().putString(KEY_ENTRIES, gson.toJson(entries, entriesType)).apply()
    }
}
