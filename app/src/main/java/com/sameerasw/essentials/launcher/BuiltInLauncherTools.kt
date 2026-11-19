package com.sameerasw.essentials.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.ui.activities.ExtensionsActivity
import com.sameerasw.essentials.utils.DeviceLockUtils
import com.sameerasw.essentials.utils.PermissionUtils
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.UUID

enum class LauncherToolModal {
    CLIPBOARD_HISTORY,
    EMOJI_PICKER,
    QUICKLINKS,
    SNIPPETS,
    SPEED_TEST,
    BUS_TIMES,
}

data class BuiltInLauncherAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val iconRes: Int,
    val keywords: List<String>,
    val opensModal: LauncherToolModal? = null,
    val closesSearch: Boolean = true,
    val onExecute: (Context) -> Unit,
)

object BuiltInLauncherTools {
    @Volatile
    private var torchEnabled: Boolean = false

    @Volatile
    private var torchCallbackRegistered: Boolean = false

    private val secureRandom = SecureRandom()

    fun allActions(): List<BuiltInLauncherAction> = listOf(
        BuiltInLauncherAction(
            id = "clipboard_history",
            title = "Clipboard History",
            subtitle = "Browse, pin, and paste recent copied items",
            badge = "Tool",
            iconRes = R.drawable.ic_clipboard,
            keywords = listOf("clipboard", "history", "paste", "copy", "clip"),
            opensModal = LauncherToolModal.CLIPBOARD_HISTORY,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "clear_clipboard",
            title = "Clear Clipboard History",
            subtitle = "Remove all unpinned clipboard entries",
            badge = "Action",
            iconRes = R.drawable.rounded_delete_24,
            keywords = listOf("clear clipboard", "delete clipboard", "wipe clipboard", "purge"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                ClipboardHistoryManager.clearUnpinned(ctx)
                Toast.makeText(ctx, "Cleared unpinned clipboard history", Toast.LENGTH_SHORT).show()
            },
        ),
        BuiltInLauncherAction(
            id = "emoji_picker",
            title = "Emoji & Symbols",
            subtitle = "Search and copy emojis with recent picks",
            badge = "Tool",
            iconRes = R.drawable.ic_emoji,
            keywords = listOf("emoji", "emojis", "symbol", "smiley", "reaction", "icon"),
            opensModal = LauncherToolModal.EMOJI_PICKER,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "quicklinks",
            title = "Quicklinks",
            subtitle = "Search, launch, and manage web shortcuts",
            badge = "Tool",
            iconRes = R.drawable.rounded_link_24,
            keywords = listOf("quicklink", "quicklinks", "shortcut", "bookmark", "url", "web"),
            opensModal = LauncherToolModal.QUICKLINKS,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "snippets",
            title = "Snippets",
            subtitle = "Expand reusable text templates & placeholders",
            badge = "Tool",
            iconRes = R.drawable.rounded_edit_note_24,
            keywords = listOf("snippet", "snippets", "template", "text", "macro", "expand"),
            opensModal = LauncherToolModal.SNIPPETS,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "speed_test",
            title = "Internet Speed Test",
            subtitle = "Measure latency, download & upload Mbps natively",
            badge = "Tool",
            iconRes = R.drawable.rounded_speed_24,
            keywords = listOf("speed", "speedtest", "internet", "network", "ping", "bandwidth", "ookla"),
            opensModal = LauncherToolModal.SPEED_TEST,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "bus_times",
            title = "Nearby Bus Times",
            subtitle = "Find nearby stops and live departures using your location",
            badge = "Tool",
            iconRes = R.drawable.rounded_directions_bus_24,
            keywords = listOf("bus", "buses", "bus stop", "departures", "transit", "timetable", "nearby"),
            opensModal = LauncherToolModal.BUS_TIMES,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "show_qr_code",
            title = "Show QR Code",
            subtitle = "Display the clipboard (or query) as a scannable QR code",
            badge = "Tool",
            iconRes = R.drawable.rounded_qr_code_24,
            keywords = listOf("qr", "qr code", "barcode", "show qr", "generate qr"),
            opensModal = null,
            closesSearch = false,
            onExecute = {},
        ),
        BuiltInLauncherAction(
            id = "generate_uuid",
            title = "Generate UUID v4",
            subtitle = "Create and copy a random UUID v4",
            badge = "Utility",
            iconRes = R.drawable.rounded_bolt_24,
            keywords = listOf("uuid", "guid", "random", "identifier", "id"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                val uuid = UUID.randomUUID().toString()
                copyAndToast(ctx, "UUID v4", uuid, "Copied UUID: $uuid")
            },
        ),
        BuiltInLauncherAction(
            id = "generate_password",
            title = "Generate Strong Password",
            subtitle = "Create & copy a 20-character secure password",
            badge = "Utility",
            iconRes = R.drawable.rounded_key_24,
            keywords = listOf("password", "pass", "secret", "keygen", "generate", "security"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                val pw = generateStrongPassword(20)
                copyAndToast(ctx, "Generated Password", pw, "Copied 20-char strong password")
            },
        ),
        BuiltInLauncherAction(
            id = "copy_timestamp",
            title = "Current Timestamp",
            subtitle = "Copy current ISO-8601 UTC timestamp",
            badge = "Utility",
            iconRes = R.drawable.rounded_schedule_24,
            keywords = listOf("timestamp", "unix", "epoch", "iso", "date", "time", "now"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                val now = Instant.now()
                val iso = now.toString()
                copyAndToast(ctx, "Timestamp", iso, "Copied $iso (${now.epochSecond})")
            },
        ),
        BuiltInLauncherAction(
            id = "open_clock",
            title = "Open Clock",
            subtitle = "Open your device's Clock app",
            badge = "Clock",
            iconRes = R.drawable.rounded_schedule_24,
            keywords = listOf("clock", "time", "open clock", "show clock", "view clock", "world clock", "clock app"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                ClockIntentLauncher.openClockApp(
                    ctx,
                    failureToast = "No Clock app found",
                )
            },
        ),
        BuiltInLauncherAction(
            id = "open_alarms",
            title = "Open Alarms",
            subtitle = "Show alarms in the Clock app",
            badge = "Clock",
            iconRes = R.drawable.rounded_alarm_24,
            keywords = listOf(
                "alarm", "alarms", "open alarms", "show alarms", "view alarms",
                "wake me", "wake me up", "wake up", "clock",
            ),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                ClockIntentLauncher.launch(
                    ctx,
                    Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    failureToast = "No alarms app found",
                )
            },
        ),
        BuiltInLauncherAction(
            id = "open_timers",
            title = "Open Timers",
            subtitle = "Show active timers in the Clock app",
            badge = "Clock",
            iconRes = R.drawable.rounded_timer_24,
            keywords = listOf(
                "timer", "timers", "open timers", "show timers", "view timers",
                "active timers", "clock", "countdown",
            ),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                ClockIntentLauncher.launch(
                    ctx,
                    Intent(AlarmClock.ACTION_SHOW_TIMERS),
                    failureToast = "No timer app found",
                )
            },
        ),
        BuiltInLauncherAction(
            id = "start_stopwatch",
            title = "Start Stopwatch",
            subtitle = "Open the Clock app's stopwatch",
            badge = "Clock",
            iconRes = R.drawable.rounded_av_timer_24,
            keywords = listOf("stopwatch", "stop watch", "clock", "timer", "countdown", "time"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                ClockIntentLauncher.openClockApp(
                    ctx,
                    failureToast = "No Clock app found",
                )
            },
        ),
        BuiltInLauncherAction(
            id = "toggle_flashlight",
            title = "Toggle Flashlight",
            subtitle = "Turn device rear camera torch on or off",
            badge = "System",
            iconRes = R.drawable.rounded_flashlight_on_24,
            keywords = listOf("flashlight", "torch", "light", "lamp", "flash"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx -> toggleTorch(ctx) },
        ),
        BuiltInLauncherAction(
            id = "wifi_settings",
            title = "Wi-Fi Settings",
            subtitle = "Open system Wi-Fi connections",
            badge = "System",
            iconRes = R.drawable.rounded_wifi_24,
            keywords = listOf("wifi", "wi-fi", "wireless", "network", "internet"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx -> launchSettings(ctx, Settings.ACTION_WIFI_SETTINGS) },
        ),
        BuiltInLauncherAction(
            id = "bluetooth_settings",
            title = "Bluetooth Settings",
            subtitle = "Manage paired Bluetooth devices",
            badge = "System",
            iconRes = R.drawable.rounded_bluetooth_24,
            keywords = listOf("bluetooth", "bt", "pair", "headphones", "devices"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx -> launchSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) },
        ),
        BuiltInLauncherAction(
            id = "display_settings",
            title = "Display & Dark Mode",
            subtitle = "Open brightness, dark theme & display settings",
            badge = "System",
            iconRes = R.drawable.rounded_dark_mode_24,
            keywords = listOf("display", "dark mode", "theme", "brightness", "screen"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx -> launchSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS) },
        ),
        BuiltInLauncherAction(
            id = "sound_dnd_settings",
            title = "Sound & Do Not Disturb",
            subtitle = "Open audio, volume, and DND settings",
            badge = "System",
            iconRes = R.drawable.rounded_do_not_disturb_on_24,
            keywords = listOf("sound", "volume", "dnd", "do not disturb", "mute", "audio"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx -> launchSettings(ctx, Settings.ACTION_SOUND_SETTINGS) },
        ),
        BuiltInLauncherAction(
            id = "battery_settings",
            title = "Battery Settings",
            subtitle = "View battery usage and power saver",
            badge = "System",
            iconRes = R.drawable.rounded_battery_android_frame_6_24,
            keywords = listOf("battery", "power", "saver", "charge"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                if (!launchSettings(ctx, Intent.ACTION_POWER_USAGE_SUMMARY)) {
                    launchSettings(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS)
                }
            },
        ),
        BuiltInLauncherAction(
            id = "developer_options",
            title = "Developer Options",
            subtitle = "Open Android Developer Options",
            badge = "System",
            iconRes = R.drawable.rounded_adb_24,
            keywords = listOf("developer", "adb", "usb", "debugging", "dev"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                if (!launchSettings(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) {
                    launchSettings(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS)
                }
            },
        ),
        BuiltInLauncherAction(
            id = "lock_screen",
            title = "Lock Screen",
            subtitle = "Lock the device screen immediately",
            badge = "System",
            iconRes = R.drawable.rounded_lock_24,
            keywords = listOf("lock", "lock screen", "sleep", "screen off"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                DeviceLockUtils.performLockdownTileAction(ctx, isLongPress = false)
            },
        ),
        BuiltInLauncherAction(
            id = "take_screenshot",
            title = "Take Screenshot",
            subtitle = "Use Android Accessibility or system gesture",
            badge = "System",
            iconRes = R.drawable.rounded_screenshot_region_24,
            keywords = listOf("screenshot", "capture", "screen", "snap"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && PermissionUtils.isAccessibilityServiceEnabled(ctx)) {
                    Toast.makeText(
                        ctx,
                        "Tip: press Power + Volume Down or trigger Komorebi Accessibility action",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        ctx,
                        "Press Power + Volume Down to take a screenshot",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        ),
        BuiltInLauncherAction(
            id = "open_extensions",
            title = "Extensions Store",
            subtitle = "Browse, install, and run Raycast extensions",
            badge = "Extensions",
            iconRes = R.drawable.rounded_extension_24,
            keywords = listOf("extensions", "raycast", "store", "plugins", "vicinae"),
            opensModal = null,
            closesSearch = true,
            onExecute = { ctx ->
                val intent = Intent(ctx, ExtensionsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { ctx.startActivity(intent) }
            },
        ),
    )

    fun search(query: String): List<BuiltInLauncherAction> =
        searchScored(query).map { it.first }

    fun search(context: Context, query: String): List<BuiltInLauncherAction> =
        searchScored(context, query).map { it.first }

    fun searchScored(query: String): List<Pair<BuiltInLauncherAction, Int>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allActions().map { it to 1000 }

        return allActions().mapNotNull { action ->
            val score = com.sameerasw.essentials.utils.FuzzySearchUtil.scoreFields(
                query = trimmed,
                primary = action.title,
                secondary = action.keywords,
                tertiary = listOf(action.subtitle, action.badge),
            )
            if (score > 0) action to score else null
        }.sortedWith(
            compareByDescending<Pair<BuiltInLauncherAction, Int>> { it.second }
                .thenBy { it.first.title.lowercase(Locale.US) },
        )
    }

    fun searchScored(context: Context, query: String): List<Pair<BuiltInLauncherAction, Int>> =
        searchScored(query).map { (action, score) ->
            action to score + com.sameerasw.essentials.utils.SearchUsageTracker.boost(
                context,
                com.sameerasw.essentials.utils.SearchUsageTracker.actionKey(action.id),
            )
        }.sortedWith(
            compareByDescending<Pair<BuiltInLauncherAction, Int>> { it.second }
                .thenBy { it.first.title.lowercase(Locale.US) },
        )

    private fun copyAndToast(context: Context, label: String, value: String, message: String) {
        val appContext = context.applicationContext
        val cm = ContextCompat.getSystemService(appContext, ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        ClipboardHistoryManager.recordCopiedText(appContext, value)
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun generateStrongPassword(length: Int): String {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val symbols = "!@#\$%^&*()-_=+[]{}"
        val all = upper + lower + digits + symbols
        val chars = CharArray(length)
        chars[0] = upper[secureRandom.nextInt(upper.length)]
        chars[1] = lower[secureRandom.nextInt(lower.length)]
        chars[2] = digits[secureRandom.nextInt(digits.length)]
        chars[3] = symbols[secureRandom.nextInt(symbols.length)]
        for (i in 4 until length) {
            chars[i] = all[secureRandom.nextInt(all.length)]
        }
        for (i in chars.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    private fun toggleTorch(context: Context) {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cm == null) {
            Toast.makeText(appContext, "Camera service unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (!torchCallbackRegistered) {
            torchCallbackRegistered = true
            runCatching {
                cm.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            torchEnabled = enabled
                        }
                    },
                    null,
                )
            }
        }
        runCatching {
            val backFlashId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (backFlashId == null) {
                Toast.makeText(appContext, "No camera flash found", Toast.LENGTH_SHORT).show()
                return
            }
            val next = !torchEnabled
            cm.setTorchMode(backFlashId, next)
            torchEnabled = next
            Toast.makeText(
                appContext,
                if (next) "Flashlight turned on" else "Flashlight turned off",
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure {
            Toast.makeText(appContext, "Could not toggle flashlight: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSettings(context: Context, action: String): Boolean {
        return runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
