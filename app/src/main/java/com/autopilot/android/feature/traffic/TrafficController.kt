package com.autopilot.android.feature.traffic

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autopilot.android.data.repository.TrafficRepository
import com.autopilot.android.feature.traffic.worker.TrafficWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** UI-facing controls. Runs directly in-process for instant startup with WorkManager fallback. */
class TrafficController(
    private val context: Context,
    private val repository: TrafficRepository,
    private val executor: TrafficExecutor? = null
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun start(runId: String) {
        if (executor != null) {
            launchDirect(runId)
        } else {
            enqueue(runId)
        }
    }

    suspend fun pause(runId: String) {
        repository.setStatus(runId, TrafficStatus.PAUSED)
        activeJobs.remove(runId)?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(TrafficWorker.workName(runId))
    }

    suspend fun resume(runId: String) {
        repository.setStatus(runId, TrafficStatus.RUNNING)
        if (executor != null) {
            launchDirect(runId)
        } else {
            enqueue(runId)
        }
    }

    suspend fun stop(runId: String) {
        repository.setStatus(runId, TrafficStatus.STOPPED, markCompleted = true)
        activeJobs.remove(runId)?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(TrafficWorker.workName(runId))
    }

    /** Re-queues runs interrupted by process death or worker timeout. Paused runs wait for the user. */
    fun resumeActiveRuns() {
        appScope.launch {
            repository.getActiveRuns()
                .filter { it.status != TrafficStatus.PAUSED }
                .forEach { start(it.id) }
        }
    }

    private fun launchDirect(runId: String) {
        activeJobs.remove(runId)?.cancel()
        val job = appScope.launch {
            try {
                executor?.execute(runId, 0)
            } catch (_: CancellationException) {
                // Normal pause/stop cancellation
            } catch (e: Exception) {
                repository.getRun(runId)?.let { current ->
                    if (current.status in setOf(TrafficStatus.QUEUED, TrafficStatus.RUNNING)) {
                        repository.updateProgress(
                            current.copy(
                                status = TrafficStatus.FAILED,
                                lastError = e.message?.take(160) ?: "Traffic execution failed",
                                completedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } finally {
                activeJobs.remove(runId)
            }
        }
        activeJobs[runId] = job
    }

    private fun enqueue(runId: String) {
        val request = OneTimeWorkRequestBuilder<TrafficWorker>()
            .setInputData(workDataOf(TrafficWorker.KEY_RUN_ID to runId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TrafficWorker.workName(runId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
