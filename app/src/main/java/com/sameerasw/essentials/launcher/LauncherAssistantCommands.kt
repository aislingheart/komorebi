package com.sameerasw.essentials.launcher

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.sameerasw.essentials.R
import com.sameerasw.essentials.assistant.tools.AssistantTimeParser
import com.sameerasw.essentials.domain.model.PixelSearchResultItem
import com.sameerasw.essentials.search.SearchIndex
import java.net.URLEncoder
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToLong

object LauncherAssistantCommands {

    fun match(context: Context, query: String): List<BuiltInLauncherAction> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val actions = mutableListOf<BuiltInLauncherAction>()
        val contacts = SearchIndex.peek()?.contacts
        actions += calendarActions(context, trimmed)
        actions += timerActions(context, trimmed)
        alarmAction(context, trimmed)?.let(actions::add)
        if (actions.isEmpty()) {
            messageActions(context, trimmed, contacts).forEach(actions::add)
        }
        callAction(context, trimmed, contacts)?.let(actions::add)
        navigateAction(context, trimmed)?.let(actions::add)
        playAction(context, trimmed)?.let(actions::add)
        weatherAction(context, trimmed)?.let(actions::add)
        cameraAction(context, trimmed)?.let(actions::add)
        connectivityAction(context, trimmed)?.let(actions::add)
        webSearchAction(context, trimmed)?.let(actions::add)
        return actions.take(8)
    }

    private fun calendarActions(context: Context, query: String): List<BuiltInLauncherAction> {
        val match = CALENDAR_REGEX.matchEntire(query) ?: return emptyList()
        val rest = match.groupValues[1].trim()
        if (rest.isBlank()) {
            return listOf(
                action(
                    id = "assistant_open_calendar",
                    title = "Open calendar",
                    subtitle = "Open your calendar app",
                    badge = "Calendar",
                    iconRes = R.drawable.rounded_calendar_month_24,
                ) { CalendarIntentLauncher.openCalendar(context) },
            )
        }
        val parsed = parseCalendarRest(rest)
        val title = parsed.first
        val startRaw = parsed.second
        val begin = startRaw?.let { AssistantTimeParser.parseDateTimeMillis(it) }
        val label = if (startRaw.isNullOrBlank()) title else "$title · $startRaw"
        return listOf(
            action(
                id = "assistant_calendar_${label.hashCode()}",
                title = "Create event: $title",
                subtitle = if (startRaw.isNullOrBlank()) "Choose the time in your calendar app" else startRaw,
                badge = "Calendar",
                iconRes = R.drawable.rounded_calendar_month_24,
            ) {
                CalendarIntentLauncher.createEvent(context, title, begin)
            },
        )
    }

    private fun parseCalendarRest(rest: String): Pair<String, String?> {
        val withTime = Regex(
            "^(?<title>.+?)\\s+(?:at\\s+)?(?<start>(?:tomorrow|today)\\s+(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{1,2}(?::\\d{2})?\\s*(?:am|pm))$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(rest)
        if (withTime != null) {
            return withTime.groups["title"]!!.value.trim() to withTime.groups["start"]!!.value.trim()
        }
        val atSplit = rest.split(Regex("\\s+at\\s+", RegexOption.IGNORE_CASE), limit = 2)
        if (atSplit.size == 2) return atSplit[0].trim() to atSplit[1].trim()
        return rest to null
    }

    private fun timerActions(context: Context, query: String): List<BuiltInLauncherAction> {
        val rawDuration = timerDurationFor(query) ?: return emptyList()
        val seconds = parseDurationSeconds(rawDuration)
        if (seconds != null) return listOf(createTimerAction(context, seconds))
        return buildList {
            add(
                action(
                    id = "assistant_timer_custom",
                    title = "Set a custom timer",
                    subtitle = "Open the timer screen and choose a duration",
                    badge = "Timer",
                    iconRes = R.drawable.rounded_timer_24,
                ) {
                    ClockIntentLauncher.launch(
                        context,
                        Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                        failureToast = "Could not open your Clock app",
                    )
                },
            )
            listOf(60L, 5 * 60L, 10 * 60L, 30 * 60L, 60 * 60L).forEach { add(createTimerAction(context, it)) }
            add(
                action(
                    id = "assistant_timers",
                    title = "Open timers",
                    subtitle = "Show your active timers in the Clock app",
                    badge = "Assistant",
                    iconRes = R.drawable.rounded_timer_24,
                ) {
                    ClockIntentLauncher.launch(
                        context,
                        Intent(AlarmClock.ACTION_SHOW_TIMERS),
                        failureToast = "Could not open your Clock app",
                    )
                },
            )
        }
    }

    private fun createTimerAction(context: Context, seconds: Long): BuiltInLauncherAction {
        val label = formatDuration(seconds)
        return action(
            id = "assistant_timer_$seconds",
            title = "Start a $label timer",
            subtitle = "Starts immediately in the Clock app",
            badge = "Timer",
            iconRes = R.drawable.rounded_timer_24,
        ) {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_MESSAGE, "$label timer")
            }
            ClockIntentLauncher.launch(context, intent, failureToast = "Could not start the timer")
        }
    }

    private fun alarmAction(context: Context, query: String): BuiltInLauncherAction? {
        val rawTime = alarmTimeFor(query) ?: return null
        val time = parseRelativeAlarmTime(rawTime) ?: parseClockTime(rawTime)
        if (time == null) {
            return action(
                id = "assistant_alarms",
                title = "Open alarms",
                subtitle = "Show your alarms in the Clock app",
                badge = "Assistant",
                iconRes = R.drawable.rounded_alarm_24,
            ) {
                ClockIntentLauncher.launch(
                    context,
                    Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    failureToast = "Could not open your Clock app",
                )
            }
        }
        val (hour, minute) = time
        val timeLabel = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        return action(
            id = "assistant_alarm_${hour}_$minute",
            title = "Set alarm for $timeLabel",
            subtitle = "Creates the alarm in the Clock app",
            badge = "Alarm",
            iconRes = R.drawable.rounded_alarm_24,
        ) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            ClockIntentLauncher.launch(context, intent, failureToast = "Could not set the alarm")
        }
    }

    internal enum class MessageChannel { SMS, WHATSAPP }

    internal data class MessageRequest(
        val name: String,
        val body: String,
        val channel: MessageChannel?,
    )

    private fun messageActions(
        context: Context,
        query: String,
        contacts: List<PixelSearchResultItem.ContactItem>?,
    ): List<BuiltInLauncherAction> {
        val request = parseMessageRequest(query, contacts) ?: return emptyList()
        if (request.name.isBlank()) return emptyList()
        val phone = findPhoneNumber(request.name, contacts)
        if (phone == null && request.body.isBlank() && request.channel != MessageChannel.WHATSAPP &&
            request.name.split(" ").size == 1 && !contacts.isNullOrEmpty()
        ) {
            return emptyList()
        }
        val results = mutableListOf<BuiltInLauncherAction>()
        if (request.channel == MessageChannel.WHATSAPP) {
            results += whatsAppAction(context, request.name, request.body, phone)
            results += smsAction(context, request.name, request.body, phone)
        } else {
            results += smsAction(context, request.name, request.body, phone)
            results += whatsAppAction(context, request.name, request.body, phone)
        }
        return results
    }

    internal fun parseMessageRequest(
        query: String,
        contacts: List<PixelSearchResultItem.ContactItem>?,
    ): MessageRequest? {
        PREFIX_TO_REGEX.matchEntire(query)?.let { m ->
            return parseRecipientAndBody(
                rawRest = m.groups["rest"]!!.value,
                channelToken = m.groups["channel"]?.value,
                contacts = contacts,
            )
        }
        RECIPIENT_FIRST_REGEX.matchEntire(query)?.let { m ->
            return MessageRequest(
                name = m.groups["name"]!!.value.trim(),
                body = m.groups["body"]?.value?.trim().orEmpty(),
                channel = channelFrom(m.groups["channel"]?.value),
            )
        }
        PREFIX_REGEX.matchEntire(query)?.let { m ->
            return parseRecipientAndBody(
                rawRest = m.groups["rest"]!!.value,
                channelToken = m.groups["channel"]?.value,
                contacts = contacts,
            )
        }
        return null
    }

    private fun channelFrom(token: String?): MessageChannel? = when (token?.lowercase(Locale.ROOT)) {
        "whatsapp", "wa" -> MessageChannel.WHATSAPP
        "text", "sms", "message" -> MessageChannel.SMS
        else -> null
    }

    private fun parseRecipientAndBody(
        rawRest: String,
        channelToken: String?,
        contacts: List<PixelSearchResultItem.ContactItem>?,
    ): MessageRequest {
        var rest = rawRest.trim()
        var channel = channelFrom(channelToken)
        if (rest.contains(" on whatsapp", ignoreCase = true)) {
            channel = MessageChannel.WHATSAPP
            rest = rest.replace(Regex("\\s+on\\s+whatsapp", RegexOption.IGNORE_CASE), "")
        }
        if (rest.contains(" via whatsapp", ignoreCase = true)) {
            channel = MessageChannel.WHATSAPP
            rest = rest.replace(Regex("\\s+via\\s+whatsapp", RegexOption.IGNORE_CASE), "")
        }
        rest = rest.removePrefix("to ").trim()
        val separator = Regex("\\s+(?:saying|that|about)\\s+|\\s*:\\s*", RegexOption.IGNORE_CASE)
            .find(rest)
        if (separator != null) {
            return MessageRequest(
                name = rest.substring(0, separator.range.first).trim(),
                body = rest.substring(separator.range.last + 1).trim(),
                channel = channel,
            )
        }
        val words = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1 && !contacts.isNullOrEmpty()) {
            for (count in words.size downTo 1) {
                val candidate = words.take(count).joinToString(" ")
                if (findPhoneNumber(candidate, contacts) != null) {
                    return MessageRequest(
                        name = candidate,
                        body = words.drop(count).joinToString(" "),
                        channel = channel,
                    )
                }
            }
        }
        if (words.size > 1) {
            return MessageRequest(
                name = words.first(),
                body = words.drop(1).joinToString(" "),
                channel = channel,
            )
        }
        return MessageRequest(rest, "", channel)
    }

    private fun smsAction(
        context: Context,
        name: String,
        body: String,
        phone: String?,
    ): BuiltInLauncherAction = action(
        id = "assistant_sms_${name}_${body.hashCode()}",
        title = if (body.isBlank()) "Text $name" else "Text $name: $body",
        subtitle = if (body.isBlank()) (phone ?: "Opens the SMS composer") else body,
        badge = "SMS",
        iconRes = R.drawable.outline_mobile_chat_24,
    ) {
        val digits = phone?.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse(if (digits.isNullOrBlank()) "smsto:" else "smsto:$digits")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (body.isNotBlank()) putExtra("sms_body", body)
        }
        launch(context, intent)
    }

    private fun whatsAppAction(
        context: Context,
        name: String,
        body: String,
        phone: String?,
    ): BuiltInLauncherAction = action(
        id = "assistant_whatsapp_${name}_${body.hashCode()}",
        title = if (body.isBlank()) "Message $name on WhatsApp" else "WhatsApp $name",
        subtitle = if (body.isBlank()) (phone ?: "Opens WhatsApp with the contact prefilled") else body,
        badge = "WhatsApp",
        iconRes = R.drawable.rounded_chat_bubble_24,
    ) {
        val digits = phone?.filter { it.isDigit() }
        if (digits != null) {
            val encoded = URLEncoder.encode(body, "UTF-8")
            val suffix = if (body.isBlank()) "" else "?text=$encoded"
            val uri = Uri.parse("https://wa.me/$digits$suffix")
            val direct = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
            if (!tryLaunch(context, direct)) launch(context, Intent(Intent.ACTION_VIEW, uri))
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            }
            if (!tryLaunch(context, intent)) {
                launch(
                    context,
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                    },
                )
            }
        }
    }

    private fun callAction(
        context: Context,
        query: String,
        contacts: List<PixelSearchResultItem.ContactItem>?,
    ): BuiltInLauncherAction? {
        val match = CALL_REGEX.matchEntire(query) ?: return null
        val target = match.groupValues[1].trim()
        if (target.isBlank()) return null
        val number = findPhoneNumber(target, contacts) ?: target.takeIf { it.any(Char::isDigit) }
        if (number == null) return null
        return action(
            id = "assistant_call_${target}_${number.hashCode()}",
            title = "Call $target",
            subtitle = number ?: "Opens the dialer",
            badge = "Call",
            iconRes = R.drawable.rounded_call_24,
        ) {
            val uri = Uri.parse("tel:" + Uri.encode(number ?: ""))
            launch(context, Intent(Intent.ACTION_DIAL, uri))
        }
    }

    private fun navigateAction(context: Context, query: String): BuiltInLauncherAction? {
        val match = NAVIGATE_REGEX.matchEntire(query) ?: return null
        val destination = match.groupValues[1].trim()
        if (destination.isBlank()) return null
        return action(
            id = "assistant_navigate_${destination.hashCode()}",
            title = "Navigate to $destination",
            subtitle = "Opens directions in your maps app",
            badge = "Directions",
            iconRes = R.drawable.rounded_navigation_24,
        ) {
            val encoded = Uri.encode(destination)
            val maps = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=$encoded"),
            ).setPackage("com.google.android.apps.maps")
            if (!tryLaunch(context, maps)) {
                launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")))
            }
        }
    }

    private fun playAction(context: Context, query: String): BuiltInLauncherAction? {
        val match = PLAY_REGEX.matchEntire(query) ?: return null
        val media = match.groupValues[1].trim()
        if (media.isBlank() || media.equals("store", ignoreCase = true)) return null
        return action(
            id = "assistant_play_${media.hashCode()}",
            title = "Play $media",
            subtitle = "Hands the query to your default media app",
            badge = "Media",
            iconRes = R.drawable.rounded_play_arrow_24,
        ) {
            val mediaIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                putExtra(SearchManager.QUERY, media)
            }
            if (!tryLaunch(context, mediaIntent)) {
                launch(
                    context,
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(media)}"),
                    ),
                )
            }
        }
    }

    private fun weatherAction(context: Context, query: String): BuiltInLauncherAction? {
        val match = WEATHER_REGEX.matchEntire(query) ?: return null
        val place = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val label = if (place.isBlank()) "the weather" else "the weather in $place"
        return action(
            id = "assistant_weather_${place.hashCode()}",
            title = "Show $label",
            subtitle = "Opens a weather search",
            badge = "Weather",
            iconRes = R.drawable.clock_weather,
        ) {
            val q = "weather" + if (place.isBlank()) "" else " $place"
            launch(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")),
            )
        }
    }

    private fun cameraAction(context: Context, query: String): BuiltInLauncherAction? {
        if (!CAMERA_REGEX.matches(query)) return null
        return action(
            id = "assistant_camera",
            title = "Take a photo",
            subtitle = "Opens the camera",
            badge = "Camera",
            iconRes = R.drawable.rounded_photo_camera_24,
        ) {
            launch(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
    }

    private fun connectivityAction(context: Context, query: String): BuiltInLauncherAction? {
        val match = CONNECTIVITY_REGEX.matchEntire(query) ?: return null
        val target = match.groupValues[1].lowercase(Locale.ROOT)
        val isWifi = target.startsWith("wi")
        val settingsAction = if (isWifi) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_BLUETOOTH_SETTINGS
        val panel = when {
            isWifi && android.os.Build.VERSION.SDK_INT >= 29 -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            else -> null
        }
        val label = if (isWifi) "Wi-Fi" else "Bluetooth"
        return action(
            id = "assistant_panel_${target.hashCode()}",
            title = "Open $label settings",
            subtitle = "Toggle $label from the system panel",
            badge = "Settings",
            iconRes = if (isWifi) R.drawable.rounded_wifi_24 else R.drawable.rounded_bluetooth_24,
        ) {
            if (panel != null && !tryLaunch(context, Intent(panel))) {
                launch(context, Intent(settingsAction))
            }
        }
    }

    private fun webSearchAction(context: Context, query: String): BuiltInLauncherAction? {
        val match = SEARCH_REGEX.matchEntire(query) ?: return null
        val term = match.groupValues[1].trim()
        if (term.isBlank()) return null
        return action(
            id = "assistant_search_${term.hashCode()}",
            title = "Search the web for \"$term\"",
            subtitle = "Opens your search app",
            badge = "Web",
            iconRes = R.drawable.rounded_search_24,
        ) {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, term)
            if (!tryLaunch(context, intent)) {
                launch(
                    context,
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(term)}"),
                    ),
                )
            }
        }
    }

    private fun action(
        id: String,
        title: String,
        subtitle: String,
        badge: String,
        iconRes: Int,
        onExecute: (Context) -> Unit,
    ): BuiltInLauncherAction = BuiltInLauncherAction(
        id = id,
        title = title,
        subtitle = subtitle,
        badge = badge,
        iconRes = iconRes,
        keywords = emptyList(),
        opensModal = null,
        closesSearch = true,
        onExecute = { ctx -> runCatching { onExecute(ctx) } },
    )

    private fun launch(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun tryLaunch(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)

    private fun findPhoneNumber(
        name: String,
        contacts: List<PixelSearchResultItem.ContactItem>?,
    ): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("^\\+?[\\d\\s()\\-]{3,}$"))) {
            return trimmed.filter { it.isDigit() || it == '+' }
        }
        if (contacts.isNullOrEmpty()) return null
        val exact = contacts.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (exact?.phoneNumber != null) return exact.phoneNumber
        return contacts.firstOrNull {
            it.phoneNumber != null && it.name.contains(trimmed, ignoreCase = true)
        }?.phoneNumber
    }

    internal fun parseDurationSeconds(raw: String): Long? {
        val normalized = raw.trim().lowercase(Locale.ROOT).replace(',', '.')
        if (normalized.isBlank()) return null
        Regex("^(\\d{1,3}):(\\d{2})(?::(\\d{2}))?$").matchEntire(normalized)?.let { m ->
            val first = m.groupValues[1].toLong()
            val second = m.groupValues[2].toLong()
            val third = m.groupValues[3].toLongOrNull()
            return if (third != null) {
                first * 3600 + second * 60 + third
            } else {
                first * 60 + second
            }
        }
        normalized.toDoubleOrNull()?.let { bare ->
            if (bare > 0) return (bare * 60).roundToLong()
        }
        var total = 0.0
        var found = false
        Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-zA-Z])")
            .findAll(normalized)
            .forEach { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
                val unit = match.groupValues[2]
                total += when {
                    unit.startsWith("h") -> value * 3600
                    unit.startsWith("m") -> value * 60
                    else -> value
                }
                found = true
            }
        return if (found && total > 0) total.roundToLong() else null
    }

    internal fun timerDurationFor(query: String): String? {
        TIMER_REGEX.matchEntire(query)?.let { return it.groupValues[1].trim() }
        TIMER_DURATION_FIRST_REGEX.matchEntire(query)?.let { return it.groupValues[1].trim() }
        return null
    }

    internal fun alarmTimeFor(query: String): String? =
        ALARM_REGEX.matchEntire(query)?.groupValues?.get(1)?.trim()

    private fun parseRelativeAlarmTime(raw: String): Pair<Int, Int>? {
        if (!RELATIVE_DURATION_REGEX.containsMatchIn(raw)) return null
        val seconds = parseDurationSeconds(raw) ?: return null
        val target = LocalTime.now().plusSeconds(seconds)
        return target.hour to target.minute
    }

    private fun parseClockTime(raw: String): Pair<Int, Int>? {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            .find(raw) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val suffix = match.groupValues[3].lowercase(Locale.ROOT)
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 || isEmpty()) append("${secs}s")
        }.trim()
    }

    private val CALENDAR_REGEX = Regex(
        "^(?:cal(?=\\s|$)|calendar(?=\\s|$)|create\\s+(?:a\\s+)?(?:calendar\\s+)?event(?=\\s|$)|add\\s+(?:a\\s+)?(?:calendar\\s+)?event(?=\\s|$)|new\\s+event(?=\\s|$))\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )

    private val TIMER_REGEX = Regex(
        "^(?:(?:set|start|create|begin)\\s+)?(?:(?:a|an|the)\\s+)?(?:timer|countdown)(?:\\s+(?:for\\s+)?(.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val TIMER_DURATION_FIRST_REGEX = Regex(
        "^(?:set|start|create|begin)\\s+(?:(?:a|an)\\s+)?(.+?)\\s+(?:timer|countdown)$",
        RegexOption.IGNORE_CASE,
    )
    private val ALARM_REGEX = Regex(
        "^(?:(?:set|start|create)\\s+)?(?:(?:an?|the)\\s+)?(?:alarm|wake\\s+me(?:\\s+up)?)(?:\\s+(?:for\\s+|at\\s+|in\\s+)?(.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val RELATIVE_DURATION_REGEX = Regex(
        "\\d+(?:\\.\\d+)?\\s*(?:hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-zA-Z])",
        RegexOption.IGNORE_CASE,
    )
    private val PREFIX_TO_REGEX = Regex(
        "^(?:send\\s+(?:a\\s+)?)?(?<channel>whatsapp|text|sms|message)\\s+(?:message\\s+)?to\\s+(?<rest>.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val RECIPIENT_FIRST_REGEX = Regex(
        "^send\\s+(?<name>.+?)\\s+(?:a\\s+)?(?<channel>whatsapp|text|sms|message)(?:\\s+message)?(?:\\s+(?:saying|that|about)\\s+(?<body>.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val PREFIX_REGEX = Regex(
        "^(?<channel>whatsapp|wa|text|sms|message)\\s+(?<rest>.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val CALL_REGEX = Regex("^(?:call|dial|phone)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val NAVIGATE_REGEX = Regex(
        "^(?:navigate|directions|take\\s+me)\\s+(?:to\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val PLAY_REGEX = Regex("^(?:play|listen\\s+to)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val WEATHER_REGEX = Regex(
        "^weather(?:\\s+(?:in|for)\\s+(.+))?$",
        RegexOption.IGNORE_CASE,
    )
    private val CAMERA_REGEX = Regex(
        "^take\\s+(?:a\\s+)?(?:photo|picture)$",
        RegexOption.IGNORE_CASE,
    )
    private val CONNECTIVITY_REGEX = Regex(
        "^(?:turn\\s+on|open|show|toggle)\\s+(wi-?fi|wifi|bluetooth)$",
        RegexOption.IGNORE_CASE,
    )
    private val SEARCH_REGEX = Regex(
        "^(?:google|search(?:\\s+for)?|look\\s+up)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
}
