package com.autopilot.android.feature.scheduling.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.feature.scheduling.receiver.ScheduleReceiver

class AutomationScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AutomationScheduler"
        const val ACTION_TRIGGER_SCHEDULE = "com.autopilot.android.action.TRIGGER_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Checks if the application is permitted to schedule exact alarms (API 31+).
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    /**
     * Registers an alarm for the given schedule.
     * Uses setExactAndAllowWhileIdle if eligible, falling back to setAndAllowWhileIdle.
     */
    fun scheduleAlarm(schedule: AutomationSchedule) {
        val triggerAtMillis = schedule.nextExecutionAt ?: schedule.targetEpochMillis

        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Cannot schedule alarm in the past: $triggerAtMillis for schedule ${schedule.id}")
            return
        }

        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service unavailable on this device")
            return
        }

        val pendingIntent = createPendingIntent(schedule.id, schedule.profileId)

        try {
            if (canScheduleExactAlarms()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Registered EXACT alarm for schedule '${schedule.scheduleName}' at $triggerAtMillis")
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Registered INEXACT alarm fallback for schedule '${schedule.scheduleName}' at $triggerAtMillis")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for ${schedule.id}: ${e.message}. Falling back.")
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling: ${fallbackEx.message}")
            }
        }
    }

    /**
     * Cancels any registered alarm for the given schedule ID.
     */
    fun cancelAlarm(scheduleId: String, profileId: String = "") {
        if (alarmManager == null) return

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_TRIGGER_SCHEDULE
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_PROFILE_ID, profileId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for schedule ID $scheduleId")
        }
    }

    private fun createPendingIntent(scheduleId: String, profileId: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_TRIGGER_SCHEDULE
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_PROFILE_ID, profileId)
        }

        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
