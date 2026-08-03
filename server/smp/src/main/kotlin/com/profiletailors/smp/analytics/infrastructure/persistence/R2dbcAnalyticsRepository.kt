package com.profiletailors.smp.analytics.infrastructure.persistence

import com.profiletailors.smp.analytics.domain.AnalyticsOverview
import com.profiletailors.smp.analytics.domain.AnalyticsRepository
import com.profiletailors.smp.analytics.domain.BestTimeSlot
import com.profiletailors.smp.analytics.domain.BestTimesRecommendation
import com.profiletailors.smp.analytics.domain.DailyMetric
import com.profiletailors.smp.analytics.domain.DateRange
import com.profiletailors.smp.analytics.domain.PostAnalyticsList
import com.profiletailors.smp.analytics.domain.PostAnalyticsSummary
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import kotlin.math.roundToInt

private const val PERCENTAGE_SCALE = 100
private const val ROUNDING_FACTOR = 100
private const val DATE_PREFIX_LENGTH = 10

@Repository
internal class R2dbcAnalyticsRepository(private val databaseClient: DatabaseClient) : AnalyticsRepository {

    override suspend fun getOverview(workspaceId: String, range: DateRange): AnalyticsOverview {
        val posts = fetchPublishedPosts(workspaceId, range)
        val totalImpressions = posts.sumOf { it.impressions }
        val totalEngagements = posts.sumOf { it.engagements }
        val totalClicks = posts.sumOf { it.clicks }
        val engagementRate = if (totalImpressions > 0) {
            totalEngagements.toDouble() / totalImpressions * PERCENTAGE_SCALE
        } else {
            0.0
        }
        val ctr = if (totalImpressions > 0) {
            totalClicks.toDouble() / totalImpressions * PERCENTAGE_SCALE
        } else {
            0.0
        }
        val dailyMetrics = aggregateDailyMetrics(posts, range)
        return AnalyticsOverview(
            period = range,
            totalImpressions = totalImpressions,
            totalEngagements = totalEngagements,
            engagementRate = engagementRate.roundTo2(),
            totalClicks = totalClicks,
            newFollowers = 0L,
            clickThroughRate = ctr.roundTo2(),
            dailyMetrics = dailyMetrics,
        )
    }

    override suspend fun getPostAnalytics(
        workspaceId: String,
        range: DateRange,
        page: Int,
        size: Int,
    ): PostAnalyticsList {
        val all = fetchPublishedPosts(workspaceId, range)
        val total = all.size
        val paged = all.drop(page * size).take(size)
        return PostAnalyticsList(posts = paged, total = total, page = page, size = size)
    }

    override suspend fun getBestTimes(workspaceId: String): BestTimesRecommendation {
        val slots = databaseClient.sql(
            """
            SELECT EXTRACT(DOW FROM published_at AT TIME ZONE 'UTC') AS day_of_week,
                   EXTRACT(HOUR FROM published_at AT TIME ZONE 'UTC') AS hour,
                   COUNT(*) AS post_count
            FROM publications
            WHERE workspace_id = :workspaceId
              AND status = 'PUBLISHED'
              AND published_at IS NOT NULL
            GROUP BY day_of_week, hour
            ORDER BY post_count DESC
            LIMIT 10
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .fetch()
            .all()
            .map { row ->
                BestTimeSlot(
                    dayOfWeek = (row["day_of_week"] as? Number)?.toInt() ?: 0,
                    hour = (row["hour"] as? Number)?.toInt() ?: 0,
                    score = (row["post_count"] as? Number)?.toDouble() ?: 0.0,
                )
            }
            .collectList()
            .awaitSingle()
        return BestTimesRecommendation(slots = slots)
    }

    override suspend fun exportPostAnalytics(workspaceId: String, range: DateRange): List<PostAnalyticsSummary> =
        fetchPublishedPosts(workspaceId, range)

    private suspend fun fetchPublishedPosts(workspaceId: String, range: DateRange): List<PostAnalyticsSummary> =
        databaseClient.sql(
            """
            SELECT id, title, body_text, provider, published_at
            FROM publications
            WHERE workspace_id = :workspaceId
              AND status = 'PUBLISHED'
              AND published_at >= :startDate
              AND published_at < :endDate
            ORDER BY published_at DESC
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("startDate", range.startDate.atStartOfDay())
            .bind("endDate", range.endDate.plusDays(1).atStartOfDay())
            .fetch()
            .all()
            .map { row -> row.toPostAnalyticsSummary() }
            .collectList()
            .awaitSingle()

    private fun Map<String, Any>.toPostAnalyticsSummary(): PostAnalyticsSummary {
        val publishedAt = this["published_at"]
        val publishedAtStr = when (publishedAt) {
            is OffsetDateTime -> publishedAt.toInstant().toString()
            else -> publishedAt?.toString() ?: ""
        }
        return PostAnalyticsSummary(
            postId = this["id"]?.toString() ?: "",
            title = this["title"]?.toString(),
            bodyText = this["body_text"]?.toString(),
            provider = this["provider"]?.toString() ?: "",
            publishedAt = publishedAtStr,
            // Impression/engagement data not yet tracked per-post in this schema;
            // placeholder zeros until provider sync is implemented.
            impressions = 0L,
            clicks = 0L,
            engagements = 0L,
            reactions = 0L,
            comments = 0L,
            shares = 0L,
            engagementRate = 0.0,
        )
    }

    private fun aggregateDailyMetrics(posts: List<PostAnalyticsSummary>, range: DateRange): List<DailyMetric> {
        val byDate = posts.groupBy { it.publishedAt.take(DATE_PREFIX_LENGTH) }
        return generateSequence(range.startDate) { d ->
            if (d < range.endDate) d.plusDays(1) else null
        }.map { date ->
            val dateStr = date.toString()
            val dayPosts = byDate[dateStr] ?: emptyList()
            DailyMetric(
                date = date,
                impressions = dayPosts.sumOf { it.impressions },
                engagements = dayPosts.sumOf { it.engagements },
                clicks = dayPosts.sumOf { it.clicks },
            )
        }.toList()
    }
}

private fun Double.roundTo2(): Double = (this * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR.toDouble()
