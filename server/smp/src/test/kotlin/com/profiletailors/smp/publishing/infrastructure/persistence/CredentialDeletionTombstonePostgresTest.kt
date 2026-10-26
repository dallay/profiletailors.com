package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.CredentialDeletionReason
import com.profiletailors.smp.publishing.domain.CredentialDeletionTombstone
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredentialDeletionTombstonePostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private lateinit var tombstones: R2dbcCredentialDeletionTombstoneRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status)
            VALUES ('00000000-0000-0000-0000-00000000049d', 'Tombstone', 'ACTIVE')
            """.trimIndent(),
        )
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status)
            VALUES ('00000000-0000-0000-0000-00000000049e', 'Other', 'ACTIVE')
            """.trimIndent(),
        )
            .fetch().rowsUpdated().awaitSingle()
        tombstones = R2dbcCredentialDeletionTombstoneRepository(databaseClient)
    }

    @Test
    fun `records and reads tombstone with every field`() = runTest {
        val deletedAt = Instant.parse("2026-09-30T00:00:00Z")
        tombstones.record(
            CredentialDeletionTombstone(
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                connectionId = "00000000-0000-0000-0000-000000000403",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = "credential-1",
                activityId = "pa-006",
                policyVersion = "v1",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = deletedAt,
            ),
        )

        val loaded = tombstones.findByWorkspaceAndConnection(
            "00000000-0000-0000-0000-00000000049d",
            "00000000-0000-0000-0000-000000000403",
        )

        assertEquals(SocialProvider.THREADS, loaded?.provider)
        assertEquals("threads-user-1", loaded?.providerConnectionRef)
        assertEquals("credential-1", loaded?.credentialReference)
        assertEquals("pa-006", loaded?.activityId)
        assertEquals("v1", loaded?.policyVersion)
        assertEquals(CredentialDeletionReason.DISCONNECT, loaded?.reason)
        assertEquals(deletedAt, loaded?.deletedAt)
    }

    @Test
    fun `re-recording refreshes reason and timestamp`() = runTest {
        tombstones.record(
            CredentialDeletionTombstone(
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                connectionId = "00000000-0000-0000-0000-000000000403",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.parse("2026-09-29T00:00:00Z"),
            ),
        )
        tombstones.record(
            CredentialDeletionTombstone(
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                connectionId = "00000000-0000-0000-0000-000000000403",
                provider = SocialProvider.THREADS,
                providerConnectionRef = "threads-user-1",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.EXPIRED,
                deletedAt = Instant.parse("2026-09-30T00:00:00Z"),
            ),
        )

        val loaded = tombstones.findByWorkspaceAndConnection(
            "00000000-0000-0000-0000-00000000049d",
            "00000000-0000-0000-0000-000000000403",
        )

        assertEquals(CredentialDeletionReason.EXPIRED, loaded?.reason)
        assertEquals(Instant.parse("2026-09-30T00:00:00Z"), loaded?.deletedAt)
    }

    @Test
    fun `missing tombstone and other workspace stay isolated`() = runTest {
        assertNull(
            tombstones.findByWorkspaceAndConnection(
                "00000000-0000-0000-0000-00000000049d",
                "00000000-0000-0000-0000-00000000042d",
            ),
        )
        tombstones.record(
            CredentialDeletionTombstone(
                workspaceId = "00000000-0000-0000-0000-00000000049d",
                connectionId = "00000000-0000-0000-0000-000000000403",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-user-1",
                credentialReference = null,
                activityId = "pa-006",
                policyVersion = "",
                reason = CredentialDeletionReason.DISCONNECT,
                deletedAt = Instant.now(),
            ),
        )

        assertNull(
            tombstones.findByWorkspaceAndConnection(
                "00000000-0000-0000-0000-00000000049e",
                "00000000-0000-0000-0000-000000000403",
            ),
        )
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("credential_tombstones")
    }
}
