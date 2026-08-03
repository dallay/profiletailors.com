package com.profiletailors.smp.analytics.infrastructure.persistence

import com.profiletailors.smp.analytics.domain.DateRange
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcAnalyticsRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val repository by lazy { R2dbcAnalyticsRepository(databaseClient) }
    private val range = DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"))

    @Test
    fun `returns published posts for the requested workspace and computes empty metrics safely`() = runTest {
        seedWorkspace("workspace-1")
        seedWorkspace("workspace-2")
        seedPrincipal("principal-1")
        seedAccount("account-1", "workspace-1", "connection-1")
        seedPublication("post-1", "workspace-1", "principal-1", "account-1", "2026-08-01T10:00:00Z")
        seedPublication("post-2", "workspace-2", "principal-1", "account-1", "2026-08-01T10:00:00Z")

        val overview = repository.getOverview("workspace-1", range)
        val posts = repository.getPostAnalytics("workspace-1", range, page = 0, size = 20)

        assertEquals(1, posts.total)
        assertEquals(3, overview.dailyMetrics.size)
        assertEquals(0.0, overview.engagementRate)
        assertEquals(0.0, overview.clickThroughRate)
    }

    @Test
    fun `paginates published posts and returns best-time rows`() = runTest {
        seedWorkspace("workspace-1")
        seedPrincipal("principal-1")
        seedAccount("account-1", "workspace-1", "connection-1")
        seedPublication("post-1", "workspace-1", "principal-1", "account-1", "2026-08-01T10:00:00Z")
        seedPublication("post-2", "workspace-1", "principal-1", "account-1", "2026-08-02T10:00:00Z")

        val page = repository.getPostAnalytics("workspace-1", range, page = 1, size = 1)
        val bestTimes = repository.getBestTimes("workspace-1")

        assertEquals(2, page.total)
        assertEquals(1, page.posts.size)
        assertTrue(bestTimes.slots.isNotEmpty())
    }

    private suspend fun seedWorkspace(id: String) {
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES (:id, :name, 'ACTIVE', NULL)")
            .bind("id", id).bind("name", id).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPrincipal(id: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, :display)
            """.trimIndent(),
        )
            .bind("id", id).bind("subject", id).bind("display", id).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedAccount(id: String, workspaceId: String, connectionId: String) {
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status
            ) VALUES (:connection, :workspace, 'LINKEDIN', :ref, 'ACTIVE')
            """.trimIndent(),
        )
            .bind(
                "connection",
                connectionId,
            ).bind("workspace", workspaceId).bind("ref", connectionId).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                :id, :connection, :workspace, 'LINKEDIN', :providerId,
                'PERSONAL_PROFILE', :display, 'ACTIVE'
            )
            """.trimIndent(),
        )
            .bind(
                "id",
                id,
            ).bind(
                "connection",
                connectionId,
            ).bind(
                "workspace",
                workspaceId,
            ).bind("providerId", id).bind("display", id).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPublication(
        id: String,
        workspaceId: String,
        principalId: String,
        accountId: String,
        publishedAt: String,
    ) {
        databaseClient.sql(
            """
            INSERT INTO publications (
                id, workspace_id, author_principal_id, provider, social_account_id,
                status, schedule_mode, priority, title, body_text, published_at
            ) VALUES (
                :id, :workspace, :principal, 'LINKEDIN', :account, 'PUBLISHED',
                'NOW', FALSE, :title, :body, :publishedAt
            )
            """.trimIndent(),
        )
            .bind("id", id).bind("workspace", workspaceId).bind("principal", principalId).bind("account", accountId)
            .bind("title", id).bind("body", "Body").bind("publishedAt", java.time.OffsetDateTime.parse(publishedAt))
            .fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("analytics_repositories")
    }
}
