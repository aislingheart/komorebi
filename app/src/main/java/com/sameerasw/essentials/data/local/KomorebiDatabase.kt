/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Persistence (Room + FTS4)
 * File: KomorebiDatabase.kt
 * Description: Structured Room + FTS4 database for fast full-text searchable local data
 *              (clipboard history, snippets, quicklinks, tasks) and local intent decision
 *              feedback reinforcement weights.
 */

package com.sameerasw.essentials.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sameerasw.essentials.assistant.LocalIntentDecisionEngine
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "clipboard_entries")
data class ClipboardEntryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "pinned") val pinned: Boolean = false,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
)

@Fts4(contentEntity = ClipboardEntryEntity::class)
@Entity(tableName = "clipboard_entries_fts")
data class ClipboardEntryFts(
    @ColumnInfo(name = "text") val text: String,
)

@Entity(tableName = "local_tasks")
data class LocalTaskEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "completed") val completed: Boolean = false,
    @ColumnInfo(name = "due_at_ms") val dueAtMs: Long? = null,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

@Fts4(contentEntity = LocalTaskEntity::class)
@Entity(tableName = "local_tasks_fts")
data class LocalTaskFts(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "notes") val notes: String,
)

@Entity(tableName = "intent_feedback", primaryKeys = ["token", "tool_id"])
data class IntentFeedbackEntity(
    @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "hits") val hits: Int,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Dao
interface ClipboardEntryDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY pinned DESC, timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<ClipboardEntryEntity>

    @Query(
        """
        SELECT c.* FROM clipboard_entries c
        JOIN clipboard_entries_fts f ON c.rowId = f.docid
        WHERE clipboard_entries_fts MATCH :ftsQuery
        ORDER BY c.pinned DESC, c.timestamp_ms DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 50): List<ClipboardEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ClipboardEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ClipboardEntryEntity>)

    @Query("DELETE FROM clipboard_entries WHERE entry_id = :entryId")
    suspend fun deleteById(entryId: String)

    @Query("DELETE FROM clipboard_entries WHERE pinned = 0")
    suspend fun clearUnpinned()

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun count(): Int
}

@Dao
interface LocalTaskDao {
    @Query("SELECT * FROM local_tasks ORDER BY completed ASC, COALESCE(due_at_ms, 9223372036854775807) ASC, created_at_ms DESC")
    suspend fun all(): List<LocalTaskEntity>

    @Query(
        """
        SELECT t.* FROM local_tasks t
        JOIN local_tasks_fts f ON t.rowId = f.docid
        WHERE local_tasks_fts MATCH :ftsQuery
        ORDER BY t.completed ASC, t.created_at_ms DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 30): List<LocalTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: LocalTaskEntity)

    @Query("DELETE FROM local_tasks WHERE task_id = :taskId")
    suspend fun deleteById(taskId: String)
}

@Dao
interface IntentFeedbackDao {
    @Query("SELECT * FROM intent_feedback ORDER BY updated_at_ms DESC LIMIT 400")
    suspend fun allWeights(): List<IntentFeedbackEntity>

    @Query("SELECT * FROM intent_feedback WHERE token = :token AND tool_id = :toolId LIMIT 1")
    suspend fun get(token: String, toolId: String): IntentFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: IntentFeedbackEntity)

    @Query("DELETE FROM intent_feedback")
    suspend fun clearAll()
}

@Database(
    entities = [
        ClipboardEntryEntity::class,
        ClipboardEntryFts::class,
        LocalTaskEntity::class,
        LocalTaskFts::class,
        IntentFeedbackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KomorebiDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardEntryDao
    abstract fun taskDao(): LocalTaskDao
    abstract fun intentFeedbackDao(): IntentFeedbackDao

    companion object {
        @Volatile
        private var instance: KomorebiDatabase? = null

        fun getInstance(context: Context): KomorebiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KomorebiDatabase::class.java,
                    "komorebi_local_v1.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        /**
         * Formats raw user input into a safe SQLite FTS4 prefix query (`tok1* tok2*`).
         */
        fun toFtsPrefixQuery(raw: String): String {
            val tokens = raw.lowercase(Locale.US)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 }
                .take(8)
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}

/**
 * Local reinforcement store for [LocalIntentDecisionEngine]. Records which tool the user tapped
 * for given query tokens and returns bounded weights (`0f..0.16f` per token) for future ranking.
 */
object IntentFeedbackStore {
    @Volatile
    private var cachedWeights: Map<String, Float>? = null

    suspend fun loadWeights(context: Context): Map<String, Float> {
        cachedWeights?.let { return it }
        return withContext(Dispatchers.IO) {
            val rows = runCatching {
                KomorebiDatabase.getInstance(context).intentFeedbackDao().allWeights()
            }.getOrDefault(emptyList())
            val map = buildMap<String, Float>(rows.size) {
                for (row in rows) {
                    val weight = (0.04f * row.hits).coerceAtMost(0.16f)
                    put("${row.token}->${row.toolId}", weight)
                }
            }
            cachedWeights = map
            map
        }
    }

    suspend fun recordSelection(context: Context, query: String, toolId: String) {
        val tokens = LocalIntentDecisionEngine.tokenize(query).distinct().take(6)
        if (tokens.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = KomorebiDatabase.getInstance(context).intentFeedbackDao()
                val now = System.currentTimeMillis()
                for (tok in tokens) {
                    val existing = dao.get(tok, toolId)
                    val hits = ((existing?.hits ?: 0) + 1).coerceAtMost(20)
                    dao.upsert(IntentFeedbackEntity(tok, toolId, hits, now))
                }
                cachedWeights = null
            }
        }
    }
}
