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
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class R2dbcAnalyticsRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private val repository by lazy { R2dbcAnalyticsRepository(databaseClient) }
    private val range = DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"))

    @Test
    fun `returns published posts for the requested workspace and computes empty metrics safely`() = runTest {
        seedWorkspace(WORKSPACE_1)
        seedWorkspace(WORKSPACE_2)
        seedPrincipal(PRINCIPAL_1)
        seedAccount(ACCOUNT_1, WORKSPACE_1, CONNECTION_1)
        seedPublication(POST_1, WORKSPACE_1, PRINCIPAL_1, ACCOUNT_1, "2026-08-01T10:00:00Z")
        seedPublication(POST_2, WORKSPACE_2, PRINCIPAL_1, ACCOUNT_1, "2026-08-01T10:00:00Z")

        val overview = repository.getOverview(WORKSPACE_1, range)
        val posts = repository.getPostAnalytics(WORKSPACE_1, range, page = 0, size = 20)

        assertEquals(1, posts.total)
        assertEquals(3, overview.dailyMetrics.size)
        assertEquals(0.0, overview.engagementRate)
        assertEquals(0.0, overview.clickThroughRate)
    }

    @Test
    fun `paginates published posts and returns best-time rows`() = runTest {
        seedWorkspace(WORKSPACE_1)
        seedPrincipal(PRINCIPAL_1)
        seedAccount(ACCOUNT_1, WORKSPACE_1, CONNECTION_1)
        seedPublication(POST_1, WORKSPACE_1, PRINCIPAL_1, ACCOUNT_1, "2026-08-01T10:00:00Z")
        seedPublication(POST_2, WORKSPACE_1, PRINCIPAL_1, ACCOUNT_1, "2026-08-02T10:00:00Z")

        val page = repository.getPostAnalytics(WORKSPACE_1, range, page = 1, size = 1)
        val bestTimes = repository.getBestTimes(WORKSPACE_1)

        assertEquals(2, page.total)
        assertEquals(1, page.posts.size)
        assertTrue(bestTimes.slots.isNotEmpty())
    }

    private suspend fun seedWorkspace(id: String) {
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES (:id, :name, 'ACTIVE', NULL)")
            .bind("id", UUID.fromString(id)).bind("name", id).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPrincipal(id: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, :display)
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(id)).bind("subject", id).bind("display", id).fetch().rowsUpdated().awaitSingle()
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
                UUID.fromString(connectionId),
            ).bind(
                "workspace",
                UUID.fromString(workspaceId),
            ).bind("ref", "connection-1").fetch().rowsUpdated().awaitSingle()
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
                UUID.fromString(id),
            ).bind(
                "connection",
                UUID.fromString(connectionId),
            ).bind(
                "workspace",
                UUID.fromString(workspaceId),
            ).bind("providerId", "account-1").bind("display", id).fetch().rowsUpdated().awaitSingle()
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
            .bind("id", UUID.fromString(id))
            .bind("workspace", UUID.fromString(workspaceId))
            .bind("principal", UUID.fromString(principalId))
            .bind("account", UUID.fromString(accountId))
            .bind("title", id).bind("body", "Body").bind("publishedAt", java.time.OffsetDateTime.parse(publishedAt))
            .fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val WORKSPACE_2 = "57af0326-afba-57ec-83e4-15ec87ecc8bc"
        private const val PRINCIPAL_1 = "f4b0c946-5bac-5e21-a5af-5213b71804cc"
        private const val ACCOUNT_1 = "abac071a-6efe-5775-ac56-1bf5097d19b8"
        private const val CONNECTION_1 = "909e5845-5822-5fb5-a3a3-a1d8a707cb02"
        private const val POST_1 = "93a052b0-4d1d-51d4-8e12-507b25cfcf39"
        private const val POST_2 = "f9d98c00-cb5c-59d0-aa32-066c96469c7b"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("analytics_repositories")
    }
}
