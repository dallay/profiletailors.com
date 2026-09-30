package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredentialPurgeRepositoryPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private lateinit var purge: R2dbcCredentialPurgeRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Purge', 'ACTIVE')")
            .fetch().rowsUpdated().awaitSingle()
        purge = R2dbcCredentialPurgeRepository(databaseClient)
    }

    @Test
    fun `finds only expired orphans for the requested provider`() = runTest {
        val expiredOrphan = seedCredential("LINKEDIN", "40 days")
        seedCredential("LINKEDIN", "1 hour")
        seedCredential("THREADS", "40 days")
        val liveId = seedCredential("LINKEDIN", "40 days")
        seedConnection("live-conn", "LINKEDIN", "ACTIVE", liveId)
        val terminalId = seedCredential("LINKEDIN", "40 days")
        seedConnection("terminal-conn", "LINKEDIN", "EXPIRED", terminalId)

        val threshold = Instant.now().minus(Duration.ofDays(30))
        val linkedin = purge.findOrphanExpiredCandidates(SocialProvider.LINKEDIN, threshold, 100).toList()
        val threads = purge.findOrphanExpiredCandidates(SocialProvider.THREADS, threshold, 100).toList()

        assertEquals(setOf(expiredOrphan, terminalId), linkedin.map { it.id }.toSet())
        assertEquals(1, threads.size)
        assertEquals(SocialProvider.THREADS, threads.single().provider)
    }

    @Test
    fun `deleteById reports absent rows as skipped`() = runTest {
        val id = seedCredential("LINKEDIN", "40 days")

        assertTrue(purge.deleteById(id))
        assertFalse(purge.deleteById(id))
        assertFalse(purge.deleteById(UUID.randomUUID()))
    }

    private suspend fun seedCredential(provider: String, age: String): UUID {
        val id = UUID.randomUUID()
        databaseClient.sql(
            """
            INSERT INTO secure_credentials
                (id, owner_type, owner_id, provider, encrypted_payload, access_token_expires_at)
            VALUES
                (:id, :ownerType, :ownerId, :provider, :payload, NOW() - INTERVAL '$age')
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("ownerType", "test:user")
            .bind("ownerId", UUID.randomUUID())
            .bind("provider", provider)
            .bind("payload", byteArrayOf(1, 2, 3))
            .fetch().rowsUpdated().awaitSingle()
        return id
    }

    private suspend fun seedConnection(id: String, provider: String, status: String, credentialId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO social_connections
                (id, workspace_id, provider, provider_connection_ref, status,
                 credential_reference, connected_at, last_synced_at, created_at)
            VALUES
                (:id, 'workspace-1', :provider, :ref, :status,
                 :credentialReference, NOW(), NOW(), NOW())
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("provider", provider)
            .bind("ref", "ref-$id")
            .bind("status", status)
            .bind("credentialReference", credentialId.toString())
            .fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("credential_purge")
    }
}
