package com.profiletailors.smp.analytics.domain

import java.time.LocalDate

data class DateRange(val startDate: LocalDate, val endDate: LocalDate)

data class AnalyticsOverview(
    val period: DateRange,
    val totalImpressions: Long,
    val totalEngagements: Long,
    val engagementRate: Double,
    val totalClicks: Long,
    val newFollowers: Long,
    val clickThroughRate: Double,
    val dailyMetrics: List<DailyMetric>,
)

data class DailyMetric(val date: LocalDate, val impressions: Long, val engagements: Long, val clicks: Long)

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
)

data class PostAnalyticsList(val posts: List<PostAnalyticsSummary>, val total: Int, val page: Int, val size: Int)

data class BestTimeSlot(val dayOfWeek: Int, val hour: Int, val score: Double)

data class BestTimesRecommendation(val slots: List<BestTimeSlot>)
