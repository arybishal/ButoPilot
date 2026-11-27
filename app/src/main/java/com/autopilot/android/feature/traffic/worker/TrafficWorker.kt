package com.autopilot.android.feature.traffic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.feature.traffic.TrafficStatus
import kotlinx.coroutines.CancellationException

class TrafficWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AutoPilotApplication ?: return Result.failure()
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        val repository = app.trafficRepository
        val run = repository.getRun(runId) ?: return Result.failure()

        if (run.status in setOf(
                TrafficStatus.PAUSED, TrafficStatus.COMPLETED,
                TrafficStatus.FAILED, TrafficStatus.STOPPED
            )
        ) return Result.success()

        return try {
            app.trafficExecutor.execute(runId, runAttemptCount)
            Result.success()
        } catch (e: CancellationException) {
            val latest = repository.getRun(runId)
            if (latest?.status in setOf(
                    TrafficStatus.RUNNING, TrafficStatus.QUEUED,
                    TrafficStatus.INTERRUPTED
                )
            ) {
                repository.setStatus(runId, TrafficStatus.INTERRUPTED)
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            repository.getRun(runId)?.let { current ->
                repository.updateProgress(
                    current.copy(
                        status = TrafficStatus.FAILED,
                        lastError = e.message?.take(160) ?: "Execution failed",
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_RUN_ID = "traffic_run_id"

        fun workName(runId: String): String = "traffic_run_$runId"

        fun enqueue(context: Context, runId: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<TrafficWorker>()
                .setInputData(workDataOf(KEY_RUN_ID to runId))
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                workName(runId),
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
