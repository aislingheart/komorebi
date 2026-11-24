package com.sameerasw.essentials.launcher

import android.content.Context
import com.sameerasw.essentials.assistant.tools.AssistantTimeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class CalcResult(
    val expression: String,
    val formattedResult: String,
    val subtitle: String,
    val copyValue: String,
    val category: String,
)

object LauncherCalculator {
    private const val PREF_NAME = "komorebi_launcher_calc"
    private const val KEY_RATES_JSON = "usd_rates_json"
    private const val KEY_RATES_UPDATED_AT = "usd_rates_updated_at"
    private const val RATES_TTL_MS = 12L * 60L * 60L * 1000L
    private const val RATES_URL = "https://open.er-api.com/v6/latest/USD"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Approximate USD-based fallback rates so currency conversion works offline.
    private val FALLBACK_USD_RATES: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 0.92,
        "GBP" to 0.79,
        "JPY" to 153.5,
        "CAD" to 1.39,
        "AUD" to 1.53,
        "CHF" to 0.88,
        "CNY" to 7.24,
        "INR" to 84.4,
        "KRW" to 1395.0,
        "SEK" to 10.75,
        "NOK" to 11.02,
        "DKK" to 6.86,
        "PLN" to 4.02,
        "BRL" to 5.78,
        "MXN" to 20.15,
        "SGD" to 1.34,
        "HKD" to 7.78,
        "NZD" to 1.69,
        "ZAR" to 18.05,
        "TRY" to 34.4,
        "AED" to 3.6725,
        "SAR" to 3.75,
        "THB" to 34.6,
        "IDR" to 15850.0,
        "TWD" to 32.4,
        "PHP" to 58.6,
        "MYR" to 4.44,
        "CZK" to 23.4,
        "HUF" to 381.0,
    )

    private val CURRENCY_SYMBOLS: Map<String, String> = mapOf(
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "¥" to "JPY",
        "₹" to "INR",
        "₩" to "KRW",
        "CHF" to "CHF",
    )

    private val CURRENCY_DISPLAY_SYMBOLS: Map<String, String> = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "INR" to "₹",
        "KRW" to "₩",
    )

    private val CURRENCY_NAMES: Map<String, String> = mapOf(
        "dollar" to "USD", "dollars" to "USD", "buck" to "USD", "bucks" to "USD",
        "euro" to "EUR", "euros" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "sterling" to "GBP", "quid" to "GBP",
        "yen" to "JPY", "rupee" to "INR", "rupees" to "INR",
        "won" to "KRW", "franc" to "CHF", "francs" to "CHF",
        "yuan" to "CNY", "renminbi" to "CNY",
        "peso" to "MXN", "pesos" to "MXN", "real" to "BRL", "reais" to "BRL",
        "lira" to "TRY", "dirham" to "AED", "dirhams" to "AED",
        "riyal" to "SAR", "riyals" to "SAR", "baht" to "THB",
        "ringgit" to "MYR", "krona" to "SEK", "krone" to "NOK",
    )

    private val CITY_TIMEZONES: Map<String, Pair<String, String>> = mapOf(
        "utc" to ("UTC" to "UTC"),
        "gmt" to ("GMT" to "GMT"),
        "tokyo" to ("Asia/Tokyo" to "Tokyo"),
        "jst" to ("Asia/Tokyo" to "Tokyo (JST)"),
        "london" to ("Europe/London" to "London"),
        "dublin" to ("Europe/Dublin" to "Dublin"),
        "new york" to ("America/New_York" to "New York"),
        "nyc" to ("America/New_York" to "New York"),
        "est" to ("America/New_York" to "US Eastern"),
        "edt" to ("America/New_York" to "US Eastern"),
        "los angeles" to ("America/Los_Angeles" to "Los Angeles"),
        "la" to ("America/Los_Angeles" to "Los Angeles"),
        "san francisco" to ("America/Los_Angeles" to "San Francisco"),
        "sf" to ("America/Los_Angeles" to "San Francisco"),
        "pst" to ("America/Los_Angeles" to "US Pacific"),
        "pdt" to ("America/Los_Angeles" to "US Pacific"),
        "chicago" to ("America/Chicago" to "Chicago"),
        "cst" to ("America/Chicago" to "US Central"),
        "denver" to ("America/Denver" to "Denver"),
        "mst" to ("America/Denver" to "US Mountain"),
        "toronto" to ("America/Toronto" to "Toronto"),
        "vancouver" to ("America/Vancouver" to "Vancouver"),
        "paris" to ("Europe/Paris" to "Paris"),
        "berlin" to ("Europe/Berlin" to "Berlin"),
        "cet" to ("Europe/Berlin" to "Central European"),
        "amsterdam" to ("Europe/Amsterdam" to "Amsterdam"),
        "zurich" to ("Europe/Zurich" to "Zurich"),
        "madrid" to ("Europe/Madrid" to "Madrid"),
        "rome" to ("Europe/Rome" to "Rome"),
        "stockholm" to ("Europe/Stockholm" to "Stockholm"),
        "oslo" to ("Europe/Oslo" to "Oslo"),
        "copenhagen" to ("Europe/Copenhagen" to "Copenhagen"),
        "warsaw" to ("Europe/Warsaw" to "Warsaw"),
        "istanbul" to ("Europe/Istanbul" to "Istanbul"),
        "dubai" to ("Asia/Dubai" to "Dubai"),
        "riyadh" to ("Asia/Riyadh" to "Riyadh"),
        "mumbai" to ("Asia/Kolkata" to "Mumbai"),
        "delhi" to ("Asia/Kolkata" to "Delhi"),
        "bangalore" to ("Asia/Kolkata" to "Bangalore"),
        "ist" to ("Asia/Kolkata" to "India (IST)"),
        "singapore" to ("Asia/Singapore" to "Singapore"),
        "hong kong" to ("Asia/Hong_Kong" to "Hong Kong"),
        "seoul" to ("Asia/Seoul" to "Seoul"),
        "kst" to ("Asia/Seoul" to "Seoul (KST)"),
        "beijing" to ("Asia/Shanghai" to "Beijing"),
        "shanghai" to ("Asia/Shanghai" to "Shanghai"),
        "bangkok" to ("Asia/Bangkok" to "Bangkok"),
        "jakarta" to ("Asia/Jakarta" to "Jakarta"),
        "sydney" to ("Australia/Sydney" to "Sydney"),
        "melbourne" to ("Australia/Melbourne" to "Melbourne"),
        "auckland" to ("Pacific/Auckland" to "Auckland"),
        "sao paulo" to ("America/Sao_Paulo" to "São Paulo"),
        "mexico city" to ("America/Mexico_City" to "Mexico City"),
        "cairo" to ("Africa/Cairo" to "Cairo"),
        "johannesburg" to ("Africa/Johannesburg" to "Johannesburg"),
        "honolulu" to ("Pacific/Honolulu" to "Honolulu"),
    )

    suspend fun refreshRatesIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val lastUpdated = prefs.getLong(KEY_RATES_UPDATED_AT, 0L)
                val now = System.currentTimeMillis()
                if (now - lastUpdated < RATES_TTL_MS && !prefs.getString(KEY_RATES_JSON, null).isNullOrBlank()) {
                    return@withContext
                }
                val request = Request.Builder()
                    .url(RATES_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Komorebi-Launcher/1.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val body = response.body.string()
                    val json = JSONObject(body)
                    val ratesObj = json.optJSONObject("rates") ?: return@withContext
                    if (ratesObj.length() > 0) {
                        prefs.edit()
                            .putString(KEY_RATES_JSON, ratesObj.toString())
                            .putLong(KEY_RATES_UPDATED_AT, now)
                            .apply()
                    }
                }
            }
        }
    }

    private fun loadRates(context: Context?): Pair<Map<String, Double>, Boolean> {
        if (context == null) return FALLBACK_USD_RATES to false
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RATES_JSON, null)
            if (raw.isNullOrBlank()) return FALLBACK_USD_RATES to false
            val json = JSONObject(raw)
            val merged = FALLBACK_USD_RATES.toMutableMap()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optDouble(k, Double.NaN)
                if (!v.isNaN() && v > 0.0) {
                    merged[k.uppercase(Locale.US)] = v
                }
            }
            merged to true
        } catch (_: Exception) {
            FALLBACK_USD_RATES to false
        }
    }

    fun evaluate(query: String, context: Context? = null): CalcResult? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        evaluateProgrammerOrColor(trimmed)?.let { return it }
        evaluateTimezoneOrDate(trimmed)?.let { return it }
        evaluateTip(trimmed)?.let { return it }
        evaluateAge(trimmed)?.let { return it }
        evaluateDateMath(trimmed)?.let { return it }
        evaluateEpoch(trimmed)?.let { return it }
        evaluateSleep(trimmed)?.let { return it }
        evaluateRandom(trimmed)?.let { return it }
        evaluateUnitConversion(trimmed)?.let { return it }
        evaluateCurrencyConversion(trimmed, context)?.let { return it }
        evaluateArithmetic(trimmed)?.let { return it }

        return null
    }

    // -------------------------------------------------------------------------
    // 1. Programmer & Color
    // -------------------------------------------------------------------------

    private val BASE_CONV_REGEX = Regex(
        """^(0x[0-9a-fA-F]+|0b[01]+|0o[0-7]+|-?\d+)\s+(?:to|in|as|->)\s+(dec|decimal|hex|hexadecimal|bin|binary|oct|octal)$""",
        RegexOption.IGNORE_CASE,
    )
    private val HEX_COLOR_REGEX = Regex(
        """^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\s+(?:to|in|as|->)\s+(rgb|rgba|hsl)$""",
        RegexOption.IGNORE_CASE,
    )
    private val RGB_COLOR_REGEX = Regex(
        """^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)\s+(?:to|in|as|->)\s+(hex|hsl)$""",
        RegexOption.IGNORE_CASE,
    )
    private val STANDALONE_HEX_COLOR_REGEX = Regex(
        """^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""",
    )
    private val STANDALONE_RGB_COLOR_REGEX = Regex(
        """^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)$""",
        RegexOption.IGNORE_CASE,
    )

    private fun evaluateProgrammerOrColor(input: String): CalcResult? {
        BASE_CONV_REGEX.matchEntire(input)?.let { m ->
            val rawNum = m.groupValues[1]
            val target = m.groupValues[2].lowercase(Locale.US)
            val parsed = parseIntLiteral(rawNum) ?: return null
            val (formatted, label) = when {
                target.startsWith("dec") -> parsed.toString(10) to "Decimal"
                target.startsWith("hex") -> {
                    val sign = if (parsed < 0) "-" else ""
                    "${sign}0x${abs(parsed).toString(16).uppercase(Locale.US)}" to "Hexadecimal"
                }
                target.startsWith("bin") -> {
                    val sign = if (parsed < 0) "-" else ""
                    "${sign}0b${abs(parsed).toString(2)}" to "Binary"
                }
                target.startsWith("oct") -> {
                    val sign = if (parsed < 0) "-" else ""
                    "${sign}0o${abs(parsed).toString(8)}" to "Octal"
                }
                else -> return null
            }
            return CalcResult(
                expression = input,
                formattedResult = formatted,
                subtitle = "$label • Dec: $parsed",
                copyValue = formatted,
                category = "Programmer",
            )
        }

        HEX_COLOR_REGEX.matchEntire(input)?.let { m ->
            val rawHex = m.groupValues[1]
            val target = m.groupValues[2].lowercase(Locale.US)
            val (r, g, b) = parseHexRgb(rawHex) ?: return null
            val resultStr = when (target) {
                "rgb", "rgba" -> "rgb($r, $g, $b)"
                "hsl" -> rgbToHslString(r, g, b)
                else -> return null
            }
            return CalcResult(
                expression = input,
                formattedResult = resultStr,
                subtitle = "Color conversion from #${rawHex.uppercase(Locale.US)}",
                copyValue = resultStr,
                category = "Color",
            )
        }

        RGB_COLOR_REGEX.matchEntire(input)?.let { m ->
            val r = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val g = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val b = m.groupValues[3].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val target = m.groupValues[5].lowercase(Locale.US)
            val resultStr = when (target) {
                "hex" -> String.format(Locale.US, "#%02X%02X%02X", r, g, b)
                "hsl" -> rgbToHslString(r, g, b)
                else -> return null
            }
            return CalcResult(
                expression = input,
                formattedResult = resultStr,
                subtitle = "Color conversion from rgb($r, $g, $b)",
                copyValue = resultStr,
                category = "Color",
            )
        }

        STANDALONE_HEX_COLOR_REGEX.matchEntire(input)?.let { m ->
            val rawHex = m.groupValues[1]
            val (r, g, b) = parseHexRgb(rawHex) ?: return null
            val rgbStr = "rgb($r, $g, $b)"
            val hslStr = rgbToHslString(r, g, b)
            return CalcResult(
                expression = input,
                formattedResult = "$rgbStr · $hslStr",
                subtitle = "Color #${rawHex.uppercase(Locale.US)}",
                copyValue = rgbStr,
                category = "Color",
            )
        }

        STANDALONE_RGB_COLOR_REGEX.matchEntire(input)?.let { m ->
            val r = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val g = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val b = m.groupValues[3].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val hexStr = String.format(Locale.US, "#%02X%02X%02X", r, g, b)
            val hslStr = rgbToHslString(r, g, b)
            return CalcResult(
                expression = input,
                formattedResult = hexStr,
                subtitle = "Color • $hslStr",
                copyValue = hexStr,
                category = "Color",
            )
        }

        return null
    }

    private fun parseHexRgb(rawHex: String): Triple<Int, Int, Int>? {
        val expanded = when (rawHex.length) {
            3 -> rawHex.map { "$it$it" }.joinToString("")
            6 -> rawHex
            8 -> rawHex.substring(2) // strip alpha for rgb/hsl
            else -> return null
        }
        val r = expanded.substring(0, 2).toInt(16)
        val g = expanded.substring(2, 4).toInt(16)
        val b = expanded.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    private fun parseIntLiteral(raw: String): Long? {
        val lower = raw.lowercase(Locale.US)
        return try {
            when {
                lower.startsWith("0x") -> lower.substring(2).toLong(16)
                lower.startsWith("0b") -> lower.substring(2).toLong(2)
                lower.startsWith("0o") -> lower.substring(2).toLong(8)
                else -> lower.toLong(10)
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun rgbToHslString(r: Int, g: Int, b: Int): String {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val l = (max + min) / 2.0
        val d = max - min
        val h: Double
        val s: Double
        if (d == 0.0) {
            h = 0.0
            s = 0.0
        } else {
            s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
            h = when (max) {
                rf -> ((gf - bf) / d + (if (gf < bf) 6.0 else 0.0))
                gf -> ((bf - rf) / d + 2.0)
                else -> ((rf - gf) / d + 4.0)
            } / 6.0
        }
        return "hsl(${round(h * 360).toInt()}°, ${round(s * 100).toInt()}%, ${round(l * 100).toInt()}%)"
    }

    // -------------------------------------------------------------------------
    // 2. Timezone & Date Math
    // -------------------------------------------------------------------------

    private val NOW_IN_ZONE_REGEX = Regex("""^(?:now|time)\s+in\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val DATE_OFFSET_REGEX = Regex(
        """^(today|now|tomorrow|yesterday)\s*([+-])\s*(\d+)\s*(day|days|d|week|weeks|wk|wks|w|month|months|mo|year|years|yr|yrs|y)$""",
        RegexOption.IGNORE_CASE,
    )
    private val DAYS_UNTIL_REGEX = Regex(
        """^days\s+(until|since|to|from)\s+(\d{4}-\d{2}-\d{2})$""",
        RegexOption.IGNORE_CASE,
    )
    private val DAYS_BETWEEN_REGEX = Regex(
        """^days\s+between\s+(\d{4}-\d{2}-\d{2})\s+and\s+(\d{4}-\d{2}-\d{2})$""",
        RegexOption.IGNORE_CASE,
    )
    private val UNIX_TO_DATE_REGEX = Regex(
        """^(\d{9,13})\s+(?:to|in|as|->)\s+(date|iso|time|utc)$""",
        RegexOption.IGNORE_CASE,
    )

    private fun evaluateTimezoneOrDate(input: String): CalcResult? {
        val lower = input.lowercase(Locale.US)

        if (lower == "now unix" || lower == "unix now" || lower == "unix timestamp") {
            val epochSec = Instant.now().epochSecond
            return CalcResult(
                expression = input,
                formattedResult = epochSec.toString(),
                subtitle = "Current Unix timestamp (seconds)",
                copyValue = epochSec.toString(),
                category = "Date & Time",
            )
        }

        UNIX_TO_DATE_REGEX.matchEntire(input)?.let { m ->
            val rawTs = m.groupValues[1].toLongOrNull() ?: return null
            val target = m.groupValues[2].lowercase(Locale.US)
            val instant = if (rawTs > 99_999_999_999L) Instant.ofEpochMilli(rawTs) else Instant.ofEpochSecond(rawTs)
            val zone = if (target == "utc" || target == "iso") ZoneId.of("UTC") else ZoneId.systemDefault()
            val zdt = instant.atZone(zone)
            val formatted = if (target == "iso") {
                DateTimeFormatter.ISO_INSTANT.format(instant)
            } else {
                zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm:ss z", Locale.US))
            }
            return CalcResult(
                expression = input,
                formattedResult = formatted,
                subtitle = "Unix timestamp (${zone.id})",
                copyValue = formatted,
                category = "Date & Time",
            )
        }

        NOW_IN_ZONE_REGEX.matchEntire(input)?.let { m ->
            val placeRaw = m.groupValues[1].trim()
            val resolved = resolveZone(placeRaw) ?: return null
            val (zoneId, label) = resolved
            val zdt = ZonedDateTime.now(zoneId)
            val timeStr = zdt.format(DateTimeFormatter.ofPattern("h:mm a (HH:mm)", Locale.US))
            val dateStr = zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d • z (XXX)", Locale.US))
            val copyTime = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US))
            return CalcResult(
                expression = input,
                formattedResult = timeStr,
                subtitle = "$label • $dateStr",
                copyValue = copyTime,
                category = "Timezone",
            )
        }

        DATE_OFFSET_REGEX.matchEntire(input)?.let { m ->
            val baseWord = m.groupValues[1].lowercase(Locale.US)
            val sign = if (m.groupValues[2] == "-") -1L else 1L
            val amount = m.groupValues[3].toLongOrNull() ?: return null
            val unit = m.groupValues[4].lowercase(Locale.US)

            val baseDate = when (baseWord) {
                "tomorrow" -> LocalDate.now().plusDays(1)
                "yesterday" -> LocalDate.now().minusDays(1)
                else -> LocalDate.now()
            }
            val delta = sign * amount
            val targetDate = when {
                unit.startsWith("d") -> baseDate.plusDays(delta)
                unit.startsWith("w") -> baseDate.plusWeeks(delta)
                unit.startsWith("mo") -> baseDate.plusMonths(delta)
                unit.startsWith("y") -> baseDate.plusYears(delta)
                else -> return null
            }
            val formatted = targetDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))
            val iso = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            return CalcResult(
                expression = input,
                formattedResult = formatted,
                subtitle = "ISO: $iso",
                copyValue = iso,
                category = "Date & Time",
            )
        }

        DAYS_UNTIL_REGEX.matchEntire(input)?.let { m ->
            val prep = m.groupValues[1].lowercase(Locale.US)
            val targetDate = runCatching { LocalDate.parse(m.groupValues[2]) }.getOrNull() ?: return null
            val today = LocalDate.now()
            val diff = if (prep == "since" || prep == "from") {
                ChronoUnit.DAYS.between(targetDate, today)
            } else {
                ChronoUnit.DAYS.between(today, targetDate)
            }
            val absDiff = abs(diff)
            val label = when {
                diff == 0L -> "Today"
                diff > 0L && (prep == "until" || prep == "to") -> "$diff days remaining"
                diff < 0L && (prep == "until" || prep == "to") -> "$absDiff days ago"
                diff > 0L -> "$diff days elapsed"
                else -> "$absDiff days in the future"
            }
            return CalcResult(
                expression = input,
                formattedResult = "$diff days",
                subtitle = "$label • Target: $targetDate",
                copyValue = diff.toString(),
                category = "Date & Time",
            )
        }

        DAYS_BETWEEN_REGEX.matchEntire(input)?.let { m ->
            val d1 = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: return null
            val d2 = runCatching { LocalDate.parse(m.groupValues[2]) }.getOrNull() ?: return null
            val diff = ChronoUnit.DAYS.between(d1, d2)
            return CalcResult(
                expression = input,
                formattedResult = "${abs(diff)} days",
                subtitle = "$d1 → $d2",
                copyValue = abs(diff).toString(),
                category = "Date & Time",
            )
        }

        return null
    }

    private fun resolveZone(raw: String): Pair<ZoneId, String>? {
        val clean = raw.trim().lowercase(Locale.US)
        CITY_TIMEZONES[clean]?.let { (id, label) ->
            return ZoneId.of(id) to label
        }
        // Check UTC+X / GMT-X offsets
        val offsetMatch = Regex("""^(?:utc|gmt)\s*([+-]\d{1,2})(?::(\d{2}))?$""", RegexOption.IGNORE_CASE).matchEntire(clean)
        if (offsetMatch != null) {
            val hours = offsetMatch.groupValues[1].toIntOrNull() ?: return null
            val mins = offsetMatch.groupValues[2].toIntOrNull() ?: 0
            if (hours in -18..18 && mins in 0..59) {
                val sign = if (hours >= 0) "+" else "-"
                val zoneStr = String.format(Locale.US, "UTC%s%02d:%02d", sign, abs(hours), mins)
                return ZoneId.of(zoneStr) to zoneStr
            }
        }
        // Fallback: match against ZoneId available zones
        val direct = runCatching { ZoneId.of(raw.trim()) }.getOrNull()
        if (direct != null) return direct to direct.id
        val normalizedQuery = clean.replace(' ', '_')
        val matched = ZoneId.getAvailableZoneIds().firstOrNull {
            it.lowercase(Locale.US).endsWith("/$normalizedQuery") ||
                it.lowercase(Locale.US).contains(normalizedQuery)
        }
        if (matched != null) {
            val z = ZoneId.of(matched)
            return z to matched.substringAfterLast('/').replace('_', ' ')
        }
        return null
    }

    // -------------------------------------------------------------------------
    // 2b. Productivity: Tip, Age, Days, Epoch, Sleep, Random
    // -------------------------------------------------------------------------

    private val TIP_REGEX = Regex(
        """^tip\s+(\d+(?:\.\d+)?)\s+(?:at\s+)?(\d+(?:\.\d+)?)\s*%?\s*(?:(?:split(?:\s+between)?|between|for)\s+(\d{1,3}))?$""",
        RegexOption.IGNORE_CASE,
    )

    internal fun evaluateTip(input: String): CalcResult? {
        val m = TIP_REGEX.matchEntire(input) ?: return null
        val bill = m.groupValues[1].toDoubleOrNull() ?: return null
        val pct = m.groupValues[2].toDoubleOrNull() ?: return null
        val split = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
        if (bill < 0.0 || pct < 0.0 || pct > 100.0 || split < 1) return null
        val tip = bill * pct / 100.0
        val total = bill + tip
        val perPerson = total / split
        val pctStr = formatNumber(pct, 2)
        val subtitle = if (split > 1) {
            "Total ${money(total)} · ${money(perPerson)} each ($split people)"
        } else {
            "Total ${money(total)}"
        }
        return CalcResult(
            expression = input,
            formattedResult = "${money(tip)} tip on ${money(bill)} ($pctStr%)",
            subtitle = subtitle,
            copyValue = formatNumber(total, 2),
            category = "Tip",
        )
    }

    private fun money(value: Double): String =
        "$" + DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(value)

    private val AGE_REGEX = Regex("""^age\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val SLASH_DATE_REGEX = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""")
    private val MONTH_DAY_YEAR_REGEX = Regex(
        """^([a-z]+)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\s*,?\s*(\d{4})$""",
        RegexOption.IGNORE_CASE,
    )
    private val DAY_MONTH_YEAR_REGEX = Regex(
        """^(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]+)\.?\s*,?\s*(\d{4})$""",
        RegexOption.IGNORE_CASE,
    )
    private val MONTH_NAMES: Map<String, Int> = mapOf(
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    /** Flexible date parsing: ISO, d/M/yyyy (or M/d/yyyy fallback), month-name forms, AssistantTimeParser. */
    internal fun parseFlexibleDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        runCatching { LocalDate.parse(text) }.getOrNull()?.let { return it }
        AssistantTimeParser.parseLocalDate(text)?.let { return it }
        SLASH_DATE_REGEX.matchEntire(text)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            val year = m.groupValues[3].toIntOrNull()
            if (a != null && b != null && year != null) {
                runCatching { LocalDate.of(year, b, a) }.getOrNull()
                    ?: runCatching { LocalDate.of(year, a, b) }.getOrNull()
            } else {
                null
            }
        }?.let { return it }
        MONTH_DAY_YEAR_REGEX.matchEntire(text)?.let { m ->
            val month = MONTH_NAMES[m.groupValues[1].lowercase(Locale.US)]
            val day = m.groupValues[2].toIntOrNull()
            val year = m.groupValues[3].toIntOrNull()
            if (month != null && day != null && year != null) {
                runCatching { LocalDate.of(year, month, day) }.getOrNull()
            } else {
                null
            }
        }?.let { return it }
        DAY_MONTH_YEAR_REGEX.matchEntire(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = MONTH_NAMES[m.groupValues[2].lowercase(Locale.US)]
            val year = m.groupValues[3].toIntOrNull()
            if (month != null && day != null && year != null) {
                runCatching { LocalDate.of(year, month, day) }.getOrNull()
            } else {
                null
            }
        }?.let { return it }
        return null
    }

    internal fun evaluateAge(input: String): CalcResult? {
        val m = AGE_REGEX.matchEntire(input) ?: return null
        val birth = parseFlexibleDate(m.groupValues[1]) ?: return null
        val today = LocalDate.now()
        if (birth.isAfter(today)) return null
        val period = Period.between(birth, today)
        val ageStr = "${period.years} years, ${period.months} months, ${period.days} days"
        val nextBirthday = runCatching { LocalDate.of(today.year, birth.monthValue, birth.dayOfMonth) }
            .getOrElse {
                val month = YearMonth.of(today.year, birth.monthValue)
                month.atDay(minOf(birth.dayOfMonth, month.lengthOfMonth()))
            }
            .let { if (it.isBefore(today)) it.plusYears(1) else it }
        val daysToNext = ChronoUnit.DAYS.between(today, nextBirthday)
        val bornStr = birth.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        val nextStr = nextBirthday.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        return CalcResult(
            expression = input,
            formattedResult = ageStr,
            subtitle = "Born $bornStr · next birthday in $daysToNext days ($nextStr)",
            copyValue = ageStr,
            category = "Age",
        )
    }

    private val DAYS_FROM_TO_REGEX = Regex(
        """^days\s+(?:between|from)\s+(.+?)\s+(?:and|to)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val DAYS_UNTIL_WORD_REGEX = Regex(
        """^days\s+(?:until|till)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    internal fun evaluateDateMath(input: String): CalcResult? {
        val betweenMatch = DAYS_FROM_TO_REGEX.matchEntire(input)
        if (betweenMatch != null) {
            val d1 = parseFlexibleDate(betweenMatch.groupValues[1]) ?: return null
            val d2 = parseFlexibleDate(betweenMatch.groupValues[2]) ?: return null
            val diff = abs(ChronoUnit.DAYS.between(d1, d2))
            return CalcResult(
                expression = input,
                formattedResult = "$diff days",
                subtitle = "${weeksAndDays(diff)} • $d1 → $d2",
                copyValue = diff.toString(),
                category = "Date Math",
            )
        }

        val untilMatch = DAYS_UNTIL_WORD_REGEX.matchEntire(input)
        if (untilMatch != null) {
            val targetRaw = untilMatch.groupValues[1].trim()
            val today = LocalDate.now()
            val target = nextHolidayDate(targetRaw, today) ?: parseFlexibleDate(targetRaw) ?: return null
            val diff = ChronoUnit.DAYS.between(today, target)
            val targetStr = target.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
            val formatted = if (diff < 0) "${abs(diff)} days ago" else "$diff days"
            return CalcResult(
                expression = input,
                formattedResult = formatted,
                subtitle = "Target: $targetStr",
                copyValue = abs(diff).toString(),
                category = "Date Math",
            )
        }
        return null
    }

    private fun weeksAndDays(days: Long): String {
        val weeks = days / 7
        val rem = days % 7
        return when {
            weeks <= 0L -> "$days days"
            rem == 0L -> "$weeks weeks"
            else -> "$weeks weeks, $rem days"
        }
    }

    private fun nextHolidayDate(raw: String, today: LocalDate): LocalDate? {
        val name = raw.trim().lowercase(Locale.US).replace("'", "")
        val monthDay = when (name) {
            "christmas", "xmas" -> MonthDay.of(12, 25)
            "halloween" -> MonthDay.of(10, 31)
            "new year", "new years" -> MonthDay.of(1, 1)
            else -> return null
        }
        val thisYear = monthDay.atYear(today.year)
        return if (!thisYear.isBefore(today)) thisYear else thisYear.plusYears(1)
    }

    private val BARE_EPOCH_REGEX = Regex("""^(\d{10}|\d{13})$""")

    internal fun evaluateEpoch(input: String): CalcResult? {
        val lower = input.trim().lowercase(Locale.US)
        if (lower == "epoch" || lower == "epoch now" || lower == "now to epoch" || lower == "current epoch") {
            val epochSec = Instant.now().epochSecond
            return CalcResult(
                expression = input,
                formattedResult = epochSec.toString(),
                subtitle = "Current Unix timestamp (seconds)",
                copyValue = epochSec.toString(),
                category = "Epoch",
            )
        }
        val bare = BARE_EPOCH_REGEX.matchEntire(input.trim()) ?: return null
        val rawTs = bare.groupValues[1].toLongOrNull() ?: return null
        val instant = if (rawTs > 99_999_999_999L) Instant.ofEpochMilli(rawTs) else Instant.ofEpochSecond(rawTs)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy HH:mm", Locale.US)
        val localStr = instant.atZone(ZoneId.systemDefault()).format(formatter)
        val utcStr = instant.atZone(ZoneId.of("UTC")).format(formatter)
        return CalcResult(
            expression = input,
            formattedResult = localStr,
            subtitle = "UTC: $utcStr",
            copyValue = rawTs.toString(),
            category = "Epoch",
        )
    }

    private const val FALL_ASLEEP_MINUTES = 14L
    private const val SLEEP_CYCLE_MINUTES = 90L
    private val WAKE_AT_REGEX = Regex("""^wake(?:\s*up)?\s+at\s+(.+)$""", RegexOption.IGNORE_CASE)

    internal fun evaluateSleep(input: String): CalcResult? {
        val lower = input.trim().lowercase(Locale.US)
        if (lower == "sleep now") {
            val start = LocalTime.now().plusMinutes(FALL_ASLEEP_MINUTES)
            val timeStrs = (4L..6L).map { k -> start.plusMinutes(SLEEP_CYCLE_MINUTES * k) }
                .map { formatSleepClock(it) }
            return CalcResult(
                expression = input,
                formattedResult = "You'd wake at:",
                subtitle = timeStrs.joinToString(" · "),
                copyValue = timeStrs.first(),
                category = "Sleep",
            )
        }

        val m = WAKE_AT_REGEX.matchEntire(input) ?: return null
        val wake = parseFlexibleTime(m.groupValues[1]) ?: return null
        val wakeStr = formatSleepClock(wake)
        val wakeDateTime = LocalDate.now().atTime(wake)
        val bedtimeStrs = (6L downTo 3L)
            .map { k -> wakeDateTime.minusMinutes(FALL_ASLEEP_MINUTES + SLEEP_CYCLE_MINUTES * k) }
            .map { formatSleepClock(it) }
        return CalcResult(
            expression = input,
            formattedResult = "To wake at $wakeStr, sleep at:",
            subtitle = bedtimeStrs.joinToString(" · "),
            copyValue = bedtimeStrs.first(),
            category = "Sleep",
        )
    }

    private fun parseFlexibleTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        AssistantTimeParser.parseLocalTime(text)?.let { return it }
        val hourOnly = Regex("""^(\d{1,2})$""").matchEntire(text) ?: return null
        val hour = hourOnly.groupValues[1].toIntOrNull() ?: return null
        if (hour !in 0..23) return null
        return LocalTime.of(hour, 0)
    }

    private fun formatSleepClock(value: LocalTime): String =
        value.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

    private fun formatSleepClock(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

    private val DICE_REGEX = Regex("""^roll(?:\s+(?:(\d{0,3})d(\d{1,4})|dice))?$""", RegexOption.IGNORE_CASE)
    private val RANDOM_RANGE_REGEX = Regex(
        """^random(?:\s+number)?\s+(-?\d{1,9})\s*(?:-|to|and)\s*(-?\d{1,9})$""",
        RegexOption.IGNORE_CASE,
    )

    internal fun evaluateRandom(input: String): CalcResult? {
        val lower = input.trim().lowercase(Locale.US)
        val rng = Random()

        if (lower == "flip a coin" || lower == "flip coin" || lower == "coin flip" ||
            lower == "toss a coin" || lower == "toss coin" || lower == "coin toss"
        ) {
            val outcome = if (rng.nextBoolean()) "Heads" else "Tails"
            return CalcResult(
                expression = input,
                formattedResult = outcome,
                subtitle = "50/50 flip",
                copyValue = outcome,
                category = "Random",
            )
        }

        val diceMatch = DICE_REGEX.matchEntire(input)
        if (diceMatch != null) {
            val countRaw = diceMatch.groupValues[1]
            val sidesRaw = diceMatch.groupValues[2]
            if (countRaw.isEmpty() && sidesRaw.isEmpty()) {
                return buildDiceResult(input, rng, 1, 6)
            }
            val count = countRaw.ifEmpty { "1" }.toIntOrNull() ?: return null
            val sides = sidesRaw.toIntOrNull() ?: return null
            if (count !in 1..100 || sides !in 2..1000) return null
            return buildDiceResult(input, rng, count, sides)
        }

        val rangeMatch = RANDOM_RANGE_REGEX.matchEntire(input)
        if (rangeMatch != null) {
            val a = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val b = rangeMatch.groupValues[2].toIntOrNull() ?: return null
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            val span = hi - lo + 1
            if (span <= 0) return null
            val value = lo + rng.nextInt(span)
            return CalcResult(
                expression = input,
                formattedResult = value.toString(),
                subtitle = "Random number between $lo and $hi",
                copyValue = value.toString(),
                category = "Random",
            )
        }
        return null
    }

    private fun buildDiceResult(input: String, rng: Random, count: Int, sides: Int): CalcResult {
        val rolls = (0 until count).map { 1 + rng.nextInt(sides) }
        val total = rolls.sum()
        return CalcResult(
            expression = input,
            formattedResult = total.toString(),
            subtitle = "${count}d$sides: ${rolls.joinToString(" + ")}",
            copyValue = total.toString(),
            category = "Random",
        )
    }

    // -------------------------------------------------------------------------
    // 3. Unit Conversions
    // -------------------------------------------------------------------------

    private enum class UnitFamily {
        LENGTH, MASS, TEMPERATURE, DATA, VOLUME, SPEED, TIME, AREA, ENERGY, ANGLE, CSS
    }

    private data class UnitDef(
        val family: UnitFamily,
        val canonical: String,
        val toBaseFactor: Double,
        val aliases: Set<String>,
    )

    private val UNIT_DEFS: List<UnitDef> = listOf(
        // Length (base = meter)
        UnitDef(UnitFamily.LENGTH, "mm", 0.001, setOf("mm", "millimeter", "millimeters", "millimetre", "millimetres")),
        UnitDef(UnitFamily.LENGTH, "cm", 0.01, setOf("cm", "centimeter", "centimeters", "centimetre", "centimetres")),
        UnitDef(UnitFamily.LENGTH, "m", 1.0, setOf("m", "meter", "meters", "metre", "metres")),
        UnitDef(UnitFamily.LENGTH, "km", 1000.0, setOf("km", "kilometer", "kilometers", "kilometre", "kilometres")),
        UnitDef(UnitFamily.LENGTH, "in", 0.0254, setOf("in", "inch", "inches")),
        UnitDef(UnitFamily.LENGTH, "ft", 0.3048, setOf("ft", "foot", "feet")),
        UnitDef(UnitFamily.LENGTH, "yd", 0.9144, setOf("yd", "yard", "yards")),
        UnitDef(UnitFamily.LENGTH, "mi", 1609.344, setOf("mi", "mile", "miles")),
        UnitDef(UnitFamily.LENGTH, "nmi", 1852.0, setOf("nmi", "nm")),

        // CSS / Typography (base = px)
        UnitDef(UnitFamily.CSS, "px", 1.0, setOf("px", "pixel", "pixels")),
        UnitDef(UnitFamily.CSS, "rem", 16.0, setOf("rem")),
        UnitDef(UnitFamily.CSS, "em", 16.0, setOf("em")),
        UnitDef(UnitFamily.CSS, "pt", 4.0 / 3.0, setOf("pt", "pts", "point", "points")),

        // Mass (base = gram)
        UnitDef(UnitFamily.MASS, "mg", 0.001, setOf("mg", "milligram", "milligrams")),
        UnitDef(UnitFamily.MASS, "g", 1.0, setOf("g", "gram", "grams")),
        UnitDef(UnitFamily.MASS, "kg", 1000.0, setOf("kg", "kilogram", "kilograms", "kilo", "kilos")),
        UnitDef(UnitFamily.MASS, "t", 1_000_000.0, setOf("t", "tonne", "tonnes", "metric ton")),
        UnitDef(UnitFamily.MASS, "oz", 28.349523125, setOf("oz", "ounce", "ounces")),
        UnitDef(UnitFamily.MASS, "lb", 453.59237, setOf("lb", "lbs", "pound", "pounds")),
        UnitDef(UnitFamily.MASS, "st", 6350.29318, setOf("st", "stone", "stones")),

        // Temperature (handled specially)
        UnitDef(
            UnitFamily.TEMPERATURE,
            "°C",
            1.0,
            setOf("c", "°c", "° c", "deg c", "degc", "degree c", "degrees c", "celsius", "deg celsius", "degrees celsius", "centigrade"),
        ),
        UnitDef(
            UnitFamily.TEMPERATURE,
            "°F",
            1.0,
            setOf("f", "°f", "° f", "deg f", "degf", "degree f", "degrees f", "fahrenheit", "deg fahrenheit", "degrees fahrenheit"),
        ),
        UnitDef(
            UnitFamily.TEMPERATURE,
            "K",
            1.0,
            setOf("k", "kelvin", "kelvins", "deg k", "degrees kelvin"),
        ),

        // Data (binary 1024 base = byte)
        UnitDef(UnitFamily.DATA, "B", 1.0, setOf("b", "byte", "bytes")),
        UnitDef(UnitFamily.DATA, "KB", 1024.0, setOf("kb", "kib", "kilobyte", "kilobytes")),
        UnitDef(UnitFamily.DATA, "MB", 1024.0 * 1024.0, setOf("mb", "mib", "megabyte", "megabytes")),
        UnitDef(UnitFamily.DATA, "GB", 1024.0 * 1024.0 * 1024.0, setOf("gb", "gib", "gigabyte", "gigabytes")),
        UnitDef(UnitFamily.DATA, "TB", 1024.0 * 1024.0 * 1024.0 * 1024.0, setOf("tb", "tib", "terabyte", "terabytes")),
        UnitDef(UnitFamily.DATA, "PB", 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0, setOf("pb", "pib", "petabyte", "petabytes")),

        // Volume (base = milliliter)
        UnitDef(UnitFamily.VOLUME, "ml", 1.0, setOf("ml", "milliliter", "milliliters", "millilitre", "millilitres")),
        UnitDef(UnitFamily.VOLUME, "l", 1000.0, setOf("l", "liter", "liters", "litre", "litres")),
        UnitDef(UnitFamily.VOLUME, "fl oz", 29.5735295625, setOf("floz", "fl oz")),
        UnitDef(UnitFamily.VOLUME, "cup", 236.5882365, setOf("cup", "cups")),
        UnitDef(UnitFamily.VOLUME, "pint", 473.176473, setOf("pint", "pints")),
        UnitDef(UnitFamily.VOLUME, "qt", 946.352946, setOf("qt", "quart", "quarts")),
        UnitDef(UnitFamily.VOLUME, "gal", 3785.411784, setOf("gal", "gallon", "gallons")),
        UnitDef(UnitFamily.VOLUME, "tbsp", 14.78676478125, setOf("tbsp")),
        UnitDef(UnitFamily.VOLUME, "tsp", 4.92892159375, setOf("tsp")),

        // Speed (base = m/s)
        UnitDef(UnitFamily.SPEED, "m/s", 1.0, setOf("mps", "m/s")),
        UnitDef(UnitFamily.SPEED, "km/h", 1.0 / 3.6, setOf("kmh", "km/h", "kph")),
        UnitDef(UnitFamily.SPEED, "mph", 0.44704, setOf("mph")),
        UnitDef(UnitFamily.SPEED, "kn", 0.5144444444, setOf("kt", "kts", "knot", "knots", "kn")),

        // Time (base = second)
        UnitDef(UnitFamily.TIME, "ms", 0.001, setOf("ms", "millisecond", "milliseconds")),
        UnitDef(UnitFamily.TIME, "s", 1.0, setOf("s", "sec", "secs", "second", "seconds")),
        UnitDef(UnitFamily.TIME, "min", 60.0, setOf("min", "mins", "minute", "minutes")),
        UnitDef(UnitFamily.TIME, "hr", 3600.0, setOf("h", "hr", "hrs", "hour", "hours")),
        UnitDef(UnitFamily.TIME, "day", 86400.0, setOf("d", "day", "days")),
        UnitDef(UnitFamily.TIME, "wk", 604800.0, setOf("wk", "wks", "week", "weeks")),

        // Area (base = sq m)
        UnitDef(UnitFamily.AREA, "m²", 1.0, setOf("sqm", "m2", "m²")),
        UnitDef(UnitFamily.AREA, "km²", 1_000_000.0, setOf("sqkm", "km2", "km²")),
        UnitDef(UnitFamily.AREA, "ft²", 0.09290304, setOf("sqft", "ft2", "ft²")),
        UnitDef(UnitFamily.AREA, "mi²", 2_589_988.110336, setOf("sqmi", "mi2", "mi²")),
        UnitDef(UnitFamily.AREA, "acre", 4046.8564224, setOf("acre", "acres")),
        UnitDef(UnitFamily.AREA, "ha", 10_000.0, setOf("ha", "hectare", "hectares")),

        // Energy (base = joule)
        UnitDef(UnitFamily.ENERGY, "J", 1.0, setOf("j", "joule", "joules")),
        UnitDef(UnitFamily.ENERGY, "kJ", 1000.0, setOf("kj", "kilojoule", "kilojoules")),
        UnitDef(UnitFamily.ENERGY, "cal", 4.184, setOf("cal", "calorie", "calories")),
        UnitDef(UnitFamily.ENERGY, "kcal", 4184.0, setOf("kcal", "kilocalorie", "kilocalories")),
        UnitDef(UnitFamily.ENERGY, "Wh", 3600.0, setOf("wh")),
        UnitDef(UnitFamily.ENERGY, "kWh", 3_600_000.0, setOf("kwh")),

        // Angle (base = radian)
        UnitDef(UnitFamily.ANGLE, "rad", 1.0, setOf("rad", "radian", "radians")),
        UnitDef(UnitFamily.ANGLE, "deg", PI / 180.0, setOf("deg", "degree", "degrees", "°")),
    )

    private val LOCALIZED_UNIT_ALIASES: Map<String, String> = mapOf(
        // Length
        "metro" to "m", "metros" to "m", "metre" to "m", "metres" to "m",
        "centimetro" to "cm", "centimetros" to "cm", "centimètre" to "cm", "centimètres" to "cm",
        "zentimeter" to "cm", "centimetri" to "cm", "сантиметр" to "cm",
        "kilometro" to "km", "kilometros" to "km", "kilomètre" to "km", "kilomètres" to "km",
        "kilometer" to "km", "kilometers" to "km", "kilometre" to "km", "kilometres" to "km",
        "quilometro" to "km", "quilometros" to "km", "chilometro" to "km", "chilometri" to "km",
        "километр" to "km", "километров" to "km",
        "pulgada" to "in", "pulgadas" to "in", "pouce" to "in", "pouces" to "in",
        "zoll" to "in", "duim" to "in", "pollice" to "in", "pollici" to "in", "polegada" to "in", "дюйм" to "in",
        "pie" to "ft", "pies" to "ft", "pied" to "ft", "pieds" to "ft", "fuß" to "ft", "fuss" to "ft",
        "voet" to "ft", "piede" to "ft", "piedi" to "ft", "pé" to "ft", "pés" to "ft", "фут" to "ft",
        "milla" to "mi", "millas" to "mi", "mille" to "mi", "meile" to "mi", "mijl" to "mi",
        "miglio" to "mi", "milha" to "mi", "миля" to "mi",
        "square foot" to "ft²", "square feet" to "ft²", "sq ft" to "ft²",
        "square meter" to "m²", "square meters" to "m²", "sq m" to "m²",
        "square metre" to "m²", "square metres" to "m²",
        "square mile" to "mi²", "square miles" to "mi²",
        "square kilometer" to "km²", "square kilometers" to "km²",
        // Mass
        "gramo" to "g", "gramos" to "g", "gramme" to "g", "grammes" to "g", "gramm" to "g",
        "grammo" to "g", "grammi" to "g", "grama" to "g", "грамм" to "g",
        "kilogramo" to "kg", "kilogramos" to "kg", "kilogramme" to "kg", "kilogrammes" to "kg",
        "kilogramm" to "kg", "chilogrammo" to "kg", "chilogrammi" to "kg", "quilograma" to "kg",
        "килограмм" to "kg",
        "libra" to "lb", "libras" to "lb", "livre" to "lb", "livres" to "lb", "pfund" to "lb",
        "pond" to "lb", "libbra" to "lb", "libbre" to "lb", "фунт" to "lb",
        "onza" to "oz", "onzas" to "oz", "once" to "oz", "onces" to "oz", "unze" to "oz",
        "ons" to "oz", "oncia" to "oz", "onça" to "oz", "унция" to "oz",
        "tonelada" to "t", "toneladas" to "t", "tonne" to "t", "tonnes" to "t", "ton" to "t", "tons" to "t",
        // Volume
        "litro" to "l", "litros" to "l", "litre" to "l", "litres" to "l", "liter" to "l",
        "liters" to "l", "litri" to "l", "литр" to "l",
        "mililitro" to "ml", "mililitros" to "ml", "millilitre" to "ml",
        "milliliter" to "ml", "milliliters" to "ml", "millilitri" to "ml", "миллилитр" to "ml",
        "galon" to "gal", "galones" to "gal", "gallone" to "gal", "galloni" to "gal",
        "galão" to "gal", "galões" to "gal", "галлон" to "gal",
        // Speed / energy
        "kilometre per hour" to "km/h", "kilometer per hour" to "km/h", "kilometres per hour" to "km/h",
        "kilometers per hour" to "km/h", "mile per hour" to "mph", "miles per hour" to "mph",
        "kilojoule" to "kJ", "kilojoules" to "kJ", "kilocaloria" to "kcal", "kilocalorias" to "kcal",
    )

    private val UNIT_LOOKUP: Map<String, UnitDef> by lazy {
        val map = HashMap<String, UnitDef>()
        for (def in UNIT_DEFS) {
            for (alias in def.aliases) {
                map[alias] = def
            }
        }
        val byCanonical = UNIT_DEFS.associateBy { it.canonical }
        for ((alias, canonical) in LOCALIZED_UNIT_ALIASES) {
            byCanonical[canonical]?.let { map[alias] = it }
        }
        map
    }

    private val UNIT_CONV_REGEX = Regex(
        """^([+-]?\s*\d+(?:[.,]\d+)?)\s*([^\s\d]+(?:\s+[^\s\d]+)?)\s+(?:to|in|as|->)\s+([^\s\d]+(?:\s+[^\s\d]+)?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val IMPLICIT_SUFFIX_REGEX = Regex(
        """^([+-]?\s*\d+(?:[.,]\d+)?)\s*([a-zA-Z°²/]+(?:\s+[a-zA-Z°²/]+)?)$""",
    )
    private val IMPLICIT_PREFIX_REGEX = Regex(
        """^([a-zA-Z°²/]+(?:\s+[a-zA-Z°²/]+)?)\s*([+-]?\s*\d+(?:[.,]\d+)?)$""",
    )

    private fun normalizeUnitKey(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun parseQuantityNumber(raw: String): Double? {
        val cleaned = raw.trim()
            .replace('\u2212', '-')
            .replace('\u2013', '-')
            .replace("\u00A0", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val decimal = symbols.decimalSeparator
        val grouping = symbols.groupingSeparator
        val normalized = when {
            cleaned.contains('.') && cleaned.contains(',') -> {
                if (cleaned.lastIndexOf('.') > cleaned.lastIndexOf(',')) {
                    cleaned.replace(",", "")
                } else {
                    cleaned.replace(".", "").replace(',', '.')
                }
            }
            decimal != '.' && cleaned.contains(',') ->
                cleaned.replace(grouping.toString(), "").replace(',', '.')
            else -> cleaned.replace(",", "")
        }
        return normalized.toDoubleOrNull()
    }

    private fun formatLocalizedNumber(value: Double, maxDecimals: Int = 4): String {
        val cleaned = if (abs(value) < 1e-12) 0.0 else value
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val pattern = "0." + "#".repeat(maxDecimals.coerceIn(1, 10))
        return DecimalFormat(pattern, symbols).format(cleaned)
    }

    private fun evaluateUnitConversion(input: String): CalcResult? {
        val normalizedInput = input.replace('\u2212', '-').replace('\u2013', '-')
        UNIT_CONV_REGEX.matchEntire(normalizedInput)?.let { m ->
            val value = parseQuantityNumber(m.groupValues[1]) ?: return null
            val fromUnit = UNIT_LOOKUP[normalizeUnitKey(m.groupValues[2])] ?: return null
            val toUnit = UNIT_LOOKUP[normalizeUnitKey(m.groupValues[3])] ?: return null
            if (fromUnit.family != toUnit.family) return null
            return buildConversionResult(input, value, fromUnit, toUnit)
        }

        val (value, unit) = parseImplicitUnit(normalizedInput) ?: return null
        val target = implicitTargetUnit(unit, value) ?: return null
        return buildConversionResult(input, value, unit, target)
    }

    private fun parseImplicitUnit(input: String): Pair<Double, UnitDef>? {
        IMPLICIT_SUFFIX_REGEX.matchEntire(input)?.let { m ->
            val unit = UNIT_LOOKUP[normalizeUnitKey(m.groupValues[2])] ?: return@let
            val value = parseQuantityNumber(m.groupValues[1]) ?: return@let
            return value to unit
        }
        IMPLICIT_PREFIX_REGEX.matchEntire(input)?.let { m ->
            val unit = UNIT_LOOKUP[normalizeUnitKey(m.groupValues[1])] ?: return@let
            val value = parseQuantityNumber(m.groupValues[2]) ?: return@let
            return value to unit
        }
        return null
    }

    private fun implicitTargetUnit(from: UnitDef, value: Double): UnitDef? {
        val baseValue = if (from.family == UnitFamily.TEMPERATURE) value else value * from.toBaseFactor
        val canonical = when (from.family) {
            UnitFamily.LENGTH -> {
                if (from.canonical in setOf("ft", "in", "yd", "mi", "nmi")) {
                    when {
                        baseValue >= 1000.0 -> "km"
                        baseValue >= 1.0 -> "m"
                        baseValue >= 0.01 -> "cm"
                        else -> "mm"
                    }
                } else {
                    when {
                        baseValue >= 1609.344 -> "mi"
                        baseValue >= 0.3048 -> "ft"
                        else -> "in"
                    }
                }
            }
            UnitFamily.MASS -> {
                if (from.canonical in setOf("lb", "oz", "st")) {
                    if (baseValue >= 1000.0) "kg" else "g"
                } else {
                    if (baseValue >= 453.59237) "lb" else "oz"
                }
            }
            UnitFamily.TEMPERATURE -> when (from.canonical) {
                "°C" -> "°F"
                "°F" -> "°C"
                else -> "°C"
            }
            UnitFamily.VOLUME -> {
                if (from.canonical in setOf("fl oz", "cup", "pint", "qt", "gal")) {
                    if (baseValue >= 1000.0) "l" else "ml"
                } else {
                    when {
                        baseValue >= 3785.411784 -> "gal"
                        baseValue >= 236.5882365 -> "cup"
                        else -> "fl oz"
                    }
                }
            }
            UnitFamily.SPEED -> when (from.canonical) {
                "km/h" -> "mph"
                "mph" -> "km/h"
                else -> "km/h"
            }
            UnitFamily.AREA -> when (from.canonical) {
                "m²" -> "ft²"
                "ft²" -> "m²"
                "km²" -> "mi²"
                "mi²" -> "km²"
                "acre" -> "ha"
                "ha" -> "acre"
                else -> "m²"
            }
            UnitFamily.ENERGY -> if (from.canonical in setOf("kcal", "cal")) "kJ" else "kcal"
            UnitFamily.ANGLE -> if (from.canonical == "rad") "deg" else "rad"
            UnitFamily.DATA, UnitFamily.TIME, UnitFamily.CSS -> return null
        }
        if (canonical == from.canonical) return null
        return UNIT_DEFS.firstOrNull { it.family == from.family && it.canonical == canonical }
    }

    private fun buildConversionResult(input: String, value: Double, from: UnitDef, to: UnitDef): CalcResult? {
        val converted = if (from.family == UnitFamily.TEMPERATURE) {
            val celsius = when (from.canonical) {
                "°C" -> value
                "°F" -> (value - 32.0) * 5.0 / 9.0
                "K" -> value - 273.15
                else -> return null
            }
            when (to.canonical) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> return null
            }
        } else {
            value * from.toBaseFactor / to.toBaseFactor
        }

        val numFormatted = formatLocalizedNumber(converted)
        val display = "$numFormatted ${to.canonical}"
        val family = from.family.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        return CalcResult(
            expression = input,
            formattedResult = display,
            subtitle = "${formatLocalizedNumber(value)} ${from.canonical} → $display ($family)",
            copyValue = numFormatted,
            category = "Unit Conversion",
        )
    }

    // -------------------------------------------------------------------------
    // 4. Currency Conversion
    // -------------------------------------------------------------------------

    private val CURRENCY_EXPLICIT_REGEX = Regex(
        """^(?:(?<pre>[$€£¥₹₩]|[A-Za-z]{3}|[A-Za-z]+)\s*)?(?<num>-?\d+(?:[.,]\d+)?)\s*(?<post>[$€£¥₹₩]|[A-Za-z]{3}|[A-Za-z]+)?\s+(?:to|in|as|->)\s+(?<to>[$€£¥₹₩]|[A-Za-z]{3}|[A-Za-z]+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val CURRENCY_IMPLICIT_SUFFIX = Regex(
        """^(-?\d+(?:[.,]\d+)?)\s*([$€£¥₹₩]|[A-Za-z]{3}|[A-Za-z]+)$""",
    )
    private val CURRENCY_IMPLICIT_PREFIX = Regex(
        """^([$€£¥₹₩]|[A-Za-z]{3}|[A-Za-z]+)\s*(-?\d+(?:[.,]\d+)?)$""",
    )

    private fun resolveCurrency(raw: String?, rates: Map<String, Double>): String? {
        val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        CURRENCY_SYMBOLS[token]?.let { return it }
        val upper = token.uppercase(Locale.ROOT)
        if (upper.length == 3 && rates.containsKey(upper)) return upper
        CURRENCY_NAMES[token.lowercase(Locale.ROOT)]?.let { name ->
            val localized = localizedCurrencyForName(name)
            return if (rates.containsKey(localized)) localized else name
        }
        return null
    }

    private fun localizedCurrencyForName(name: String): String {
        val country = Locale.getDefault().country.uppercase(Locale.ROOT)
        return when (name) {
            "USD" -> when (country) {
                "CA" -> "CAD"
                "AU" -> "AUD"
                "NZ" -> "NZD"
                "SG" -> "SGD"
                "HK" -> "HKD"
                else -> "USD"
            }
            "GBP" -> if (country == "EG") "EGP" else "GBP"
            "MXN" -> when (country) {
                "AR" -> "ARS"
                "CL" -> "CLP"
                "CO" -> "COP"
                "PH" -> "PHP"
                else -> "MXN"
            }
            else -> name
        }
    }

    private fun localeCurrencyCode(rates: Map<String, Double>): String {
        val code = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
        return if (rates.containsKey(code)) code else "USD"
    }

    private fun evaluateCurrencyConversion(input: String, context: Context?): CalcResult? {
        val (rates, isLive) = loadRates(context)
        var amount: Double? = null
        var fromCode: String? = null
        var toCode: String? = null
        var explicitTarget = false

        CURRENCY_EXPLICIT_REGEX.matchEntire(input)?.let { m ->
            val pre = m.groups["pre"]?.value
            val post = m.groups["post"]?.value
            fromCode = resolveCurrency(pre ?: post, rates)
            amount = parseQuantityNumber(m.groups["num"]!!.value)
            toCode = resolveCurrency(m.groups["to"]?.value, rates)
            explicitTarget = true
        }

        if (amount == null) {
            CURRENCY_IMPLICIT_SUFFIX.matchEntire(input)?.let { m ->
                amount = parseQuantityNumber(m.groupValues[1])
                fromCode = resolveCurrency(m.groupValues[2], rates)
            }
            CURRENCY_IMPLICIT_PREFIX.matchEntire(input)?.let { m ->
                if (amount == null) {
                    fromCode = resolveCurrency(m.groupValues[1], rates)
                    amount = parseQuantityNumber(m.groupValues[2])
                }
            }
        }

        val a = amount ?: return null
        val from = fromCode ?: return null
        var to = toCode ?: localeCurrencyCode(rates)
        if (to == from) {
            if (!explicitTarget) {
                to = when (from) {
                    "EUR" -> "USD"
                    "USD" -> "EUR"
                    else -> localeCurrencyCode(rates).takeIf { it != from } ?: "USD"
                }
            } else {
                return null
            }
        }

        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        val converted = (a / fromRate) * toRate

        val targetCurrency = runCatching { Currency.getInstance(to) }.getOrNull()
        val localized = runCatching {
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                if (targetCurrency != null) currency = targetCurrency
            }.format(converted)
        }.getOrNull() ?: "${formatLocalizedNumber(converted, 2)} $to"

        val unitRate = formatLocalizedNumber((1.0 / fromRate) * toRate, 4)
        val rateNote = if (isLive) "Cached live rate" else "Offline reference rate"
        return CalcResult(
            expression = input,
            formattedResult = "$localized $to",
            subtitle = "1 $from = $unitRate $to • $rateNote",
            copyValue = formatLocalizedNumber(converted, 4),
            category = "Currency",
        )
    }

    // -------------------------------------------------------------------------
    // 5. Arithmetic & Scientific Expressions
    // -------------------------------------------------------------------------

    private val PERCENT_OF_REGEX = Regex(
        """^(-?\d+(?:\.\d+)?)(?:\s*%\s*|\s+(?:percent|pct)\s+)of\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val PERCENT_ADJUST_REGEX = Regex(
        """^(.+?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*(?:%|percent|pct)$""",
        RegexOption.IGNORE_CASE,
    )

    private val IMPLICIT_MUL_DETECT_REGEX = Regex(
        """(?:\d|\))\s*\(|\)\s*(?:\d|[a-zA-Z])|\d\s*(?:pi|tau|e|sqrt|cbrt|sin|cos|tan|asin|acos|atan|log|ln|abs|ceil|floor|round|exp)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val MATH_CONSTANTS = setOf("pi", "tau", "e")

    private fun evaluateArithmetic(input: String): CalcResult? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.replace('\u2212', '-').replace('\u2013', '-')

        // Ignore plain phone numbers or ISO dates
        if (normalized.startsWith('+') && !normalized.drop(1).any { it in "+-*×/÷^%(" }) return null
        if (normalized.matches(Regex("""^\d{2,4}-\d{1,4}-\d{1,4}$"""))) return null

        PERCENT_OF_REGEX.matchEntire(normalized)?.let { m ->
            val pct = m.groupValues[1].toDoubleOrNull() ?: return null
            val rightExpr = m.groupValues[2].trim()
            val base = tryParseExpression(rightExpr) ?: return null
            val res = (pct / 100.0) * base
            if (res.isNaN() || res.isInfinite()) return null
            val formatted = formatNumber(res)
            return CalcResult(
                expression = trimmed,
                formattedResult = formatted,
                subtitle = "Percentage (${formatNumber(pct)}% of ${formatNumber(base)})",
                copyValue = formatted,
                category = "Calculator",
            )
        }

        // Handle "200 + 15%", "200 - 10%", "200 * 15%", "200 / 50%"
        PERCENT_ADJUST_REGEX.matchEntire(normalized)?.let { m ->
            val leftExpr = m.groupValues[1].trim()
            val op = m.groupValues[2]
            val pct = m.groupValues[3].toDoubleOrNull() ?: return null
            val leftVal = tryParseExpression(leftExpr)
            if (leftVal != null) {
                val res = when (op) {
                    "+" -> leftVal + (leftVal * pct / 100.0)
                    "-" -> leftVal - (leftVal * pct / 100.0)
                    "*" -> leftVal * (pct / 100.0)
                    "/" -> {
                        val factor = pct / 100.0
                        if (factor == 0.0) return null
                        leftVal / factor
                    }
                    else -> return null
                }
                if (!res.isNaN() && !res.isInfinite()) {
                    val formatted = formatNumber(res)
                    return CalcResult(
                        expression = trimmed,
                        formattedResult = formatted,
                        subtitle = "Percentage adjustment ($op$pct%)",
                        copyValue = formatted,
                        category = "Calculator",
                    )
                }
            }
        }

        // Must contain an operator, implicit multiplication, or a supported math function call so plain numbers don't trigger
        val hasOperator = normalized.any { it in "+-*×/÷^%" } ||
            Regex("""(?<=\d)\s*x\s*(?=\d|\()""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasFunction = Regex("""\b(sqrt|cbrt|sin|cos|tan|asin|acos|atan|log|ln|abs|ceil|floor|round|exp)\s*\(""", RegexOption.IGNORE_CASE)
            .containsMatchIn(normalized)
        val hasImplicitMul = IMPLICIT_MUL_DETECT_REGEX.containsMatchIn(normalized)
        if (!hasOperator && !hasFunction && !hasImplicitMul) return null

        val value = tryParseExpression(normalized) ?: return null
        if (value.isNaN() || value.isInfinite()) return null

        val formatted = formatNumber(value)
        return CalcResult(
            expression = trimmed,
            formattedResult = formatted,
            subtitle = "Result • Tap to copy",
            copyValue = formatted,
            category = "Calculator",
        )
    }

    private fun tryParseExpression(raw: String): Double? {
        val normalized = raw
            .replace('\u2212', '-')
            .replace('\u2013', '-')
            .replace('×', '*')
            .replace('÷', '/')
            .replace(Regex("""(?<=[\d)])\s*[xX]\s*(?=[\d(a-zA-Z])"""), "*")
            .replace(",", "")
        return try {
            ExpressionParser(normalized).parse()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatNumber(value: Double, maxDecimals: Int = 6): String {
        val cleaned = if (abs(value) < 1e-12) 0.0 else value
        val nearInt = round(cleaned)
        if (abs(cleaned - nearInt) < 1e-9 && abs(nearInt) < 1e15) {
            return nearInt.toLong().toString()
        }
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = "0." + "#".repeat(maxDecimals.coerceIn(1, 10))
        return DecimalFormat(pattern, symbols).format(cleaned)
    }

    private class ExpressionParser(private val input: String) {
        private var pos = -1
        private var ch: Char = ' '
        private var lastFactorEndedWithParen = false

        private fun nextChar() {
            pos++
            ch = if (pos < input.length) input[pos] else '\u0000'
        }

        private fun skipWhitespace() {
            while (ch == ' ' || ch == '\t') nextChar()
        }

        private fun eat(charToEat: Char): Boolean {
            skipWhitespace()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        private fun isImplicitMultiplicationNext(): Boolean {
            skipWhitespace()
            if (ch == '(' || ch in 'a'..'z' || ch in 'A'..'Z') return true
            if (lastFactorEndedWithParen && (ch in '0'..'9' || ch == '.')) return true
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            skipWhitespace()
            if (pos < input.length) throw IllegalArgumentException("Unexpected char: $ch")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*') -> x *= parseFactor()
                    eat('/') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                        x %= divisor
                    }
                    isImplicitMultiplicationNext() -> x *= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (eat('+')) return parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            var endedWithParen = false
            val startPos = pos
            if (eat('(')) {
                x = parseExpression()
                if (!eat(')')) throw IllegalArgumentException("Missing ')'")
                endedWithParen = true
            } else if ((ch in '0'..'9') || ch == '.') {
                while ((ch in '0'..'9') || ch == '.') nextChar()
                val numStr = input.substring(startPos, pos)
                x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                while (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') nextChar()
                val ident = input.substring(startPos, pos).lowercase(Locale.US)
                skipWhitespace()
                x = if (ident !in MATH_CONSTANTS && eat('(')) {
                    val arg = parseExpression()
                    if (!eat(')')) throw IllegalArgumentException("Missing ')' after $ident")
                    endedWithParen = true
                    applyFunction(ident, arg)
                } else {
                    when (ident) {
                        "pi" -> PI
                        "tau" -> 2.0 * PI
                        "e" -> E
                        else -> throw IllegalArgumentException("Unknown identifier: $ident")
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected char: $ch")
            }

            if (eat('^')) {
                x = x.pow(parseFactor())
                endedWithParen = lastFactorEndedWithParen
            }
            lastFactorEndedWithParen = endedWithParen
            return x
        }

        private fun applyFunction(name: String, arg: Double): Double = when (name) {
            "sqrt" -> {
                if (arg < 0) throw ArithmeticException("sqrt of negative")
                sqrt(arg)
            }
            "cbrt" -> cbrt(arg)
            "sin" -> sin(arg)
            "cos" -> cos(arg)
            "tan" -> tan(arg)
            "asin" -> asin(arg)
            "acos" -> acos(arg)
            "atan" -> atan(arg)
            "log", "log10" -> {
                if (arg <= 0) throw ArithmeticException("log of non-positive")
                log10(arg)
            }
            "ln" -> {
                if (arg <= 0) throw ArithmeticException("ln of non-positive")
                ln(arg)
            }
            "abs" -> abs(arg)
            "ceil" -> ceil(arg)
            "floor" -> floor(arg)
            "round" -> round(arg)
            "exp" -> exp(arg)
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }
}
