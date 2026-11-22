package com.autopilot.android.domain.scheduling

import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.domain.model.RecurrenceRule
import com.autopilot.android.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleCalculator {

    /**
     * Calculates the next execution epoch millisecond for a given schedule,
     * starting strictly after [fromEpochMillis].
     * Returns null if a one-time schedule is already in the past, or if recurrence cannot be scheduled.
     */
    fun calculateNextExecution(
        schedule: AutomationSchedule,
        fromEpochMillis: Long = System.currentTimeMillis()
    ): Long? {
        val zone = try {
            ZoneId.of(schedule.timezoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

        return when (schedule.scheduleType) {
            ScheduleType.ONE_TIME -> {
                if (schedule.targetEpochMillis > fromEpochMillis) {
                    schedule.targetEpochMillis
                } else {
                    null
                }
            }

            ScheduleType.RECURRING -> {
                calculateRecurringNextRun(
                    rule = schedule.recurrenceRule,
                    targetEpochMillis = schedule.targetEpochMillis,
                    fromEpochMillis = fromEpochMillis,
                    zone = zone
                )
            }
        }
    }

    private fun calculateRecurringNextRun(
        rule: RecurrenceRule,
        targetEpochMillis: Long,
        fromEpochMillis: Long,
        zone: ZoneId
    ): Long? {
        val targetZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetEpochMillis), zone)
        val fromZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromEpochMillis), zone)

        val targetHour = targetZdt.hour
        val targetMinute = targetZdt.minute
        val targetSecond = targetZdt.second

        return when (rule) {
            RecurrenceRule.NONE -> {
                if (targetEpochMillis > fromEpochMillis) targetEpochMillis else null
            }

            RecurrenceRule.DAILY -> {
                var candidate = fromZdt.withHour(targetHour).withMinute(targetMinute).withSecond(targetSecond).withNano(0)
                if (!candidate.isAfter(fromZdt)) {
                    candidate = candidate.plusDays(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            RecurrenceRule.WEEKDAYS -> {
                var candidate = fromZdt.withHour(targetHour).withMinute(targetMinute).withSecond(targetSecond).withNano(0)
                if (!candidate.isAfter(fromZdt)) {
                    candidate = candidate.plusDays(1)
                }
                while (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
                    candidate = candidate.plusDays(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            RecurrenceRule.WEEKLY -> {
                val targetDayOfWeek = targetZdt.dayOfWeek
                var candidate = fromZdt.withHour(targetHour).withMinute(targetMinute).withSecond(targetSecond).withNano(0)

                // Advance days until day of week matches target
                while (candidate.dayOfWeek != targetDayOfWeek) {
                    candidate = candidate.plusDays(1)
                }
                // If candidate is not in the future relative to fromZdt, advance by 1 week
                if (!candidate.isAfter(fromZdt)) {
                    candidate = candidate.plusWeeks(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            RecurrenceRule.MONTHLY -> {
                val targetDayOfMonth = targetZdt.dayOfMonth
                val candidateYearMonth = java.time.YearMonth.from(fromZdt)
                val maxDayInMonth = candidateYearMonth.lengthOfMonth()
                var candidate = fromZdt.withDayOfMonth(targetDayOfMonth.coerceAtMost(maxDayInMonth))
                    .withHour(targetHour)
                    .withMinute(targetMinute)
                    .withSecond(targetSecond)
                    .withNano(0)

                if (!candidate.isAfter(fromZdt)) {
                    val nextYearMonth = candidateYearMonth.plusMonths(1)
                    val nextMaxDay = nextYearMonth.lengthOfMonth()
                    candidate = candidate.plusMonths(1)
                        .withDayOfMonth(targetDayOfMonth.coerceAtMost(nextMaxDay))
                }
                candidate.toInstant().toEpochMilli()
            }
        }
    }
}
