/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: TasksTool.kt
 * Description: Assistant tools for adding, completing, listing, and deleting tasks.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import com.sameerasw.essentials.assistant.TasksRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TasksTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val ADD_SCHEMA =
        """{"type":"object","properties":{"title":{"type":"string","description":"Task title"},"due":{"type":"string","description":"Optional due time as ISO-8601, yyyy-MM-dd HH:mm, HH:mm, or today/tomorrow"}},"required":["title"]}"""

    private const val ID_SCHEMA =
        """{"type":"object","properties":{"id":{"type":"string","description":"Task ID"}},"required":["id"]}"""

    private const val LIST_SCHEMA =
        """{"type":"object","properties":{"includeDone":{"type":"boolean","description":"Include completed tasks"}}}"""

    private val DUE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "add_task",
            title = "Add Task",
            description = "Add a task with an optional due time and reminder",
            keywords = listOf("add task", "new task", "remind me", "todo", "task", "task add"),
            parametersJson = ADD_SCHEMA,
            execute = { context, args -> addTask(context, args) },
        ),
        AssistantTool(
            id = "complete_task",
            title = "Complete Task",
            description = "Mark a task as done and cancel its reminder",
            keywords = listOf("complete task", "finish task", "done task", "mark done", "task"),
            parametersJson = ID_SCHEMA,
            execute = { context, args -> completeTask(context, args) },
        ),
        AssistantTool(
            id = "list_tasks",
            title = "List Tasks",
            description = "List pending tasks, optionally including completed ones",
            keywords = listOf("list tasks", "my tasks", "show tasks", "todo list", "tasks", "task list", "todo"),
            parametersJson = LIST_SCHEMA,
            execute = { context, args -> listTasks(context, args) },
        ),
        AssistantTool(
            id = "delete_task",
            title = "Delete Task",
            description = "Delete a task by its ID and cancel its reminder",
            keywords = listOf("delete task", "remove task", "cancel task", "task"),
            parametersJson = ID_SCHEMA,
            execute = { context, args -> deleteTask(context, args) },
        ),
    )

    private fun addTask(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val title = (args["title"] ?: args["task"] ?: args["text"] ?: "").trim()
        if (title.isEmpty()) return ToolResult(false, "A task title is required.")

        val dueRaw = (args["due"] ?: args["dueAt"] ?: args["when"] ?: "").trim()
        val dueAt: Long?
        if (dueRaw.isEmpty()) {
            dueAt = null
        } else {
            dueAt = AssistantTimeParser.parseDateTimeMillis(dueRaw)
                ?: return ToolResult(
                    false,
                    "Could not parse the due time '$dueRaw'. Use ISO-8601, yyyy-MM-dd HH:mm, HH:mm, or today/tomorrow.",
                )
        }

        val task = TasksRepository.add(context, title, dueAt)
        val dueText = dueAt?.let { "due ${formatDue(it)}" } ?: "no due time"
        return ToolResult(true, "Added task '${task.title}' ($dueText).", task.id)
    }

    private fun completeTask(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val id = taskId(args)
            ?: return ToolResult(false, "A task ID is required.")
        return if (TasksRepository.complete(context, id)) {
            ToolResult(true, "Completed task $id.")
        } else {
            ToolResult(false, "No task was found with ID $id.")
        }
    }

    private fun listTasks(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val includeDone = parseBoolean(args["includeDone"] ?: args["include_done"] ?: args["all"])
        val tasks = TasksRepository.list(context, includeDone)
        if (tasks.isEmpty()) {
            return ToolResult(true, "No tasks.", "")
        }
        val data = tasks.joinToString("\n") { task ->
            val status = if (task.done) "[x]" else "[ ]"
            val due = task.dueAt?.let { " (due ${formatDue(it)})" }.orEmpty()
            "$status ${task.title}$due — id=${task.id}"
        }
        val pending = tasks.count { !it.done }
        return ToolResult(true, "$pending pending task(s), ${tasks.size} total.", data)
    }

    private fun deleteTask(
        context: Context,
        args: Map<String, String>,
    ): ToolResult {
        val id = taskId(args)
            ?: return ToolResult(false, "A task ID is required.")
        return if (TasksRepository.delete(context, id)) {
            ToolResult(true, "Deleted task $id.")
        } else {
            ToolResult(false, "No task was found with ID $id.")
        }
    }

    private fun taskId(args: Map<String, String>): String? =
        (args["id"] ?: args["taskId"] ?: args["task_id"] ?: args["value"])
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun formatDue(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DUE_FORMATTER)

    private fun parseBoolean(raw: String?): Boolean {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return false
        return value in setOf("1", "true", "yes", "y", "on", "all")
    }
}
