/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Persistence (Room + FTS4)
 * File: KomorebiDatabase.kt
 * Description: Structured Room + FTS4 database for fast full-text searchable local data
 *              (clipboard history, snippets, quicklinks, tasks).
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

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

@Database(
    entities = [
        ClipboardEntryEntity::class,
        ClipboardEntryFts::class,
        LocalTaskEntity::class,
        LocalTaskFts::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class KomorebiDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardEntryDao
    abstract fun taskDao(): LocalTaskDao

    companion object {
        @Volatile
        private var instance: KomorebiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS intent_feedback")
            }
        }

        fun getInstance(context: Context): KomorebiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KomorebiDatabase::class.java,
                    "komorebi_local_v1.db",
                )
                    .addMigrations(MIGRATION_1_2)
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
