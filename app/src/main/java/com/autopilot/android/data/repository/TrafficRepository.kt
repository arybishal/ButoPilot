package com.autopilot.android.data.repository

import com.autopilot.android.data.local.dao.TrafficDao
import com.autopilot.android.data.local.entity.TrafficRunEntity
import com.autopilot.android.feature.traffic.TrafficMode
import com.autopilot.android.feature.traffic.TrafficRun
import com.autopilot.android.feature.traffic.TrafficSpeed
import com.autopilot.android.feature.traffic.TrafficStatus
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TrafficRepository {
    fun observeRuns(): Flow<List<TrafficRun>>
    suspend fun getRun(id: String): TrafficRun?
    suspend fun getActiveRuns(): List<TrafficRun>
    suspend fun createRun(run: TrafficRun)
    suspend fun updateProgress(run: TrafficRun)
    suspend fun setStatus(id: String, status: TrafficStatus, markCompleted: Boolean = false)
    suspend fun deleteRun(id: String)
}

class TrafficRepositoryImpl(
    private val dao: TrafficDao,
    private val gson: Gson = Gson()
) : TrafficRepository {

    override fun observeRuns(): Flow<List<TrafficRun>> = dao.getAllFlow().map { entities ->
        entities.map { it.toDomain(gson) }
    }

    override suspend fun getRun(id: String): TrafficRun? = dao.getById(id)?.toDomain(gson)

    override suspend fun getActiveRuns(): List<TrafficRun> = dao.getActive().map { it.toDomain(gson) }

    override suspend fun createRun(run: TrafficRun) = dao.insert(run.toEntity(gson))

    override suspend fun updateProgress(run: TrafficRun) = dao.update(run.toEntity(gson))

    override suspend fun setStatus(id: String, status: TrafficStatus, markCompleted: Boolean) {
        val run = getRun(id) ?: return
        updateProgress(
            run.copy(
                status = status,
                completedAt = if (markCompleted || status == TrafficStatus.COMPLETED ||
                    status == TrafficStatus.FAILED || status == TrafficStatus.STOPPED
                ) System.currentTimeMillis() else null
            )
        )
    }

    override suspend fun deleteRun(id: String) = dao.delete(id)
}

private fun TrafficRunEntity.toDomain(gson: Gson): TrafficRun {
    val paths: List<String> = try {
        gson.fromJson(pagePathsJson, Array<String>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
    return TrafficRun(
        id = id,
        websiteUrl = websiteUrl,
        plannedEvents = plannedEvents,
        mode = safeEnum(mode, TrafficMode.PAGE_REQUEST),
        speed = safeEnum(speed, TrafficSpeed.NORMAL),
        concurrency = concurrency.coerceIn(1, 4),
        pagePaths = paths,
        status = safeEnum(status, TrafficStatus.INTERRUPTED),
        submitted = submitted,
        failed = failed,
        lastError = lastError,
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt
    )
}

private fun TrafficRun.toEntity(gson: Gson): TrafficRunEntity = TrafficRunEntity(
    id = id,
    websiteUrl = websiteUrl,
    plannedEvents = plannedEvents,
    mode = mode.name,
    speed = speed.name,
    concurrency = concurrency.coerceIn(1, 4),
    pagePathsJson = gson.toJson(pagePaths),
    status = status.name,
    submitted = submitted,
    failed = failed,
    lastError = lastError,
    createdAt = createdAt,
    startedAt = startedAt,
    completedAt = completedAt
)

private inline fun <reified T : Enum<T>> safeEnum(value: String, fallback: T): T = try {
    enumValueOf<T>(value)
} catch (e: Exception) {
    fallback
}
