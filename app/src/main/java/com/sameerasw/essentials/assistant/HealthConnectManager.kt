/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Health
 * File: HealthConnectManager.kt
 * Description: On-device Health Connect reader using aggregation APIs, descending pagination,
 * per-metric partial permissions, interval deduplication, and background-read awareness.
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object HealthConnectManager {
    private const val TAG = "HealthConnectManager"

    val PERM_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val PERM_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val PERM_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val PERM_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val PERM_EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val PERM_BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        PERM_SLEEP,
        PERM_STEPS,
        PERM_HEART_RATE,
        PERM_WEIGHT,
        PERM_EXERCISE,
    )

    data class HealthPermissionStatus(
        val availability: String,
        val grantedCount: Int,
        val totalCoreCount: Int,
        val grantedMetrics: Set<String>,
        val missingMetrics: Set<String>,
        val hasBackgroundRead: Boolean,
        val supportsBackgroundRead: Boolean,
    ) {
        val isAvailable: Boolean get() = availability == "available"
        val hasAnyCorePermission: Boolean get() = grantedCount > 0
        val hasAllCorePermissions: Boolean get() = grantedCount >= totalCoreCount
    }

    data class HealthSnapshot(
        val missingPermissions: Boolean = false,
        val backgroundReadBlocked: Boolean = false,
        val grantedMetrics: Set<String> = emptySet(),
        val missingMetrics: Set<String> = emptySet(),
        val map: Map<String, Any?> = emptyMap(),
    )

    fun availability(context: Context): String =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> "available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
            else -> "unavailable"
        }

    fun supportsBackgroundRead(context: Context): Boolean = runCatching {
        if (availability(context) != "available") return false
        val client = HealthConnectClient.getOrCreate(context)
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    fun permissionsToRequest(context: Context): Set<String> =
        if (supportsBackgroundRead(context)) REQUIRED_PERMISSIONS + PERM_BACKGROUND else REQUIRED_PERMISSIONS

    suspend fun getPermissionStatus(context: Context): HealthPermissionStatus {
        val avail = availability(context)
        if (avail != "available") {
            return HealthPermissionStatus(
                availability = avail,
                grantedCount = 0,
                totalCoreCount = REQUIRED_PERMISSIONS.size,
                grantedMetrics = emptySet(),
                missingMetrics = metricLabels.values.toSet(),
                hasBackgroundRead = false,
                supportsBackgroundRead = false,
            )
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        val grantedMetrics = metricLabels.filterKeys { it in granted }.values.toSet()
        val missingMetrics = metricLabels.filterKeys { it !in granted }.values.toSet()
        val supportsBg = supportsBackgroundRead(context)
        return HealthPermissionStatus(
            availability = avail,
            grantedCount = grantedMetrics.size,
            totalCoreCount = REQUIRED_PERMISSIONS.size,
            grantedMetrics = grantedMetrics,
            missingMetrics = missingMetrics,
            hasBackgroundRead = !supportsBg || PERM_BACKGROUND in granted,
            supportsBackgroundRead = supportsBg,
        )
    }

    fun requestPermissions(context: Context): Boolean {
        if (availability(context) != "available") {
            return openHealthConnectSettings(context)
        }
        val contract = PermissionController.createRequestPermissionResultContract()
        val requested = permissionsToRequest(context)
        val intent = runCatching {
            contract.createIntent(context, requested).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrNull()
        if (intent != null && runCatching { context.startActivity(intent) }.isSuccess) {
            return true
        }
        return openHealthConnectSettings(context)
    }

    fun openHealthConnectSettings(context: Context): Boolean {
        val intents = listOf(
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            },
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.healthdata"),
            ),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    suspend fun readSnapshot(context: Context): Result<HealthSnapshot> = runCatching {
        val avail = availability(context)
        if (avail != "available") {
            return@runCatching HealthSnapshot(missingPermissions = true)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val grantedMetrics = metricLabels.filterKeys { it in granted }.values.toSet()
        val missingMetrics = metricLabels.filterKeys { it !in granted }.values.toSet()

        if (grantedMetrics.isEmpty()) {
            return@runCatching HealthSnapshot(
                missingPermissions = true,
                grantedMetrics = emptySet(),
                missingMetrics = missingMetrics,
            )
        }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()
        val yesterday = today.minusDays(1)
        val yesterdayStart = yesterday.atStartOfDay(zone).toInstant()
        val sevenDaysAgo = today.minusDays(6).atStartOfDay(zone).toInstant()
        val eightDaysAgo = today.minusDays(7).atStartOfDay(zone).toInstant()

        var backgroundBlocked = false
        fun handleMetricError(metric: String, error: Throwable) {
            Log.w(TAG, "Failed reading $metric: ${error.message}")
            if (isBackgroundReadSecurityException(error)) {
                backgroundBlocked = true
            }
        }

        // ---- 1. SLEEP (paginated, descending, stage-aware, deduplicated across sources) ----
        var sleepMinutesLastNight = 0L
        var sleepDeepMin = 0L
        var sleepRemMin = 0L
        var sleepLightMin = 0L
        var sleepAwakeMin = 0L
        var sleepStartIso: String? = null
        var sleepEndIso: String? = null
        var sleepLastDateIso: String? = null
        var sleepAvg7Min = 0L
        var sleepHistory: List<Map<String, Any>> = emptyList()

        if (PERM_SLEEP in granted) {
            runCatching {
                val allSessions = readAllSleepSessions(client, eightDaysAgo, now)
                if (allSessions.isNotEmpty()) {
                    val lastNightCutoff = todayStart.minus(Duration.ofHours(4))
                    val lastNightCandidates = allSessions.filter { !it.endTime.isBefore(lastNightCutoff) }
                    val primarySession = (lastNightCandidates.ifEmpty {
                        // Fall back to most recent session within last 36 hours if today hasn't synced
                        val recentCutoff = now.minus(Duration.ofHours(36))
                        allSessions.filter { !it.endTime.isBefore(recentCutoff) }
                    }).maxByOrNull { sessionAsleepDurationMin(it) }

                    if (primarySession != null) {
                        val breakdown = sleepStageBreakdown(primarySession)
                        sleepMinutesLastNight = breakdown.asleepMin
                        sleepDeepMin = breakdown.deepMin
                        sleepRemMin = breakdown.remMin
                        sleepLightMin = breakdown.lightMin
                        sleepAwakeMin = breakdown.awakeMin
                        sleepStartIso = primarySession.startTime.toString()
                        sleepEndIso = primarySession.endTime.toString()
                        sleepLastDateIso = primarySession.endTime.atZone(zone).toLocalDate().toString()
                    }

                    // Group by wake-up date and merge overlapping intervals so multiple trackers don't double-count
                    val byDate = allSessions
                        .filter { it.endTime.atZone(zone).toLocalDate() >= today.minusDays(6) }
                        .groupBy { it.endTime.atZone(zone).toLocalDate() }
                        .mapValues { (_, sessions) -> mergedSleepMinutes(sessions) }
                        .filterValues { it > 0L }

                    sleepHistory = byDate.entries
                        .sortedBy { it.key }
                        .takeLast(7)
                        .map { (date, mins) -> mapOf("date" to date.toString(), "minutes" to mins) }
                    sleepAvg7Min = if (sleepHistory.isNotEmpty()) {
                        (sleepHistory.sumOf { (it["minutes"] as Long) } / sleepHistory.size.toDouble()).roundToLong()
                    } else {
                        0L
                    }
                }
            }.onFailure { handleMetricError("sleep", it) }
        }

        // ---- 2. STEPS (Health Connect aggregation API — auto-deduplicates sources & avoids 1k cap) ----
        var stepsToday = 0L
        var stepsYesterday = 0L
        var stepsAvg7 = 0L
        var stepsHistory: List<Map<String, Any>> = emptyList()

        if (PERM_STEPS in granted) {
            runCatching {
                val dailySteps = linkedMapOf<LocalDate, Long>()
                for (offset in 6 downTo 0) {
                    dailySteps[today.minusDays(offset.toLong())] = 0L
                }

                // LocalDateTime range is REQUIRED for AggregateGroupByPeriodRequest
                val localStart = today.minusDays(6).atStartOfDay()
                val localEnd = today.plusDays(1).atStartOfDay()
                runCatching {
                    val buckets = client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(localStart, localEnd),
                            timeRangeSlicer = Period.ofDays(1),
                        ),
                    )
                    for (bucket in buckets) {
                        val bucketDate = bucket.startTime.toLocalDate()
                        val count = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                        if (bucketDate in dailySteps) {
                            dailySteps[bucketDate] = count
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "aggregateGroupByPeriod failed, falling back to per-day aggregate: ${it.message}")
                    if (isBackgroundReadSecurityException(it)) {
                        throw it
                    }
                    for (day in dailySteps.keys.toList()) {
                        val start = day.atStartOfDay(zone).toInstant()
                        val end = if (day == today) now else day.plusDays(1).atStartOfDay(zone).toInstant()
                        if (end.isAfter(start)) {
                            val res = client.aggregate(
                                AggregateRequest(
                                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                                    timeRangeFilter = TimeRangeFilter.between(start, end),
                                ),
                            )
                            dailySteps[day] = res[StepsRecord.COUNT_TOTAL] ?: 0L
                        }
                    }
                }

                // Anchor today & yesterday with exact Instant aggregates for timezone/DST precision
                if (now.isAfter(todayStart)) {
                    runCatching {
                        val todayRes = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(StepsRecord.COUNT_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                            ),
                        )
                        todayRes[StepsRecord.COUNT_TOTAL]?.let { dailySteps[today] = it }
                    }
                }
                runCatching {
                    val yestRes = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(yesterdayStart, todayStart),
                        ),
                    )
                    yestRes[StepsRecord.COUNT_TOTAL]?.let { dailySteps[yesterday] = it }
                }

                stepsToday = dailySteps[today] ?: 0L
                stepsYesterday = dailySteps[yesterday] ?: 0L
                val nonZeroDays = dailySteps.values.filter { it > 0L }
                stepsAvg7 = if (nonZeroDays.isNotEmpty()) {
                    (nonZeroDays.sum() / nonZeroDays.size.toDouble()).roundToLong()
                } else {
                    0L
                }
                stepsHistory = dailySteps.entries.map { (date, count) ->
                    mapOf("date" to date.toString(), "steps" to count)
                }
            }.onFailure { handleMetricError("steps", it) }
        }

        // ---- 3. HEART RATE (descending latest sample + aggregate 7-day avg/min/max) ----
        var latestHr: Int? = null
        var hrAvg7: Int? = null
        var hrMin7: Int? = null
        var hrMax7: Int? = null

        if (PERM_HEART_RATE in granted) {
            runCatching {
                val recentHr = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(sevenDaysAgo, now),
                        ascendingOrder = false,
                        pageSize = 20,
                    ),
                ).records
                latestHr = recentHr
                    .flatMap { it.samples }
                    .maxByOrNull { it.time }
                    ?.beatsPerMinute
                    ?.toInt()

                val hrAgg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MIN,
                            HeartRateRecord.BPM_MAX,
                        ),
                        timeRangeFilter = TimeRangeFilter.between(sevenDaysAgo, now),
                    ),
                )
                hrAvg7 = hrAgg[HeartRateRecord.BPM_AVG]?.toInt()
                hrMin7 = hrAgg[HeartRateRecord.BPM_MIN]?.toInt()
                hrMax7 = hrAgg[HeartRateRecord.BPM_MAX]?.toInt()

                if (hrAvg7 == null && recentHr.isNotEmpty()) {
                    val samples = recentHr.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        hrAvg7 = samples.map { it.beatsPerMinute }.average().roundToInt()
                    }
                }
            }.onFailure { handleMetricError("heart_rate", it) }
        }

        // ---- 4. WEIGHT (descending so newest weigh-in is always returned) ----
        var latestWeightKg: Double? = null

        if (PERM_WEIGHT in granted) {
            runCatching {
                val weights = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now),
                        ascendingOrder = false,
                        pageSize = 10,
                    ),
                ).records
                latestWeightKg = weights.maxByOrNull { it.time }?.weight?.inKilograms
            }.onFailure { handleMetricError("weight", it) }
        }

        // ---- 5. EXERCISES (descending, most recent 5 workouts in last 7 days) ----
        var exercises: List<Map<String, Any>> = emptyList()

        if (PERM_EXERCISE in granted) {
            runCatching {
                val records = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(sevenDaysAgo, now),
                        ascendingOrder = false,
                        pageSize = 20,
                    ),
                ).records
                exercises = records
                    .sortedByDescending { it.startTime }
                    .take(5)
                    .map { rec ->
                        mapOf(
                            "title" to (rec.title?.takeIf { it.isNotBlank() } ?: exerciseTypeName(rec.exerciseType)),
                            "minutes" to Duration.between(rec.startTime, rec.endTime).toMinutes(),
                            "start" to rec.startTime.toString(),
                        )
                    }
            }.onFailure { handleMetricError("exercise", it) }
        }

        val map = mapOf(
            "sleepMinutesLastNight" to sleepMinutesLastNight,
            "sleepDeepMin" to sleepDeepMin,
            "sleepRemMin" to sleepRemMin,
            "sleepLightMin" to sleepLightMin,
            "sleepAwakeMin" to sleepAwakeMin,
            "sleepStart" to sleepStartIso,
            "sleepEnd" to sleepEndIso,
            "sleepLastDate" to sleepLastDateIso,
            "sleepAvg7Min" to sleepAvg7Min,
            "sleepHistory" to sleepHistory,
            "stepsToday" to stepsToday,
            "stepsYesterday" to stepsYesterday,
            "stepsAvg7" to stepsAvg7,
            "stepsHistory" to stepsHistory,
            "latestHr" to latestHr,
            "hrAvg7" to hrAvg7,
            "hrMin7" to hrMin7,
            "hrMax7" to hrMax7,
            "latestWeightKg" to latestWeightKg,
            "exercises" to exercises,
            "grantedMetrics" to grantedMetrics.toList(),
            "missingMetrics" to missingMetrics.toList(),
            "backgroundReadBlocked" to backgroundBlocked,
        )

        HealthSnapshot(
            missingPermissions = false,
            backgroundReadBlocked = backgroundBlocked,
            grantedMetrics = grantedMetrics,
            missingMetrics = missingMetrics,
            map = map,
        )
    }

    private suspend fun readAllSleepSessions(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<SleepSessionRecord> {
        val all = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = 100,
                    pageToken = pageToken,
                ),
            )
            all += response.records
            pageToken = response.pageToken
            pages++
        } while (pageToken != null && pages < 5)
        return all
    }

    private data class SleepBreakdown(
        val asleepMin: Long,
        val deepMin: Long,
        val remMin: Long,
        val lightMin: Long,
        val awakeMin: Long,
    )

    private fun sleepStageBreakdown(session: SleepSessionRecord): SleepBreakdown {
        val totalSessionMin = Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0L)
        if (session.stages.isEmpty()) {
            return SleepBreakdown(asleepMin = totalSessionMin, deepMin = 0L, remMin = 0L, lightMin = 0L, awakeMin = 0L)
        }
        var deepSec = 0L
        var remSec = 0L
        var lightSec = 0L
        var awakeSec = 0L
        var otherAsleepSec = 0L

        for (stage in session.stages) {
            val sec = Duration.between(stage.startTime, stage.endTime).seconds.coerceAtLeast(0L)
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP -> deepSec += sec
                SleepSessionRecord.STAGE_TYPE_REM -> remSec += sec
                SleepSessionRecord.STAGE_TYPE_LIGHT -> lightSec += sec
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                -> awakeSec += sec
                else -> otherAsleepSec += sec
            }
        }
        val asleepFromStagesMin = ((deepSec + remSec + lightSec + otherAsleepSec) / 60.0).roundToLong()
        val asleepMin = if (asleepFromStagesMin > 0L) asleepFromStagesMin else totalSessionMin
        return SleepBreakdown(
            asleepMin = asleepMin,
            deepMin = (deepSec / 60.0).roundToLong(),
            remMin = (remSec / 60.0).roundToLong(),
            lightMin = (lightSec / 60.0).roundToLong(),
            awakeMin = (awakeSec / 60.0).roundToLong(),
        )
    }

    private fun sessionAsleepDurationMin(session: SleepSessionRecord): Long =
        sleepStageBreakdown(session).asleepMin

    /**
     * Merges overlapping [startTime, endTime] sleep intervals from multiple trackers writing
     * to Health Connect so the same night isn't double-counted.
     */
    private fun mergedSleepMinutes(sessions: List<SleepSessionRecord>): Long {
        if (sessions.isEmpty()) return 0L
        if (sessions.size == 1) return sessionAsleepDurationMin(sessions.first())

        val sorted = sessions.sortedBy { it.startTime }
        val merged = mutableListOf<Pair<Instant, Instant>>()
        for (s in sorted) {
            if (!s.endTime.isAfter(s.startTime)) continue
            if (merged.isEmpty()) {
                merged += s.startTime to s.endTime
            } else {
                val (prevStart, prevEnd) = merged.last()
                if (!s.startTime.isAfter(prevEnd)) {
                    val newEnd = if (s.endTime.isAfter(prevEnd)) s.endTime else prevEnd
                    merged[merged.lastIndex] = prevStart to newEnd
                } else {
                    merged += s.startTime to s.endTime
                }
            }
        }
        // Subtract awake time from primary longest session if stages exist
        val totalMergedMin = merged.sumOf { (s, e) -> Duration.between(s, e).toMinutes().coerceAtLeast(0L) }
        val primary = sessions.maxByOrNull { Duration.between(it.startTime, it.endTime).toMinutes() }
        val primaryAwakeMin = primary?.let { sleepStageBreakdown(it).awakeMin } ?: 0L
        return (totalMergedMin - primaryAwakeMin).coerceAtLeast(
            primary?.let { sessionAsleepDurationMin(it) } ?: totalMergedMin,
        )
    }

    private fun isBackgroundReadSecurityException(error: Throwable): Boolean {
        if (error !is SecurityException) return false
        val msg = error.message?.lowercase().orEmpty()
        return "background" in msg || "foreground" in msg || "read_health_data_in_background" in msg
    }

    private val metricLabels: Map<String, String> = linkedMapOf(
        PERM_SLEEP to "Sleep",
        PERM_STEPS to "Steps",
        PERM_HEART_RATE to "Heart rate",
        PERM_WEIGHT to "Weight",
        PERM_EXERCISE to "Exercise",
    )

    private fun exerciseTypeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Stationary bike"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> "Swim"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        -> "Strength"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        else -> "Workout"
    }
}
