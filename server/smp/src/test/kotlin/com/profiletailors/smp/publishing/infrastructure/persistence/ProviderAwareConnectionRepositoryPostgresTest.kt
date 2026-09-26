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
    fun `should find remaining credential references when another workspace owns the connection`() = runTest {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-2', 'Other', 'ACTIVE')")
            .fetch().rowsUpdated().awaitSingle()
        val connection = SocialConnection(
            id = "shared-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.THREADS,
            providerConnectionRef = "shared-profile",
            status = SocialConnectionStatus.ACTIVE,
            credentialReference = "shared-credential",
        )
        connections.upsert(connection)
        connections.upsert(connection.copy(id = "shared-2", workspaceId = "workspace-2"))

        connections.deleteByWorkspaceAndId("workspace-1", "shared-1")
        assertEquals(true, connections.existsByCredentialReference("shared-credential"))
        connections.deleteByWorkspaceAndId("workspace-2", "shared-2")
        assertEquals(false, connections.existsByCredentialReference("shared-credential"))
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("provider_connections")
    }
}
