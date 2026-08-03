package com.profiletailors.smp.analytics.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.analytics.application.ExportAnalyticsCommand
import com.profiletailors.smp.analytics.application.ExportAnalyticsResult
import com.profiletailors.smp.analytics.application.GetAnalyticsOverviewQuery
import com.profiletailors.smp.analytics.application.GetBestTimesQuery
import com.profiletailors.smp.analytics.application.GetPostAnalyticsQuery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AnalyticsControllerTest {
    @Test
    fun `overview defaults missing dates and posts preserves pagination`() = runTest {
        val mediator = CapturingMediator()
        val controller = AnalyticsController(mediator)

        controller.getOverview(null, null)
        val overview = mediator.lastQuery as GetAnalyticsOverviewQuery
        assertEquals(29, overview.endDate.toEpochDay() - overview.startDate.toEpochDay())

        controller.getPostAnalytics(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"), 2, 10)
        assertEquals(
            GetPostAnalyticsQuery(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"), 2, 10),
            mediator.lastQuery,
        )
    }

    @Test
    fun `export returns CSV attachment headers`() = runTest {
        val mediator = CapturingMediator()
        val controller = AnalyticsController(mediator)

        val response = controller.exportAnalytics(
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-03"),
        )

        assertEquals("text/csv", response.headers.contentType?.toString())
        assertEquals("analytics-2026-08-01-2026-08-03.csv", response.headers.contentDisposition.filename)
        assertEquals(
            ExportAnalyticsCommand(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03")),
            mediator.lastCommand,
        )
    }

    @Test
    fun `controller defaults date ranges for posts and export and dispatches best times`() = runTest {
        val mediator = CapturingMediator()
        val controller = AnalyticsController(mediator)

        controller.getPostAnalytics(null, LocalDate.parse("2026-08-03"), 0, 20)
        val posts = mediator.lastQuery as GetPostAnalyticsQuery
        assertEquals(29, posts.endDate.toEpochDay() - posts.startDate.toEpochDay())

        controller.getBestTimes()
        assertEquals(GetBestTimesQuery, mediator.lastQuery)

        controller.exportAnalytics(null, LocalDate.parse("2026-08-03"))
        val export = mediator.lastCommand as ExportAnalyticsCommand
        assertEquals(29, export.endDate.toEpochDay() - export.startDate.toEpochDay())
    }

    private class CapturingMediator : Mediator {
        var lastQuery: Any? = null
        var lastCommand: Any? = null

        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            @Suppress("UNCHECKED_CAST")
            return when (query) {
                is GetAnalyticsOverviewQuery -> com.profiletailors.smp.analytics.domain.AnalyticsOverview(
                    com.profiletailors.smp.analytics.domain.DateRange(query.startDate, query.endDate),
                    0,
                    0,
                    0.0,
                    0,
                    0,
                    0.0,
                    emptyList(),
                ) as TResponse
                is GetPostAnalyticsQuery -> com.profiletailors.smp.analytics.domain.PostAnalyticsList(
                    emptyList(),
                    0,
                    query.page,
                    query.size,
                ) as TResponse
                is GetBestTimesQuery -> com.profiletailors.smp.analytics.domain.BestTimesRecommendation(
                    emptyList(),
                ) as TResponse
                else -> error("Unsupported query")
            }
        }

        override suspend fun <TCommand : Command> send(command: TCommand) = error("Not used")

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            @Suppress("UNCHECKED_CAST")
            return ExportAnalyticsResult(
                "date,platform,title,impressions,clicks,engagements,engagement_rate\n",
            ) as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) = error("Not used")

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) =
            error("Not used")
    }
}
