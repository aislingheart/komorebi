package com.sameerasw.essentials.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast

/**
 * Launches AlarmClock intents defensively. Some OEM Clock apps reject
 * EXTRA_SKIP_UI, do not advertise the AlarmClock actions, or resolve but then
 * throw a SecurityException. Every failure is reported back to the caller (and
 * optionally as a toast) instead of being swallowed by runCatching.
 */
object ClockIntentLauncher {
    private const val TAG = "ClockIntentLauncher"

    data class Result(
        val success: Boolean,
        val action: String? = null,
        val usedFallback: Boolean = false,
        val error: String? = null,
    )

    fun openClockApp(
        context: Context,
        failureToast: String? = null,
    ): Result {
        val knownClockPackages = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.oneplus.deskclock",
            "com.miui.clock",
        )
        for (packageName in knownClockPackages) {
            val launch = runCatching {
                context.packageManager.getLaunchIntentForPackage(packageName)
            }.getOrNull() ?: continue
            val started = runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (started) {
                return Result(success = true, action = launch.action, usedFallback = true)
            }
        }
        return launch(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), failureToast)
    }

    fun launch(
        context: Context,
        intent: Intent,
        failureToast: String? = null,
    ): Result {
        val candidates = buildCandidates(intent)
        val packageManager = context.packageManager
        var lastError: String? = "No Clock app is available on this device."

        for ((index, candidate) in candidates.withIndex()) {
            val resolved = runCatching {
                packageManager.resolveActivity(candidate, 0)
            }.getOrNull()

            // If resolution fails, keep looking. The final fallback is still
            // attempted below because some devices hide Clock activities from
            // package visibility even when they can be launched.
            if (resolved == null && index < candidates.lastIndex) {
                lastError = "No app can handle ${candidate.action ?: "the Clock intent"}"
                continue
            }

            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return Result(
                    success = true,
                    action = candidate.action,
                    usedFallback = index > 0,
                )
            } catch (e: ActivityNotFoundException) {
                lastError = e.message ?: "No app can handle ${candidate.action}"
            } catch (e: SecurityException) {
                lastError = e.message ?: "The Clock app denied ${candidate.action}"
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            Log.w(TAG, "Failed to launch ${candidate.action}: $lastError")
        }

        Log.w(TAG, "All Clock launch attempts failed for ${intent.action}: $lastError")
        if (failureToast != null) {
            showToast(context, failureToast)
        }
        return Result(success = false, action = intent.action, error = lastError)
    }

    private fun showToast(context: Context, message: String) {
        val appContext = context.applicationContext
        val show = Runnable {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show.run()
        } else {
            Handler(Looper.getMainLooper()).post(show)
        }
    }

    private fun buildCandidates(intent: Intent): List<Intent> {
        val candidates = mutableListOf<Intent>()
        candidates += Intent(intent)

        if (intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            candidates += Intent(intent).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

        val showAction = when (intent.action) {
            AlarmClock.ACTION_SET_TIMER, AlarmClock.ACTION_SHOW_TIMERS -> AlarmClock.ACTION_SHOW_TIMERS
            else -> AlarmClock.ACTION_SHOW_ALARMS
        }
        candidates += Intent(showAction)
        if (showAction != AlarmClock.ACTION_SHOW_ALARMS) {
            candidates += Intent(AlarmClock.ACTION_SHOW_ALARMS)
        }

        return candidates.distinctBy { candidate ->
            listOf(
                candidate.action,
                candidate.component?.flattenToString(),
                candidate.categories?.sorted()?.joinToString("|"),
                candidate.`package`,
                candidate.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false),
            ).joinToString("::")
        }
    }
}
