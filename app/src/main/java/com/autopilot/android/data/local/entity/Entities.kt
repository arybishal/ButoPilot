package com.autopilot.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus

@Entity(tableName = "automation_profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val urlsJson: String, // serialized List<String>
    val tabCount: Int,
    val executionMode: String,
    val refreshIntervalSec: Int,
    val scrollIntervalSec: Int,
    val randomizeIntervals: Boolean,
    val minRefreshIntervalSec: Int,
    val maxRefreshIntervalSec: Int,
    val minScrollIntervalSec: Int,
    val maxScrollIntervalSec: Int,
    val customScript: String?,
    val allowedDomainsJson: String,
    val userAgent: String?,
    val clearCookiesOnStart: Boolean,
    val clearCacheOnStart: Boolean,
    val timeoutSeconds: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String
)

@Entity(tableName = "execution_history")
data class ExecutionHistoryEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val profileName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationSeconds: Long,
    val totalActions: Int,
    val status: String,
    val errorMessage: String?
)
