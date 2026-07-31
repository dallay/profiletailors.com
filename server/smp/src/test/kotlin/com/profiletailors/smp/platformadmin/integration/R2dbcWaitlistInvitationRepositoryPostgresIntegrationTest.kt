package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

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
class R2dbcWaitlistInvitationRepositoryPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var invitationRepository: WaitlistInvitationRepository

    private val waitlistId = "waitlist-a"
    private val entryId = "entry-1"
    private val operatorId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val issuedAt: Instant = Instant.parse("2026-07-01T10:00:00Z")
    private val expiresAt: Instant = Instant.parse("2026-07-08T10:00:00Z")

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status, created_at, updated_at)
            VALUES (:id, 'launch', 'Launch', 'profile-tailors', 'ACTIVE', :now, :now)
            """.trimIndent(),
        )
            .bind("id", waitlistId)
            .bind("now", Timestamp.from(issuedAt))
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source, locale,
               consent_early_access, consent_marketing, consent_version, status, joined_at)
            VALUES
              (:id, :waitlistId, 'alice@example.com', 'alice@example.com', 'marketing', 'en',
               true, true, '1', 'PENDING', :now)
            """.trimIndent(),
        )
            .bind("id", entryId)
            .bind("waitlistId", waitlistId)
            .bind("now", Timestamp.from(issuedAt))
            .fetch().rowsUpdated().awaitSingle()
    }

    private fun invitation() = WaitlistInvitation(
        id = WaitlistInvitationId(UUID.fromString("55555555-6666-7777-8888-999999999999")),
        waitlistEntryId = entryId,
        tokenHash = "token-hash-1",
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.PENDING,
        deliveryAttemptCount = 0,
        version = 0,
    )

    @Test
    fun `save persists invitation and findById reads it back`() = runTest {
        val saved = invitationRepository.save(invitation())

        assertEquals(0L, saved.version)
        assertEquals(WaitlistInvitationStatus.ACTIVE, saved.status)

        val found = invitationRepository.findById(invitation().id)
        assertNotNull(found)
        assertEquals(entryId, found!!.waitlistEntryId)
        assertEquals("token-hash-1", found.tokenHash)
        assertEquals(issuedAt, found.issuedAt)
        assertEquals(expiresAt, found.expiresAt)
        assertEquals(operatorId, found.createdBy)
        assertEquals(InvitationDeliveryStatus.PENDING, found.deliveryStatus)
        assertEquals(0, found.deliveryAttemptCount)
    }

    @Test
    fun `findById returns null for unknown invitation`() = runTest {
        assertNull(invitationRepository.findById(WaitlistInvitationId(UUID.randomUUID())))
    }

    @Test
    fun `findActiveByWaitlistEntryId returns only active invitation`() = runTest {
        invitationRepository.save(invitation())
        invitationRepository.save(
            invitation().copy(
                id = WaitlistInvitationId(UUID.fromString("66666666-7777-8888-9999-000000000000")),
                status = WaitlistInvitationStatus.REVOKED,
                tokenHash = "token-hash-revoked",
            ),
        )

        val active = invitationRepository.findActiveByWaitlistEntryId(entryId)

        assertNotNull(active)
        assertEquals("55555555-6666-7777-8888-999999999999", active!!.id.value.toString())
    }

    @Test
    fun `findAllByWaitlistEntryId returns all invitations ordered by issued_at desc`() = runTest {
        invitationRepository.save(invitation())
        invitationRepository.save(
            invitation().copy(
                id = WaitlistInvitationId(UUID.fromString("66666666-7777-8888-9999-000000000000")),
                issuedAt = issuedAt.plusSeconds(3600),
                tokenHash = "token-hash-2",
                status = WaitlistInvitationStatus.SUPERSEDED,
            ),
        )

        val all = invitationRepository.findAllByWaitlistEntryId(entryId)

        assertEquals(2, all.size)
        assertEquals("66666666-7777-8888-9999-000000000000", all.first().id.value.toString())
    }

    @Test
    fun `findByTokenHash finds invitation`() = runTest {
        invitationRepository.save(invitation())

        val found = invitationRepository.findByTokenHash("token-hash-1")

        assertNotNull(found)
        assertEquals(invitation().id, found!!.id)
    }

    @Test
    fun `update persists status change and bumps version`() = runTest {
        invitationRepository.save(invitation())

        val revoked = invitationRepository.update(invitation().revoke(issuedAt.plusSeconds(60), operatorId))

        assertEquals(1L, revoked.version)
        assertEquals(WaitlistInvitationStatus.REVOKED, revoked.status)
        assertEquals(issuedAt.plusSeconds(60), revoked.revokedAt)
        assertEquals(operatorId, revoked.revokedBy)
    }

    @Test
    fun `update with stale version throws version conflict`() = runTest {
        val saved = invitationRepository.save(invitation())
        invitationRepository.update(saved.revoke(issuedAt.plusSeconds(60), operatorId))

        assertFailsWith<InvitationVersionConflictException> {
            invitationRepository.update(saved)
        }
    }

    @Test
    fun `countResendsSince counts matching invitations after timestamp`() = runTest {
        invitationRepository.save(invitation())
        invitationRepository.save(
            invitation().copy(
                id = WaitlistInvitationId(UUID.fromString("66666666-7777-8888-9999-000000000000")),
                issuedAt = issuedAt.plusSeconds(7200),
                tokenHash = "token-hash-2",
                status = WaitlistInvitationStatus.SUPERSEDED,
            ),
        )

        val count = invitationRepository.countResendsSince(entryId, issuedAt.toEpochMilli())

        assertEquals(2, count)
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
