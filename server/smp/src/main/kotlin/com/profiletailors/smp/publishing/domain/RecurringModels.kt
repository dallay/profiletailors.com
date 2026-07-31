package com.profiletailors.smp.publishing.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Supported MVP recurrence frequencies. */
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }
enum class RecurringScheduleStatus { ACTIVE, PAUSED, CANCELLED }

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<Int> = emptySet(),
    val dayOfMonth: Int? = null,
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
) {
    init {
        require(interval > 0) { "Recurrence interval must be positive." }
        require(daysOfWeek.all { it in MIN_DAY_OF_WEEK..MAX_DAY_OF_WEEK }) {
            "daysOfWeek values must be between 0 and 6."
        }
        require(dayOfMonth == null || dayOfMonth in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH) {
            "dayOfMonth must be between 1 and 31."
        }
        require(maxOccurrences == null || maxOccurrences > 0) { "maxOccurrences must be positive." }
        if (frequency == RecurrenceFrequency.WEEKLY) {
            require(daysOfWeek.isNotEmpty()) { "Weekly recurrence requires at least one day." }
        } else {
            require(daysOfWeek.isEmpty()) { "daysOfWeek is only valid for weekly recurrence." }
        }
        if (frequency == RecurrenceFrequency.MONTHLY) {
            require(dayOfMonth != null) { "Monthly recurrence requires dayOfMonth." }
        } else {
            require(dayOfMonth == null) { "dayOfMonth is only valid for monthly recurrence." }
        }
    }

    /** Returns occurrences in the inclusive range, never exceeding maxOccurrences. */
    fun occurrences(start: ZonedDateTime, until: LocalDate): List<ZonedDateTime> = when (frequency) {
        RecurrenceFrequency.DAILY -> dailyOccurrences(start, until)
        RecurrenceFrequency.WEEKLY -> weeklyOccurrences(start, until)
        RecurrenceFrequency.MONTHLY -> monthlyOccurrences(start, until)
    }

    private fun dailyOccurrences(start: ZonedDateTime, until: LocalDate): List<ZonedDateTime> {
        val limit = maxOccurrences ?: Int.MAX_VALUE
        val result = mutableListOf<ZonedDateTime>()
        val effectiveEnd = listOfNotNull(endDate, until).minOrNull() ?: until
        var current = start
        while (!current.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
            result += current
            current = current.plusDays(interval.toLong())
        }
        return result
    }

    private fun weeklyOccurrences(start: ZonedDateTime, until: LocalDate): List<ZonedDateTime> {
        val limit = maxOccurrences ?: Int.MAX_VALUE
        val result = mutableListOf<ZonedDateTime>()
        val effectiveEnd = listOfNotNull(endDate, until).minOrNull() ?: until
        var week = start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        while (!week.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
            for (day in daysOfWeek.sorted()) {
                if (result.size < limit) {
                    val candidate = week.plusDays(day.toLong()).with(start.toLocalTime())
                    if (!candidate.isBefore(start) && !candidate.toLocalDate().isAfter(effectiveEnd)) {
                        result += candidate
                    }
                }
            }
            week = week.plusWeeks(interval.toLong())
        }
        return result
    }

    private fun monthlyOccurrences(start: ZonedDateTime, until: LocalDate): List<ZonedDateTime> {
        val limit = maxOccurrences ?: Int.MAX_VALUE
        val result = mutableListOf<ZonedDateTime>()
        val effectiveEnd = listOfNotNull(endDate, until).minOrNull() ?: until
        var month = start.withDayOfMonth(MIN_DAY_OF_MONTH)
        while (!month.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
            val day = dayOfMonth!!
            if (day <= month.toLocalDate().lengthOfMonth()) {
                val candidate = month.withDayOfMonth(day).with(start.toLocalTime())
                if (!candidate.isBefore(start) && !candidate.toLocalDate().isAfter(effectiveEnd)) {
                    result += candidate
                }
            }
            month = month.plusMonths(interval.toLong())
        }
        return result
    }

    private companion object {
        const val MIN_DAY_OF_WEEK = 0
        const val MAX_DAY_OF_WEEK = 6
        const val MIN_DAY_OF_MONTH = 1
        const val MAX_DAY_OF_MONTH = 31
    }
}

data class RecurringSchedule(
    val id: String,
    val workspaceId: String,
    val createdBy: String,
    val templatePostId: String,
    val recurrenceRule: RecurrenceRule,
    val timezone: String,
    val nextScheduledAt: Instant?,
    val status: RecurringScheduleStatus,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
