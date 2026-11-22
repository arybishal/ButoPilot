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
    val status: String,
    val proxyId: String? = null
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

@Entity(
    tableName = "automation_schedules",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["profileId"]),
        androidx.room.Index(value = ["enabled"]),
        androidx.room.Index(value = ["nextExecutionAt"]),
        androidx.room.Index(value = ["status"])
    ]
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val profileName: String,
    val scheduleName: String,
    val scheduleType: String,
    val recurrenceRule: String,
    val targetEpochMillis: Long,
    val nextExecutionAt: Long?,
    val lastExecutionAt: Long?,
    val timezoneId: String,
    val enabled: Boolean,
    val maxDurationMinutes: Int?,
    val status: String,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "proxy_configurations",
    indices = [
        androidx.room.Index(value = ["host", "port"]),
        androidx.room.Index(value = ["enabled"]),
        androidx.room.Index(value = ["lastHealthStatus"])
    ]
)
data class ProxyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val encryptedUsername: String?,
    val encryptedPassword: String?,
    val usernameIv: String?,
    val passwordIv: String?,
    val region: String?,
    val notes: String,
    val sourceId: String?,
    val enabled: Boolean,
    val lastHealthStatus: String,
    val lastLatencyMs: Long?,
    val lastTestedAt: Long?,
    val lastErrorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "proxy_sources")
data class ProxySourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceType: String,
    val urlOrPath: String?,
    val lastRefreshedAt: Long?,
    val proxyCount: Int,
    val createdAt: Long
)

