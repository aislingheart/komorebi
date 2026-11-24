package com.sameerasw.essentials.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class ClipboardEntry(
    val id: String,
    val text: String,
    val timestamp: Long,
    val pinned: Boolean = false,
    val contentType: String = "text",
)

object ClipboardHistoryManager {
    private const val PREF_NAME = "komorebi_clipboard_history"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 100
    private const val MAX_ENTRY_TEXT_LENGTH = 4096

    private val URL_SCHEME_REGEX = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
    private val DOMAIN_REGEX = Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/\S*)?$""")
    private val HEX_COLOR_REGEX = Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val RGB_COLOR_REGEX = Regex(
        """^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}(?:\s*,\s*[0-9.]+)?\s*\)$""",
        RegexOption.IGNORE_CASE,
    )
    private val HSL_COLOR_REGEX = Regex(
        """^hsla?\(\s*\d{1,3}\s*,\s*\d{1,3}%\s*,\s*\d{1,3}%(?:\s*,\s*[0-9.]+)?\s*\)$""",
        RegexOption.IGNORE_CASE,
    )

    private val lock = Any()
    private val gson = Gson()
    private val listType = object : TypeToken<List<ClipboardEntry>>() {}.type

    @Volatile
    private var observerRegistered = false

    @Volatile
    private var lastInternalCopyText: String? = null

    fun startObserving(context: Context) {
        val appContext = context.applicationContext
        captureCurrent(appContext)
        if (observerRegistered) return
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java) ?: return
        synchronized(this) {
            if (observerRegistered) return
            cm.addPrimaryClipChangedListener {
                captureCurrent(appContext)
            }
            observerRegistered = true
        }
    }

    fun captureCurrent(context: Context): ClipboardEntry? {
        val appContext = context.applicationContext
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java) ?: return null
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isSensitive = clip.description?.extras
                ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (isSensitive) return null
        }
        if (clip.itemCount <= 0) return null
        val rawText = clip.getItemAt(0)?.coerceToText(appContext)?.toString()?.trim()
            ?.take(MAX_ENTRY_TEXT_LENGTH)
        if (rawText.isNullOrEmpty()) return null

        synchronized(lock) {
            val currentEntries = getEntriesLocked(appContext).toMutableList()
            val mostRecentUnpinned = currentEntries.firstOrNull { !it.pinned } ?: currentEntries.firstOrNull()
            if (mostRecentUnpinned?.text == rawText) {
                return mostRecentUnpinned
            }

            // If this change came from copyEntry() for an item already in history, avoid creating a duplicate.
            val existingIdx = currentEntries.indexOfFirst { it.text == rawText }
            if (lastInternalCopyText == rawText && existingIdx >= 0) {
                lastInternalCopyText = null
                return currentEntries[existingIdx]
            }
            lastInternalCopyText = null

            val preservedPin = if (existingIdx >= 0) currentEntries[existingIdx].pinned else false
            if (existingIdx >= 0) {
                currentEntries.removeAt(existingIdx)
            }

            val entry = ClipboardEntry(
                id = UUID.randomUUID().toString(),
                text = rawText,
                timestamp = System.currentTimeMillis(),
                pinned = preservedPin,
                contentType = detectContentType(rawText),
            )
            currentEntries.add(0, entry)
            saveTrimmedLocked(appContext, currentEntries)
            return entry
        }
    }

    fun getEntries(context: Context): List<ClipboardEntry> = synchronized(lock) {
        getEntriesLocked(context.applicationContext)
    }

    private fun getEntriesLocked(appContext: Context): List<ClipboardEntry> {
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val parsed: List<ClipboardEntry> = gson.fromJson(raw, listType) ?: emptyList()
            parsed.sortedWith(
                compareByDescending<ClipboardEntry> { it.pinned }
                    .thenByDescending { it.timestamp },
            )
        }.getOrDefault(emptyList())
    }

    fun search(context: Context, query: String): List<ClipboardEntry> {
        val all = getEntries(context)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all
        return all.mapNotNull { entry ->
            val score = com.sameerasw.essentials.utils.FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = entry.text.take(300),
                secondary = listOf(entry.contentType),
            )
            if (score > 0) entry to score else null
        }.sortedWith(
            compareByDescending<Pair<ClipboardEntry, Int>> { it.second }
                .thenByDescending { it.first.pinned }
                .thenByDescending { it.first.timestamp },
        ).map { it.first }
    }

    fun copyEntry(context: Context, entry: ClipboardEntry) {
        val appContext = context.applicationContext
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java) ?: return
        lastInternalCopyText = entry.text
        cm.setPrimaryClip(ClipData.newPlainText("Komorebi Clipboard", entry.text))

        // Promote unpinned entry timestamp cleanly without creating duplicates
        synchronized(lock) {
            val current = getEntriesLocked(appContext).toMutableList()
            val idx = current.indexOfFirst { it.id == entry.id || it.text == entry.text }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = existing.copy(timestamp = System.currentTimeMillis())
                saveTrimmedLocked(appContext, current)
            }
        }
    }

    fun recordCopiedText(context: Context, text: String) {
        val clean = text.trim().take(MAX_ENTRY_TEXT_LENGTH)
        if (clean.isEmpty()) return
        val appContext = context.applicationContext
        synchronized(lock) {
            val current = getEntriesLocked(appContext).toMutableList()
            if (current.firstOrNull()?.text == clean) return
            val existingIdx = current.indexOfFirst { it.text == clean }
            val pinned = if (existingIdx >= 0) current[existingIdx].pinned else false
            if (existingIdx >= 0) current.removeAt(existingIdx)
            current.add(
                0,
                ClipboardEntry(
                    id = UUID.randomUUID().toString(),
                    text = clean,
                    timestamp = System.currentTimeMillis(),
                    pinned = pinned,
                    contentType = detectContentType(clean),
                ),
            )
            saveTrimmedLocked(appContext, current)
        }
    }

    fun togglePin(context: Context, id: String) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val current = getEntriesLocked(appContext).map { entry ->
                if (entry.id == id) entry.copy(pinned = !entry.pinned) else entry
            }
            saveTrimmedLocked(appContext, current)
        }
    }

    fun deleteEntry(context: Context, id: String) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val current = getEntriesLocked(appContext).filterNot { it.id == id }
            saveTrimmedLocked(appContext, current)
        }
    }

    fun clearUnpinned(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val pinnedOnly = getEntriesLocked(appContext).filter { it.pinned }
            saveTrimmedLocked(appContext, pinnedOnly)
        }
    }

    private fun saveTrimmedLocked(context: Context, entries: List<ClipboardEntry>) {
        val sorted = entries.sortedWith(
            compareByDescending<ClipboardEntry> { it.pinned }
                .thenByDescending { it.timestamp },
        )
        val pinned = sorted.filter { it.pinned }
        val remainingCap = (MAX_ENTRIES - pinned.size).coerceAtLeast(0)
        val unpinned = sorted.filterNot { it.pinned }.take(remainingCap)
        val finalList = (pinned + unpinned).sortedWith(
            compareByDescending<ClipboardEntry> { it.pinned }
                .thenByDescending { it.timestamp },
        )
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, gson.toJson(finalList))
            .apply()
    }

    internal fun detectContentType(text: String): String {
        val trimmed = text.trim()
        if (trimmed.matches(URL_SCHEME_REGEX) || trimmed.matches(DOMAIN_REGEX)) {
            return "url"
        }
        if (trimmed.matches(HEX_COLOR_REGEX) ||
            trimmed.matches(RGB_COLOR_REGEX) ||
            trimmed.matches(HSL_COLOR_REGEX)
        ) {
            return "color"
        }
        if (
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            trimmed.contains("fun ") ||
            trimmed.contains("function ") ||
            trimmed.contains("const ") ||
            trimmed.contains("import ") ||
            trimmed.contains("class ") ||
            (trimmed.contains('\n') && (trimmed.contains(";") || trimmed.contains("=>") || trimmed.contains("{")))
        ) {
            return "code"
        }
        return "text"
    }
}
