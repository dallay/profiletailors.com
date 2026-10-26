package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.notifications.infrastructure.email.SendInvitationEmailConsumer
import com.profiletailors.smp.platformadmin.application.command.CancelWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.CreateInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendInvitationHandler
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

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
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    TestStorageConfiguration::class,
    InvitationTransactionTestConfiguration::class,
)
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
    private lateinit var createDirectInvitationHandler: CreateInvitationHandler

    @Autowired
    private lateinit var resendDirectInvitationHandler: ResendInvitationHandler

    @Autowired
    private lateinit var invitationRepository: WaitlistInvitationRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var recordingDispatcher: RecordingInvitationEmailDispatcher

    @Autowired
    private lateinit var toggleablePublisher: ToggleableInvitationEventPublisher

    @Autowired
    private lateinit var invitationEmailConsumer: SendInvitationEmailConsumer

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
        seedPrincipal(operatorId.toString())
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('00000000-0000-0000-0000-000000000601', 'profile-tailors-beta', 'Beta', 'profile-tailors', 'ACTIVE')
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at, last_explicit_action_at)
            VALUES
              ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000601', 'test@example.com', 'test@example.com', 'web',
               true, false, '1.0', 'PENDING', NOW(), NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        databaseClient.sql(
            """
            INSERT INTO waitlist_entries
              (id, waitlist_id, email_original, normalized_email, source,
               consent_early_access, consent_marketing, consent_version, status, joined_at, last_explicit_action_at)
            VALUES
              ('00000000-0000-0000-0000-000000000603', '00000000-0000-0000-0000-000000000601', 'invited@example.com', 'invited@example.com', 'web',
               true, false, '1.0', 'PENDING', NOW(), NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM notifications",
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM waitlist_invitations",
        """
        DELETE FROM invitations WHERE source_reference_id IN (
            '00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000603'
        )
        """.trimIndent(),
        "DELETE FROM invitations WHERE invited_email_normalized = 'direct-unknown-tx@example.com'",
        "DELETE FROM invitations WHERE invited_email_normalized = 'direct-new-workspace-tx@example.com'",
        "DELETE FROM invitations WHERE invited_email_normalized LIKE 'direct-live-%@example.com'",
        "DELETE FROM invitations WHERE invited_email_normalized LIKE 'direct-rollback-%@example.com'",
        "DELETE FROM invitations WHERE invited_email_normalized LIKE 'direct-failed-%@example.com'",
        "DELETE FROM platform_role_assignments",
        """
        DELETE FROM waitlist_entries
        WHERE id IN ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000603')
        """.trimIndent(),
        "DELETE FROM waitlists WHERE id = '00000000-0000-0000-0000-000000000601'",
        """
        DELETE FROM workspaces
        WHERE id IN ('00000000-0000-0000-0000-000000000604', '00000000-0000-0000-0000-000000000605')
        """.trimIndent(),
    ) + super.cleanupStatements()

    @Test
    fun `should create active invitation and transition entry to INVITED when invited`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "00000000-0000-0000-0000-000000000602",
            ),
        )

        val invitation =
            requireNotNull(invitationRepository.findActiveByWaitlistEntryId("00000000-0000-0000-0000-000000000602")) {
                "Expected an active invitation for entry-test-1"
            }
        assertEquals(WaitlistInvitationStatus.ACTIVE, invitation.status)

        val entryStatus = databaseClient.sql(
            "SELECT status FROM waitlist_entries WHERE id = '00000000-0000-0000-0000-000000000602'",
        )
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals("INVITED", entryStatus)
    }

    @Test
    fun `should persist audit event when invite succeeds`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "00000000-0000-0000-0000-000000000602",
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
    fun `should reject a second active invitation at the database level`() = runTest {
        insertActiveInvitation(entryId = "00000000-0000-0000-0000-000000000603", tokenHash = "token-hash-1")

        val error = try {
            insertActiveInvitation(entryId = "00000000-0000-0000-0000-000000000603", tokenHash = "token-hash-2")
            null
        } catch (e: Exception) {
            e
        }

        assertNotNull(error, "Expected the second active invitation insert to fail")
        val mentionsIndex = generateSequence(error as Throwable) { it.cause }
            .any { it.message?.contains("uq_waitlist_invitations_one_active") == true }
        assertTrue(mentionsIndex, "Expected unique index violation, got: ${error.message}")

        val activeCount = databaseClient.sql(
            """
            SELECT COUNT(*) FROM waitlist_invitations
            WHERE waitlist_entry_id = '00000000-0000-0000-0000-000000000603' AND status = 'ACTIVE'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, activeCount, "Only one active invitation should exist per entry")
    }

    private suspend fun insertActiveInvitation(entryId: String, tokenHash: String) {
        databaseClient.sql(
            """
            INSERT INTO waitlist_invitations
              (id, waitlist_entry_id, token_hash, status, issued_at, expires_at, created_by, delivery_status)
            VALUES
              (:id, :entryId, :tokenHash, 'ACTIVE', NOW(), NOW() + INTERVAL '7 days', :createdBy, 'PENDING')
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("entryId", UUID.fromString(entryId))
            .bind("tokenHash", tokenHash)
            .bind("createdBy", operatorId)
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `should write neither invitation nor audit when the direct workspace lookup misses`() = runTest {
        val email = "direct-unknown-tx@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()

        assertThrows<WorkspaceNotFoundException> {
            createDirectInvitationHandler.handle(
                CreateInvitationCommand(
                    operatorPrincipalId = operatorId,
                    operatorRoles = operatorRoles,
                    email = email,
                    target = InvitationTarget.EXISTING_WORKSPACE,
                    workspaceId = "00000000-0000-0000-0000-000000000699",
                ),
            )
        }

        val invitationCount = databaseClient.sql(
            "SELECT COUNT(*) FROM invitations WHERE invited_email_normalized = :email",
        )
            .bind("email", email)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, invitationCount)

        val auditCount = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'INVITATION_CREATED'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, auditCount)

        val notificationCount = databaseClient.sql(
            "SELECT COUNT(*) FROM notifications WHERE recipient = :email",
        )
            .bind("email", email)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, notificationCount)
        assertEquals(0, recordingDispatcher.calls.size)
    }

    @Test
    fun `should persist an active new-workspace invitation with audit when created`() = runTest {
        val email = "direct-new-workspace-tx@example.com"

        val result = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.NEW_WORKSPACE,
                workspaceId = null,
            ),
        )

        val status = databaseClient.sql(
            "SELECT status FROM invitations WHERE id = :id",
        )
            .bind("id", result.invitationId)
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals("ACTIVE", status)

        val auditCount = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'INVITATION_CREATED'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(1L, auditCount)
    }

    @Test
    fun `should dispatch once when new-workspace invitation commits`() = runTest {
        val email = "direct-live-new@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()

        val result = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.NEW_WORKSPACE,
                workspaceId = null,
            ),
        )

        val key = IdempotencyKey("invitation:${result.invitationId}:initial")
        val notification = awaitNotification(key)
        assertNotNull(notification)
        assertEquals(NotificationStatus.SENT, requireNotNull(notification).status)
        assertEquals(email, requireNotNull(notification).recipient.value)
        assertEquals(1, recordingDispatcher.calls.size)

        val invitationStatus = databaseClient.sql(
            "SELECT status FROM invitations WHERE id = :id",
        )
            .bind("id", result.invitationId)
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals("ACTIVE", invitationStatus)
    }

    @Test
    fun `should dispatch once with resolved name when existing-workspace invitation commits`() = runTest {
        val email = "direct-live-existing@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()
        seedLiveWorkspace("00000000-0000-0000-0000-000000000604", "Live Workspace")

        val result = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.EXISTING_WORKSPACE,
                workspaceId = "00000000-0000-0000-0000-000000000604",
            ),
        )

        val key = IdempotencyKey("invitation:${result.invitationId}:initial")
        val notification = awaitNotification(key)
        assertNotNull(notification)
        assertEquals(NotificationStatus.SENT, requireNotNull(notification).status)
        assertEquals("Live Workspace", requireNotNull(notification).payload.variables["workspaceName"])
        assertEquals(1, recordingDispatcher.calls.size)
    }

    @Test
    fun `should dispatch distinct resend deliveries when existing invitation is resent twice`() = runTest {
        val email = "direct-live-resend@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()
        seedLiveWorkspace("00000000-0000-0000-0000-000000000605", "Resend Workspace")

        val created = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.EXISTING_WORKSPACE,
                workspaceId = "00000000-0000-0000-0000-000000000605",
            ),
        )
        awaitNotification(IdempotencyKey("invitation:${created.invitationId}:initial"))

        resendDirectInvitationHandler.handle(
            ResendInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                invitationId = created.invitationId,
            ),
        )
        resendDirectInvitationHandler.handle(
            ResendInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                invitationId = created.invitationId,
            ),
        )

        val keys = awaitNotificationKeys(email, 3)
        assertEquals(3, keys.size)
        assertEquals(3, keys.toSet().size)
        assertEquals(1, keys.count { it == "invitation:${created.invitationId}:initial" })
        assertEquals(2, keys.count { it.startsWith("invitation:${created.invitationId}:resend:") })
        awaitDispatcherCalls(3)
        assertEquals(3, recordingDispatcher.calls.size)
    }

    @Test
    fun `should dispatch distinct resend delivery when new-workspace invitation is resent`() = runTest {
        val email = "direct-live-new-resend@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()

        val created = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.NEW_WORKSPACE,
                workspaceId = null,
            ),
        )
        awaitNotification(IdempotencyKey("invitation:${created.invitationId}:initial"))

        resendDirectInvitationHandler.handle(
            ResendInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                invitationId = created.invitationId,
            ),
        )

        val keys = awaitNotificationKeys(email, 2)
        assertEquals(2, keys.size)
        assertEquals(2, keys.toSet().size)
        awaitDispatcherCalls(2)
        assertEquals(2, recordingDispatcher.calls.size)
    }

    @Test
    fun `should roll back invitation and audit without dispatch when publication fails`() = runTest {
        val email = "direct-rollback-live@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()
        toggleablePublisher.failOnce()

        assertThrows<IllegalStateException> {
            createDirectInvitationHandler.handle(
                CreateInvitationCommand(
                    operatorPrincipalId = operatorId,
                    operatorRoles = operatorRoles,
                    email = email,
                    target = InvitationTarget.NEW_WORKSPACE,
                    workspaceId = null,
                ),
            )
        }

        val invitationCount = databaseClient.sql(
            "SELECT COUNT(*) FROM invitations WHERE invited_email_normalized = :email",
        )
            .bind("email", email)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, invitationCount)

        val auditCount = databaseClient.sql(
            "SELECT COUNT(*) FROM platform_admin_audit_events WHERE action = 'INVITATION_CREATED'",
        )
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, auditCount)

        val notificationCount = databaseClient.sql(
            "SELECT COUNT(*) FROM notifications WHERE recipient = :email",
        )
            .bind("email", email)
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
        assertEquals(0L, notificationCount)
        assertEquals(0, recordingDispatcher.calls.size)
    }

    @Test
    fun `should persist FAILED notification while invitation stays ACTIVE and reuse FAILED on replay`() = runTest {
        val email = "direct-failed-live@example.com"
        recordingDispatcher.reset()
        toggleablePublisher.reset()
        recordingDispatcher.shouldFail = true

        val result = createDirectInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                email = email,
                target = InvitationTarget.NEW_WORKSPACE,
                workspaceId = null,
            ),
        )

        val key = IdempotencyKey("invitation:${result.invitationId}:initial")
        val failed = awaitNotification(key)
        assertNotNull(failed)
        assertEquals(NotificationStatus.FAILED, requireNotNull(failed).status)
        assertNotNull(requireNotNull(failed).errorMessage)
        assertEquals(1, recordingDispatcher.calls.size)

        val invitationStatus = databaseClient.sql(
            "SELECT status FROM invitations WHERE id = :id",
        )
            .bind("id", result.invitationId)
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals("ACTIVE", invitationStatus)

        recordingDispatcher.shouldFail = false
        invitationEmailConsumer.onInvitationIssued(
            InvitationIssued(
                invitationId = result.invitationId,
                recipientEmail = email,
                workspaceName = com.profiletailors.notifications.domain.InvitationEmail.NEW_WORKSPACE_COPY_EN,
                target = InvitationTarget.NEW_WORKSPACE,
                locale = null,
                rawToken = "replay-token-live",
            ),
        )

        assertEquals(1, recordingDispatcher.calls.size)
        val replayed = notificationRepository.findByIdempotencyKey(key)
        assertNotNull(replayed)
        assertEquals(NotificationStatus.FAILED, requireNotNull(replayed).status)
    }

    @Test
    fun `should revoke active invitation and transition entry to CANCELLED when cancelled`() = runTest {
        inviteHandler.handle(
            InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "00000000-0000-0000-0000-000000000602",
            ),
        )

        cancelHandler.handle(
            CancelWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = "00000000-0000-0000-0000-000000000602",
                reason = "spam account",
                expectedVersion = 1,
            ),
        )

        assertNull(invitationRepository.findActiveByWaitlistEntryId("00000000-0000-0000-0000-000000000602"))

        val entryStatus = databaseClient.sql(
            "SELECT status FROM waitlist_entries WHERE id = '00000000-0000-0000-0000-000000000602'",
        )
            .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
            .one()
            .awaitSingle()
        assertEquals("CANCELLED", entryStatus)
    }

    private suspend fun seedLiveWorkspace(id: String, name: String) {
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES (:id, :name, 'ACTIVE', NULL) ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", UUID.fromString(id))
            .bind("name", name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun awaitNotification(key: IdempotencyKey): com.profiletailors.notifications.domain.Notification? {
        var attempts = 0
        while (attempts < 50) {
            val found = notificationRepository.findByIdempotencyKey(key)
            if (found != null && found.status != NotificationStatus.PENDING) return found
            delay(100)
            attempts += 1
        }
        return notificationRepository.findByIdempotencyKey(key)
    }

    private suspend fun awaitNotificationKeys(email: String, expected: Int): List<String> {
        var keys: List<String> = emptyList()
        var attempts = 0
        while (attempts < 50) {
            keys = databaseClient.sql(
                "SELECT idempotency_key FROM notifications WHERE recipient = :email ORDER BY created_at",
            )
                .bind("email", email)
                .map { row, _ -> requireNotNull(row.get("idempotency_key", String::class.java)) }
                .all()
                .collectList()
                .awaitSingle()
            if (keys.size >= expected) return keys
            delay(100)
            attempts += 1
        }
        return keys
    }

    private suspend fun awaitDispatcherCalls(expected: Int) {
        var attempts = 0
        while (attempts < 50) {
            if (recordingDispatcher.calls.size >= expected) return
            delay(100)
            attempts += 1
        }
    }
}

@TestConfiguration
class InvitationTransactionTestConfiguration {
    @Bean
    @Primary
    fun recordingDispatcher(): RecordingInvitationEmailDispatcher = RecordingInvitationEmailDispatcher()

    @Bean
    @Primary
    fun toggleablePublisher(
        transactionalEventPublisher: org.springframework.transaction.reactive.TransactionalEventPublisher,
    ): ToggleableInvitationEventPublisher = ToggleableInvitationEventPublisher(transactionalEventPublisher)
}

class RecordingInvitationEmailDispatcher : EmailDispatcher {
    val calls: CopyOnWriteArrayList<Pair<String, RenderedEmail>> = CopyOnWriteArrayList()

    @Volatile
    var shouldFail: Boolean = false

    override suspend fun dispatch(to: String, email: RenderedEmail): EmailDispatchResult {
        calls.add(to to email)
        return if (shouldFail) {
            EmailDispatchResult.Failure("injected provider failure")
        } else {
            EmailDispatchResult.Success
        }
    }

    fun reset() {
        calls.clear()
        shouldFail = false
    }
}

class ToggleableInvitationEventPublisher(
    private val transactionalEventPublisher: org.springframework.transaction.reactive.TransactionalEventPublisher,
) : InvitationEventPublisher {
    @Volatile
    private var failNext: Boolean = false

    override suspend fun publish(event: DomainEvent) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("injected publish failure")
        }
        transactionalEventPublisher.publishEvent { context ->
            org.springframework.context.PayloadApplicationEvent(context, event)
        }.awaitSingleOrNull()
    }

    fun failOnce() {
        failNext = true
    }

    fun reset() {
        failNext = false
    }
}
