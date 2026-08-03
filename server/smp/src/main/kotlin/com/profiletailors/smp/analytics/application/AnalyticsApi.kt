package com.profiletailors.smp.analytics.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.analytics.domain.AnalyticsOverview
import com.profiletailors.smp.analytics.domain.BestTimesRecommendation
import com.profiletailors.smp.analytics.domain.PostAnalyticsList
import java.time.LocalDate

data class GetAnalyticsOverviewQuery(val startDate: LocalDate, val endDate: LocalDate) : Query<AnalyticsOverview>

data class GetPostAnalyticsQuery(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val page: Int = 0,
    val size: Int = 20,
) : Query<PostAnalyticsList>

object GetBestTimesQuery : Query<BestTimesRecommendation>

data class ExportAnalyticsCommand(val startDate: LocalDate, val endDate: LocalDate) :
    CommandWithResult<ExportAnalyticsResult>

data class ExportAnalyticsResult(val csvContent: String)
