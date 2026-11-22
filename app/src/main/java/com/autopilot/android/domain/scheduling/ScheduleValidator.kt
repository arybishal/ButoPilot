package com.autopilot.android.domain.scheduling

import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.domain.model.ScheduleType
import java.time.ZoneId

object ScheduleValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validate(
        schedule: AutomationSchedule,
        profileExists: Boolean,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): ValidationResult {
        if (schedule.scheduleName.isBlank()) {
            return ValidationResult.Invalid("Schedule name cannot be empty.")
        }
        if (schedule.scheduleName.length > 100) {
            return ValidationResult.Invalid("Schedule name must be 100 characters or fewer.")
        }
        if (schedule.profileId.isBlank() || !profileExists) {
            return ValidationResult.Invalid("Selected automation profile does not exist.")
        }
        try {
            ZoneId.of(schedule.timezoneId)
        } catch (e: Exception) {
            return ValidationResult.Invalid("Invalid timezone ID: ${schedule.timezoneId}")
        }
        if (schedule.scheduleType == ScheduleType.ONE_TIME) {
            // Allow 60 seconds of grace window for execution setup
            if (schedule.targetEpochMillis <= nowEpochMillis - 60_000L) {
                return ValidationResult.Invalid("One-time schedule date and time must be in the future.")
            }
        }
        if (schedule.maxDurationMinutes != null) {
            if (schedule.maxDurationMinutes <= 0 || schedule.maxDurationMinutes > 1440) {
                return ValidationResult.Invalid("Maximum duration must be between 1 and 1440 minutes (24 hours).")
            }
        }

        return ValidationResult.Valid
    }
}
