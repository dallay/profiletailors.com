package com.profiletailors.smp.publishing.domain

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceRuleTest {
    private val start = ZonedDateTime.of(2026, 8, 3, 9, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `daily rule keeps time and respects end date`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 1, endDate = start.toLocalDate().plusDays(2))
        assertEquals(3, rule.occurrences(start, start.toLocalDate().plusDays(30)).size)
        assertEquals(9, rule.occurrences(start, start.toLocalDate().plusDays(30)).first().hour)
    }

    @Test
    fun `weekly rule emits selected weekdays in chronological order`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(0, 2, 4))
        val occurrences = rule.occurrences(start, start.toLocalDate().plusDays(6))
        assertEquals(listOf(1, 3, 5), occurrences.map { it.dayOfWeek.value })
    }

    @Test
    fun `monthly rule skips invalid day instead of clamping`() {
        val monthlyStart = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, ZoneOffset.UTC)
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, dayOfMonth = 31)
        val occurrences = rule.occurrences(monthlyStart, monthlyStart.toLocalDate().plusMonths(3))
        assertEquals(listOf(1, 3), occurrences.map { it.monthValue })
    }
}
