/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: MediaSessionTool.kt
 * Description: Media session playback tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

object MediaSessionTool {
    private const val EMPTY_SCHEMA = """{"type":"object","properties":{}}"""

    private const val PLAY_MEDIA_SCHEMA =
        """{"type":"object","properties":{"query":{"type":"string","description":"Song, album, artist, or media search text"}},"required":["query"]}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "media_play",
            title = "Play Media",
            description = "Resume playback on the active media session",
            keywords = listOf("play", "resume", "media", "music", "pause"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> play(context) },
        ),
        AssistantTool(
            id = "media_pause",
            title = "Pause Media",
            description = "Pause playback on the active media session",
            keywords = listOf("pause", "stop", "media", "music"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> pause(context) },
        ),
        AssistantTool(
            id = "media_toggle",
            title = "Toggle Media Playback",
            description = "Play or pause the active media session",
            keywords = listOf("play pause", "toggle media", "media", "music", "resume"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> toggle(context) },
        ),
        AssistantTool(
            id = "media_next",
            title = "Next Track",
            description = "Skip to the next track on the active media session",
            keywords = listOf("next track", "next song", "skip", "media", "music"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> skip(context, next = true) },
        ),
        AssistantTool(
            id = "media_previous",
            title = "Previous Track",
            description = "Skip to the previous track on the active media session",
            keywords = listOf("previous track", "previous song", "back", "media", "music"),
            parametersJson = EMPTY_SCHEMA,
            execute = { context, _ -> skip(context, next = false) },
        ),
        AssistantTool(
            id = "play_media",
            title = "Play Media Search",
            description = "Ask the default media app to play a searched song, album, or artist",
            keywords = listOf("play media", "play song", "play album", "play artist", "listen to", "music search"),
            parametersJson = PLAY_MEDIA_SCHEMA,
            execute = { context, args -> playFromSearch(context, args) },
        ),
    )

    private fun play(context: Context): ToolResult = withController(context) { controller ->
        controller.transportControls.play()
        "Playing ${controller.packageName}"
    }

    private fun pause(context: Context): ToolResult = withController(context) { controller ->
        controller.transportControls.pause()
        "Paused ${controller.packageName}"
    }

    private fun toggle(context: Context): ToolResult = withController(context) { controller ->
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
            "Paused ${controller.packageName}"
        } else {
            controller.transportControls.play()
            "Playing ${controller.packageName}"
        }
    }

    private fun skip(
        context: Context,
        next: Boolean,
    ): ToolResult = withController(context) { controller ->
        if (next) {
            controller.transportControls.skipToNext()
            "Skipped to next track"
        } else {
            controller.transportControls.skipToPrevious()
            "Skipped to previous track"
        }
    }

    private fun playFromSearch(context: Context, args: Map<String, String>): ToolResult {
        val query = (args["query"] ?: args["q"] ?: args["text"])?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolResult(false, "A media search query is required.")
        }
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SearchManager.QUERY, query)
        }
        return try {
            context.startActivity(intent)
            ToolResult(true, "Asked the media app to play \"$query\"")
        } catch (_: ActivityNotFoundException) {
            ToolResult(false, "No media app can handle play-from-search on this device.")
        } catch (e: Exception) {
            ToolResult(false, "Could not start media search: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun withController(
        context: Context,
        block: (MediaController) -> String,
    ): ToolResult {
        val controller = activeController(context)
            ?: return ToolResult(
                false,
                "No active media session. Start playback in a media app and grant notification listener access.",
            )
        return try {
            ToolResult(true, block(controller), controller.packageName)
        } catch (e: Exception) {
            ToolResult(false, "Media control failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun activeController(context: Context): MediaController? {
        val sessions = try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            val component = ComponentName(
                context,
                "com.sameerasw.essentials.services.NotificationListener",
            )
            manager.getActiveSessions(component)
        } catch (_: Exception) {
            return null
        }
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState != null }
            ?: sessions.firstOrNull()
    }
}
