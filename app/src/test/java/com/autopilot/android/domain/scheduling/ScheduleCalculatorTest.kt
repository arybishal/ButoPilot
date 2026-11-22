package com.autopilot.android.domain.scheduling

import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.domain.model.RecurrenceRule
import com.autopilot.android.domain.model.ScheduleType
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun testOneTimeScheduleInFuture() {
        val target = 2_000_000_000_000L
        val from = 1_000_000_000_000L
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "Test One-Time",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = target,
            nextExecutionAt = target,
            timezoneId = "UTC"
        )

        val nextRun = ScheduleCalculator.calculateNextExecution(schedule, from)
        assertEquals(target, nextRun)
    }

    @Test
    fun testOneTimeScheduleInPastReturnsNull() {
        val target = 1_000_000_000_000L
        val from = 2_000_000_000_000L
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "Test One-Time",
            scheduleType = ScheduleType.ONE_TIME,
            targetEpochMillis = target,
            nextExecutionAt = target,
            timezoneId = "UTC"
        )

        val nextRun = ScheduleCalculator.calculateNextExecution(schedule, from)
        assertNull(nextRun)
    }

    @Test
    fun testDailyRecurrenceCalculation() {
        // Target: 2026-11-20 09:00:00 UTC
        val targetZdt = ZonedDateTime.of(2026, 11, 20, 9, 0, 0, 0, zone)
        val targetEpoch = targetZdt.toInstant().toEpochMilli()

        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "Daily 9AM",
            scheduleType = ScheduleType.RECURRING,
            recurrenceRule = RecurrenceRule.DAILY,
            targetEpochMillis = targetEpoch,
            nextExecutionAt = targetEpoch,
            timezoneId = "UTC"
        )

        // Case A: Query at 2026-11-22 08:00:00 (before 9AM) -> Should fire today at 9AM
        val fromBefore9 = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextA = ScheduleCalculator.calculateNextExecution(schedule, fromBefore9)
        assertNotNull(nextA)
        val nextAZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextA!!), zone)
        assertEquals(2026, nextAZdt.year)
        assertEquals(11, nextAZdt.monthValue)
        assertEquals(22, nextAZdt.dayOfMonth)
        assertEquals(9, nextAZdt.hour)
        assertEquals(0, nextAZdt.minute)

        // Case B: Query at 2026-11-22 10:00:00 (after 9AM) -> Should fire tomorrow (2026-11-23 09:00:00)
        val fromAfter9 = ZonedDateTime.of(2026, 11, 22, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextB = ScheduleCalculator.calculateNextExecution(schedule, fromAfter9)
        assertNotNull(nextB)
        val nextBZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextB!!), zone)
        assertEquals(2026, nextBZdt.year)
        assertEquals(11, nextBZdt.monthValue)
        assertEquals(23, nextBZdt.dayOfMonth)
        assertEquals(9, nextBZdt.hour)
    }

    @Test
    fun testWeekdaysRecurrenceSkipsWeekend() {
        // Target: 14:00 UTC
        val targetZdt = ZonedDateTime.of(2026, 11, 1, 14, 0, 0, 0, zone)
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "Weekday Check",
            scheduleType = ScheduleType.RECURRING,
            recurrenceRule = RecurrenceRule.WEEKDAYS,
            targetEpochMillis = targetZdt.toInstant().toEpochMilli(),
            nextExecutionAt = targetZdt.toInstant().toEpochMilli(),
            timezoneId = "UTC"
        )

        // Friday afternoon (2026-11-20 15:00 UTC) -> Next run must be Monday 2026-11-23 14:00
        val fridayAfternoon = ZonedDateTime.of(2026, 11, 20, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextFromFriday = ScheduleCalculator.calculateNextExecution(schedule, fridayAfternoon)
        assertNotNull(nextFromFriday)

        val nextZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextFromFriday!!), zone)
        assertEquals(DayOfWeek.MONDAY, nextZdt.dayOfWeek)
        assertEquals(23, nextZdt.dayOfMonth)
        assertEquals(14, nextZdt.hour)

        // Saturday (2026-11-21 10:00 UTC) -> Next run must also be Monday 2026-11-23 14:00
        val saturday = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextFromSaturday = ScheduleCalculator.calculateNextExecution(schedule, saturday)
        assertNotNull(nextFromSaturday)
        val saturdayZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextFromSaturday!!), zone)
        assertEquals(DayOfWeek.MONDAY, saturdayZdt.dayOfWeek)
        assertEquals(23, saturdayZdt.dayOfMonth)
    }

    @Test
    fun testWeeklyRecurrenceCalculation() {
        // Schedule on Wednesdays at 10:00 UTC
        // 2026-11-18 was a Wednesday
        val wednesdayZdt = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, zone)
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "Weekly Wednesday",
            scheduleType = ScheduleType.RECURRING,
            recurrenceRule = RecurrenceRule.WEEKLY,
            targetEpochMillis = wednesdayZdt.toInstant().toEpochMilli(),
            nextExecutionAt = wednesdayZdt.toInstant().toEpochMilli(),
            timezoneId = "UTC"
        )

        // Query on Thursday 2026-11-19 -> Next run must be Wednesday 2026-11-25 10:00
        val thursday = ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextRun = ScheduleCalculator.calculateNextExecution(schedule, thursday)
        assertNotNull(nextRun)

        val nextZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRun!!), zone)
        assertEquals(DayOfWeek.WEDNESDAY, nextZdt.dayOfWeek)
        assertEquals(25, nextZdt.dayOfMonth)
        assertEquals(10, nextZdt.hour)
    }

    @Test
    fun testMonthlyRecurrenceWithMonthEndBoundary() {
        // Target: 31st of the month at 12:00 UTC
        // Jan 31, 2026
        val jan31Zdt = ZonedDateTime.of(2026, 1, 31, 12, 0, 0, 0, zone)
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "End of Month Run",
            scheduleType = ScheduleType.RECURRING,
            recurrenceRule = RecurrenceRule.MONTHLY,
            targetEpochMillis = jan31Zdt.toInstant().toEpochMilli(),
            nextExecutionAt = jan31Zdt.toInstant().toEpochMilli(),
            timezoneId = "UTC"
        )

        // Query on Feb 1, 2026 -> February 2026 has 28 days -> Must clamp to Feb 28, 2026 12:00
        val feb1 = ZonedDateTime.of(2026, 2, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextRun = ScheduleCalculator.calculateNextExecution(schedule, feb1)
        assertNotNull(nextRun)

        val nextZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRun!!), zone)
        assertEquals(2, nextZdt.monthValue)
        assertEquals(28, nextZdt.dayOfMonth)
        assertEquals(12, nextZdt.hour)
    }

    @Test
    fun testTimezoneAndDaylightSavingTransition() {
        // New York (America/New_York) has DST transitions in March and November
        val nyZone = ZoneId.of("America/New_York")
        val targetZdt = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, nyZone)
        val schedule = AutomationSchedule(
            profileId = "p1",
            profileName = "Profile 1",
            scheduleName = "NY Daily",
            scheduleType = ScheduleType.RECURRING,
            recurrenceRule = RecurrenceRule.DAILY,
            targetEpochMillis = targetZdt.toInstant().toEpochMilli(),
            nextExecutionAt = targetZdt.toInstant().toEpochMilli(),
            timezoneId = "America/New_York"
        )

        // US DST begins on Sunday, March 8, 2026.
        // Query after 9 AM on March 7 -> Next run must be March 8 at 9:00 AM local EDT!
        val march7Afternoon = ZonedDateTime.of(2026, 3, 7, 15, 0, 0, 0, nyZone).toInstant().toEpochMilli()
        val nextRun = ScheduleCalculator.calculateNextExecution(schedule, march7Afternoon)
        assertNotNull(nextRun)

        val nextZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRun!!), nyZone)
        assertEquals(2026, nextZdt.year)
        assertEquals(3, nextZdt.monthValue)
        assertEquals(8, nextZdt.dayOfMonth)
        assertEquals(9, nextZdt.hour)
        assertEquals(0, nextZdt.minute)
    }
}
