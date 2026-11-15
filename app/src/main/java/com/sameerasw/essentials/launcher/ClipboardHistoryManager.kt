package com.sameerasw.essentials.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        if (clip.itemCount <= 0) return null
        val rawText = clip.getItemAt(0)?.coerceToText(appContext)?.toString()?.trim()
        if (rawText.isNullOrEmpty()) return null

        val currentEntries = getEntries(appContext).toMutableList()
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
        saveTrimmed(appContext, currentEntries)
        return entry
    }

    fun getEntries(context: Context): List<ClipboardEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        return all.filter {
            it.text.contains(trimmed, ignoreCase = true) ||
                it.contentType.contains(trimmed, ignoreCase = true)
        }
    }

    fun copyEntry(context: Context, entry: ClipboardEntry) {
        val appContext = context.applicationContext
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java) ?: return
        lastInternalCopyText = entry.text
        cm.setPrimaryClip(ClipData.newPlainText("Komorebi Clipboard", entry.text))

        // Promote unpinned entry timestamp cleanly without creating duplicates
        val current = getEntries(appContext).toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id || it.text == entry.text }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = existing.copy(timestamp = System.currentTimeMillis())
            saveTrimmed(appContext, current)
        }
    }

    fun recordCopiedText(context: Context, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val appContext = context.applicationContext
        val current = getEntries(appContext).toMutableList()
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
        saveTrimmed(appContext, current)
    }

    fun togglePin(context: Context, id: String) {
        val appContext = context.applicationContext
        val current = getEntries(appContext).map { entry ->
            if (entry.id == id) entry.copy(pinned = !entry.pinned) else entry
        }
        saveTrimmed(appContext, current)
    }

    fun deleteEntry(context: Context, id: String) {
        val appContext = context.applicationContext
        val current = getEntries(appContext).filterNot { it.id == id }
        saveTrimmed(appContext, current)
    }

    fun clearUnpinned(context: Context) {
        val appContext = context.applicationContext
        val pinnedOnly = getEntries(appContext).filter { it.pinned }
        saveTrimmed(appContext, pinnedOnly)
    }

    private fun saveTrimmed(context: Context, entries: List<ClipboardEntry>) {
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
        if (trimmed.matches(Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)) ||
            trimmed.matches(Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/\S*)?$"""))
        ) {
            return "url"
        }
        if (trimmed.matches(Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")) ||
            trimmed.matches(Regex("""^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}(?:\s*,\s*[0-9.]+)?\s*\)$""", RegexOption.IGNORE_CASE)) ||
            trimmed.matches(Regex("""^hsla?\(\s*\d{1,3}\s*,\s*\d{1,3}%\s*,\s*\d{1,3}%(?:\s*,\s*[0-9.]+)?\s*\)$""", RegexOption.IGNORE_CASE))
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
