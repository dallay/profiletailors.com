package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcInvitationAcceptanceRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var repository: R2dbcInvitationAcceptanceRepository

    @BeforeEach
    fun setUpRepository() {
        repository = R2dbcInvitationAcceptanceRepository(databaseClient)
    }

    @Test
    fun `finds an invitation by candidate key and conditionally marks it accepted`() = runTest {
        val invitationId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-08-10T10:00:00Z")
        val expiresAt = Instant.parse("2026-08-17T10:00:00Z")
        seedInvitation(
            invitationId = invitationId,
            candidateKey = "candidate-key-1",
            status = InvitationStatus.ACTIVE,
            createdAt = createdAt,
            expiresAt = expiresAt,
        )

        val invitation = repository.findByTokenCandidateKeyForUpdate("candidate-key-1")
        assertNotNull(invitation)
        assertEquals(invitationId, invitation?.id?.value)
        assertEquals(InvitationSource.DIRECT, invitation?.source)
        assertEquals("workspace-1", invitation?.workspaceId)
        assertEquals("invitee@example.com", invitation?.invitedEmailNormalized)
        assertEquals(createdAt, invitation?.createdAt)
        assertEquals(expiresAt, invitation?.expiresAt)

        val acceptedAt = Instant.parse("2026-08-10T11:00:00Z")
        assertTrue(
            repository.markAccepted(
                invitationId = InvitationId(invitationId),
                acceptedAt = acceptedAt,
                principalId = "principal-2",
            ),
        )
        assertEquals(
            1L,
            databaseClient.sql(
                "SELECT COUNT(*) FROM invitations WHERE id = :id AND status = 'ACCEPTED' AND accepted_principal_id = 'principal-2'",
            )
                .bind("id", invitationId)
                .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
                .one()
                .awaitSingle(),
        )
        assertEquals(
            0L,
            databaseClient.sql("SELECT COUNT(*) FROM invitations WHERE id = :id AND status = 'ACTIVE'")
                .bind("id", invitationId)
                .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
                .one()
                .awaitSingle(),
        )
        assertTrue(
            !repository.markAccepted(
                invitationId = InvitationId(invitationId),
                acceptedAt = acceptedAt,
                principalId = "principal-3",
            ),
        )
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("invitation_acceptance_repository_test")
    }

    private suspend fun seedInvitation(
        invitationId: UUID,
        candidateKey: String,
        status: InvitationStatus,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        seedReferenceData()
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at,
                accepted_at, accepted_principal_id
            ) VALUES (
                :id, 'DIRECT', NULL, 'workspace-1', 'invitee@example.com',
                :candidateKey, 'token-hash-1', :status, 'principal-1', :createdAt, :expiresAt,
                NULL, NULL
            )
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("candidateKey", candidateKey)
            .bind("status", status.name)
            .bind("createdAt", createdAt)
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedReferenceData() {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'principal-1', NULL, 'Issuer')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-2', 'USER', 'principal-2', NULL, 'Invitee')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-3', 'USER', 'principal-3', NULL, 'Other')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('workspace-1', 'Workspace One', 'ACTIVE', NULL)",
        ).fetch().rowsUpdated().awaitSingle()
    }
}
