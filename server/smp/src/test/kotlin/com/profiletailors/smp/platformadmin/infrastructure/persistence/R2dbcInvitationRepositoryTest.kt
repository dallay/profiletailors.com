package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.application.InvitationRegistrationGateway
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepository
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepositoryFacade
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.infrastructure.InvitationRegistrationGatewayAdapter
import com.profiletailors.smp.tenancy.application.R2dbcWorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.infrastructure.R2dbcWorkspaceMembershipRepository
import com.profiletailors.smp.test.TestStorageConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class R2dbcInvitationRepositoryTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    private val independentConnectionFactory by lazy {
        PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgresContainer.host)
                .port(postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(postgresContainer.databaseName)
                .username(postgresContainer.username)
                .password(postgresContainer.password)
                .build(),
        )
    }

    @Autowired
    private lateinit var repository: R2dbcInvitationRepository

    override suspend fun seedScenario() = Unit

    @Test
    fun `findById round trips the invitation with version`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-1", version = 3)

        val loaded = repository.findById(InvitationId(invitationId))

        assertNotNull(loaded)
        assertEquals(invitationId, loaded?.id?.value)
        assertEquals(3L, loaded?.version)
        assertEquals(InvitationStatus.ACTIVE, loaded?.status)
        assertEquals("candidate-key-1", loaded?.tokenHash)
    }

    @Test
    fun `findById returns null when the invitation does not exist`() = runTest {
        seedReferenceData()
        val loaded = repository.findById(InvitationId(UUID.randomUUID()))
        assertThat(loaded).isNull()
    }

    @Test
    fun `findByCandidateKeyForUpdate returns the invitation carrying only opaque token material`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-1", version = 0)

        val loaded = repository.findByCandidateKeyForUpdate("candidate-key-1")

        assertNotNull(loaded)
        assertEquals(invitationId, loaded?.id?.value)
        assertThat(loaded?.tokenHash)
            .withFailMessage(
                "Repository MUST only persist the opaque lookup material (tokenHash/candidateKey), not a raw token",
            )
            .isEqualTo("candidate-key-1")
        val declaredFields = Invitation::class.java.declaredFields.map { it.name }
        assertThat(declaredFields)
            .withFailMessage("Invitation aggregate MUST NOT introduce a raw token, URL, or delivery field")
            .doesNotContain("rawToken", "acceptUrl", "deliveryStatus", "lastDeliveryAttemptAt")
    }

    @Test
    fun `save persists a new invitation with version zero and returns the canonical aggregate`() = runTest {
        seedReferenceData()
        val invitation = newInvitation()

        val saved = repository.save(invitation, candidateKey = "candidate-key-save-1")

        assertEquals(invitation.id, saved.id)
        assertEquals(0L, saved.version)
        assertEquals(invitation.status, saved.status)
        assertEquals(invitation.tokenHash, saved.tokenHash)

        val storedCount = databaseClient.sql(
            "SELECT COUNT(*) FROM invitations WHERE id = :id AND version = 0",
        )
            .bind("id", invitation.id.value)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, storedCount)
    }

    @Test
    fun `updateIfVersionMatches persists a transition when its version follows the stored version`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-cas-1", version = 1)
        val stored = repository.findById(InvitationId(invitationId))!!
        val accepted = stored.accept(stored.createdAt.plusSeconds(1), "principal-1")

        val result = repository.updateIfVersionMatches(accepted)

        assertTrue(result)
        val storedVersion = databaseClient.sql("SELECT version FROM invitations WHERE id = :id")
            .bind("id", invitationId)
            .map { row, _ -> requireNotNull(row.get("version", Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(2L, storedVersion)
    }

    @Test
    fun `updateIfVersionMatches persists an accepted transition from stored version zero`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-cas-accepted", version = 0)
        val stored = repository.findById(InvitationId(invitationId))!!
        val acceptedAt = stored.createdAt.plusSeconds(1)
        val accepted = stored.accept(acceptedAt, "principal-1")

        val result = repository.updateIfVersionMatches(accepted)

        assertTrue(result)
        val persisted = repository.findById(InvitationId(invitationId))!!
        assertEquals(InvitationStatus.ACCEPTED, persisted.status)
        assertEquals(acceptedAt, persisted.acceptedAt)
        assertEquals("principal-1", persisted.acceptedPrincipalId)
        assertEquals(1L, persisted.version)
    }

    @Test
    fun `concurrent acceptance clients allow one success and one membership`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-contention", version = 0)
        val initial = repository.findById(InvitationId(invitationId))!!
        val acceptedAt = initial.createdAt.plusSeconds(1)
        val outcomes = runConcurrentAcceptance(concurrentAcceptanceFixture(acceptedAt))

        assertEquals(1, outcomes.count { it.isSuccess })
        val acceptedRows = databaseClient.sql(
            "SELECT COUNT(*) FROM invitations WHERE id = :id AND status = 'ACCEPTED' AND version = 1",
        )
            .bind("id", invitationId)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, acceptedRows)
        val membershipRows = databaseClient.sql(
            """
            SELECT COUNT(*)
            FROM workspace_memberships
            WHERE workspace_id = 'workspace-1'
              AND principal_id = 'principal-1'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, membershipRows)
    }

    @Test
    fun `updateIfVersionMatches reports a lost update when the stored version is newer`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-cas-2", version = 2)

        val staleAttempt = newInvitation(id = invitationId, version = 0)
            .accept(Instant.parse("2026-08-10T10:00:01Z"), "principal-1")

        val result = repository.updateIfVersionMatches(staleAttempt)

        assertFalse(result)
        val storedVersion = databaseClient.sql("SELECT version FROM invitations WHERE id = :id")
            .bind("id", invitationId)
            .map { row, _ -> requireNotNull(row.get("version", Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(2L, storedVersion)
    }

    @Test
    fun `updateIfVersionMatches reports no row when the stored version is older than the transition predecessor`() =
        runTest {
            seedReferenceData()
            val invitationId = UUID.randomUUID()
            seedActiveInvitation(invitationId, "candidate-key-cas-3", version = 5)

            val transitionAttempt = newInvitation(id = invitationId, version = 7, status = InvitationStatus.ACTIVE)

            val result = repository.updateIfVersionMatches(transitionAttempt)

            assertFalse(result, "A transition must compare against the version immediately before its target version")
            val storedVersion = databaseClient.sql("SELECT version FROM invitations WHERE id = :id")
                .bind("id", invitationId)
                .map { row, _ -> requireNotNull(row.get("version", Long::class.java)) }
                .one()
                .awaitSingle()
            assertEquals(5L, storedVersion)
        }

    @Test
    fun `findByCandidateKeyForUpdate locks the row so a concurrent read sees the locked state`() = runTest {
        seedReferenceData()
        val invitationId = UUID.randomUUID()
        seedActiveInvitation(invitationId, "candidate-key-lock", version = 0)
        val loaded = repository.findByCandidateKeyForUpdate("candidate-key-lock")
        assertNotNull(loaded)

        val secondLookup = repository.findByCandidateKeyForUpdate("candidate-key-lock")
        assertNotNull(secondLookup)
        assertEquals(loaded?.id, secondLookup?.id)
        assertEquals(loaded?.version, secondLookup?.version)
    }

    private fun newInvitation(
        id: UUID = UUID.randomUUID(),
        version: Long = 0,
        status: InvitationStatus = InvitationStatus.ACTIVE,
    ): Invitation = Invitation(
        id = InvitationId(id),
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        workspaceId = "workspace-1",
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "candidate-key-new",
        status = status,
        issuedBy = "principal-1",
        createdAt = Instant.parse("2026-08-10T10:00:00Z"),
        expiresAt = Instant.parse("2026-08-17T10:00:00Z"),
        version = version,
    )

    private suspend fun seedActiveInvitation(invitationId: UUID, candidateKey: String, version: Long): Invitation {
        seedInvitation(invitationId, candidateKey, InvitationStatus.ACTIVE, version)
        return newInvitation(id = invitationId, version = version)
    }

    private suspend fun seedInvitation(
        invitationId: UUID,
        candidateKey: String,
        status: InvitationStatus,
        version: Long,
    ) {
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at,
                accepted_at, accepted_principal_id, version
            ) VALUES (
                :id, 'DIRECT', NULL, 'workspace-1', 'invitee@example.com',
                :candidateKey, :candidateKey, :status, 'principal-1', NOW(), NOW() + INTERVAL '7 days',
                NULL, NULL, :version
            )
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("candidateKey", candidateKey)
            .bind("status", status.name)
            .bind("version", version)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
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

    private data class ConcurrentAcceptanceFixture(
        val firstGateway: InvitationRegistrationGateway,
        val secondGateway: InvitationRegistrationGateway,
        val firstOperator: TransactionalOperator,
        val secondOperator: TransactionalOperator,
        val firstLocked: CompletableDeferred<Unit>,
    )

    private fun concurrentAcceptanceFixture(acceptedAt: Instant): ConcurrentAcceptanceFixture {
        val tokenHasher = FixedInvitationTokenHasher()
        val firstClient = DatabaseClient.create(independentConnectionFactory)
        val secondClient = DatabaseClient.create(independentConnectionFactory)
        val firstRepository = R2dbcInvitationRepository(firstClient)
        val secondRepository = R2dbcInvitationRepository(secondClient)
        val firstMembershipProvisioner = R2dbcWorkspaceMembershipProvisioner(
            R2dbcWorkspaceMembershipRepository(firstClient),
        )
        val secondMembershipProvisioner = R2dbcWorkspaceMembershipProvisioner(
            R2dbcWorkspaceMembershipRepository(secondClient),
        )
        val firstLocked = CompletableDeferred<Unit>()
        val secondLookupStarted = CompletableDeferred<Unit>()
        val firstBlockingProvisioner = object : WorkspaceMembershipProvisioner {
            override suspend fun reconcile(
                workspaceId: String,
                principalId: String,
            ): com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot {
                firstLocked.complete(Unit)
                secondLookupStarted.await()
                return firstMembershipProvisioner.reconcile(workspaceId, principalId)
            }
        }
        val secondAcceptanceRepository = object : InvitationAcceptanceRepository {
            private val delegate = InvitationAcceptanceRepositoryFacade(secondRepository)

            override suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation? {
                secondLookupStarted.complete(Unit)
                return delegate.findByCandidateKeyForUpdate(candidateKey)
            }

            override suspend fun markAccepted(
                invitationId: InvitationId,
                acceptedAt: Instant,
                principalId: String,
            ): Boolean = delegate.markAccepted(invitationId, acceptedAt, principalId)
        }
        return ConcurrentAcceptanceFixture(
            firstGateway = InvitationRegistrationGatewayAdapter(
                invitationRepository = InvitationAcceptanceRepositoryFacade(firstRepository),
                tokenHasher = tokenHasher,
                membershipProvisioner = firstBlockingProvisioner,
                clock = Clock.fixed(acceptedAt, ZoneOffset.UTC),
            ),
            secondGateway = InvitationRegistrationGatewayAdapter(
                invitationRepository = secondAcceptanceRepository,
                tokenHasher = tokenHasher,
                membershipProvisioner = secondMembershipProvisioner,
                clock = Clock.fixed(acceptedAt, ZoneOffset.UTC),
            ),
            firstOperator = TransactionalOperator.create(R2dbcTransactionManager(independentConnectionFactory)),
            secondOperator = TransactionalOperator.create(R2dbcTransactionManager(independentConnectionFactory)),
            firstLocked = firstLocked,
        )
    }

    private suspend fun runConcurrentAcceptance(fixture: ConcurrentAcceptanceFixture): List<Result<String>> =
        coroutineScope {
            val first = async {
                runCatching {
                    fixture.firstOperator.transactional(
                        mono {
                            fixture.firstGateway.acceptForRegistration(
                                rawToken = "raw-token",
                                email = "invitee@example.com",
                                principalId = "principal-1",
                            )
                        },
                    ).awaitSingle()
                }
            }
            fixture.firstLocked.await()
            val second = async {
                runCatching {
                    fixture.secondOperator.transactional(
                        mono {
                            fixture.secondGateway.acceptForRegistration(
                                rawToken = "raw-token",
                                email = "invitee@example.com",
                                principalId = "principal-1",
                            )
                        },
                    ).awaitSingle()
                }
            }
            awaitAll(first, second)
        }

    private class FixedInvitationTokenHasher :
        TokenHasher,
        InvitationTokenCandidateKey {
        override fun hash(rawToken: String): String = "candidate-key-contention"

        override fun matches(rawToken: String, storedHash: String): Boolean =
            rawToken == "raw-token" && storedHash == "candidate-key-contention"

        override fun candidateKey(rawToken: String): String = "candidate-key-contention"
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgresTestContainerSupport.newContainer(
            "r2dbc_invitation_repository_test",
        )

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
