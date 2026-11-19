/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Assistant
 * File: HealthConnectManager.kt
 * Description: On-device health data reads via Health Connect (sleep, steps, heart rate, weight, exercise).
 */

package com.sameerasw.essentials.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HealthConnectManager {

    private const val TAG = "HealthConnectManager"
    const val ACTION_MANAGE_HEALTH_PERMISSIONS =
        "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

    val REQUIRED_PERMISSIONS = setOf(
        "android.permission.health.READ_SLEEP",
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_WEIGHT",
        "android.permission.health.READ_EXERCISE",
    )

    fun availability(context: Context): String = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> "available"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
        else -> "unavailable"
    }

    suspend fun grantedPermissions(context: Context): Set<String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                HealthConnectClient.getOrCreate(context)
                    .permissionController
                    .getGrantedPermissions()
            }.getOrNull()
        }

    fun hasAllRequiredPermissions(context: Context, granted: Set<String>?): Boolean {
        if (granted == null) return false
        return REQUIRED_PERMISSIONS.all { granted.contains(it) }
    }

    fun openPermissionsScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opened = runCatching {
                val intent = Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (opened) return true
        }
        return openHealthConnectSettings(context)
    }

    /**
     * Opens the Health Connect permission request screen using Jetpack's PermissionController contract.
     */
    fun requestPermissions(context: Context): Boolean {
        val direct = runCatching {
            val contract = PermissionController.createRequestPermissionResultContract()
            val intent = contract.createIntent(context, REQUIRED_PERMISSIONS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.onFailure {
            Log.w(TAG, "Direct Health Connect permission request failed, falling back", it)
        }.getOrDefault(false)
        if (direct) return true
        return openPermissionsScreen(context)
    }

    fun openHealthConnectSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)

    data class HealthSnapshot(
        val map: Map<String, Any?>,
        val missingPermissions: Boolean,
    )

    suspend fun readSnapshot(context: Context): Result<HealthSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                val missing = REQUIRED_PERMISSIONS.filterNot { granted.contains(it) }
                if (missing.isNotEmpty()) {
                    return@runCatching HealthSnapshot(emptyMap(), missingPermissions = true)
                }

                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val lastNightStart = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
                val weekAgo = now.minus(Duration.ofDays(7))

                val sleepWeek = client.readRecords(
                    ReadRecordsRequest(
                        SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(weekAgo, now),
                    ),
                ).records
                    .sortedByDescending { it.startTime }
                val lastNight = sleepWeek.firstOrNull { it.startTime >= lastNightStart }

                val stepsWeek = client.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(weekAgo, now),
                    ),
                ).records

                val hrWeek = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(weekAgo, now),
                        ),
                    ).records
                }.getOrDefault(emptyList())

                val weights = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            WeightRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now),
                        ),
                    ).records
                }.getOrDefault(emptyList())

                val exercises = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            ExerciseSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(weekAgo, now),
                        ),
                    ).records
                        .sortedByDescending { it.startTime }
                        .take(5)
                }.getOrDefault(emptyList())

                val sleepMinutesByWakeDate = sleepWeek.groupBy { record ->
                    record.endTime.atZone(zone).toLocalDate()
                }

                val sleepHistory = (0..6).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val minutes = sleepMinutesByWakeDate[date]
                        ?.filter { it.stages.isNotEmpty() || it.endTime.isAfter(it.startTime) }
                        ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                        ?: 0L
                    mapOf("date" to date.toString(), "minutes" to minutes)
                }

                val stepsByDay = stepsWeek.groupBy { record ->
                    record.startTime.atZone(zone).toLocalDate()
                }
                val stepsHistory = (0..6).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val count = stepsByDay[date]?.sumOf { it.count } ?: 0L
                    mapOf("date" to date.toString(), "steps" to count)
                }
                val stepsToday = stepsByDay[today]?.sumOf { it.count } ?: 0L
                val stepsYesterday = stepsByDay[today.minusDays(1)]?.sumOf { it.count } ?: 0L
                val stepsAvg7 = stepsHistory.map { (it["steps"] as? Long) ?: 0L }.drop(1)
                    .filter { it > 0 }.let { list -> if (list.isEmpty()) 0L else list.sum() / list.size }

                val hrSamples = hrWeek.flatMap { it.samples }
                val latestHr = hrSamples.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                val hrAvg7 = hrSamples.takeIf { it.isNotEmpty() }?.map { it.beatsPerMinute.toInt() }
                    ?.average()?.toInt()

                val latestWeight = weights.maxByOrNull { it.time }?.weight?.inKilograms

                val sleepStages = lastNight?.stages.orEmpty()
                fun stageMinutes(type: Int): Long = sleepStages
                    .filter { it.stage == type }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

                val lastNightMinutes = lastNight?.let {
                    Duration.between(it.startTime, it.endTime).toMinutes()
                } ?: 0L
                val sleepAvg7 = sleepHistory.map { (it["minutes"] as? Long) ?: 0L }
                    .filter { it > 0 }.let { list -> if (list.isEmpty()) 0L else list.sum() / list.size }

                val exerciseList = exercises.map { record ->
                    mapOf(
                        "title" to (record.title ?: exerciseTypeName(record.exerciseType)),
                        "type" to exerciseTypeName(record.exerciseType),
                        "minutes" to Duration.between(record.startTime, record.endTime).toMinutes(),
                        "start" to record.startTime.toString(),
                    )
                }

                val snapshot = mapOf(
                    "sleepMinutesLastNight" to lastNightMinutes,
                    "sleepDeepMin" to stageMinutes(SleepSessionRecord.STAGE_TYPE_DEEP),
                    "sleepRemMin" to stageMinutes(SleepSessionRecord.STAGE_TYPE_REM),
                    "sleepLightMin" to stageMinutes(SleepSessionRecord.STAGE_TYPE_LIGHT),
                    "sleepAwakeMin" to stageMinutes(SleepSessionRecord.STAGE_TYPE_AWAKE),
                    "sleepStart" to lastNight?.startTime?.toString(),
                    "sleepEnd" to lastNight?.endTime?.toString(),
                    "sleepAvg7Min" to sleepAvg7,
                    "sleepHistory" to sleepHistory,
                    "stepsToday" to stepsToday,
                    "stepsYesterday" to stepsYesterday,
                    "stepsAvg7" to stepsAvg7,
                    "stepsHistory" to stepsHistory,
                    "latestHr" to latestHr,
                    "hrAvg7" to hrAvg7,
                    "latestWeightKg" to latestWeight,
                    "exercises" to exerciseList,
                    "updatedAt" to now.toEpochMilli(),
                )
                HealthSnapshot(snapshot, missingPermissions = false)
            }.onFailure { Log.e(TAG, "Health read failed", it) }
        }

    private fun exerciseTypeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        -> "Swim"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        else -> "Workout"
    }
}
