package com.autopilot.android.domain.model

import java.time.ZoneId
import java.util.UUID

enum class ScheduleType(val displayName: String) {
    ONE_TIME("One-Time"),
    RECURRING("Recurring")
}

enum class RecurrenceRule(val displayName: String, val description: String) {
    NONE("None", "Runs once at specified date and time"),
    DAILY("Daily", "Runs every day at the scheduled time"),
    WEEKDAYS("Weekdays (Mon-Fri)", "Runs Monday through Friday"),
    WEEKLY("Weekly", "Runs once a week on the scheduled day"),
    MONTHLY("Monthly", "Runs once a month on the scheduled date")
}

enum class ScheduleStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    STARTING("Starting"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
    FAILED("Failed"),
    MISSED("Missed")
}

data class AutomationSchedule(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val profileName: String,
    val scheduleName: String,
    val scheduleType: ScheduleType = ScheduleType.ONE_TIME,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val targetEpochMillis: Long,
    val nextExecutionAt: Long?,
    val lastExecutionAt: Long? = null,
    val timezoneId: String = ZoneId.systemDefault().id,
    val enabled: Boolean = true,
    val maxDurationMinutes: Int? = null,
    val status: ScheduleStatus = ScheduleStatus.SCHEDULED,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
