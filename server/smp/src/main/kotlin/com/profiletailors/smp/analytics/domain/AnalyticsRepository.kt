package com.profiletailors.smp.analytics.domain

interface AnalyticsRepository {
    suspend fun getOverview(workspaceId: String, range: DateRange): AnalyticsOverview
    suspend fun getPostAnalytics(workspaceId: String, range: DateRange, page: Int, size: Int): PostAnalyticsList
    suspend fun getBestTimes(workspaceId: String): BestTimesRecommendation
    suspend fun exportPostAnalytics(workspaceId: String, range: DateRange): List<PostAnalyticsSummary>
}
