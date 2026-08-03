package com.profiletailors.smp.analytics.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.analytics.domain.AnalyticsOverview
import com.profiletailors.smp.analytics.domain.AnalyticsRepository
import com.profiletailors.smp.analytics.domain.BestTimesRecommendation
import com.profiletailors.smp.analytics.domain.DateRange
import com.profiletailors.smp.analytics.domain.PostAnalyticsList
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

@Service
internal class GetAnalyticsOverviewHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val analyticsRepository: AnalyticsRepository,
) : QueryHandler<GetAnalyticsOverviewQuery, AnalyticsOverview> {
    override suspend fun handle(query: GetAnalyticsOverviewQuery): AnalyticsOverview {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        return analyticsRepository.getOverview(workspaceId, DateRange(query.startDate, query.endDate))
    }
}

@Service
internal class GetPostAnalyticsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val analyticsRepository: AnalyticsRepository,
) : QueryHandler<GetPostAnalyticsQuery, PostAnalyticsList> {
    override suspend fun handle(query: GetPostAnalyticsQuery): PostAnalyticsList {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        return analyticsRepository.getPostAnalytics(
            workspaceId = workspaceId,
            range = DateRange(query.startDate, query.endDate),
            page = query.page,
            size = query.size,
        )
    }
}

@Service
internal class GetBestTimesHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val analyticsRepository: AnalyticsRepository,
) : QueryHandler<GetBestTimesQuery, BestTimesRecommendation> {
    override suspend fun handle(query: GetBestTimesQuery): BestTimesRecommendation {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        return analyticsRepository.getBestTimes(workspaceId)
    }
}

@Service
internal class ExportAnalyticsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val analyticsRepository: AnalyticsRepository,
) : CommandWithResultHandler<ExportAnalyticsCommand, ExportAnalyticsResult> {
    override suspend fun handle(command: ExportAnalyticsCommand): ExportAnalyticsResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val posts = analyticsRepository.exportPostAnalytics(
            workspaceId = workspaceId,
            range = DateRange(command.startDate, command.endDate),
        )
        val csv = buildString {
            appendLine("date,platform,title,impressions,clicks,engagements,engagement_rate")
            posts.forEach { post ->
                appendLine(
                    """${post.publishedAt},${post.provider},"${post.title?.replace("\"", "\"\"") ?: ""}",""" +
                        "${post.impressions},${post.clicks},${post.engagements},${post.engagementRate}",
                )
            }
        }
        return ExportAnalyticsResult(csvContent = csv)
    }
}
