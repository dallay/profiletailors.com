package com.profiletailors.smp.identity.integration

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import com.profiletailors.smp.credentials.infrastructure.R2dbcRefreshSessionGateway
import com.profiletailors.smp.identity.application.InvalidPasswordResetTokenException
import com.profiletailors.smp.identity.application.PasswordHasher
import com.profiletailors.smp.identity.application.PasswordResetTokenHasher
import com.profiletailors.smp.identity.application.PasswordResetTokenRepository
import com.profiletailors.smp.identity.application.ResetPasswordCommand
import com.profiletailors.smp.identity.application.ResetPasswordHandler
import com.profiletailors.smp.identity.infrastructure.R2dbcPasswordResetTokenRepository
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordResetTransactionPostgresIntegrationTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val now = Instant.parse("2026-07-27T12:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var repository: PasswordResetTokenRepository
    private lateinit var transactionRunner: AtomicTransactionRunner

    @BeforeEach
    fun setUp() = runTest {
        cleanup()
        seedPrincipalCredentialAndSession()
        repository = R2dbcPasswordResetTokenRepository(databaseClient)
        transactionRunner = R2dbcAtomicTransactionRunner(
            TransactionalOperator.create(R2dbcTransactionManager(connectionFactory)),
        )
    }

    @Test
    fun `reset immediately before expiration commits through handler and repository`() = runTest {
        seedToken("before-expiration", now.plusSeconds(1))

        handler(repository, realRefreshLifecycle()).handle(
            ResetPasswordCommand("before-expiration", "NewPassword123!"),
        )

        assertEquals("hashed:NewPassword123!", passwordHash())
        assertTrue(tokenUsed(PasswordResetTokenHasher.hash("before-expiration")))
        assertEquals(0L, activeSessions())
    }

    @Test
    fun `reset exactly at expiration is rejected through handler and repository`() = runTest {
        seedToken("at-expiration", now)

        assertThrows(InvalidPasswordResetTokenException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler(repository, realRefreshLifecycle()).handle(
                    ResetPasswordCommand("at-expiration", "NewPassword123!"),
                )
            }
        }

        assertEquals("old-hash", passwordHash())
        assertFalse(tokenUsed(PasswordResetTokenHasher.hash("at-expiration")))
        assertEquals(1L, activeSessions())
    }

    @Test
    fun `password update failure rolls back persisted token consumption`() = runTest {
        seedToken("password-failure", now.plusSeconds(60))
        val failingRepository = object : PasswordResetTokenRepository by repository {
            override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) {
                repository.consumeAndUpdatePassword(tokenHash, now, newPasswordHash)
                throw InjectedPasswordUpdateFailure()
            }
        }

        assertThrows(InjectedPasswordUpdateFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler(failingRepository, realRefreshLifecycle()).handle(
                    ResetPasswordCommand("password-failure", "NewPassword123!"),
                )
            }
        }

        assertEquals("old-hash", passwordHash())
        assertFalse(tokenUsed(PasswordResetTokenHasher.hash("password-failure")))
        assertEquals(1L, activeSessions())
    }

    @Test
    fun `token consumption failure rolls back persisted password update`() = runTest {
        seedToken("consume-failure", now.plusSeconds(60))
        val failingRepository = object : PasswordResetTokenRepository by repository {
            override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) {
                repository.consumeAndUpdatePassword(tokenHash, now, newPasswordHash)
                throw InjectedTokenConsumptionFailure()
            }
        }

        assertThrows(InjectedTokenConsumptionFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler(failingRepository, realRefreshLifecycle()).handle(
                    ResetPasswordCommand("consume-failure", "NewPassword123!"),
                )
            }
        }

        assertEquals("old-hash", passwordHash())
        assertFalse(tokenUsed(PasswordResetTokenHasher.hash("consume-failure")))
        assertEquals(1L, activeSessions())
    }

    @Test
    fun `session revocation failure rolls back password and token persisted state`() = runTest {
        seedToken("session-failure", now.plusSeconds(60))
        val failingLifecycle = object : RefreshSessionLifecycleService(
            R2dbcRefreshSessionGateway(databaseClient, TestRefreshTokenHasher),
            RefreshSessionTokenService(),
            refreshProperties(),
            clock,
        ) {
            override suspend fun revokeAllForPrincipal(principalId: String) {
                super.revokeAllForPrincipal(principalId)
                throw InjectedSessionRevocationFailure()
            }
        }

        assertThrows(InjectedSessionRevocationFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler(repository, failingLifecycle).handle(
                    ResetPasswordCommand("session-failure", "NewPassword123!"),
                )
            }
        }

        assertEquals("old-hash", passwordHash())
        assertFalse(tokenUsed(PasswordResetTokenHasher.hash("session-failure")))
        assertEquals(1L, activeSessions())
    }

    private fun handler(
        tokenRepository: PasswordResetTokenRepository,
        refreshLifecycle: RefreshSessionLifecycleService,
    ) = ResetPasswordHandler(
        passwordResetTokenRepository = tokenRepository,
        passwordHasher = TestPasswordHasher,
        refreshSessionLifecycleService = refreshLifecycle,
        transactionRunner = transactionRunner,
        clock = clock,
        passwordRecoveryEnabled = { true },
        passwordResetAudit = com.profiletailors.smp.identity.application.PasswordResetAudit { },
    )

    private fun realRefreshLifecycle() = RefreshSessionLifecycleService(
        R2dbcRefreshSessionGateway(databaseClient, TestRefreshTokenHasher),
        RefreshSessionTokenService(),
        refreshProperties(),
        clock,
    )

    private fun refreshProperties() = RefreshSessionProperties(
        cookieName = "pt_refresh",
        cookiePath = "/api/auth",
        sameSite = "Lax",
        secure = false,
        ttlSeconds = 604_800,
    )

    private suspend fun cleanup() {
        databaseClient.sql("DELETE FROM password_reset_tokens WHERE principal_id = 'reset-tx-user'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM refresh_sessions WHERE principal_id = 'reset-tx-user'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM local_password_credentials WHERE principal_id = 'reset-tx-user'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM user_identities WHERE principal_id = 'reset-tx-user'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM principals WHERE id = 'reset-tx-user'")
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPrincipalCredentialAndSession() {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, display_identity) " +
                "VALUES ('reset-tx-user', 'USER', 'local:reset-tx@example.com', 'reset-tx')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username) " +
                "VALUES ('reset-tx-user', 'reset-tx@example.com', 'reset-tx')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO local_password_credentials (principal_id, password_hash) " +
                "VALUES ('reset-tx-user', 'old-hash')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO refresh_sessions " +
                "(id, principal_id, lookup_key, token_verifier, status, expires_at) " +
                "VALUES ('reset-tx-session', 'reset-tx-user', 'lookup', 'verifier', 'ACTIVE', :expiresAt)",
        ).bind("expiresAt", now.plusSeconds(3600)).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedToken(rawToken: String, expiresAt: Instant) {
        repository.create(
            principalId = "reset-tx-user",
            tokenHash = PasswordResetTokenHasher.hash(rawToken),
            requestedAt = now.minusSeconds(60),
            expiresAt = expiresAt,
        )
    }

    private suspend fun passwordHash(): String = databaseClient.sql(
        "SELECT password_hash FROM local_password_credentials WHERE principal_id = 'reset-tx-user'",
    ).map { row, _ -> requireNotNull(row.get("password_hash", String::class.java)) }.one().awaitSingle()

    private suspend fun tokenUsed(tokenHash: String): Boolean = databaseClient.sql(
        "SELECT used_at IS NOT NULL AS used FROM password_reset_tokens WHERE token_hash = :tokenHash",
    ).bind("tokenHash", tokenHash)
        .map { row, _ -> requireNotNull(row.get("used", java.lang.Boolean::class.java)).booleanValue() }
        .one().awaitSingle()

    private suspend fun activeSessions(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM refresh_sessions " +
            "WHERE principal_id = 'reset-tx-user' AND status = 'ACTIVE'",
    ).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    private object TestPasswordHasher : PasswordHasher {
        override val algorithm = "test"
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, passwordHash: String) = passwordHash == hash(rawPassword)
    }

    private object TestRefreshTokenHasher : RefreshTokenHasher {
        override fun hash(secret: String) = "hashed:$secret"
        override fun matches(presentedSecret: String, storedVerifier: String) = true
    }

    private class InjectedPasswordUpdateFailure : RuntimeException("injected password update failure")
    private class InjectedTokenConsumptionFailure : RuntimeException("injected token consumption failure")
    private class InjectedSessionRevocationFailure : RuntimeException("injected session revocation failure")

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("password_reset_transactions")
    }
}
