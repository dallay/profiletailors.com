package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.command.CancelWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
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
import java.util.UUID

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "platform.admin.invitation.ttl-days=7",
        "platform.admin.invitation.resend-limit=3",
        "platform.admin.invitation.resend-window-hours=24",
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
class PlatformAdminInvitationTransactionPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var inviteHandler: InviteWaitlistEntryHandler

    @Autowired
    private lateinit var cancelHandler: CancelWaitlistEntryHandler

    @Autowired
    private lateinit var invitationRepository: WaitlistInvitationRepository

    private val operatorId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('wl-1', 'profile-tailors-beta', 'Beta', 'profile-tailors', 'ACTIVE')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at)
            VALUES
              ('entry-test-1', 'wl-1', 'test@example.com', 'test@example.com', 'web',
               true, false, '1.0', 'PENDING', NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at)
            VALUES
              ('entry-test-2', 'wl-1', 'invited@example.com', 'invited@example.com', 'web',
               true, false, '1.0', 'PENDING', NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM waitlist_invitations",
        "DELETE FROM platform_role_assignments",
        "DELETE FROM waitlist_entries WHERE id IN ('entry-test-1', 'entry-test-2')",
        "DELETE FROM waitlists WHERE id = 'wl-1'",
    ) + super.cleanupStatements()

    @Test
    fun `invite creates active invitation and transitions entry to INVITED`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-1",
            ),
        )

        val invitation = invitationRepository.findActiveByWaitlistEntryId("entry-test-1")
        assertNotNull(invitation)
        assertEquals(WaitlistInvitationStatus.ACTIVE, invitation!!.status)

        val entryStatus = databaseClient.sql("SELECT status FROM waitlist_entries WHERE id = 'entry-test-1'")
            .map { row, _ -> row.get("status", String::class.java) as String }
            .one()
            .awaitSingle()
        assertEquals("INVITED", entryStatus)
    }

    @Test
    fun `audit event is persisted after successful invite`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-1",
            ),
        )

        val auditCount = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'WAITLIST_ENTRY_INVITED'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, auditCount)
    }

    @Test
    fun `database enforces at most one active invitation per waitlist entry`() = runTest {
        // First invite — creates active invitation
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-2",
            ),
        )

        // The entry is now INVITED — a second invite supersedes the first
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-2",
            ),
        )

        val activeCount = databaseClient.sql(
            "SELECT COUNT(*) FROM waitlist_invitations WHERE waitlist_entry_id = 'entry-test-2' AND status = 'ACTIVE'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, activeCount, "Only one active invitation should exist per entry")
    }

    @Test
    fun `cancel revokes active invitation and transitions entry to CANCELLED`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-1",
            ),
        )

        cancelHandler.handle(
            CancelWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "entry-test-1",
                reason = "spam account",
            ),
        )

        assertNull(invitationRepository.findActiveByWaitlistEntryId("entry-test-1"))

        val entryStatus = databaseClient.sql("SELECT status FROM waitlist_entries WHERE id = 'entry-test-1'")
            .map { row, _ -> row.get("status", String::class.java) as String }
            .one()
            .awaitSingle()
        assertEquals("CANCELLED", entryStatus)
    }
}
