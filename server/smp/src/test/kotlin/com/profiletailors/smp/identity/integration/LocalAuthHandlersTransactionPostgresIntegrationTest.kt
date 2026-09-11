package com.profiletailors.smp.identity.integration

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import com.profiletailors.smp.identity.application.InvitationRegistrationContext
import com.profiletailors.smp.identity.application.InvitationRegistrationGateway
import com.profiletailors.smp.identity.application.InvitationRegistrationResult
import com.profiletailors.smp.identity.application.IssuedAccessToken
import com.profiletailors.smp.identity.application.LocalJwtIssuer
import com.profiletailors.smp.identity.application.RegisterUserCommand
import com.profiletailors.smp.identity.application.RegisterUserHandler
import com.profiletailors.smp.identity.application.ResendVerificationCommand
import com.profiletailors.smp.identity.application.ResendVerificationHandler
import com.profiletailors.smp.identity.application.UserAlreadyExistsException
import com.profiletailors.smp.identity.application.VerifyEmailCommand
import com.profiletailors.smp.identity.application.VerifyEmailHandler
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.infrastructure.R2dbcIdentityRegistrationGateway
import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.infrastructure.BCryptTokenHasher
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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

@Tag("postgres")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "app.identity.registration.mode=OPEN",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestStorageConfiguration::class,
    LocalAuthHandlersTransactionPostgresIntegrationTest.RegistrationFailureConfiguration::class,
)
class LocalAuthHandlersTransactionPostgresIntegrationTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var transactionManager: R2dbcTransactionManager

    @Autowired
    private lateinit var registerUserHandler: RegisterUserHandler

    private lateinit var identityRegistrationGateway: R2dbcIdentityRegistrationGateway
    private lateinit var principalIdentityLookup: R2dbcPrincipalIdentityLookup
    private lateinit var transactionRunner: AtomicTransactionRunner
    private val now: Instant = Instant.parse("2026-05-20T10:15:30Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeEach
    fun setUpRepositories() = runTest {
        cleanupTestData()
        seedPendingIdentity()
        identityRegistrationGateway = R2dbcIdentityRegistrationGateway(databaseClient)
        principalIdentityLookup = R2dbcPrincipalIdentityLookup(databaseClient)
        transactionRunner = R2dbcAtomicTransactionRunner(TransactionalOperator.create(transactionManager))
        failAfterInvitationCompletion = false
    }

    @Test
    fun `verify email rolls back token use when status update fails`() = runTest {
        val rawToken = "verify-token-193"
        val tokenHash = EmailVerificationTokenHasher.hash(rawToken)
        identityRegistrationGateway.createEmailVerificationToken(
            email = "issue193@example.com",
            tokenHash = tokenHash,
            expiresAt = now.plusSeconds(3600),
        )
        val handler = VerifyEmailHandler(
            identityRegistrationGateway = FailingEmailStatusGateway(identityRegistrationGateway),
            principalIdentityLookup = principalIdentityLookup,
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = clock,
            transactionRunner = transactionRunner,
        )

        assertThrows(InjectedEmailStatusFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(VerifyEmailCommand(rawToken))
            }
        }

        assertNull(tokenUsedAt(tokenHash))
        assertEquals(EmailStatus.PENDING.name, emailStatus("issue193@example.com"))
    }

    @Test
    fun `resend verification rolls back token invalidation when replacement token creation fails`() = runTest {
        val oldTokenHash = EmailVerificationTokenHasher.hash("old-resend-token-193")
        identityRegistrationGateway.createEmailVerificationToken(
            email = "issue193@example.com",
            tokenHash = oldTokenHash,
            expiresAt = now.plusSeconds(3600),
        )
        val eventPublisher = RecordingEventPublisher()
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = FailingTokenCreationGateway(identityRegistrationGateway),
            eventPublisher = eventPublisher,
            principalIdentityLookup = principalIdentityLookup,
            transactionRunner = transactionRunner,
        )

        assertThrows(InjectedTokenCreationFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(ResendVerificationCommand("issue193@example.com"))
            }
        }

        assertNull(tokenUsedAt(oldTokenHash))
        assertEquals(1, activeTokenCount("issue193@example.com"))
        assertEquals(emptyList<DomainEvent>(), eventPublisher.published)
    }

    @Test
    fun `registration rolls back invitation completion when a later mutation fails`() = runTest {
        val invitation = seedInvitation(
            email = "transaction-rollback@example.com",
            rawToken = "transaction-rollback-token",
            target = InvitationTarget.EXISTING_WORKSPACE,
        )
        failAfterInvitationCompletion = true

        assertThrows(InjectedInvitationCompletionFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                registerUserHandler.handle(registerCommand(invitation))
            }
        }

        assertEquals(
            1,
            countRows("SELECT COUNT(*) FROM invitations WHERE id = '${invitation.id}' AND status = 'ACTIVE'"),
        )
        assertEquals(0, countRows("SELECT COUNT(*) FROM user_identities WHERE email = '${invitation.email}'"))
        assertEquals(
            0,
            countRows("SELECT COUNT(*) FROM workspace_memberships WHERE workspace_id = '${invitation.workspaceId}'"),
        )
        assertEquals(
            0,
            countRows("SELECT COUNT(*) FROM email_verification_tokens WHERE email = '${invitation.email}'"),
        )
        assertEquals(
            0,
            countRows("SELECT COUNT(*) FROM consent_records WHERE workspace_id = '${invitation.workspaceId}'"),
        )
    }

    @Test
    fun `concurrent registration with one invitation produces one winner`() = runTest {
        val invitation = seedInvitation(
            email = "transaction-race@example.com",
            rawToken = "transaction-race-token",
            target = InvitationTarget.EXISTING_WORKSPACE,
        )

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { registerUserHandler.handle(registerCommand(invitation)) } },
                async { runCatching { registerUserHandler.handle(registerCommand(invitation)) } },
            ).awaitAll()
        }

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.exceptionOrNull() is UserAlreadyExistsException })
        val winner = outcomes.single { it.isSuccess }.getOrThrow()
        assertEquals(
            1,
            countRows(
                "SELECT COUNT(*) FROM invitations WHERE id = '${invitation.id}' AND status = 'ACCEPTED' " +
                    "AND accepted_principal_id = '${winner.tokens.principalId}'",
            ),
        )
        assertEquals(
            1,
            countRows(
                "SELECT COUNT(*) FROM workspace_memberships " +
                    "WHERE workspace_id = '${invitation.workspaceId}' " +
                    "AND principal_id = '${winner.tokens.principalId}'",
            ),
        )
        assertEquals(1, countRows("SELECT COUNT(*) FROM user_identities WHERE email = '${invitation.email}'"))
    }

    @Test
    fun `new workspace invitation provisions one resolved workspace and membership`() = runTest {
        val invitation = seedInvitation(
            email = "transaction-new-workspace@example.com",
            rawToken = "transaction-new-workspace-token",
            target = InvitationTarget.NEW_WORKSPACE,
        )

        val result = registerUserHandler.handle(registerCommand(invitation))
        val workspaceId = requireNotNull(result.tokens.workspaceId)

        assertEquals(
            1,
            countRows(
                "SELECT COUNT(*) FROM invitations WHERE id = '${invitation.id}' AND status = 'ACCEPTED' " +
                    "AND workspace_id = '$workspaceId'",
            ),
        )
        assertEquals(1, countRows("SELECT COUNT(*) FROM workspaces WHERE id = '$workspaceId'"))
        assertEquals(
            1,
            countRows(
                "SELECT COUNT(*) FROM workspace_memberships " +
                    "WHERE workspace_id = '$workspaceId' AND principal_id = '${result.tokens.principalId}'",
            ),
        )
    }

    private fun registerCommand(invitation: SeededInvitation): RegisterUserCommand = RegisterUserCommand(
        email = invitation.email,
        password = "TEST_PASSWORD_S3cr3tP@ssw0rd*123",
        username = invitation.email.substringBefore('@'),
        confirmedAgeEligibility = true,
        acceptedTermsVersion = "terms-v1.0.0",
        invitationToken = invitation.rawToken,
    )

    private suspend fun seedInvitation(email: String, rawToken: String, target: InvitationTarget): SeededInvitation {
        val invitationId = java.util.UUID.randomUUID()
        val issuerId = "transaction-issuer-${java.util.UUID.randomUUID()}"
        val workspaceId = "transaction-workspace-${java.util.UUID.randomUUID()}"
        val tokenHasher = BCryptTokenHasher()
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:issuerId, 'USER', :subject, NULL, 'transaction issuer')
            """.trimIndent(),
        )
            .bind("issuerId", issuerId)
            .bind("subject", "local:$issuerId@example.com")
            .fetch().rowsUpdated().awaitSingle()
        if (target == InvitationTarget.EXISTING_WORKSPACE) {
            databaseClient.sql(
                """
                INSERT INTO workspaces (id, name, status, icon)
                VALUES (:workspaceId, 'Transaction Workspace', 'ACTIVE', NULL)
                """.trimIndent(),
            )
                .bind("workspaceId", workspaceId)
                .fetch().rowsUpdated().awaitSingle()
        }
        val workspaceExpression = if (target == InvitationTarget.EXISTING_WORKSPACE) ":workspaceId" else "NULL"
        databaseClient.sql(
            """
            INSERT INTO invitations (
                id, source, source_reference_id, target, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at
            ) VALUES (
                :id, 'DIRECT', NULL, '${target.name}', $workspaceExpression, :email,
                :candidateKey, :tokenHash, 'ACTIVE', :issuerId, :createdAt, :expiresAt
            )
            """.trimIndent(),
        )
            .bind("id", invitationId)
            .bind("email", email)
            .bind("candidateKey", tokenHasher.candidateKey(rawToken))
            .bind("tokenHash", tokenHasher.hash(rawToken))
            .bind("issuerId", issuerId)
            .bind("createdAt", now)
            .bind("expiresAt", Instant.parse("2099-01-01T00:00:00Z"))
            .let { spec ->
                if (target == InvitationTarget.EXISTING_WORKSPACE) {
                    spec.bind("workspaceId", workspaceId)
                } else {
                    spec
                }
            }
            .fetch().rowsUpdated().awaitSingle()
        return SeededInvitation(invitationId.toString(), email, rawToken, workspaceId)
    }

    private data class SeededInvitation(
        val id: String,
        val email: String,
        val rawToken: String,
        val workspaceId: String,
    )

    private suspend fun countRows(sql: String): Long = databaseClient.sql(sql)
        .map { row, _ -> (row.get(0) as Number).toLong() }
        .one()
        .awaitSingle()

    private suspend fun cleanupTestData() {
        databaseClient.sql("DELETE FROM email_verification_tokens WHERE email = 'issue193@example.com'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM user_identities WHERE principal_id = 'issue-193-principal'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM principals WHERE id = 'issue-193-principal'")
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPendingIdentity() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('issue-193-principal', 'USER', 'local:issue193@example.com', NULL, 'issue193')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username, email_status)
            VALUES ('issue-193-principal', 'issue193@example.com', 'issue193', 'PENDING')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun tokenUsedAt(tokenHash: String): Instant? = databaseClient.sql(
        "SELECT used_at FROM email_verification_tokens WHERE token_hash = :tokenHash",
    )
        .bind("tokenHash", tokenHash)
        .fetch()
        .one()
        .awaitSingleOrNull()
        ?.get("used_at") as Instant?

    private suspend fun emailStatus(email: String): String? = databaseClient.sql(
        "SELECT email_status FROM user_identities WHERE email = :email",
    )
        .bind("email", email)
        .map { row, _ -> requireNotNull(row.get("email_status", String::class.java)) }
        .one()
        .awaitSingleOrNull()

    private suspend fun activeTokenCount(email: String): Int = databaseClient.sql(
        "SELECT COUNT(*) AS token_count FROM email_verification_tokens WHERE email = :email AND used_at IS NULL",
    )
        .bind("email", email)
        .map { row, _ -> (row.get("token_count", Number::class.java) ?: 0).toInt() }
        .one()
        .awaitSingle()

    private fun fakeRefreshLifecycleService(): RefreshSessionLifecycleService = RefreshSessionLifecycleService(
        refreshSessionGateway = FakeRefreshSessionGateway(),
        refreshSessionTokenService = object : RefreshSessionTokenService() {
            override fun issue(): RefreshSessionToken = RefreshSessionToken("refresh-lookup", "refresh-secret")
        },
        properties = RefreshSessionProperties(
            cookieName = "pt_refresh",
            cookiePath = "/api/auth",
            sameSite = "Lax",
            secure = false,
            ttlSeconds = 604_800,
        ),
        clock = clock,
    )

    private class InjectedEmailStatusFailure : RuntimeException("Injected email status update failure")

    private class InjectedTokenCreationFailure : RuntimeException("Injected token creation failure")

    private class InjectedInvitationCompletionFailure :
        RuntimeException("Injected invitation completion failure")

    @TestConfiguration
    class RegistrationFailureConfiguration {
        @Bean
        @Primary
        fun invitationRegistrationGateway(
            @Qualifier("invitationRegistrationGatewayAdapter") delegate: InvitationRegistrationGateway,
        ): InvitationRegistrationGateway = object : InvitationRegistrationGateway by delegate {
            override suspend fun complete(
                context: InvitationRegistrationContext,
                rawToken: String,
                principalId: String,
                displayName: String,
            ): InvitationRegistrationResult {
                val result = delegate.complete(context, rawToken, principalId, displayName)
                if (failAfterInvitationCompletion) {
                    throw InjectedInvitationCompletionFailure()
                }
                return result
            }
        }
    }

    private class FailingEmailStatusGateway(private val delegate: IdentityRegistrationGateway) :
        IdentityRegistrationGateway by delegate {
        override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus): Unit =
            throw InjectedEmailStatusFailure()
    }

    private class FailingTokenCreationGateway(private val delegate: IdentityRegistrationGateway) :
        IdentityRegistrationGateway by delegate {
        override suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant): Unit =
            throw InjectedTokenCreationFailure()
    }

    private class FakeLocalJwtIssuer : LocalJwtIssuer {
        override fun issue(
            principalId: String,
            subject: String,
            email: String,
            username: String?,
            emailStatus: EmailStatus,
            issuedAt: Instant,
        ): IssuedAccessToken = IssuedAccessToken("token-for-$email", 900)
    }

    private class FakeRefreshSessionGateway : RefreshSessionGateway {
        override suspend fun create(
            principalId: String,
            refreshToken: RefreshSessionToken,
            expiresAt: Instant,
        ): CreatedRefreshSession = CreatedRefreshSession(
            id = "refresh-session-193",
            principalId = principalId,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
        )

        override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession =
            ActiveRefreshSession(
                id = "refresh-session-193",
                principalId = "issue-193-principal",
                lookupKey = refreshToken.lookupKey,
                tokenVerifier = "verifier",
                expiresAt = now.plusSeconds(3600),
                createdAt = now,
                lastUsedAt = null,
            )

        override suspend fun rotate(
            currentSessionId: String,
            replacementToken: RefreshSessionToken,
            expiresAt: Instant,
            now: Instant,
        ): CreatedRefreshSession = CreatedRefreshSession(
            id = "refresh-session-194",
            principalId = "issue-193-principal",
            refreshToken = replacementToken,
            expiresAt = expiresAt,
        )

        override suspend fun revoke(currentSessionId: String, now: Instant) = Unit
    }

    private class RecordingEventPublisher : EventPublisher<DomainEvent> {
        val published = mutableListOf<DomainEvent>()

        override suspend fun publish(event: DomainEvent) {
            published += event
        }
    }

    companion object {
        @Volatile
        private var failAfterInvitationCompletion: Boolean = false

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("identity_transactions_postgres")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl =
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(
                    PostgreSQLContainer.POSTGRESQL_PORT,
                )}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
