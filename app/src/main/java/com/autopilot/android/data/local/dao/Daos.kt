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
}

@Dao
interface ExecutionHistoryDao {
    @Query("SELECT * FROM execution_history ORDER BY startedAt DESC")
    fun getAllFlow(): Flow<List<ExecutionHistoryEntity>>

    @Query("SELECT * FROM execution_history WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun getForProfile(profileId: String): Flow<List<ExecutionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExecutionHistoryEntity)

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
