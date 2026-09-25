package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.command.BulkInviteOutcome
import com.profiletailors.smp.platformadmin.application.command.BulkInviteWaitlistEntriesCommand
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.handler.BulkInviteWaitlistEntriesHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        "platform.storage.providers.local.base-path=/tmp/smp-platform-admin-bulk-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BulkInvitePostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    @Autowired
    private lateinit var bulkHandler: BulkInviteWaitlistEntriesHandler

    @Autowired
    private lateinit var inviteHandler: InviteWaitlistEntryHandler

    @Autowired
    private lateinit var invitationRepository: WaitlistInvitationRepository

    private val operatorId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)

    override suspend fun seedScenario() {
        seedPrincipal(operatorId.toString())
        seedPrincipal("user-$operatorId")
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('wl-bulk', 'profile-tailors-bulk', 'Bulk', 'profile-tailors', 'ACTIVE')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        seedEntry("entry-bulk-ok", "bulk-ok@example.com", "PENDING")
        seedEntry("entry-bulk-converted", "bulk-converted@example.com", "CONVERTED")
        seedEntry("entry-bulk-invited", "bulk-invited@example.com", "INVITED")
        databaseClient.sql(
            """
            INSERT INTO waitlist_invitations
              (id, waitlist_entry_id, token_hash, status, issued_at, expires_at, created_by, delivery_status)
            VALUES
              (:id, 'entry-bulk-invited', 'bulk-seed-hash', 'ACTIVE', NOW(), NOW() + INTERVAL '7 days', :createdBy, 'PENDING')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("createdBy", operatorId)
            .fetch().rowsUpdated().awaitSingle()
    }

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM waitlist_invitations",
        "DELETE FROM invitations WHERE source_reference_id LIKE 'entry-bulk-%'",
        "DELETE FROM platform_role_assignments",
        "DELETE FROM waitlist_entries WHERE id LIKE 'entry-bulk-%'",
        "DELETE FROM waitlists WHERE id = 'wl-bulk'",
    ) + super.cleanupStatements()

    @Test
    fun `mixed batch commits each entry independently with per-entry audit`() = runTest {
        val result = bulkHandler.handle(
            BulkInviteWaitlistEntriesCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                entryIds = listOf("entry-bulk-ok", "entry-bulk-invited", "entry-bulk-converted"),
            ),
        )

        assertEquals(3, result.summary.requested)
        assertEquals(1, result.summary.invited)
        assertEquals(1, result.summary.skipped)
        assertEquals(1, result.summary.failed)
        assertEquals(BulkInviteOutcome.INVITED, result.results[0].outcome)
        assertEquals(BulkInviteOutcome.SKIPPED, result.results[1].outcome)
        assertEquals("ALREADY_INVITED", result.results[1].code)
        assertEquals(BulkInviteOutcome.FAILED, result.results[2].outcome)
        assertEquals("ENTRY_ALREADY_CONVERTED", result.results[2].code)

        assertEquals("INVITED", entryStatus("entry-bulk-ok"))
        assertEquals("INVITED", entryStatus("entry-bulk-invited"))
        assertEquals("CONVERTED", entryStatus("entry-bulk-converted"))
        assertEquals(1L, activeInvitationCount("entry-bulk-ok"))

        assertEquals(1L, auditCount("SUCCEEDED"))
        assertEquals(1L, auditCount("REJECTED"))
        assertEquals(1L, auditCount("FAILED"))
    }

    @Test
    fun `retry of a completed batch yields skipped without duplicate invitations`() = runTest {
        val ids = listOf("entry-bulk-ok", "entry-bulk-invited", "entry-bulk-converted")
        bulkHandler.handle(
            BulkInviteWaitlistEntriesCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                entryIds = ids,
            ),
        )

        val retry = bulkHandler.handle(
            BulkInviteWaitlistEntriesCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                entryIds = ids,
            ),
        )

        assertEquals(0, retry.summary.invited)
        assertEquals(2, retry.summary.skipped)
        assertEquals(1, retry.summary.failed)
        assertEquals(1L, activeInvitationCount("entry-bulk-ok"))
    }

    @Test
    fun `concurrent single and bulk invites create exactly one invitation`() = runTest {
        val single = async {
            runCatching {
                inviteHandler.handle(
                    InviteWaitlistEntryCommand(
                        operatorPrincipalId = operatorId,
                        operatorRoles = operatorRoles,
                        waitlistEntryId = "entry-bulk-ok",
                    ),
                )
            }
        }
        val bulk = async {
            bulkHandler.handle(
                BulkInviteWaitlistEntriesCommand(
                    operatorPrincipalId = operatorId,
                    operatorRoles = operatorRoles,
                    entryIds = listOf("entry-bulk-ok"),
                ),
            )
        }
        val singleOutcome = single.await()
        val bulkOutcome = bulk.await()

        assertEquals(1L, activeInvitationCount("entry-bulk-ok"))
        val invitedCount = listOf(
            singleOutcome.isSuccess,
            bulkOutcome.results.single().outcome == BulkInviteOutcome.INVITED,
        ).count { it }
        assertEquals(1, invitedCount)

        val retry = bulkHandler.handle(
            BulkInviteWaitlistEntriesCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                entryIds = listOf("entry-bulk-ok"),
            ),
        )
        assertEquals(BulkInviteOutcome.SKIPPED, retry.results.single().outcome)
        assertEquals(1L, activeInvitationCount("entry-bulk-ok"))
    }

    private suspend fun seedEntry(id: String, email: String, status: String) {
        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at, last_explicit_action_at)
            VALUES
              (:id, 'wl-bulk', :email, :email, 'web', true, false, '1.0', :status, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("email", email)
            .bind("status", status)
            .fetch().rowsUpdated().awaitSingle()
        if (status == "INVITED" || status == "CONVERTED") {
            databaseClient.sql("UPDATE waitlist_entries SET invited_at = NOW() WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated().awaitSingle()
        }
        if (status == "CONVERTED") {
            databaseClient.sql("UPDATE waitlist_entries SET converted_at = NOW() WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated().awaitSingle()
        }
    }

    private suspend fun entryStatus(entryId: String): String? = databaseClient.sql(
        "SELECT status FROM waitlist_entries WHERE id = :id",
    )
        .bind("id", entryId)
        .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
        .one()
        .awaitSingle()

    private suspend fun activeInvitationCount(entryId: String): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM waitlist_invitations WHERE waitlist_entry_id = :entryId AND status = 'ACTIVE'",
    )
        .bind("entryId", entryId)
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

    private suspend fun auditCount(result: String): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'WAITLIST_ENTRY_INVITED' AND result = :result",
    )
        .bind("result", result)
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

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
