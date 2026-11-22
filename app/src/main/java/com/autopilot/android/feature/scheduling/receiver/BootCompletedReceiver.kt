package com.autopilot.android.feature.scheduling.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.domain.model.ScheduleStatus
import com.autopilot.android.domain.model.ScheduleType
import com.autopilot.android.domain.scheduling.ScheduleCalculator
import com.autopilot.android.feature.scheduling.worker.ScheduleReconciliationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Log.i(TAG, "Device reboot or package replace event detected: $action. Initiating schedule reconciliation.")

        val pendingResult = goAsync()
        val app = context.applicationContext as AutoPilotApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                reconcileSchedulesAfterReboot(app)
                // Schedule WorkManager periodic reconciliation
                ScheduleReconciliationWorker.enqueuePeriodic(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error reconciling schedules on reboot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reconcileSchedulesAfterReboot(app: AutoPilotApplication) {
        val scheduleRepo = app.scheduleRepository
        val scheduler = app.automationScheduler
        val now = System.currentTimeMillis()

        // 1. Reconcile stale RUNNING or STARTING states from pre-reboot process termination
        val staleSchedules = scheduleRepo.getRunningOrStartingSchedules()
        for (stale in staleSchedules) {
            Log.w(TAG, "Reconciling stale schedule '${stale.scheduleName}' (status: ${stale.status}) after reboot.")
            if (stale.scheduleType == ScheduleType.ONE_TIME) {
                scheduleRepo.updateExecutionState(
                    id = stale.id,
                    status = ScheduleStatus.MISSED,
                    nextExecutionAt = null,
                    lastExecutionAt = stale.lastExecutionAt ?: now,
                    error = "Process terminated by device reboot or system shutdown."
                )
            } else {
                val nextRun = ScheduleCalculator.calculateNextExecution(stale, now)
                scheduleRepo.updateExecutionState(
                    id = stale.id,
                    status = ScheduleStatus.SCHEDULED,
                    nextExecutionAt = nextRun,
                    lastExecutionAt = stale.lastExecutionAt ?: now,
                    error = "Previous run interrupted by device reboot."
                )
                if (nextRun != null && stale.enabled) {
                    scheduler.scheduleAlarm(stale.copy(nextExecutionAt = nextRun))
                }
            }
        }

        // 2. Re-register all enabled future schedules
        val activeSchedules = scheduleRepo.getActiveSchedules()
        for (sched in activeSchedules) {
            val nextRun = sched.nextExecutionAt
            if (nextRun != null && nextRun > now) {
                scheduler.scheduleAlarm(sched)
                Log.d(TAG, "Re-registered alarm for '${sched.scheduleName}' at $nextRun")
            } else {
                // If it passed while device was off
                if (sched.scheduleType == ScheduleType.ONE_TIME) {
                    scheduleRepo.updateExecutionState(
                        id = sched.id,
                        status = ScheduleStatus.MISSED,
                        nextExecutionAt = null,
                        lastExecutionAt = sched.lastExecutionAt,
                        error = "Device was powered off during scheduled execution time."
                    )
                } else {
                    val futureNextRun = ScheduleCalculator.calculateNextExecution(sched, now)
                    scheduleRepo.updateExecutionState(
                        id = sched.id,
                        status = ScheduleStatus.SCHEDULED,
                        nextExecutionAt = futureNextRun,
                        lastExecutionAt = sched.lastExecutionAt,
                        error = null
                    )
                    if (futureNextRun != null && sched.enabled) {
                        scheduler.scheduleAlarm(sched.copy(nextExecutionAt = futureNextRun))
                    }
                }
            }
        }
    }
}
