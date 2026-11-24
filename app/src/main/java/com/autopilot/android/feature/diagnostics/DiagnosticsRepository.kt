package com.autopilot.android.feature.diagnostics

import com.autopilot.android.data.local.dao.DiagnosticDao
import com.autopilot.android.data.local.entity.DiagnosticRecordEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DiagnosticsRepository {
    fun getAllHistoryFlow(): Flow<List<DiagnosticHistoryRecord>>
    suspend fun getAllHistory(): List<DiagnosticHistoryRecord>
    suspend fun saveCheckResults(results: List<DiagnosticCheckResult>)
    suspend fun clearHistory()
    suspend fun pruneOldHistory(daysToKeep: Int = 30): Int
}

class DiagnosticsRepositoryImpl(
    private val diagnosticDao: DiagnosticDao
) : DiagnosticsRepository {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    override fun getAllHistoryFlow(): Flow<List<DiagnosticHistoryRecord>> {
        return diagnosticDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllHistory(): List<DiagnosticHistoryRecord> {
        return diagnosticDao.getAll().map { it.toDomain() }
    }

    override suspend fun saveCheckResults(results: List<DiagnosticCheckResult>) {
        val entities = results.map { result ->
            DiagnosticRecordEntity(
                id = result.id,
                timestamp = result.timestamp,
                category = result.category.name,
                status = result.status.name,
                shortResult = result.shortResult,
                durationMs = result.durationMs,
                technicalDetailsJson = gson.toJson(result.technicalDetails)
            )
        }
        diagnosticDao.insertAll(entities)
    }

    override suspend fun clearHistory() {
        diagnosticDao.clearAll()
    }

    override suspend fun pruneOldHistory(daysToKeep: Int): Int {
        val cutoff = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 3600 * 1000)
        return diagnosticDao.deleteOlderThan(cutoff)
    }

    private fun DiagnosticRecordEntity.toDomain(): DiagnosticHistoryRecord {
        val details: Map<String, String> = try {
            gson.fromJson(technicalDetailsJson, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val parsedCategory = try {
            DiagnosticCategory.valueOf(category)
        } catch (e: Exception) {
            DiagnosticCategory.DATABASE
        }

        val parsedStatus = try {
            DiagnosticStatus.valueOf(status)
        } catch (e: Exception) {
            DiagnosticStatus.NOT_CHECKED
        }

        return DiagnosticHistoryRecord(
            id = id,
            timestamp = timestamp,
            category = parsedCategory,
            status = parsedStatus,
            shortResult = shortResult,
            durationMs = durationMs,
            technicalDetails = details
        )
    }
}
