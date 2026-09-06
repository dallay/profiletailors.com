package com.profiletailors.smp.analytics.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class AnalyticsModelsTest {
    private val date = LocalDate.parse("2026-09-06")
    private val range = DateRange(date, date)

    @Test
    fun `date range accepts a single day`() {
        range.startDate shouldBe range.endDate
    }

    @Test
    fun `date range rejects a start after its end`() {
        val exception = shouldThrow<IllegalArgumentException> {
            DateRange(date.plusDays(1), date)
        }

        exception.message shouldBe "startDate must not be after endDate"
    }

    @Test
    fun `analytics overview accepts zero impressions`() {
        overview(totalImpressions = 0).totalImpressions shouldBe 0
    }

    @Test
    fun `analytics overview rejects negative impressions`() {
        shouldThrow<IllegalArgumentException> {
            overview(totalImpressions = -1)
        }
    }

    @Test
    fun `daily metric rejects negative impressions`() {
        shouldThrow<IllegalArgumentException> {
            DailyMetric(date, impressions = -1, engagements = 0, clicks = 0)
        }
    }

    @Test
    fun `post analytics summary rejects a blank post id`() {
        shouldThrow<IllegalArgumentException> {
            postSummary(postId = "  ")
        }
    }

    @ParameterizedTest
    @CsvSource("-1, 0", "0, -1")
    fun `post analytics list rejects negative pagination`(page: Int, size: Int) {
        shouldThrow<IllegalArgumentException> {
            PostAnalyticsList(emptyList(), total = 0, page = page, size = size)
        }
    }

    @Test
    fun `post analytics list accepts zero pagination boundaries`() {
        val list = PostAnalyticsList(emptyList(), total = 0, page = 0, size = 0)

        list.page shouldBe 0
        list.size shouldBe 0
    }

    @ParameterizedTest
    @CsvSource("0, 0", "8, 23", "1, -1", "7, 24")
    fun `best time slot rejects values outside calendar boundaries`(dayOfWeek: Int, hour: Int) {
        shouldThrow<IllegalArgumentException> {
            BestTimeSlot(dayOfWeek = dayOfWeek, hour = hour, score = 0.0)
        }
    }

    @ParameterizedTest
    @CsvSource("1, 0", "7, 23")
    fun `best time slot accepts inclusive calendar boundaries`(dayOfWeek: Int, hour: Int) {
        val slot = BestTimeSlot(dayOfWeek = dayOfWeek, hour = hour, score = 0.0)

        slot.dayOfWeek shouldBe dayOfWeek
        slot.hour shouldBe hour
    }

    @Test
    fun `best times recommendation rejects a negative score`() {
        val negativeSlot = BestTimeSlot(dayOfWeek = 1, hour = 0, score = -0.01)

        shouldThrow<IllegalArgumentException> {
            BestTimesRecommendation(listOf(negativeSlot))
        }
    }

    @Test
    fun `best times recommendation accepts a zero score`() {
        val zeroScoreSlot = BestTimeSlot(dayOfWeek = 1, hour = 0, score = 0.0)

        BestTimesRecommendation(listOf(zeroScoreSlot)).slots shouldBe listOf(zeroScoreSlot)
    }

    private fun overview(totalImpressions: Long) = AnalyticsOverview(
        period = range,
        totalImpressions = totalImpressions,
        totalEngagements = 0,
        engagementRate = 0.0,
        totalClicks = 0,
        newFollowers = 0,
        clickThroughRate = 0.0,
        dailyMetrics = emptyList(),
    )

    private fun postSummary(postId: String) = PostAnalyticsSummary(
        postId = postId,
        title = null,
        bodyText = "Body",
        provider = "linkedin",
        publishedAt = "2026-09-06T00:00:00Z",
        impressions = 0,
        clicks = 0,
        engagements = 0,
        reactions = 0,
        comments = 0,
        shares = 0,
        engagementRate = 0.0,
    )
}
