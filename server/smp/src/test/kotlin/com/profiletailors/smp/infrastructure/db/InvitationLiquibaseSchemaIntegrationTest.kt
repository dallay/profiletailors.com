package com.profiletailors.smp.infrastructure.db

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepository
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationLiquibaseSchemaIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var invitationRepository: R2dbcInvitationRepository

    override suspend fun seedScenario() = Unit

    @Test
    fun `canonical invitation schema exposes hardening constraints and indexes`() = runTest {
        val constraints = databaseClient.sql(
            """
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'invitations'::regclass
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("conname", String::class.java)) }
            .all()
            .collectList()
            .awaitSingle()

        assertTrue(
            constraints.containsAll(
                setOf(
                    "chk_invitations_source_reference",
                    "chk_invitations_email_normalized",
                    "chk_invitations_expiry_after_creation",
                    "chk_invitations_acceptance_metadata",
                    "chk_invitations_token_material",
                    "chk_invitations_version_nonnegative",
                    "chk_invitations_status",
                    "chk_invitations_source",
                ),
            ),
        )

        val indexes = databaseClient.sql(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE tablename = 'invitations'
            """.trimIndent(),
        )
            .map { row, _ ->
                requireNotNull(row.get("indexname", String::class.java)) to
                    requireNotNull(row.get("indexdef", String::class.java))
            }
            .all()
            .collectList()
            .awaitSingle()
            .toMap()

        assertNotNull(indexes["uq_invitations_workspace_active_email"])
        assertTrue(indexes.getValue("uq_invitations_workspace_active_email").contains("WHERE"))
        assertNotNull(indexes["idx_invitations_candidate_key_lookup"])
        assertNotNull(indexes["idx_invitations_expiration"])
    }

    @Test
    fun `rejects a second active invitation for the same workspace and normalized email`() = runTest {
        seedReferenceData()
        val firstId = UUID.randomUUID()
        insertActiveInvitation(
            invitationId = firstId,
            candidateKey = "candidate-key-active-1",
            tokenHash = "token-hash-1",
        )

        val error = runCatching {
            insertActiveInvitation(
                invitationId = UUID.randomUUID(),
                candidateKey = "candidate-key-active-2",
                tokenHash = "token-hash-2",
            )
        }.exceptionOrNull()

        assertNotNull(error, "Expected the second active invitation to be rejected by the unique partial index")
        val messageChain = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
        assertThat(messageChain)
            .withFailMessage(
                "Expected the partial unique index on (workspace_id, invited_email_normalized) WHERE " +
                    "status = 'ACTIVE' to be reported. Got: $messageChain",
            )
            .contains("uq_invitations_workspace_active_email")

        val activeCount = countActiveInvitations()
        assertEquals(1L, activeCount, "Only one ACTIVE invitation may exist per workspace and email")
    }

    @Test
    fun `rejects an invitation row whose acceptance status contradicts its metadata`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()

        val error = runCatching {
            insertInvitationWithStatus(
                invitationId = invitationId,
                candidateKey = "candidate-key-bad-acceptance",
                tokenHash = "token-hash-bad",
                status = InvitationStatus.ACCEPTED,
                acceptedAt = null,
                acceptedPrincipalId = null,
            )
        }.exceptionOrNull()

        assertNotNull(
            error,
            "Expected the invitation row with status=ACCEPTED but no accepted_at/principal to be rejected",
        )
        val messageChain = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
        assertThat(messageChain)
            .withFailMessage(
                "Expected the acceptance-metadata check constraint to reject the row. Got: $messageChain",
            )
            .contains("chk_invitations_acceptance_metadata")
    }

    @Test
    fun `conditional update rolls back state when the version does not match`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        val initial = newActiveInvitation(id = invitationId)
        invitationRepository.save(initial, candidateKey = "candidate-key-rollback")

        val accepted = initial.copy(version = 0).accept(Instant.parse("2026-08-10T11:00:00Z"), "principal-1")

        val firstUpdate = invitationRepository.updateIfVersionMatches(accepted)
        assertTrue(firstUpdate, "First conditional update with the transition version must succeed")

        val replayAttempt = accepted
        val replayResult = invitationRepository.updateIfVersionMatches(replayAttempt)
        assertThat(replayResult)
            .withFailMessage("Replay attempt with the original transition version must report no row changed")
            .isFalse()

        val persisted = databaseClient.sql(
            """
            SELECT version, status, accepted_principal_id FROM invitations WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .map { row, _ ->
                Triple(
                    requireNotNull(row.get("version", Long::class.javaObjectType)),
                    requireNotNull(row.get("status", String::class.java)),
                    row.get("accepted_principal_id", String::class.java),
                )
            }
            .one()
            .awaitSingle()
        assertEquals(1L, persisted.first, "Only the first conditional update may increment version")
        assertEquals(InvitationStatus.ACCEPTED.name, persisted.second)
        assertEquals("principal-1", persisted.third)
    }

    private suspend fun seedReferenceData() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'principal-1', NULL, 'Issuer')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Workspace One', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertActiveInvitation(invitationId: UUID, candidateKey: String, tokenHash: String) {
        insertInvitationWithStatus(
            invitationId = invitationId,
            candidateKey = candidateKey,
            tokenHash = tokenHash,
            status = InvitationStatus.ACTIVE,
            acceptedAt = null,
            acceptedPrincipalId = null,
        )
    }

    private suspend fun insertInvitationWithStatus(
        invitationId: UUID,
        candidateKey: String,
        tokenHash: String,
        status: InvitationStatus,
        acceptedAt: Instant?,
        acceptedPrincipalId: String?,
    ) {
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at,
                accepted_at, accepted_principal_id, version
            ) VALUES (
                :id, 'DIRECT', NULL, 'workspace-1', 'invitee@example.com',
                :candidateKey, :tokenHash, :status, 'principal-1',
                TIMESTAMPTZ '2026-08-10T10:00:00Z', TIMESTAMPTZ '2026-08-17T10:00:00Z',
                :acceptedAt, :acceptedPrincipalId, 0
            )
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("candidateKey", candidateKey)
            .bind("tokenHash", tokenHash)
            .bind("status", status.name)
            .bind("acceptedAt", acceptedAt?.let { java.time.OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) })
            .bindNullableString("acceptedPrincipalId", acceptedPrincipalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun countActiveInvitations(): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM invitations WHERE workspace_id = 'workspace-1' AND status = 'ACTIVE'",
    )
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

    private fun newActiveInvitation(id: UUID): Invitation = Invitation(
        id = InvitationId(id),
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        target = InvitationTarget.EXISTING_WORKSPACE,
        workspaceId = "workspace-1",
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "candidate-key-rollback",
        status = InvitationStatus.ACTIVE,
        issuedBy = "principal-1",
        createdAt = Instant.parse("2026-08-10T10:00:00Z"),
        expiresAt = Instant.parse("2026-08-17T10:00:00Z"),
        version = 0,
    )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgresTestContainerSupport.newContainer("invitation_schema")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}

private fun DatabaseClient.GenericExecuteSpec.bind(
    name: String,
    value: java.time.OffsetDateTime?,
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, java.time.OffsetDateTime::class.java)

private fun DatabaseClient.GenericExecuteSpec.bindNullableString(
    name: String,
    value: String?,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, String::class.java)
