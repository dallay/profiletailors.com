package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.transaction.reactive.executeAndAwait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderAwareConnectionRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private lateinit var connections: R2dbcSocialConnectionRepository
    private lateinit var accounts: R2dbcSocialAccountRepository
    private lateinit var channelReads: ConnectedSocialChannelReadRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:provider-test@example.com', NULL, 'provider-test')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Provider Test Workspace', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        connections = R2dbcSocialConnectionRepository(databaseClient)
        accounts = R2dbcSocialAccountRepository(databaseClient, SimpleMeterRegistry())
        channelReads = R2dbcConnectedSocialChannelReadRepository(databaseClient)
    }

    @Test
    fun `upserts Threads connection and account without secrets in read models`() = runTest {
        val connection = connections.upsert(
            SocialConnection(
                id = "soconn-threads",
                workspaceId = "workspace-1",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-profile-1",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-reference-only",
            ),
        )
        val account = accounts.upsert(
            SocialAccount(
                id = "soacc-threads",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.THREADS,
                providerAccountId = "threads-profile-1",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Threads profile",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        val loadedConnection = connections.findByWorkspaceAndId("workspace-1", connection.id)
        val loadedAccount = accounts.findByWorkspaceAndId("workspace-1", account.id)

        assertEquals(SocialProvider.THREADS, loadedConnection?.provider)
        assertEquals("credential-reference-only", loadedConnection?.credentialReference)
        assertEquals(SocialProvider.THREADS, loadedAccount?.provider)
        assertEquals("threads-profile-1", loadedAccount?.providerAccountId)
        assertNull(loadedAccount?.avatarUrl)
        val readModel = channelReads.listByWorkspace("workspace-1").single()
        assertEquals(SocialProvider.THREADS, readModel.provider)
        assertEquals("Threads profile", readModel.displayName)
        assertNotNull(loadedConnection)
        assertNotNull(loadedAccount)
    }

    @Test
    fun `disconnect deletes account owned content and preserves other connections and workspaces`() = runTest {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-2', 'Other', 'ACTIVE')")
            .fetch().rowsUpdated().awaitSingle()
        seedContent("removed-1", "removed", "workspace-1")
        seedContent("removed-2", "removed", "workspace-1")
        seedContent("retained", "retained", "workspace-1")
        seedContent("other-workspace", "other-workspace", "workspace-2")

        transactionalOperator.executeAndAwait {
            accounts.deleteByConnectionId("removed")
            connections.deleteByWorkspaceAndId("workspace-1", "removed")
        }

        assertNull(connections.findByWorkspaceAndId("workspace-1", "removed"))
        listOf(
            "social_accounts",
            "social_content_posts",
            "social_content_comments",
            "social_content_actor_capabilities",
            "social_content_sync_checkpoints",
            "social_content_webhook_events",
            "social_content_reply_commands",
        ).forEach { table ->
            val ids = databaseClient.sql("SELECT id FROM $table ORDER BY id")
                .map { row, _ -> requireNotNull(row.get("id", String::class.java)) }
                .all().collectList().awaitSingle()
            assertEquals(listOf("other-workspace", "retained"), ids, table)
        }
    }

    private suspend fun seedAccount(id: String, connectionId: String, workspaceId: String) {
        connections.upsert(
            SocialConnection(
                id = connectionId,
                workspaceId = workspaceId,
                provider = SocialProvider.THREADS,
                providerConnectionRef = connectionId,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
        accounts.upsert(
            SocialAccount(
                id = id,
                socialConnectionId = connectionId,
                workspaceId = workspaceId,
                provider = SocialProvider.THREADS,
                providerAccountId = id,
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = id,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
    }

    private suspend fun seedContent(id: String, connectionId: String, workspaceId: String) {
        seedAccount(id, connectionId, workspaceId)
        listOf(
            """
            INSERT INTO social_content_actor_capabilities
                (id, workspace_id, social_account_id, provider, role_state, granted_scopes,
                 activity_ttl_seconds, commenter_profile_ttl_seconds)
            VALUES (:id, :workspaceId, :id, 'THREADS', 'APPROVED', '[]', 3600, 3600)
            """,
            """
            INSERT INTO social_content_posts
                (id, workspace_id, social_account_id, provider, external_post_id, published_at,
                 origin, lifecycle, expires_at)
            VALUES (:id, :workspaceId, :id, 'THREADS', :id, CURRENT_TIMESTAMP,
                    'IMPORTED', 'PUBLISHED', CURRENT_TIMESTAMP + INTERVAL '1 hour')
            """,
            """
            INSERT INTO social_content_comments
                (id, workspace_id, post_id, provider, external_comment_id, actor_external_id,
                 created_at, state, expires_at)
            VALUES (:id, :workspaceId, :id, 'THREADS', :id, :id,
                    CURRENT_TIMESTAMP, 'VISIBLE', CURRENT_TIMESTAMP + INTERVAL '1 hour')
            """,
            """
            INSERT INTO social_content_sync_checkpoints (id, workspace_id, social_account_id, resource)
            VALUES (:id, :workspaceId, :id, 'POSTS')
            """,
            """
            INSERT INTO social_content_webhook_events
                (id, workspace_id, provider_event_id, social_account_id, received_at, payload_cache_key)
            VALUES (:id, :workspaceId, :id, :id, CURRENT_TIMESTAMP, :id)
            """,
            """
            INSERT INTO social_content_reply_commands
                (id, workspace_id, social_account_id, parent_external_comment_id, idempotency_key, body, status)
            VALUES (:id, :workspaceId, :id, :id, :id, 'Reply', 'PENDING')
            """,
        ).forEach { sql ->
            databaseClient.sql(sql.trimIndent()).bind("id", id).bind("workspaceId", workspaceId)
                .fetch().rowsUpdated().awaitSingle()
        }
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("provider_connections")
    }
}
