package com.autopilot.android.domain.scheduling

import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.domain.model.ScheduleType
import org.junit.Assert.*
import org.junit.Test

class ScheduleValidatorTest {

    @Test
    fun testValidSchedulePasses() {
        val schedule = AutomationSchedule(
            profileId = "profile-123",
            profileName = "Profile 1",
            scheduleName = "Valid Schedule",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() + 300_000L,
            nextExecutionAt = System.currentTimeMillis() + 300_000L,
            maxDurationMinutes = 30
        )

        val result = ScheduleValidator.validate(schedule, profileExists = true)
        assertTrue(result is ScheduleValidator.ValidationResult.Valid)
    }

    @Test
    fun testBlankNameFails() {
        val schedule = AutomationSchedule(
            profileId = "profile-123",
            profileName = "Profile 1",
            scheduleName = "   ",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() + 300_000L,
            nextExecutionAt = System.currentTimeMillis() + 300_000L
        )

        val result = ScheduleValidator.validate(schedule, profileExists = true)
        assertTrue(result is ScheduleValidator.ValidationResult.Invalid)
        assertEquals("Schedule name cannot be empty.", (result as ScheduleValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testMissingProfileFails() {
        val schedule = AutomationSchedule(
            profileId = "non-existent-profile",
            profileName = "Non Existent",
            scheduleName = "Check Profile",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() + 300_000L,
            nextExecutionAt = System.currentTimeMillis() + 300_000L
        )

        val result = ScheduleValidator.validate(schedule, profileExists = false)
        assertTrue(result is ScheduleValidator.ValidationResult.Invalid)
        assertEquals("Selected automation profile does not exist.", (result as ScheduleValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testPastOneTimeScheduleFails() {
        val schedule = AutomationSchedule(
            profileId = "profile-123",
            profileName = "Profile 1",
            scheduleName = "Past Run",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() - 120_000L, // 2 mins ago
            nextExecutionAt = null
        )

        val result = ScheduleValidator.validate(schedule, profileExists = true)
        assertTrue(result is ScheduleValidator.ValidationResult.Invalid)
        assertEquals("One-time schedule date and time must be in the future.", (result as ScheduleValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testInvalidMaxDurationFails() {
        val schedule = AutomationSchedule(
            profileId = "profile-123",
            profileName = "Profile 1",
            scheduleName = "Duration Check",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() + 300_000L,
            nextExecutionAt = System.currentTimeMillis() + 300_000L,
            maxDurationMinutes = 2000 // exceeds 1440 min
        )

        val result = ScheduleValidator.validate(schedule, profileExists = true)
        assertTrue(result is ScheduleValidator.ValidationResult.Invalid)
        assertEquals("Maximum duration must be between 1 and 1440 minutes (24 hours).", (result as ScheduleValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testInvalidTimezoneFails() {
        val schedule = AutomationSchedule(
            profileId = "profile-123",
            profileName = "Profile 1",
            scheduleName = "Bad TZ",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = System.currentTimeMillis() + 300_000L,
            nextExecutionAt = System.currentTimeMillis() + 300_000L,
            timezoneId = "Invalid/NonExistentZone"
        )

        val result = ScheduleValidator.validate(schedule, profileExists = true)
        assertTrue(result is ScheduleValidator.ValidationResult.Invalid)
        assertTrue((result as ScheduleValidator.ValidationResult.Invalid).reason.contains("Invalid timezone ID"))
    }
}
