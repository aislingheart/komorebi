package com.sameerasw.essentials.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
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
            val expanded = when (rawHex.length) {
                3 -> rawHex.map { "$it$it" }.joinToString("")
                6 -> rawHex
                8 -> rawHex.substring(2) // strip alpha for rgb/hsl
                else -> return null
            }
            val r = expanded.substring(0, 2).toInt(16)
            val g = expanded.substring(2, 4).toInt(16)
            val b = expanded.substring(4, 6).toInt(16)
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
            val r = m.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = m.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = m.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
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

        return null
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
        return "hsl(${round(h * 360).toInt()}, ${round(s * 100).toInt()}%, ${round(l * 100).toInt()}%)"
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
        UnitDef(UnitFamily.TEMPERATURE, "°C", 1.0, setOf("c", "°c", "celsius", "centigrade")),
        UnitDef(UnitFamily.TEMPERATURE, "°F", 1.0, setOf("f", "°f", "fahrenheit")),
        UnitDef(UnitFamily.TEMPERATURE, "K", 1.0, setOf("k", "kelvin")),

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

    private val UNIT_LOOKUP: Map<String, UnitDef> by lazy {
        val map = HashMap<String, UnitDef>()
        for (def in UNIT_DEFS) {
            for (alias in def.aliases) {
                map[alias] = def
            }
        }
        map
    }

    private val UNIT_CONV_REGEX = Regex(
        """^(-?\d+(?:\.\d+)?)\s*([a-zA-Z°²/]+(?:\s+oz)?)\s+(?:to|in|as|->)\s+([a-zA-Z°²/]+(?:\s+oz)?)$""",
        RegexOption.IGNORE_CASE,
    )

    private fun evaluateUnitConversion(input: String): CalcResult? {
        val match = UNIT_CONV_REGEX.matchEntire(input) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromKey = match.groupValues[2].trim().lowercase(Locale.US)
        val toKey = match.groupValues[3].trim().lowercase(Locale.US)

        val fromUnit = UNIT_LOOKUP[fromKey] ?: return null
        val toUnit = UNIT_LOOKUP[toKey] ?: return null
        if (fromUnit.family != toUnit.family) return null

        val converted = if (fromUnit.family == UnitFamily.TEMPERATURE) {
            val celsius = when (fromUnit.canonical) {
                "°C" -> value
                "°F" -> (value - 32.0) * 5.0 / 9.0
                "K" -> value - 273.15
                else -> return null
            }
            when (toUnit.canonical) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> return null
            }
        } else {
            val baseVal = value * fromUnit.toBaseFactor
            baseVal / toUnit.toBaseFactor
        }

        val numFormatted = formatNumber(converted, maxDecimals = 4)
        val display = "$numFormatted ${toUnit.canonical}"
        return CalcResult(
            expression = input,
            formattedResult = display,
            subtitle = "${formatNumber(value, 4)} ${fromUnit.canonical} → $display (${fromUnit.family.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }})",
            copyValue = numFormatted,
            category = "Unit Conversion",
        )
    }

    // -------------------------------------------------------------------------
    // 4. Currency Conversion
    // -------------------------------------------------------------------------

    private val CURRENCY_CODE_REGEX = Regex(
        """^(\d+(?:\.\d+)?)\s*([a-zA-Z]{3})\s+(?:to|in|as|->)\s+([a-zA-Z]{3})$""",
        RegexOption.IGNORE_CASE,
    )
    private val CURRENCY_SYMBOL_REGEX = Regex(
        """^([$€£¥₹₩])\s*(\d+(?:\.\d+)?)\s+(?:to|in|as|->)\s+([a-zA-Z]{3}|[$€£¥₹₩])$""",
        RegexOption.IGNORE_CASE,
    )

    private fun evaluateCurrencyConversion(input: String, context: Context?): CalcResult? {
        val (rates, isLive) = loadRates(context)

        var amount: Double? = null
        var fromCode: String? = null
        var toCode: String? = null

        CURRENCY_SYMBOL_REGEX.matchEntire(input)?.let { m ->
            fromCode = CURRENCY_SYMBOLS[m.groupValues[1]]
            amount = m.groupValues[2].toDoubleOrNull()
            val rawTo = m.groupValues[3]
            toCode = CURRENCY_SYMBOLS[rawTo] ?: rawTo.uppercase(Locale.US)
        }

        if (amount == null) {
            CURRENCY_CODE_REGEX.matchEntire(input)?.let { m ->
                amount = m.groupValues[1].toDoubleOrNull()
                fromCode = m.groupValues[2].uppercase(Locale.US)
                toCode = m.groupValues[3].uppercase(Locale.US)
            }
        }

        val a = amount ?: return null
        val from = fromCode ?: return null
        val to = toCode ?: return null

        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null

        val converted = (a / fromRate) * toRate
        val decimals = if (to == "JPY" || to == "KRW" || to == "IDR") 0 else 2
        val numericText = formatCurrencyAmount(converted, decimals)
        val sym = CURRENCY_DISPLAY_SYMBOLS[to]
        val formatted = if (sym != null) "$sym$numericText $to" else "$numericText $to"
        val unitRate = formatNumber((1.0 / fromRate) * toRate, 4)
        val rateNote = if (isLive) "Cached live rate" else "Offline reference rate"

        return CalcResult(
            expression = input,
            formattedResult = formatted,
            subtitle = "1 $from = $unitRate $to • $rateNote",
            copyValue = numericText.replace(",", ""),
            category = "Currency",
        )
    }

    private fun formatCurrencyAmount(value: Double, minMaxDecimals: Int): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = if (minMaxDecimals == 0) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, symbols).format(value)
    }

    // -------------------------------------------------------------------------
    // 5. Arithmetic & Scientific Expressions
    // -------------------------------------------------------------------------

    private val PERCENT_OF_REGEX = Regex(
        """^(\d+(?:\.\d+)?)\s*%\s*of\s*(-?\d+(?:\.\d+)?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val PERCENT_ADJUST_REGEX = Regex(
        """^(.+?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*%$""",
    )

    private fun evaluateArithmetic(input: String): CalcResult? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Ignore plain phone numbers or ISO dates
        if (trimmed.startsWith('+') && !trimmed.drop(1).any { it in "+-*×/÷^%(" }) return null
        if (trimmed.matches(Regex("""^\d{2,4}-\d{1,4}-\d{1,4}$"""))) return null

        PERCENT_OF_REGEX.matchEntire(trimmed)?.let { m ->
            val pct = m.groupValues[1].toDoubleOrNull() ?: return null
            val base = m.groupValues[2].toDoubleOrNull() ?: return null
            val res = (pct / 100.0) * base
            val formatted = formatNumber(res)
            return CalcResult(
                expression = trimmed,
                formattedResult = formatted,
                subtitle = "Percentage ($pct% of ${formatNumber(base)})",
                copyValue = formatted,
                category = "Calculator",
            )
        }

        // Handle "200 + 15%", "200 - 10%", "200 * 15%", "200 / 50%"
        PERCENT_ADJUST_REGEX.matchEntire(trimmed)?.let { m ->
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

        // Must contain an operator or a supported math function call so plain numbers don't trigger
        val hasOperator = trimmed.any { it in "+-*×/÷^%" } ||
            Regex("""(?<=\d)\s*x\s*(?=\d|\()""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        val hasFunction = Regex("""\b(sqrt|cbrt|sin|cos|tan|asin|acos|atan|log|ln|abs|ceil|floor|round|exp)\s*\(""", RegexOption.IGNORE_CASE)
            .containsMatchIn(trimmed)
        if (!hasOperator && !hasFunction) return null

        val value = tryParseExpression(trimmed) ?: return null
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
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (eat('+')) return parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('(')) {
                x = parseExpression()
                if (!eat(')')) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'..'9') || ch == '.') {
                while ((ch in '0'..'9') || ch == '.') nextChar()
                val numStr = input.substring(startPos, pos)
                x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                while (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') nextChar()
                val ident = input.substring(startPos, pos).lowercase(Locale.US)
                skipWhitespace()
                x = if (eat('(')) {
                    val arg = parseExpression()
                    if (!eat(')')) throw IllegalArgumentException("Missing ')' after $ident")
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
            }
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
