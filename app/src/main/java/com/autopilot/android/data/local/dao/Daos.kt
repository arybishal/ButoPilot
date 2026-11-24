package com.autopilot.android.data.local.dao

import androidx.room.*
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM automation_profiles ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM automation_profiles ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM automation_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT * FROM automation_profiles WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM automation_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automation_profiles SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM automation_profiles")
    suspend fun clearAll()
}

@Dao
interface ExecutionHistoryDao {
    @Query("SELECT * FROM execution_history ORDER BY startedAt DESC")
    fun getAllFlow(): Flow<List<ExecutionHistoryEntity>>

    @Query("SELECT * FROM execution_history ORDER BY startedAt DESC")
    suspend fun getAll(): List<ExecutionHistoryEntity>

    @Query("SELECT * FROM execution_history WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun getForProfile(profileId: String): Flow<List<ExecutionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExecutionHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExecutionHistoryEntity>)

    @Query("DELETE FROM execution_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM execution_history")
    suspend fun clearAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM automation_schedules ORDER BY nextExecutionAt ASC, updatedAt DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.ScheduleEntity>>

    @Query("SELECT * FROM automation_schedules ORDER BY nextExecutionAt ASC, updatedAt DESC")
    suspend fun getAll(): List<com.autopilot.android.data.local.entity.ScheduleEntity>

    @Query("SELECT * FROM automation_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.autopilot.android.data.local.entity.ScheduleEntity?

    @Query("SELECT * FROM automation_schedules WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<com.autopilot.android.data.local.entity.ScheduleEntity?>

    @Query("SELECT * FROM automation_schedules WHERE enabled = 1 AND nextExecutionAt IS NOT NULL AND nextExecutionAt <= :untilEpochMillis ORDER BY nextExecutionAt ASC")
    suspend fun getPendingExecution(untilEpochMillis: Long): List<com.autopilot.android.data.local.entity.ScheduleEntity>

    @Query("SELECT * FROM automation_schedules WHERE enabled = 1 AND nextExecutionAt IS NOT NULL ORDER BY nextExecutionAt ASC")
    suspend fun getActiveScheduled(): List<com.autopilot.android.data.local.entity.ScheduleEntity>

    @Query("SELECT * FROM automation_schedules WHERE status = 'RUNNING' OR status = 'STARTING'")
    suspend fun getRunningOrStarting(): List<com.autopilot.android.data.local.entity.ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: com.autopilot.android.data.local.entity.ScheduleEntity)

    @Delete
    suspend fun delete(schedule: com.autopilot.android.data.local.entity.ScheduleEntity)

    @Query("DELETE FROM automation_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automation_schedules SET status = :status, nextExecutionAt = :nextExecutionAt, lastExecutionAt = :lastExecutionAt, lastError = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateExecutionState(
        id: String,
        status: String,
        nextExecutionAt: Long?,
        lastExecutionAt: Long?,
        error: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE automation_schedules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM automation_schedules")
    suspend fun clearAll()
}

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxy_configurations ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.ProxyEntity>>

    @Query("SELECT * FROM proxy_configurations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<com.autopilot.android.data.local.entity.ProxyEntity>

    @Query("SELECT * FROM proxy_configurations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.autopilot.android.data.local.entity.ProxyEntity?

    @Query("SELECT * FROM proxy_configurations WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<com.autopilot.android.data.local.entity.ProxyEntity?>

    @Query("SELECT * FROM proxy_configurations WHERE host = :host AND port = :port LIMIT 1")
    suspend fun getByHostAndPort(host: String, port: Int): com.autopilot.android.data.local.entity.ProxyEntity?

    @Query("SELECT * FROM proxy_configurations WHERE enabled = 1")
    suspend fun getEnabled(): List<com.autopilot.android.data.local.entity.ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(proxy: com.autopilot.android.data.local.entity.ProxyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(proxies: List<com.autopilot.android.data.local.entity.ProxyEntity>)

    @Delete
    suspend fun delete(proxy: com.autopilot.android.data.local.entity.ProxyEntity)

    @Query("DELETE FROM proxy_configurations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM proxy_configurations WHERE lastHealthStatus IN ('UNHEALTHY', 'TIMEOUT', 'AUTH_FAILURE')")
    suspend fun deleteUnhealthy(): Int

    @Query("UPDATE proxy_configurations SET lastHealthStatus = :status, lastLatencyMs = :latencyMs, lastTestedAt = :testedAt, lastErrorMessage = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateHealthStatus(
        id: String,
        status: String,
        latencyMs: Long?,
        testedAt: Long,
        error: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE proxy_configurations SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ProxySourceDao {
    @Query("SELECT * FROM proxy_sources ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.ProxySourceEntity>>

    @Query("SELECT * FROM proxy_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.autopilot.android.data.local.entity.ProxySourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(source: com.autopilot.android.data.local.entity.ProxySourceEntity)

    @Query("DELETE FROM proxy_sources WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostic_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.DiagnosticRecordEntity>>

    @Query("SELECT * FROM diagnostic_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<com.autopilot.android.data.local.entity.DiagnosticRecordEntity>

    @Query("SELECT * FROM diagnostic_records WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<com.autopilot.android.data.local.entity.DiagnosticRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: com.autopilot.android.data.local.entity.DiagnosticRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<com.autopilot.android.data.local.entity.DiagnosticRecordEntity>)

    @Query("DELETE FROM diagnostic_records WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM diagnostic_records")
    suspend fun clearAll()
}

@Dao
interface ExecutionEventDao {
    @Query("SELECT * FROM execution_events WHERE executionId = :executionId ORDER BY timestamp ASC")
    fun getEventsForExecutionFlow(executionId: String): Flow<List<com.autopilot.android.data.local.entity.ExecutionEventEntity>>

    @Query("SELECT * FROM execution_events WHERE executionId = :executionId ORDER BY timestamp ASC")
    suspend fun getEventsForExecution(executionId: String): List<com.autopilot.android.data.local.entity.ExecutionEventEntity>

    @Query("SELECT * FROM execution_events WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsForProfile(profileId: String, limit: Int = 100): List<com.autopilot.android.data.local.entity.ExecutionEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: com.autopilot.android.data.local.entity.ExecutionEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<com.autopilot.android.data.local.entity.ExecutionEventEntity>)

    @Query("DELETE FROM execution_events WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM execution_events WHERE executionId = :executionId")
    suspend fun deleteForExecution(executionId: String)

    @Query("DELETE FROM execution_events")
    suspend fun clearAll()
}

@Dao
interface TrafficDao {
    @Query("SELECT * FROM traffic_runs ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.TrafficRunEntity>>

    @Query("SELECT * FROM traffic_runs WHERE id = :id")
    suspend fun getById(id: String): com.autopilot.android.data.local.entity.TrafficRunEntity?

    @Query("SELECT * FROM traffic_runs WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED', 'INTERRUPTED') ORDER BY createdAt ASC")
    suspend fun getActive(): List<com.autopilot.android.data.local.entity.TrafficRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: com.autopilot.android.data.local.entity.TrafficRunEntity)

    @Update
    suspend fun update(run: com.autopilot.android.data.local.entity.TrafficRunEntity)

    @Query("DELETE FROM traffic_runs WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<com.autopilot.android.data.local.entity.BackupRecordEntity>>

    @Query("SELECT * FROM backup_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<com.autopilot.android.data.local.entity.BackupRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: com.autopilot.android.data.local.entity.BackupRecordEntity)

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM backup_records")
    suspend fun clearAll()
}

