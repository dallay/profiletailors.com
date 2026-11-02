package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationCommand
import com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationHandler
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationStatus
import com.profiletailors.smp.platformadmin.infrastructure.SecureBootstrapTokenGenerator
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.base-path=/tmp/smp-platform-admin-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest : PostgresIntegrationTestBase() {
    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var repository: BootstrapInvitationRepository

    @Autowired
    private lateinit var identityLookup: PrincipalIdentityLookup

    @Autowired
    private lateinit var auditPublisher: AdministrativeAuditPublisher

    @Autowired
    private lateinit var transactionRunner: AtomicTransactionRunner

    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val principalId = UUID.fromString("11111111-2222-3333-4444-555555555555")

    override fun cleanupStatements(): List<String> = super.cleanupStatements() + listOf(
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM platform_admin_bootstrap_invitations",
        "DELETE FROM platform_role_assignments",
    )

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', 'bootstrap-recipient', NULL, 'bootstrap-recipient')
            """.trimIndent(),
        ).bind("id", principalId).fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `claim is conditional and completion binds the invitation to one principal`() = runTest {
        repository.lock()
        assertFalse(repository.hasActiveOwner())
        repository.replace(
            BootstrapInvitation(
                email = "owner@example.test",
                tokenHash = "one-use-token-hash",
                expiresAt = now.plusSeconds(1800),
                createdAt = now,
            ),
        )

        assertTrue(repository.claim("one-use-token-hash", principalId, now))
        assertFalse(repository.claim("one-use-token-hash", UUID.randomUUID(), now))
        assertEquals(principalId, repository.findClaimedByPrincipalId(principalId)?.claimedPrincipalId)
        assertTrue(repository.complete(principalId, now.plusSeconds(60)))
        assertFalse(repository.complete(principalId, now.plusSeconds(120)))
        assertEquals(BootstrapInvitationStatus.COMPLETED, repository.findByTokenHash("one-use-token-hash")?.status)
    }

    @Test
    fun `expired invitation cannot be claimed`() = runTest {
        repository.lock()
        repository.replace(
            BootstrapInvitation(
                email = "owner@example.test",
                tokenHash = "expired-token-hash",
                expiresAt = now,
                createdAt = now.minusSeconds(1800),
            ),
        )

        assertFalse(repository.claim("expired-token-hash", UUID.randomUUID(), now))
        assertNull(repository.findClaimedByPrincipalId(UUID.randomUUID()))
    }

    @Test
    fun `concurrent confirmed replacement leaves one pending invitation and audited outcomes`() = runTest {
        val issuer = IssueBootstrapInvitationHandler(
            repository = repository,
            identityLookup = identityLookup,
            auditPublisher = auditPublisher,
            transactionRunner = transactionRunner,
            tokenGenerator = SecureBootstrapTokenGenerator(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            adminPublicOrigin = "https://admin.example.test",
        )
        val start = CompletableDeferred<Unit>()

        val links = coroutineScope {
            val attempts = List(2) {
                async {
                    start.await()
                    issuer.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", true))
                }
            }
            start.complete(Unit)
            attempts.awaitAll()
        }

        assertEquals(2, links.map { it.link }.distinct().size)
        assertEquals("owner@example.test", repository.findCurrent()?.email)
        assertEquals(
            1L,
            databaseClient.sql("SELECT COUNT(*) FROM platform_admin_bootstrap_invitations")
                .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle(),
        )
        assertEquals(
            2L,
            databaseClient.sql("SELECT COUNT(*) FROM platform_admin_audit_events WHERE actor_kind = 'SYSTEM_BOOTSTRAP'")
                .map { row, _ -> requireNotNull(row.get(0, Long::class.javaObjectType)) }.one().awaitSingle(),
        )
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
