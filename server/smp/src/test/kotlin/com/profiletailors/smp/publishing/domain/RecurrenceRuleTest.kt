package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class RecurrenceRuleTest {
    private val start = ZonedDateTime.of(2026, 8, 3, 9, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `daily rule keeps time and respects end date`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 1, endDate = start.toLocalDate().plusDays(2))
        rule.occurrences(start, start.toLocalDate().plusDays(30)).size shouldBe 3
        rule.occurrences(start, start.toLocalDate().plusDays(30)).first().hour shouldBe 9
    }

    @Test
    fun `weekly rule emits selected weekdays in chronological order`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(0, 2, 4))
        val occurrences = rule.occurrences(start, start.toLocalDate().plusDays(6))
        occurrences.map { it.dayOfWeek.value } shouldBe listOf(1, 3, 5)
    }

    @Test
    fun `monthly rule skips invalid day instead of clamping`() {
        val monthlyStart = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, ZoneOffset.UTC)
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, dayOfMonth = 31)
        val occurrences = rule.occurrences(monthlyStart, monthlyStart.toLocalDate().plusMonths(3))
        occurrences.map { it.monthValue } shouldBe listOf(1, 3)
    }

    @Test
    fun `rejects non-positive interval`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, interval = 0)
        }
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, interval = -1)
        }
    }

    @Test
    fun `rejects daysOfWeek on non-weekly rules`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, daysOfWeek = setOf(0))
        }
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.MONTHLY, daysOfWeek = setOf(0), dayOfMonth = 15)
        }
    }

    @Test
    fun `rejects weekly rule without daysOfWeek`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = emptySet())
        }
    }

    @Test
    fun `rejects missing dayOfMonth for monthly rule`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.MONTHLY, dayOfMonth = null)
        }
    }

    @Test
    fun `rejects dayOfMonth on non-monthly rules`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, dayOfMonth = 15)
        }
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(0), dayOfMonth = 15)
        }
    }

    @Test
    fun `rejects non-positive maxOccurrences`() {
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, maxOccurrences = 0)
        }
        shouldThrow<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFrequency.DAILY, maxOccurrences = -1)
        }
    }

    @Test
    fun `respects maxOccurrences limit`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 1, maxOccurrences = 5)
        val occurrences = rule.occurrences(start, start.toLocalDate().plusDays(30))
        occurrences.size shouldBe 5
    }
}
