package com.profiletailors.smp.publishing.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        require(daysOfWeek.all { it in 0..6 }) { "daysOfWeek values must be between 0 and 6." }
        require(dayOfMonth == null || dayOfMonth in 1..31) { "dayOfMonth must be between 1 and 31." }
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
    fun occurrences(start: ZonedDateTime, until: LocalDate): List<ZonedDateTime> {
        val limit = maxOccurrences ?: Int.MAX_VALUE
        val result = mutableListOf<ZonedDateTime>()
        val effectiveEnd = listOfNotNull(endDate, until).minOrNull() ?: until
        when (frequency) {
            RecurrenceFrequency.DAILY -> {
                var current = start
                while (!current.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
                    result += current
                    current = current.plusDays(interval.toLong())
                }
            }
            RecurrenceFrequency.WEEKLY -> {
                var week = start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                while (!week.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
                    daysOfWeek.sorted().forEach { day ->
                        if (result.size >= limit) return@forEach
                        val candidate = week.plusDays(day.toLong()).with(start.toLocalTime())
                        if (!candidate.isBefore(start) && !candidate.toLocalDate().isAfter(effectiveEnd)) result += candidate
                    }
                    week = week.plusWeeks(interval.toLong())
                }
            }
            RecurrenceFrequency.MONTHLY -> {
                var month = start.withDayOfMonth(1)
                while (!month.toLocalDate().isAfter(effectiveEnd) && result.size < limit) {
                    val day = dayOfMonth!!
                    if (day <= month.toLocalDate().lengthOfMonth()) {
                        val candidate = month.withDayOfMonth(day).with(start.toLocalTime())
                        if (!candidate.isBefore(start) && !candidate.toLocalDate().isAfter(effectiveEnd)) result += candidate
                    }
                    month = month.plusMonths(interval.toLong())
                }
            }
        }
        return result
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
