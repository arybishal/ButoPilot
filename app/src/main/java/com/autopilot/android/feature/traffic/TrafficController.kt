package com.autopilot.android.feature.traffic

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autopilot.android.data.repository.TrafficRepository
import com.autopilot.android.feature.traffic.worker.TrafficWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** UI-facing controls. Work state lives in Room; WorkManager only carries the execution. */
class TrafficController(
    private val context: Context,
    private val repository: TrafficRepository
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(runId: String) = enqueue(runId)

    suspend fun pause(runId: String) {
        repository.setStatus(runId, TrafficStatus.PAUSED)
        WorkManager.getInstance(context).cancelUniqueWork(TrafficWorker.workName(runId))
    }

    suspend fun resume(runId: String) {
        repository.setStatus(runId, TrafficStatus.RUNNING)
        enqueue(runId)
    }

    suspend fun stop(runId: String) {
        repository.setStatus(runId, TrafficStatus.STOPPED, markCompleted = true)
        WorkManager.getInstance(context).cancelUniqueWork(TrafficWorker.workName(runId))
    }

    /** Re-queues runs interrupted by process death or worker timeout. Paused runs wait for the user. */
    fun resumeActiveRuns() {
        appScope.launch {
            repository.getActiveRuns()
                .filter { it.status != TrafficStatus.PAUSED }
                .forEach { enqueue(it.id) }
        }
    }

    private fun enqueue(runId: String) {
        val request = OneTimeWorkRequestBuilder<TrafficWorker>()
            .setInputData(workDataOf(TrafficWorker.KEY_RUN_ID to runId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TrafficWorker.workName(runId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
