/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: TasksRepository.kt
 * Description: Persistent task storage and alarm-backed reminders.
 */

package com.sameerasw.essentials.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class AssistantTask(
    val id: String,
    val title: String,
    val dueAt: Long? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

object TasksRepository {
    const val ACTION_TASK_REMINDER = "com.sameerasw.essentials.assistant.ACTION_TASK_REMINDER"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_DUE_AT = "extra_task_due_at"

    private const val PREFS_NAME = "komorebi_assistant_tasks"
    private const val KEY_TASKS = "tasks"

    private val gson = Gson()
    private val tasksType = object : TypeToken<MutableList<AssistantTask>>() {}.type
    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(
        context: Context,
        title: String,
        dueAt: Long? = null,
    ): AssistantTask {
        val task = AssistantTask(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            dueAt = dueAt,
            done = false,
            createdAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            val tasks = load(context)
            tasks += task
            save(context, tasks)
        }
        if (task.dueAt != null) scheduleReminder(context, task)
        return task
    }

    fun complete(context: Context, id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty()) return false
        var completed: AssistantTask? = null
        synchronized(lock) {
            val tasks = load(context)
            val index = tasks.indexOfFirst { it.id == normalized }
            if (index != -1) {
                val updated = tasks[index].copy(done = true)
                tasks[index] = updated
                completed = updated
                save(context, tasks)
            }
        }
        val task = completed ?: return false
        cancelReminder(context, task)
        return true
    }

    fun delete(context: Context, id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty()) return false
        var removed: AssistantTask? = null
        synchronized(lock) {
            val tasks = load(context)
            val index = tasks.indexOfFirst { it.id == normalized }
            if (index != -1) {
                removed = tasks.removeAt(index)
                save(context, tasks)
            }
        }
        val task = removed ?: return false
        cancelReminder(context, task)
        return true
    }

    fun list(
        context: Context,
        includeDone: Boolean = false,
    ): List<AssistantTask> = synchronized(lock) {
        load(context)
            .filter { includeDone || !it.done }
            .sortedWith(
                compareBy<AssistantTask> { it.dueAt == null }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt },
            )
    }

    private fun load(context: Context): MutableList<AssistantTask> {
        val raw = prefs(context).getString(KEY_TASKS, null) ?: return mutableListOf()
        return runCatching {
            gson.fromJson<MutableList<AssistantTask>>(raw, tasksType) ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    private fun save(
        context: Context,
        tasks: List<AssistantTask>,
    ) {
        prefs(context).edit().putString(KEY_TASKS, gson.toJson(tasks, tasksType)).apply()
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val tasks = synchronized(lock) { load(context).toList() }
        tasks.forEach { task ->
            val due = task.dueAt
            if (!task.done && due != null && due > now) {
                scheduleReminder(context, task)
            }
        }
    }

    private fun scheduleReminder(
        context: Context,
        task: AssistantTask,
    ) {
        val triggerAt = task.dueAt ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = reminderPendingIntent(context, task)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
        }
    }

    private fun cancelReminder(
        context: Context,
        task: AssistantTask,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = reminderPendingIntent(context, task)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderPendingIntent(
        context: Context,
        task: AssistantTask,
    ): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_DUE_AT, task.dueAt ?: 0L)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
