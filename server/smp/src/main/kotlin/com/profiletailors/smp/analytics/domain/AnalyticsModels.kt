package com.profiletailors.smp.analytics.domain

import com.profiletailors.common.domain.ValueObject
import java.time.LocalDate

@ValueObject
data class DateRange(val startDate: LocalDate, val endDate: LocalDate) {
    init {
        require(!startDate.isAfter(endDate)) { "startDate must not be after endDate" }
    }
}

@ValueObject
data class AnalyticsOverview(
    val period: DateRange,
    val totalImpressions: Long,
    val totalEngagements: Long,
    val engagementRate: Double,
    val totalClicks: Long,
    val newFollowers: Long,
    val clickThroughRate: Double,
    val dailyMetrics: List<DailyMetric>,
) {
    init {
        require(totalImpressions >= 0) { "totalImpressions must be non-negative" }
    }
}

@ValueObject
data class DailyMetric(val date: LocalDate, val impressions: Long, val engagements: Long, val clicks: Long) {
    init {
        require(impressions >= 0) { "impressions must be non-negative" }
    }
}

@ValueObject
data class PostAnalyticsSummary(
    val postId: String,
    val title: String?,
    val bodyText: String?,
    val provider: String,
    val publishedAt: String,
    val impressions: Long,
    val clicks: Long,
    val engagements: Long,
    val reactions: Long,
    val comments: Long,
    val shares: Long,
    val engagementRate: Double,
) {
    init {
        require(postId.isNotBlank()) { "postId must not be blank" }
    }
}

@ValueObject
data class PostAnalyticsList(val posts: List<PostAnalyticsSummary>, val total: Int, val page: Int, val size: Int) {
    init {
        require(page >= 0 && size >= 0) { "page and size must be non-negative" }
    }
}

@ValueObject
data class BestTimeSlot(val dayOfWeek: Int, val hour: Int, val score: Double) {
    init {
        require(dayOfWeek in MIN_DAY_OF_WEEK..MAX_DAY_OF_WEEK) {
            "dayOfWeek must be between $MIN_DAY_OF_WEEK and $MAX_DAY_OF_WEEK"
        }
        require(hour in MIN_HOUR..MAX_HOUR) {
            "hour must be between $MIN_HOUR and $MAX_HOUR"
        }
    }

    private companion object {
        const val MIN_DAY_OF_WEEK = 0
        const val MAX_DAY_OF_WEEK = 6
        const val MIN_HOUR = 0
        const val MAX_HOUR = 23
    }
}

@ValueObject
data class BestTimesRecommendation(val slots: List<BestTimeSlot>) {
    init {
        require(slots.all { it.score >= 0.0 }) { "slot scores must be non-negative" }
    }
}
