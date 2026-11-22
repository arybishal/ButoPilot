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
