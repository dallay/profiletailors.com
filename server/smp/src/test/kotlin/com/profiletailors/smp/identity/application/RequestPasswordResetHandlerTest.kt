package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RequestPasswordResetHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `returns accepted for unknown email without publishing any event`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(principalFacts = null)
        val credentialGateway = FakeLocalPasswordCredentialGateway()
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        val result = handler.handle(RequestPasswordResetCommand(email = "unknown@example.com"))

        assertTrue(result.accepted)
        assertEquals(0, eventPublisher.published.size)
        assertEquals(0, tokenRepository.createCalls)
        assertEquals(0, tokenRepository.invalidateCalls)
        // The email bucket MUST increment even when the email does not resolve.
        assertEquals(1, rateLimit.acquireCalls)
    }

    @Test
    fun `returns accepted for OAuth-only identity without publishing any event`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway() // no credential row
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        val result = handler.handle(RequestPasswordResetCommand(email = "user@example.com"))

        assertTrue(result.accepted)
        assertEquals(0, eventPublisher.published.size)
        assertEquals(0, tokenRepository.createCalls)
        assertEquals(0, tokenRepository.invalidateCalls)
    }

    @Test
    fun `existing and unknown identities complete post-work timing equalization`() = runTest {
        val unknownOrder = mutableListOf<String>()
        val existingOrder = mutableListOf<String>()
        val unknownHandler = newHandlerForTiming(
            principalFacts = null,
            credential = null,
            timingEqualizer = RecordingPasswordRecoveryTimingEqualizer(unknownOrder),
            order = unknownOrder,
        )
        val existingHandler = newHandlerForTiming(
            principalFacts = principalFacts("user-1"),
            credential = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
            timingEqualizer = RecordingPasswordRecoveryTimingEqualizer(existingOrder),
            order = existingOrder,
        )

        unknownHandler.handle(RequestPasswordResetCommand("missing@example.com"))
        existingHandler.handle(RequestPasswordResetCommand("user@example.com"))

        assertEquals(listOf("start", "equalize"), unknownOrder)
        assertEquals(listOf("start", "invalidate", "create", "publish", "equalize"), existingOrder)
    }

    @Test
    fun `normalizes the email before lookup`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "  USER@Example.COM  "))

        assertEquals("user@example.com", identityLookup.lastNormalizedEmail)
    }

    @Test
    fun `invalidateActiveTokens and create happen inside a transaction before publish`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val transactionRunner = RecordingAtomicTransactionRunner()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = transactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "user@example.com"))

        assertEquals(1, transactionRunner.invocations)
        assertEquals(1, tokenRepository.invalidateCalls)
        assertEquals(1, tokenRepository.createCalls)
        assertEquals(1, eventPublisher.published.size)
    }

    @Test
    fun `published event carries principalId normalized email and raw reset token`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "user@example.com"))

        val event = eventPublisher.published.first() as PasswordResetRequested
        assertEquals("user-1", event.principalId)
        assertEquals("user@example.com", event.email)
        assertNotNull(event.rawResetToken)
        assertEquals(
            PasswordResetTokenHasher.hash(event.rawResetToken),
            tokenRepository.lastCreatedHash,
        )
    }

    @Test
    fun `does not publish and does not create a token when password recovery is disabled`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { false },
        )

        try {
            handler.handle(RequestPasswordResetCommand(email = "user@example.com"))
            throw AssertionError("Expected PasswordRecoveryDisabledException")
        } catch (e: PasswordRecoveryDisabledException) {
            assertEquals("Password recovery is disabled.", e.message)
        }

        assertEquals(0, eventPublisher.published.size)
        assertEquals(0, tokenRepository.createCalls)
    }

    @Test
    fun `creates a 30 minute token aligned with the PasswordResetTokenHasher TTL`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "user@example.com"))

        val now = fixedClock.instant()
        val expiresAt = tokenRepository.lastCreatedExpiresAt
        assertEquals(now, tokenRepository.lastCreatedRequestedAt)
        assertEquals(now.plusSeconds(30 * 60), expiresAt)
    }

    @Test
    fun `event is published only after the transaction commits`() = runTest {
        val order = mutableListOf<String>()
        val tokenRepository = OrderRecordingTokenRepository(order)
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher(order)
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = transactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "user@example.com"))

        assertEquals(
            listOf("tx:start", "invalidate", "create", "tx:commit", "publish"),
            order,
        )
    }

    @Test
    fun `event is not published when the transaction rolls back`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val transactionRunner = FailingTransactionRunner()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = transactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        try {
            handler.handle(RequestPasswordResetCommand(email = "user@example.com"))
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }

        assertEquals(0, eventPublisher.published.size)
    }

    @Test
    fun `throws PasswordResetRateLimitExceededException when the email bucket is exhausted`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(
            principalFacts = principalFacts("user-1"),
        )
        val credentialGateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-1",
                email = "user@example.com",
                username = "user",
                passwordHash = "hashed",
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort().apply { admit = false }
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        try {
            handler.handle(RequestPasswordResetCommand(email = "user@example.com"))
            throw AssertionError("Expected PasswordResetRateLimitExceededException")
        } catch (e: PasswordResetRateLimitExceededException) {
            assertEquals("Authentication rate limit exceeded. Try again later.", e.message)
        }

        assertEquals(0, eventPublisher.published.size)
        assertEquals(0, tokenRepository.createCalls)
    }

    @Test
    fun `per-email bucket increments even when no account exists`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(mutableListOf())
        val identityLookup = FakePrincipalIdentityLookup(principalFacts = null)
        val credentialGateway = FakeLocalPasswordCredentialGateway()
        val eventPublisher = RecordingEventPublisher()
        val rateLimit = FakeRateLimitPort()
        val handler = RequestPasswordResetHandler(
            principalIdentityLookup = identityLookup,
            localPasswordCredentialGateway = credentialGateway,
            passwordResetTokenRepository = tokenRepository,
            transactionRunner = NoopAtomicTransactionRunner,
            eventPublisher = eventPublisher,
            rateLimit = rateLimit,
            clock = fixedClock,
            passwordRecoveryEnabled = { true },
        )

        handler.handle(RequestPasswordResetCommand(email = "unknown@example.com"))
        assertEquals(1, rateLimit.acquireCalls)
        assertEquals(
            "password-reset-request-email:unknown@example.com",
            rateLimit.lastKey,
        )
    }

    private fun newHandlerForTiming(
        principalFacts: PrincipalIdentityFacts?,
        credential: LocalPasswordCredentialRecord?,
        timingEqualizer: PasswordRecoveryTimingEqualizer,
        order: MutableList<String>,
    ) = RequestPasswordResetHandler(
        principalIdentityLookup = FakePrincipalIdentityLookup(principalFacts),
        localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(credential),
        passwordResetTokenRepository = OrderRecordingTokenRepository(order),
        transactionRunner = NoopAtomicTransactionRunner,
        eventPublisher = RecordingEventPublisher(order),
        rateLimit = FakeRateLimitPort(),
        clock = fixedClock,
        passwordRecoveryEnabled = { true },
        timingEqualizer = timingEqualizer,
    )

    private fun principalFacts(principalId: String) = PrincipalIdentityFacts(
        principalId = principalId,
        principalType = PrincipalType.USER,
        subject = "local:user@example.com",
        provider = null,
        displayIdentity = "user",
        email = "user@example.com",
        username = "user",
        emailStatus = EmailStatus.VERIFIED,
    )

    private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class RecordingAtomicTransactionRunner(private val order: MutableList<String> = mutableListOf()) :
        AtomicTransactionRunner {
        var invocations: Int = 0
            private set

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocations += 1
            order.add("tx:start")
            return try {
                block().also { order.add("tx:commit") }
            } catch (error: Throwable) {
                order.add("tx:rollback")
                throw error
            }
        }
    }

    private class FailingTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = throw IllegalStateException("boom")
    }

    private class FakePrincipalIdentityLookup(private val principalFacts: PrincipalIdentityFacts?) :
        PrincipalIdentityLookup {
        var lastNormalizedEmail: String? = null

        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = principalFacts

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? {
            lastNormalizedEmail = email
            return principalFacts
        }

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = principalFacts
    }

    private class FakeLocalPasswordCredentialGateway(private val record: LocalPasswordCredentialRecord? = null) :
        LocalPasswordCredentialGateway {
        override suspend fun create(principalId: String, passwordHash: String) = Unit

        override suspend fun updatePasswordHash(principalId: String, newPasswordHash: String) = Unit

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? =
            record?.takeIf { it.email == email }
    }

    private class RecordingEventPublisher(private val order: MutableList<String> = mutableListOf()) :
        EventPublisher<DomainEvent> {
        val published = mutableListOf<DomainEvent>()

        override suspend fun publish(event: DomainEvent) {
            order.add("publish")
            published.add(event)
        }
    }

    private class OrderRecordingTokenRepository(private val order: MutableList<String>) :
        PasswordResetTokenRepository {
        var createCalls: Int = 0
        var invalidateCalls: Int = 0
        var lastCreatedHash: String? = null
        var lastCreatedRequestedAt: Instant? = null
        var lastCreatedExpiresAt: Instant? = null

        override suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant) {
            invalidateCalls += 1
            order.add("invalidate")
        }

        override suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant) {
            createCalls += 1
            lastCreatedHash = tokenHash
            lastCreatedRequestedAt = requestedAt
            lastCreatedExpiresAt = expiresAt
            order.add("create")
        }

        override suspend fun findByTokenHash(tokenHash: String) = null

        override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) = Unit
    }

    private class FakePasswordResetTokenRepository(private val order: MutableList<String> = mutableListOf()) :
        PasswordResetTokenRepository {
        var createCalls: Int = 0
        var invalidateCalls: Int = 0
        var lastCreatedHash: String? = null
        var lastCreatedRequestedAt: Instant? = null
        var lastCreatedExpiresAt: Instant? = null

        override suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant) {
            invalidateCalls += 1
            order.add("invalidate")
        }

        override suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant) {
            createCalls += 1
            lastCreatedHash = tokenHash
            lastCreatedRequestedAt = requestedAt
            lastCreatedExpiresAt = expiresAt
            order.add("create")
        }

        override suspend fun findByTokenHash(tokenHash: String) = null

        override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) = Unit
    }

    private class RecordingPasswordRecoveryTimingEqualizer(private val order: MutableList<String>) :
        PasswordRecoveryTimingEqualizer {
        override fun markStart(): Long {
            order += "start"
            return 0L
        }

        override suspend fun equalize(startedAtNanos: Long) {
            order += "equalize"
        }
    }

    private class FakeRateLimitPort(var admit: Boolean = true) : RateLimit {
        var acquireCalls: Int = 0
        var lastKey: String? = null

        override fun tryAcquire(key: String, window: java.time.Duration, now: Instant): Boolean {
            acquireCalls += 1
            lastKey = key
            return admit
        }
    }
}
