package com.profiletailors.smp.analytics.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.analytics.domain.AnalyticsOverview
import com.profiletailors.smp.analytics.domain.AnalyticsRepository
import com.profiletailors.smp.analytics.domain.BestTimesRecommendation
import com.profiletailors.smp.analytics.domain.DailyMetric
import com.profiletailors.smp.analytics.domain.DateRange
import com.profiletailors.smp.analytics.domain.PostAnalyticsList
import com.profiletailors.smp.analytics.domain.PostAnalyticsSummary
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AnalyticsHandlersTest {
    @Test
    fun `overview handler delegates the workspace and date range`() = runTest {
        val repository = FakeAnalyticsRepository()
        val handler = GetAnalyticsOverviewHandler(FixedResourceContextProvider("workspace-1"), repository)
        val range = DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"))

        handler.handle(GetAnalyticsOverviewQuery(range.startDate, range.endDate))

        assertEquals("workspace-1", repository.overviewWorkspace)
        assertEquals(range, repository.overviewRange)
    }

    @Test
    fun `post handler passes pagination and export quotes titles`() = runTest {
        val repository = FakeAnalyticsRepository()
        val context = FixedResourceContextProvider("workspace-1")
        val range = DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"))
        val postHandler = GetPostAnalyticsHandler(context, repository)
        val exportHandler = ExportAnalyticsHandler(context, repository)

        postHandler.handle(GetPostAnalyticsQuery(range.startDate, range.endDate, page = 2, size = 10))
        val export = exportHandler.handle(ExportAnalyticsCommand(range.startDate, range.endDate))

        assertEquals(2, repository.postPage)
        assertEquals(10, repository.postSize)
        assertExportHeader(export.csvContent)
        assertEquals(true, export.csvContent.contains(quotedTitleCsv()))
    }

    @Test
    fun `best times handler scopes the request to the workspace`() = runTest {
        val repository = FakeAnalyticsRepository()

        GetBestTimesHandler(FixedResourceContextProvider("workspace-2"), repository)
            .handle(GetBestTimesQuery)

        assertEquals("workspace-2", repository.bestTimesWorkspace)
    }

    @Test
    fun `handlers reject requests without a workspace context`() {
        val context = FixedResourceContextProvider(null)
        val repository = FakeAnalyticsRepository()

        org.junit.jupiter.api.Assertions.assertThrows(
            WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                GetAnalyticsOverviewHandler(context, repository)
                    .handle(GetAnalyticsOverviewQuery(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03")))
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
            WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                GetPostAnalyticsHandler(context, repository)
                    .handle(GetPostAnalyticsQuery(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"), 0, 20))
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
            WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                GetBestTimesHandler(context, repository).handle(GetBestTimesQuery)
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
            WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                ExportAnalyticsHandler(context, repository)
                    .handle(ExportAnalyticsCommand(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03")))
            }
        }
    }

    private class FixedResourceContextProvider(private val workspaceId: String?) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private fun assertExportHeader(csvContent: String) {
        assertEquals(
            """
            date,platform,title,impressions,clicks,engagements,engagement_rate
            """.trimIndent() + '\n',
            csvContent.substringBefore("2026"),
        )
    }

    private fun quotedTitleCsv(): String = buildString {
        append('"')
        append("Title, with ")
        append('"')
        append('"')
        append("quotes")
        append('"')
        append('"')
        append('"')
    }

    private class FakeAnalyticsRepository : AnalyticsRepository {
        var overviewWorkspace: String? = null
        var overviewRange: DateRange? = null
        var postPage: Int? = null
        var postSize: Int? = null
        var bestTimesWorkspace: String? = null

        private val post = PostAnalyticsSummary(
            postId = "post-1",
            title = "Title, with \"quotes\"",
            bodyText = "Body",
            provider = "linkedin",
            publishedAt = "2026-08-01T10:00:00Z",
            impressions = 0,
            clicks = 0,
            engagements = 0,
            reactions = 0,
            comments = 0,
            shares = 0,
            engagementRate = 0.0,
        )

        override suspend fun getOverview(workspaceId: String, range: DateRange): AnalyticsOverview {
            overviewWorkspace = workspaceId
            overviewRange = range
            return AnalyticsOverview(range, 0, 0, 0.0, 0, 0, 0.0, listOf(DailyMetric(range.startDate, 0, 0, 0)))
        }

        override suspend fun getPostAnalytics(
            workspaceId: String,
            range: DateRange,
            page: Int,
            size: Int,
        ): PostAnalyticsList {
            postPage = page
            postSize = size
            return PostAnalyticsList(listOf(post), 1, page, size)
        }

        override suspend fun getBestTimes(workspaceId: String): BestTimesRecommendation {
            bestTimesWorkspace = workspaceId
            return BestTimesRecommendation(emptyList())
        }

        override suspend fun exportPostAnalytics(workspaceId: String, range: DateRange): List<PostAnalyticsSummary> =
            listOf(post)
    }
}
