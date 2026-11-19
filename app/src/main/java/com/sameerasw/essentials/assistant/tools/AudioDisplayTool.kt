/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Launcher Assistant
 * File: AudioDisplayTool.kt
 * Description: Volume and brightness tools for the launcher assistant.
 */

package com.sameerasw.essentials.assistant.tools

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.sameerasw.essentials.utils.LinearBrightness
import com.sameerasw.essentials.utils.ShellUtils
import java.util.Locale
import kotlin.math.roundToInt

object AudioDisplayTool {
    private const val VOLUME_SCHEMA =
        """{"type":"object","properties":{"stream":{"type":"string","enum":["media","ring","alarm","notification"],"description":"Audio stream"},"action":{"type":"string","enum":["up","down","mute","unmute","set"],"description":"Volume action"},"value":{"type":"integer","minimum":0,"maximum":100,"description":"Target volume percent when action is set"}},"required":["stream","action"]}"""

    private const val BRIGHTNESS_SCHEMA =
        """{"type":"object","properties":{"action":{"type":"string","enum":["up","down","set"],"description":"Brightness action"},"value":{"type":"integer","minimum":0,"maximum":100,"description":"Target brightness percent when action is set"}},"required":["action"]}"""

    fun tools(): List<AssistantTool> = listOf(
        AssistantTool(
            id = "volume",
            title = "Volume",
            description = "Change or mute a media, ring, alarm, or notification volume stream",
            keywords = listOf("volume", "sound", "audio", "louder", "quieter", "mute", "unmute"),
            parametersJson = VOLUME_SCHEMA,
            execute = { context, args -> setVolume(context, args) },
        ),
        AssistantTool(
            id = "brightness",
            title = "Brightness",
            description = "Set the screen brightness percentage or raise and lower it",
            keywords = listOf("brightness", "screen", "display", "dim", "brighter", "darker"),
            parametersJson = BRIGHTNESS_SCHEMA,
            execute = { context, args -> setBrightness(context, args) },
        ),
    )

    private fun setVolume(context: Context, args: Map<String, String>): ToolResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service unavailable.")
        val streamName = (args["stream"] ?: "media").trim().lowercase(Locale.US)
        val stream = when (streamName) {
            "ring", "ringer", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification", "notifications" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val action = (args["action"] ?: args["state"] ?: "up").trim().lowercase(Locale.US)
        return try {
            when (action) {
                "up", "raise", "increase" ->
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                "down", "lower", "decrease" ->
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                "mute", "silence" ->
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                "unmute" ->
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                "set" -> {
                    val percent = args["value"]?.toIntOrNull()
                        ?: return ToolResult(false, "Volume set needs an integer value from 0 to 100.")
                    val max = audioManager.getStreamMaxVolume(stream)
                    val index = (percent.coerceIn(0, 100) / 100f * max).roundToInt().coerceIn(0, max)
                    audioManager.setStreamVolume(stream, index, 0)
                }
                else -> return ToolResult(
                    false,
                    "Unsupported volume action '$action'. Use up, down, mute, unmute, or set.",
                )
            }
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            val percent = if (max > 0) (current * 100f / max).roundToInt() else 0
            ToolResult(
                true,
                "Volume ${streamLabel(streamName)} is $percent%",
                "$current/$max",
            )
        } catch (e: Exception) {
            ToolResult(false, "Could not change volume: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun setBrightness(context: Context, args: Map<String, String>): ToolResult {
        val current = try {
            LinearBrightness(context).percent()
        } catch (_: Exception) {
            50
        }
        val action = (args["action"] ?: args["state"] ?: "set").trim().lowercase(Locale.US)
        val target = when (action) {
            "up", "raise", "increase", "brighter" -> current + 10
            "down", "lower", "decrease", "darker" -> current - 10
            "set" -> args["value"]?.toIntOrNull() ?: args["brightness"]?.toIntOrNull()
                ?: return ToolResult(false, "Brightness set needs an integer value from 0 to 100.")
            else -> return ToolResult(
                false,
                "Unsupported brightness action '$action'. Use up, down, or set.",
            )
        }
        val percent = target.coerceIn(0, 100)
        return if (writeBrightness(context, percent)) {
            ToolResult(true, "Brightness set to $percent%", percent.toString())
        } else {
            ToolResult(false, "Brightness needs WRITE_SETTINGS or Shizuku/root.")
        }
    }

    private fun writeBrightness(context: Context, percent: Int): Boolean {
        if (Settings.System.canWrite(context)) {
            val linearBrightness = LinearBrightness(context)
            return try {
                linearBrightness.begin()
                val current = linearBrightness.percent()
                val dx = ((percent - current).toFloat() / 100f) * 1000f
                linearBrightness.slide(dx, 1000f)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (!ShellUtils.isAvailable(context) || !ShellUtils.hasPermission(context)) return false
        val value = (percent / 100f * 255f).roundToInt().coerceIn(1, 255)
        ShellUtils.runCommand(
            context,
            "settings put system screen_brightness_mode 0; " +
                "settings put system screen_brightness $value",
        )
        return true
    }

    private fun streamLabel(name: String): String = when (name) {
        "ring", "ringer", "ringtone" -> "ring"
        "alarm" -> "alarm"
        "notification", "notifications" -> "notification"
        else -> "media"
    }
}
