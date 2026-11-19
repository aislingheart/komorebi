/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: RoutineRepository.kt
 * Description: Persistent routine storage with default morning and commute routines.
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import java.util.UUID

data class AssistantRoutine(
    val id: String,
    val name: String,
    val steps: List<String> = emptyList(),
)

object RoutineRepository {
    private const val PREFS_NAME = "komorebi_assistant_routines"
    private const val KEY_ROUTINES = "routines"
    private const val KEY_SEEDED = "seeded"

    private val gson = Gson()
    private val routinesType = object : TypeToken<MutableList<AssistantRoutine>>() {}.type
    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(
        context: Context,
        routine: AssistantRoutine,
    ): AssistantRoutine = synchronized(lock) {
        val name = routine.name.trim()
        val steps = routine.steps.map { it.trim() }.filter { it.isNotEmpty() }
        val routines = load(context)
        val existingIndex = routines.indexOfFirst { existing ->
            (routine.id.isNotBlank() && existing.id == routine.id) ||
                (routine.id.isBlank() && existing.name.equals(name, ignoreCase = true))
        }
        val saved = AssistantRoutine(
            id = when {
                routine.id.isNotBlank() -> routine.id
                existingIndex >= 0 -> routines[existingIndex].id
                else -> UUID.randomUUID().toString()
            },
            name = name,
            steps = steps,
        )
        if (existingIndex >= 0) {
            routines[existingIndex] = saved
        } else {
            routines += saved
        }
        saveAll(context, routines)
        saved
    }

    fun delete(context: Context, id: String): Boolean = synchronized(lock) {
        val normalized = id.trim()
        if (normalized.isEmpty()) return@synchronized false
        val routines = load(context)
        val index = routines.indexOfFirst { it.id == normalized }
        if (index == -1) {
            false
        } else {
            routines.removeAt(index)
            saveAll(context, routines)
            true
        }
    }

    fun list(context: Context): List<AssistantRoutine> = synchronized(lock) {
        load(context).sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun find(context: Context, nameOrAlias: String): AssistantRoutine? {
        val query = nameOrAlias.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return null
        val routines = list(context)
        return routines.firstOrNull { routine ->
            routine.id.lowercase(Locale.ROOT) == query ||
                routine.name.lowercase(Locale.ROOT) == query
        } ?: routines.firstOrNull { routine ->
            val name = routine.name.lowercase(Locale.ROOT)
            name.contains(query) || query.contains(name)
        }
    }

    private fun load(context: Context): MutableList<AssistantRoutine> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_ROUTINES, null)
        val routines = if (raw == null) {
            mutableListOf()
        } else {
            runCatching {
                gson.fromJson<MutableList<AssistantRoutine>>(raw, routinesType) ?: mutableListOf()
            }.getOrDefault(mutableListOf())
        }
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            if (routines.isEmpty()) routines += defaultRoutines()
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            saveAll(context, routines)
        }
        return routines
    }

    private fun saveAll(
        context: Context,
        routines: List<AssistantRoutine>,
    ) {
        prefs(context).edit().putString(KEY_ROUTINES, gson.toJson(routines, routinesType)).apply()
    }

    private fun defaultRoutines(): List<AssistantRoutine> = listOf(
        AssistantRoutine(
            id = UUID.randomUUID().toString(),
            name = "morning",
            steps = listOf("wifi on", "volume set 40", "notification_digest"),
        ),
        AssistantRoutine(
            id = UUID.randomUUID().toString(),
            name = "commute",
            steps = listOf("wifi on", "volume set 60"),
        ),
    )
}
