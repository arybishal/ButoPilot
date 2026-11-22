package com.autopilot.android.feature.scheduling.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autopilot.android.AutoPilotApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+).
 * Broadcast by the Android OS whenever the user grants or revokes the exact alarm permission in Settings.
 * Automatically reconciles existing schedules to exact or inexact alarm mode without duplicates.
 */
class AlarmPermissionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmPermissionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }

        val app = context.applicationContext as AutoPilotApplication
        val scheduler = app.automationScheduler
        val isExactAllowed = scheduler.canScheduleExactAlarms()

        Log.i(TAG, "Exact alarm permission state changed. canScheduleExactAlarms = $isExactAllowed")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll(app.scheduleRepository)
                Log.d(TAG, "Successfully reconciled all schedules following exact alarm permission change.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconcile schedules on permission state change: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
