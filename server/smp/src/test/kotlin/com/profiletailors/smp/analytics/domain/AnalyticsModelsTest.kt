package com.profiletailors.smp.analytics.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class AnalyticsModelsTest {

    @Test
    fun `DateRange accepts valid start and end dates`() {
        assertDoesNotThrow {
            DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        }
    }

    @Test
    fun `DateRange accepts same start and end date`() {
        assertDoesNotThrow {
            DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15))
        }
    }

    @Test
    fun `DateRange rejects when startDate is after endDate`() {
        assertThrows<IllegalArgumentException> {
            DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1))
        }
    }

    @Test
    fun `AnalyticsOverview accepts non-negative impressions`() {
        assertDoesNotThrow {
            AnalyticsOverview(
                period = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                totalImpressions = 0L,
                totalEngagements = 0L,
                engagementRate = 0.0,
                totalClicks = 0L,
                newFollowers = 0L,
                clickThroughRate = 0.0,
                dailyMetrics = emptyList(),
            )
        }
    }

    @Test
    fun `AnalyticsOverview rejects negative impressions`() {
        assertThrows<IllegalArgumentException> {
            AnalyticsOverview(
                period = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                totalImpressions = -1L,
                totalEngagements = 0L,
                engagementRate = 0.0,
                totalClicks = 0L,
                newFollowers = 0L,
                clickThroughRate = 0.0,
                dailyMetrics = emptyList(),
            )
        }
    }

    @Test
    fun `DailyMetric accepts non-negative impressions`() {
        assertDoesNotThrow {
            DailyMetric(
                date = LocalDate.of(2026, 1, 1),
                impressions = 0L,
                engagements = 0L,
                clicks = 0L,
            )
        }
    }

    @Test
    fun `DailyMetric rejects negative impressions`() {
        assertThrows<IllegalArgumentException> {
            DailyMetric(
                date = LocalDate.of(2026, 1, 1),
                impressions = -1L,
                engagements = 0L,
                clicks = 0L,
            )
        }
    }

    @Test
    fun `PostAnalyticsSummary accepts non-blank postId`() {
        assertDoesNotThrow {
            PostAnalyticsSummary(
                postId = "post-1",
                title = null,
                bodyText = null,
                provider = "LINKEDIN",
                publishedAt = "2026-01-01T10:00:00Z",
                impressions = 0L,
                clicks = 0L,
                engagements = 0L,
                reactions = 0L,
                comments = 0L,
                shares = 0L,
                engagementRate = 0.0,
            )
        }
    }

    @Test
    fun `PostAnalyticsSummary rejects blank postId`() {
        assertThrows<IllegalArgumentException> {
            PostAnalyticsSummary(
                postId = "   ",
                title = null,
                bodyText = null,
                provider = "LINKEDIN",
                publishedAt = "2026-01-01T10:00:00Z",
                impressions = 0L,
                clicks = 0L,
                engagements = 0L,
                reactions = 0L,
                comments = 0L,
                shares = 0L,
                engagementRate = 0.0,
            )
        }
    }

    @Test
    fun `PostAnalyticsList accepts non-negative page and size`() {
        assertDoesNotThrow {
            PostAnalyticsList(
                posts = emptyList(),
                total = 0,
                page = 0,
                size = 10,
            )
        }
    }

    @Test
    fun `PostAnalyticsList rejects negative page`() {
        assertThrows<IllegalArgumentException> {
            PostAnalyticsList(
                posts = emptyList(),
                total = 0,
                page = -1,
                size = 10,
            )
        }
    }

    @Test
    fun `PostAnalyticsList rejects negative size`() {
        assertThrows<IllegalArgumentException> {
            PostAnalyticsList(
                posts = emptyList(),
                total = 0,
                page = 0,
                size = -1,
            )
        }
    }

    @Test
    fun `BestTimeSlot accepts dayOfWeek 0 through 6`() {
        for (day in 0..6) {
            assertDoesNotThrow {
                BestTimeSlot(dayOfWeek = day, hour = 12, score = 1.0)
            }
        }
    }

    @Test
    fun `BestTimeSlot rejects dayOfWeek below 0`() {
        assertThrows<IllegalArgumentException> {
            BestTimeSlot(dayOfWeek = -1, hour = 12, score = 1.0)
        }
    }

    @Test
    fun `BestTimeSlot rejects dayOfWeek above 6`() {
        assertThrows<IllegalArgumentException> {
            BestTimeSlot(dayOfWeek = 7, hour = 12, score = 1.0)
        }
    }

    @Test
    fun `BestTimeSlot accepts hour 0 through 23`() {
        for (hour in listOf(0, 12, 23)) {
            assertDoesNotThrow {
                BestTimeSlot(dayOfWeek = 1, hour = hour, score = 1.0)
            }
        }
    }

    @Test
    fun `BestTimeSlot rejects hour below 0`() {
        assertThrows<IllegalArgumentException> {
            BestTimeSlot(dayOfWeek = 1, hour = -1, score = 1.0)
        }
    }

    @Test
    fun `BestTimeSlot rejects hour above 23`() {
        assertThrows<IllegalArgumentException> {
            BestTimeSlot(dayOfWeek = 1, hour = 24, score = 1.0)
        }
    }

    @Test
    fun `BestTimesRecommendation accepts valid slots`() {
        assertDoesNotThrow {
            BestTimesRecommendation(
                slots = listOf(
                    BestTimeSlot(dayOfWeek = 1, hour = 9, score = 0.5),
                    BestTimeSlot(dayOfWeek = 2, hour = 10, score = 0.3),
                ),
            )
        }
    }

    @Test
    fun `BestTimesRecommendation rejects slot with negative score`() {
        assertThrows<IllegalArgumentException> {
            BestTimesRecommendation(
                slots = listOf(
                    BestTimeSlot(dayOfWeek = 1, hour = 9, score = -0.1),
                ),
            )
        }
    }
}
