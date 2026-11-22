package com.autopilot.android.feature.scheduling.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.domain.model.ScheduleStatus
import com.autopilot.android.domain.model.ScheduleType
import com.autopilot.android.domain.scheduling.ScheduleCalculator
import com.autopilot.android.feature.automation.service.AutomationService
import com.autopilot.android.feature.scheduling.service.AutomationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AutomationScheduler.ACTION_TRIGGER_SCHEDULE) return

        val scheduleId = intent.getStringExtra(AutomationScheduler.EXTRA_SCHEDULE_ID) ?: return
        Log.i(TAG, "Alarm received for schedule ID: $scheduleId")

        val pendingResult = goAsync()
        val app = context.applicationContext as AutoPilotApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleScheduledTrigger(app, scheduleId)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling schedule trigger: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleScheduledTrigger(app: AutoPilotApplication, scheduleId: String) {
        val scheduleRepo = app.scheduleRepository
        val schedule = scheduleRepo.getScheduleById(scheduleId) ?: run {
            Log.w(TAG, "Schedule $scheduleId no longer exists in database.")
            return
        }

        if (!schedule.enabled || schedule.status == ScheduleStatus.CANCELLED) {
            Log.d(TAG, "Schedule $scheduleId is disabled or cancelled. Aborting trigger.")
            return
        }

        // Concurrency Check with Phase 2 Automation Engine
        val coordinator = app.automationCoordinator
        val currentEngineStatus = coordinator.engineState.value.status
        val currentRunningProfileId = coordinator.engineState.value.profile?.id

        if (currentEngineStatus == ProfileStatus.RUNNING && currentRunningProfileId != schedule.profileId) {
            val conflictReason = "Execution conflict: Profile '${coordinator.engineState.value.profile?.name}' is currently running."
            Log.w(TAG, conflictReason)

            app.scheduleNotificationManager.notifyScheduleConflict(schedule, conflictReason)

            if (schedule.scheduleType == ScheduleType.ONE_TIME) {
                scheduleRepo.updateExecutionState(
                    id = schedule.id,
                    status = ScheduleStatus.MISSED,
                    nextExecutionAt = null,
                    lastExecutionAt = System.currentTimeMillis(),
                    error = conflictReason
                )
            } else {
                val nextRun = ScheduleCalculator.calculateNextExecution(schedule, System.currentTimeMillis() + 1000)
                scheduleRepo.updateExecutionState(
                    id = schedule.id,
                    status = ScheduleStatus.SCHEDULED,
                    nextExecutionAt = nextRun,
                    lastExecutionAt = System.currentTimeMillis(),
                    error = conflictReason
                )
                if (nextRun != null) {
                    val updatedSchedule = schedule.copy(nextExecutionAt = nextRun)
                    app.automationScheduler.scheduleAlarm(updatedSchedule)
                }
            }
            return
        }

        // Launch Foreground Automation Service
        Log.i(TAG, "Launching automation profile '${schedule.profileName}' (${schedule.profileId}) for schedule '${schedule.scheduleName}'")
        app.scheduleNotificationManager.notifyScheduleStarted(schedule)

        AutomationService.startAutomation(app, schedule.profileId)

        // Update schedule state
        val now = System.currentTimeMillis()
        if (schedule.scheduleType == ScheduleType.ONE_TIME) {
            scheduleRepo.updateExecutionState(
                id = schedule.id,
                status = ScheduleStatus.RUNNING,
                nextExecutionAt = null,
                lastExecutionAt = now,
                error = null
            )
        } else {
            val nextRun = ScheduleCalculator.calculateNextExecution(schedule, now + 1000)
            scheduleRepo.updateExecutionState(
                id = schedule.id,
                status = ScheduleStatus.RUNNING,
                nextExecutionAt = nextRun,
                lastExecutionAt = now,
                error = null
            )
            if (nextRun != null) {
                val updatedSchedule = schedule.copy(nextExecutionAt = nextRun)
                app.automationScheduler.scheduleAlarm(updatedSchedule)
            }
        }
    }
}
