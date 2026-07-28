package com.profiletailors.smp.identity.integration

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.RequestPasswordResetCommand
import com.profiletailors.smp.identity.application.RequestPasswordResetHandler
import com.profiletailors.smp.identity.infrastructure.R2dbcLocalPasswordCredentialGateway
import com.profiletailors.smp.identity.infrastructure.R2dbcPasswordResetTokenRepository
import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
class PasswordResetRequestTransactionPostgresIntegrationTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val now = Instant.parse("2026-07-27T12:00:00Z")
    private lateinit var transactionRunner: AtomicTransactionRunner

    @BeforeEach
    fun setUp() = runTest {
        seedPrincipalAndCredential()
        transactionRunner = R2dbcAtomicTransactionRunner(
            TransactionalOperator.create(R2dbcTransactionManager(connectionFactory)),
        )
    }

    @Test
    fun `persistence failure leaves no token and suppresses notification publication`() = runTest {
        val publisher = RecordingEventPublisher()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = R2dbcPrincipalIdentityLookup(databaseClient),
            localPasswordCredentialGateway = R2dbcLocalPasswordCredentialGateway(databaseClient),
            passwordResetTokenRepository = FailingCreateRepository(
                R2dbcPasswordResetTokenRepository(databaseClient),
            ),
            transactionRunner = transactionRunner,
            eventPublisher = publisher,
            rateLimitPort = com.profiletailors.smp.identity.infrastructure.InMemoryRateLimitAdapter(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            passwordRecoveryEnabled = { true },
        )

        assertThrows(InjectedTokenPersistenceFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(RequestPasswordResetCommand("request-tx@example.com"))
            }
        }

        assertEquals(0L, tokenCount())
        assertEquals(emptyList<DomainEvent>(), publisher.events)
    }

    private suspend fun seedPrincipalAndCredential() {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, display_identity) " +
                "VALUES ('request-tx-user', 'USER', 'local:request-tx@example.com', 'request-tx')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username) " +
                "VALUES ('request-tx-user', 'request-tx@example.com', 'request-tx')",
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO local_password_credentials (principal_id, password_hash) " +
                "VALUES ('request-tx-user', 'old-hash')",
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun tokenCount(): Long = databaseClient.sql(
        "SELECT COUNT(*) AS total FROM password_reset_tokens WHERE principal_id = 'request-tx-user'",
    ).map { row, _ -> (row.get("total") as Number).toLong() }.one().awaitSingle()

    private class FailingCreateRepository(
        private val delegate: com.profiletailors.smp.identity.application.PasswordResetTokenRepository,
    ) : com.profiletailors.smp.identity.application.PasswordResetTokenRepository by delegate {
        override suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant) {
            delegate.create(principalId, tokenHash, requestedAt, expiresAt)
            throw InjectedTokenPersistenceFailure()
        }
    }

    private class RecordingEventPublisher : EventPublisher<DomainEvent> {
        val events = mutableListOf<DomainEvent>()
        override suspend fun publish(event: DomainEvent) {
            events += event
        }
    }

    private class InjectedTokenPersistenceFailure : RuntimeException("injected token persistence failure")

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("password_reset_request_transactions")
    }
}
