/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Search
 * File: InternalSettingsCatalog.kt
 * Description: Comprehensive searchable catalog of all Komorebi internal toggles, multi-choice
 * settings, and app settings screens with live in-search toggling and value changes.
 */

package com.sameerasw.essentials.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.SettingsActivity
import com.sameerasw.essentials.assistant.AssistantTts
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.model.SearchableItem
import com.sameerasw.essentials.launcher.ActiveNotificationStore
import com.sameerasw.essentials.ui.activities.PixelSearchbarSettingsActivity
import com.sameerasw.essentials.utils.AppHapticMode
import com.sameerasw.essentials.utils.AutoBackupManager
import com.sameerasw.essentials.utils.DeviceUtils
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.PermissionUtils

object InternalSettingsCatalog {
    const val FEATURE_KEY_APP_SETTINGS = "Komorebi Settings"
    const val FEATURE_KEY_SEARCH_SETTINGS = "Pixel Searchbar"

    private fun appSettingsIntent(context: Context): Intent =
        Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun searchSettingsIntent(context: Context): Intent =
        Intent(context, PixelSearchbarSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun booleanToggle(
        key: String,
        defaultValue: Boolean,
        fallback: (Context) -> Intent,
        permissionGate: ((Context) -> Pair<Boolean, String>)? = null,
        onAfterChange: ((Context, Boolean) -> Unit)? = null,
    ): InteractiveSettingControl.Toggle =
        InteractiveSettingControl.Toggle(
            readState = { ctx -> SettingsRepository(ctx).getBoolean(key, defaultValue) },
            writeState = { ctx, enabled ->
                if (enabled && permissionGate != null) {
                    val (granted, reason) = permissionGate(ctx)
                    if (!granted) {
                        return@Toggle SettingActionOutcome(
                            applied = false,
                            message = reason,
                            requiresFallbackIntent = true,
                        )
                    }
                }
                SettingsRepository(ctx).putBoolean(key, enabled)
                onAfterChange?.invoke(ctx, enabled)
                SettingActionOutcome(applied = true)
            },
            fallbackIntent = fallback,
        )

    fun all(context: Context): List<SearchableItem> {
        val searchCat = "Search & Launcher"
        val appearanceCat = "Appearance & Feel"
        val voiceCat = "Voice & Assistant"
        val appCat = "Komorebi Settings"

        val items = mutableListOf<SearchableItem>()

        // =========================================================================
        // 1. APPEARANCE & FEEL (SettingsActivity)
        // =========================================================================
        items += SearchableItem(
            title = "Pitch-Black AMOLED Theme",
            description = "Use pure #000000 backgrounds in dark mode",
            category = appearanceCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_dark_mode_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PITCH_BLACK_THEME_ENABLED,
            keywords = listOf("pitch black", "amoled", "oled", "pure black", "dark theme", "theme", "appearance"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PITCH_BLACK_THEME_ENABLED,
                defaultValue = false,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Background Blur",
            description = "Frosted glass blur behind search and menus",
            category = appearanceCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_blur_on_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_USE_BLUR,
            keywords = listOf("blur", "glass", "frosted", "backdrop", "background blur", "effects"),
            control = booleanToggle(
                key = SettingsRepository.KEY_USE_BLUR,
                defaultValue = !DeviceUtils.isBlurProblematicDevice(),
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Ripple Animation",
            description = "Dynamic interactive liquid ripple & glass refraction",
            category = appearanceCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_waves_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_USE_RIPPLE,
            keywords = listOf("ripple", "wave", "refraction", "animation", "shader", "effect", "gesture ripple"),
            control = booleanToggle(
                key = SettingsRepository.KEY_USE_RIPPLE,
                defaultValue = true,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Haptic Feedback",
            description = "Adjust in-app tactile vibration intensity",
            category = appearanceCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_mobile_vibrate_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = "haptic_feedback_mode",
            keywords = listOf("haptic", "haptics", "vibration", "vibrate", "tactile", "feedback", "stronger"),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption(AppHapticMode.DISABLED.name, "Off"),
                    SettingChoiceOption(AppHapticMode.ENABLED.name, "Normal"),
                    SettingChoiceOption(AppHapticMode.STRONGER.name, "Stronger"),
                ),
                readSelectedId = { ctx -> HapticUtil.loadHapticMode(ctx).name },
                writeSelectedId = { ctx, id ->
                    val mode = runCatching { AppHapticMode.valueOf(id) }.getOrDefault(AppHapticMode.ENABLED)
                    HapticUtil.saveHapticMode(ctx, mode)
                    SettingActionOutcome(applied = true)
                },
                fallbackIntent = ::appSettingsIntent,
            ),
        )

        // =========================================================================
        // 2. ACTIVATION, OVERLAY & VOICE (PixelSearchbarSettingsActivity)
        // =========================================================================
        items += SearchableItem(
            title = "Show Search in Overlay",
            description = "Float search over any app via Accessibility overlay",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_mobile_layout_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_OVERLAY_ENABLED,
            keywords = listOf("overlay", "floating", "window", "popup", "accessibility overlay", "instant search"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_OVERLAY_ENABLED,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Use Komorebi as Assistant",
            description = "Open Komorebi from the system digital assistant gesture",
            category = voiceCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_auto_awesome_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_ASSISTANT_ENABLED,
            keywords = listOf("assistant", "digital assistant", "google assistant", "power button", "corner swipe"),
            control = InteractiveSettingControl.Toggle(
                readState = { ctx ->
                    SettingsRepository(ctx).getBoolean(SettingsRepository.KEY_PIXEL_SEARCH_ASSISTANT_ENABLED, false)
                },
                writeState = { ctx, enabled ->
                    SettingsRepository(ctx).putBoolean(SettingsRepository.KEY_PIXEL_SEARCH_ASSISTANT_ENABLED, enabled)
                    if (enabled) {
                        SettingActionOutcome(
                            applied = true,
                            message = "Choose Komorebi as your default digital assistant",
                            requiresFallbackIntent = true,
                        )
                    } else {
                        SettingActionOutcome(applied = true)
                    }
                },
                fallbackIntent = { _ ->
                    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
            ),
        )

        items += SearchableItem(
            title = "Voice Search",
            description = "Show the microphone button for voice queries",
            category = voiceCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_mic_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_VOICE_SEARCH_ENABLED,
            keywords = listOf("voice", "voice search", "microphone", "mic", "speech", "dictation"),
            control = booleanToggle(
                key = SettingsRepository.KEY_VOICE_SEARCH_ENABLED,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Speak Action Responses (TTS)",
            description = "Read matched voice command replies aloud",
            category = voiceCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_record_voice_over_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_VOICE_TTS_ENABLED,
            keywords = listOf("tts", "text to speech", "speak", "talk", "voice feedback", "read aloud"),
            control = booleanToggle(
                key = SettingsRepository.KEY_VOICE_TTS_ENABLED,
                defaultValue = false,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Voice Style",
            description = "Choose text-to-speech pitch and pace preset",
            category = voiceCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_speaker_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_VOICE_TTS_PRESET,
            keywords = listOf("voice style", "voice preset", "tts voice", "pitch", "speed", "deep", "fast"),
            control = InteractiveSettingControl.Choice(
                options = AssistantTts.presets.map { SettingChoiceOption(it.id, it.label) },
                readSelectedId = { ctx ->
                    SettingsRepository(ctx).getString(SettingsRepository.KEY_VOICE_TTS_PRESET, "default")
                        ?: "default"
                },
                writeSelectedId = { ctx, id ->
                    SettingsRepository(ctx).putString(SettingsRepository.KEY_VOICE_TTS_PRESET, id)
                    AssistantTts.speak(ctx, "Komorebi voice preview", id)
                    SettingActionOutcome(applied = true)
                },
                fallbackIntent = ::searchSettingsIntent,
            ),
        )

        // =========================================================================
        // 3. SEARCH RESULT SOURCES (PixelSearchbarSettingsActivity)
        // =========================================================================
        items += SearchableItem(
            title = "Search Apps",
            description = "Include installed apps in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_apps_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_APPS,
            keywords = listOf("search apps", "applications", "launcher apps", "results"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_APPS,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Search Photos & Media",
            description = "Include photos, videos, and audio in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_image_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_MEDIA,
            keywords = listOf("search photos", "media", "gallery", "images", "videos", "pictures"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_MEDIA,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
                permissionGate = { ctx ->
                    PermissionUtils.hasMediaPermissions(ctx) to "Grant media permission in Search settings"
                },
            ),
        )

        items += SearchableItem(
            title = "Search Files & Documents",
            description = "Include local documents and downloads in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_folder_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_FILES,
            keywords = listOf("search files", "documents", "downloads", "storage", "pdf", "local files"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_FILES,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
                permissionGate = { ctx ->
                    val ok = PermissionUtils.hasManageExternalStoragePermission(ctx) ||
                        PermissionUtils.hasStoragePermission(ctx)
                    ok to "Grant storage access in Search settings"
                },
            ),
        )

        items += SearchableItem(
            title = "Search Contacts",
            description = "Include phone contacts in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_import_contacts_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_CONTACTS,
            keywords = listOf("search contacts", "people", "phonebook", "call", "sms"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_CONTACTS,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
                permissionGate = { ctx ->
                    val ok = ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.READ_CONTACTS,
                    ) == PackageManager.PERMISSION_GRANTED
                    ok to "Grant contacts permission in Search settings"
                },
            ),
        )

        items += SearchableItem(
            title = "Search Settings",
            description = "Include system and Komorebi settings in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_settings_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_SETTINGS,
            keywords = listOf("search settings", "toggles", "preferences", "system settings"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_SETTINGS,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Search App Shortcuts",
            description = "Include deep app shortcuts in search results",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_link_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_SHORTCUTS,
            keywords = listOf("shortcuts", "app shortcuts", "deep links", "actions"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_SHORTCUTS,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Web Search",
            description = "Show fallback web search card for queries",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_web_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_WEB,
            keywords = listOf("web search", "internet", "browser", "online search"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_WEB,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Search Active Notifications",
            description = "Find and jump to live status bar notifications",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_notifications_unread_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_NOTIFICATIONS,
            keywords = listOf("notifications", "active notifications", "notification search", "alerts"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_RESULT_NOTIFICATIONS,
                defaultValue = false,
                fallback = { _ ->
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
                permissionGate = { ctx ->
                    ActiveNotificationStore.hasAccess(ctx) to "Enable Komorebi notification access first"
                },
            ),
        )

        // =========================================================================
        // 4. SEARCH LAYOUT, RANKING & WEB BEHAVIOR
        // =========================================================================
        items += SearchableItem(
            title = "One-Handed Reachability Layout",
            description = "Anchor top results near the bottom search bar",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_mobile_hand_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_REACHABILITY,
            keywords = listOf("reachability", "one handed", "bottom", "thumb", "reverse order", "layout"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_REACHABILITY,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Usage-Based Ranking",
            description = "Boost frequently launched apps and actions",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_star_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_USAGE_RANKING,
            keywords = listOf("usage ranking", "frecency", "smart ranking", "learning", "frequent"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_USAGE_RANKING,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Typo-Tolerant Fuzzy Search",
            description = "Match results even with minor spelling mistakes",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_search_check_2_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_TYPO_TOLERANCE,
            keywords = listOf("typo", "fuzzy", "spelling", "tolerance", "matching"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_TYPO_TOLERANCE,
                defaultValue = true,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Hide Results Until Typing",
            description = "Keep the search screen blank until you enter a query",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_search_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_HIDE_INITIAL_RESULTS,
            keywords = listOf("hide initial", "blank", "minimal", "clean start", "hide suggestions"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_HIDE_INITIAL_RESULTS,
                defaultValue = false,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Open Web Search in Bubbles",
            description = "Launch web queries inside an in-app browser sheet",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_bubble_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_BUBBLES_WEB,
            keywords = listOf("bubbles", "web bubbles", "custom tabs", "in-app browser"),
            control = booleanToggle(
                key = SettingsRepository.KEY_PIXEL_SEARCH_BUBBLES_WEB,
                defaultValue = false,
                fallback = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Results Per Section",
            description = "Maximum items shown in each search category",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_apps_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_MAX_RESULTS,
            keywords = listOf("max results", "results per section", "limit", "count", "how many"),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption("3", "3"),
                    SettingChoiceOption("5", "5"),
                    SettingChoiceOption("8", "8"),
                    SettingChoiceOption("10", "10"),
                    SettingChoiceOption("15", "15"),
                    SettingChoiceOption("20", "20"),
                    SettingChoiceOption("0", "All"),
                ),
                readSelectedId = { ctx ->
                    SettingsRepository(ctx).getInt(SettingsRepository.KEY_PIXEL_SEARCH_MAX_RESULTS, 5).toString()
                },
                writeSelectedId = { ctx, id ->
                    val parsed = id.toIntOrNull() ?: 5
                    SettingsRepository(ctx).putInt(SettingsRepository.KEY_PIXEL_SEARCH_MAX_RESULTS, parsed)
                    SettingActionOutcome(applied = true)
                },
                fallbackIntent = ::searchSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Web Search Engine",
            description = "Choose default search provider for web queries",
            category = searchCat,
            parentFeature = searchCat,
            icon = R.drawable.rounded_globe_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_PIXEL_SEARCH_ENGINE,
            keywords = listOf(
                "search engine",
                "google",
                "duckduckgo",
                "brave",
                "startpage",
                "kagi",
                "ecosia",
                "bing",
                "web provider",
            ),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption("google", "Google"),
                    SettingChoiceOption("duckduckgo", "DuckDuckGo"),
                    SettingChoiceOption("brave", "Brave"),
                    SettingChoiceOption("startpage", "Startpage"),
                    SettingChoiceOption("kagi", "Kagi"),
                    SettingChoiceOption("ecosia", "Ecosia"),
                    SettingChoiceOption("bing", "Bing"),
                ),
                readSelectedId = { ctx ->
                    SettingsRepository(ctx).getString(SettingsRepository.KEY_PIXEL_SEARCH_ENGINE, "google")
                        ?: "google"
                },
                writeSelectedId = { ctx, id ->
                    SettingsRepository(ctx).putString(SettingsRepository.KEY_PIXEL_SEARCH_ENGINE, id)
                    SettingActionOutcome(applied = true)
                },
                fallbackIntent = ::searchSettingsIntent,
            ),
        )

        // =========================================================================
        // 5. UPDATES, BACKUPS & ADVANCED (SettingsActivity)
        // =========================================================================
        items += SearchableItem(
            title = "Auto-Check for Updates",
            description = "Check GitHub releases automatically for new Komorebi versions",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_update_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_AUTO_UPDATE_ENABLED,
            keywords = listOf("auto update", "updates", "check updates", "version", "github"),
            control = booleanToggle(
                key = SettingsRepository.KEY_AUTO_UPDATE_ENABLED,
                defaultValue = true,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Check for Pre-Releases",
            description = "Include beta and pre-release builds when checking updates",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_update_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED,
            keywords = listOf("pre-release", "prerelease", "beta", "nightly", "updates"),
            control = booleanToggle(
                key = SettingsRepository.KEY_CHECK_PRE_RELEASES_ENABLED,
                defaultValue = false,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Update Notifications",
            description = "Show a notification when a Komorebi update is available",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_notifications_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_UPDATE_NOTIFICATION_ENABLED,
            keywords = listOf("update notification", "notify updates", "alerts"),
            control = booleanToggle(
                key = SettingsRepository.KEY_UPDATE_NOTIFICATION_ENABLED,
                defaultValue = true,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Auto Backup Schedule",
            description = "Automatically back up Komorebi configuration",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_save_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = AutoBackupManager.KEY_MODE,
            keywords = listOf("backup", "auto backup", "export", "sync", "schedule", "daily", "weekly"),
            control = InteractiveSettingControl.Choice(
                options = listOf(
                    SettingChoiceOption(AutoBackupManager.MODE_NEVER, "Off"),
                    SettingChoiceOption(AutoBackupManager.MODE_UPDATING, "On Update"),
                    SettingChoiceOption(AutoBackupManager.MODE_DAILY, "Daily"),
                    SettingChoiceOption(AutoBackupManager.MODE_WEEKLY, "Weekly"),
                ),
                readSelectedId = { ctx -> AutoBackupManager.getMode(ctx) },
                writeSelectedId = { ctx, id ->
                    AutoBackupManager.setMode(ctx, id)
                    SettingActionOutcome(applied = true)
                },
                fallbackIntent = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Use Usage Access",
            description = "Improve app ranking using Android usage statistics",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_info_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_USE_USAGE_ACCESS,
            keywords = listOf("usage access", "usage stats", "app ranking", "analytics"),
            control = booleanToggle(
                key = SettingsRepository.KEY_USE_USAGE_ACCESS,
                defaultValue = true,
                fallback = ::appSettingsIntent,
            ),
        )

        items += SearchableItem(
            title = "Developer Options (Komorebi)",
            description = "Show experimental and developer controls in Komorebi",
            category = appCat,
            parentFeature = appCat,
            icon = R.drawable.rounded_code_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            targetSettingHighlightKey = SettingsRepository.KEY_DEVELOPER_MODE_ENABLED,
            keywords = listOf("developer", "dev mode", "debug", "experimental", "advanced"),
            control = booleanToggle(
                key = SettingsRepository.KEY_DEVELOPER_MODE_ENABLED,
                defaultValue = false,
                fallback = ::appSettingsIntent,
            ),
        )

        // =========================================================================
        // 6. QUICK NAVIGATION PAGES
        // =========================================================================
        items += SearchableItem(
            title = "Komorebi Settings",
            description = "Appearance, haptics, updates, backups, and permissions",
            category = appCat,
            icon = R.drawable.rounded_settings_24,
            featureKey = FEATURE_KEY_APP_SETTINGS,
            keywords = listOf("settings", "preferences", "komorebi", "about", "app icon", "language", "permissions"),
        )

        items += SearchableItem(
            title = "Search & Launcher Settings",
            description = "Configure search sources, widget, overlay, and voice",
            category = searchCat,
            icon = R.drawable.rounded_search_24,
            featureKey = FEATURE_KEY_SEARCH_SETTINGS,
            keywords = listOf("search settings", "launcher", "widget", "pixel searchbar", "quicklinks"),
        )

        return items
    }
}
