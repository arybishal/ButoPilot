package com.autopilot.android.feature.scheduling.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.domain.model.ScheduleStatus
import com.autopilot.android.domain.model.ScheduleType
import com.autopilot.android.domain.scheduling.ScheduleCalculator
import java.util.concurrent.TimeUnit

class ScheduleReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ScheduleReconciliationWorker"
        const val WORK_NAME_PERIODIC = "autopilot_schedule_reconciliation_periodic"
        const val WORK_NAME_ONE_TIME = "autopilot_schedule_reconciliation_one_time"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleReconciliationWorker>(
                2, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // 30-min flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Enqueued periodic schedule reconciliation worker (2-hour cycle)")
        }

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleReconciliationWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting schedule reconciliation work...")
        val app = applicationContext as? AutoPilotApplication ?: return Result.failure()
        val scheduleRepo = app.scheduleRepository
        val scheduler = app.automationScheduler
        val now = System.currentTimeMillis()

        try {
            val activeSchedules = scheduleRepo.getActiveSchedules()
            for (schedule in activeSchedules) {
                if (!schedule.enabled) continue

                val nextRun = schedule.nextExecutionAt
                if (nextRun != null && nextRun <= now) {
                    // Missed schedule execution
                    Log.w(TAG, "Detected missed schedule: '${schedule.scheduleName}' (due at $nextRun, current: $now)")
                    if (schedule.scheduleType == ScheduleType.ONE_TIME) {
                        scheduleRepo.updateExecutionState(
                            id = schedule.id,
                            status = ScheduleStatus.MISSED,
                            nextExecutionAt = null,
                            lastExecutionAt = schedule.lastExecutionAt,
                            error = "Scheduled execution time passed without trigger (missed window)."
                        )
                    } else {
                        val futureNextRun = ScheduleCalculator.calculateNextExecution(schedule, now)
                        scheduleRepo.updateExecutionState(
                            id = schedule.id,
                            status = ScheduleStatus.SCHEDULED,
                            nextExecutionAt = futureNextRun,
                            lastExecutionAt = schedule.lastExecutionAt,
                            error = "Previous recurrence window was missed."
                        )
                        if (futureNextRun != null) {
                            scheduler.scheduleAlarm(schedule.copy(nextExecutionAt = futureNextRun))
                        }
                    }
                } else if (nextRun != null && nextRun > now) {
                    // Re-verify alarm registration
                    scheduler.scheduleAlarm(schedule)
                }
            }

            Log.i(TAG, "Completed schedule reconciliation successfully. Reconciled ${activeSchedules.size} schedules.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during schedule reconciliation: ${e.message}", e)
            return Result.retry()
        }
    }
}
