package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.identity.application.INVALID_RESET_TOKEN_DETAIL
import com.profiletailors.smp.identity.application.PasswordResetCredentialMissingException
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PasswordResetToken
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ResetPasswordHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-27T12:15:00Z"), ZoneOffset.UTC)
    private val passwordHasher = object : PasswordHasher {
        override val algorithm: String = "fake"
        override fun hash(rawPassword: String): String = "hashed:$rawPassword"
        override fun matches(rawPassword: String, passwordHash: String): Boolean = passwordHash == "hashed:$rawPassword"
    }

    @Test
    fun `throws InvalidPasswordResetTokenException when token hash is unknown`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository()
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "missing-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected InvalidPasswordResetTokenException")
        } catch (e: InvalidPasswordResetTokenException) {
            e.message shouldBe INVALID_RESET_TOKEN_DETAIL
        }

        refreshSvc.revokeAllCalls shouldBe 0
        tokenRepository.consumeCalls shouldBe 0
    }

    @Test
    fun `throws ExpiredPasswordResetTokenException when token has expired`() = runTest {
        val expired = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = "hash",
            requestedAt = Instant.parse("2026-07-27T11:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T11:30:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(stored = expired)
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected ExpiredPasswordResetTokenException")
        } catch (e: ExpiredPasswordResetTokenException) {
            e.shouldBeInstanceOf<InvalidPasswordResetTokenException>()
            e.message shouldBe INVALID_RESET_TOKEN_DETAIL
        }

        refreshSvc.revokeAllCalls shouldBe 0
    }

    @Test
    fun `throws UsedPasswordResetTokenException when token was already consumed`() = runTest {
        val used = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = "hash",
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
            usedAt = Instant.parse("2026-07-27T12:05:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(stored = used)
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected UsedPasswordResetTokenException")
        } catch (e: UsedPasswordResetTokenException) {
            e.shouldBeInstanceOf<InvalidPasswordResetTokenException>()
            e.message shouldBe INVALID_RESET_TOKEN_DETAIL
        }

        refreshSvc.revokeAllCalls shouldBe 0
    }

    @Test
    fun `consumeAndUpdatePassword rolls back when the password update fails`() = runTest {
        val stored = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = PasswordResetTokenHasher.hash("raw-token"),
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(
            stored = stored,
            consumeFailsWithCredentialMissing = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected InvalidPasswordResetTokenException")
        } catch (e: InvalidPasswordResetTokenException) {
            e.message shouldBe INVALID_RESET_TOKEN_DETAIL
        }

        tokenRepository.consumeCalls shouldBe 1
        refreshSvc.revokeAllCalls shouldBe 0
    }

    @Test
    fun `happy path consumes the token and revokes all refresh sessions`() = runTest {
        val stored = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = PasswordResetTokenHasher.hash("raw-token"),
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(
            stored = stored,
            consumeSucceeds = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val result = handler.handle(
            ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"),
        )

        result.passwordChanged shouldBe true
        tokenRepository.consumeCalls shouldBe 1
        tokenRepository.lastConsumedToken shouldBe PasswordResetTokenHasher.hash("raw-token")
        refreshSvc.revokeAllCalls shouldBe 1
        refreshSvc.lastRevokedPrincipalId shouldBe "user-1"
        tokenRepository.lastConsumedNewHash shouldBe "hashed:NewPassword123!"
    }

    @Test
    fun `successful reset emits a completed audit event only after the atomic mutation`() = runTest {
        val stored = validStoredToken()
        val tokenRepository = FakePasswordResetTokenRepository(stored = stored, consumeSucceeds = true)
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val calls = mutableListOf<String>()
        val transactionRunner = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
                calls += "transaction-started"
                return block().also { calls += "transaction-committed" }
            }
        }
        val auditPort = PasswordResetAuditPort { event ->
            calls += "audit"
            event.principalId shouldBe "user-1"
            event.occurredAt shouldBe fixedClock.instant()
        }
        val handler = newHandler(
            tokenRepository = tokenRepository,
            refreshSessionLifecycleService = refreshSvc,
            transactionRunner = transactionRunner,
            auditPort = auditPort,
        )

        val result = handler.handle(
            ResetPasswordCommand(token = RAW_TOKEN, newPassword = NEW_PASSWORD),
        )

        result.passwordChanged shouldBe true
        calls shouldBe listOf("transaction-started", "transaction-committed", "audit")
    }

    @Test
    fun `audit sink failure cannot turn a committed reset into a failure`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(stored = validStoredToken(), consumeSucceeds = true)
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        var committed = false
        val transactionRunner = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block().also { committed = true }
        }
        val handler = newHandler(
            tokenRepository = tokenRepository,
            refreshSessionLifecycleService = refreshSvc,
            transactionRunner = transactionRunner,
            auditPort = PasswordResetAuditPort {
                throw org.springframework.dao.DataAccessResourceFailureException("audit sink unavailable")
            },
        )

        val result = handler.handle(
            ResetPasswordCommand(token = RAW_TOKEN, newPassword = NEW_PASSWORD),
        )

        result.passwordChanged shouldBe true
        committed shouldBe true
        tokenRepository.consumeCalls shouldBe 1
        refreshSvc.revokeAllCalls shouldBe 1
    }

    @Test
    fun `hashes the raw token before the lookup`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository(stored = null)
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
        } catch (_: InvalidPasswordResetTokenException) {
            // expected
        }

        tokenRepository.lastLookedUpHash shouldBe PasswordResetTokenHasher.hash("raw-token")
    }

    @Test
    fun `rejects password shorter than the minimum length`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository()
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "short"))
            throw AssertionError("Expected PasswordRecoveryPasswordException")
        } catch (e: PasswordRecoveryPasswordException) {
            e.message shouldBe "Password does not meet policy requirements."
        }

        tokenRepository.consumeCalls shouldBe 0
    }

    // SEC-009: ASVS L2 V2.1.1 requires password >= 12 chars
    @Test
    fun `rejects password of exactly 11 characters (below ASVS L2 minimum)`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository()
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "Ab1defghijk"))
            throw AssertionError("Expected PasswordRecoveryPasswordException")
        } catch (e: PasswordRecoveryPasswordException) {
            e.message shouldBe "Password does not meet policy requirements."
        }

        tokenRepository.consumeCalls shouldBe 0
    }

    @Test
    fun `rejects password longer than the maximum length`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository()
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val tooLong = "x".repeat(129)
        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = tooLong))
            throw AssertionError("Expected PasswordRecoveryPasswordException")
        } catch (e: PasswordRecoveryPasswordException) {
            e.message shouldBe "Password does not meet policy requirements."
        }

        tokenRepository.consumeCalls shouldBe 0
    }

    @Test
    fun `accepts a password at the maximum length`() = runTest {
        val stored = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = PasswordResetTokenHasher.hash("raw-token"),
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(
            stored = stored,
            consumeSucceeds = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val maxPassword = "x".repeat(128)
        val result = handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = maxPassword))

        result.passwordChanged shouldBe true
        tokenRepository.consumeCalls shouldBe 1
    }

    @Test
    fun `throws PasswordRecoveryDisabledException when password recovery is disabled`() = runTest {
        val tokenRepository = FakePasswordResetTokenRepository()
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(
            tokenRepository = tokenRepository,
            refreshSessionLifecycleService = refreshSvc,
            enabled = false,
        )

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected PasswordRecoveryDisabledException")
        } catch (e: PasswordRecoveryDisabledException) {
            e.message shouldBe "Password recovery is disabled."
        }

        tokenRepository.consumeCalls shouldBe 0
        refreshSvc.revokeAllCalls shouldBe 0
    }

    @Test
    fun `does not issue a session token after a successful reset`() = runTest {
        val stored = PasswordResetToken(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            principalId = "user-1",
            tokenHash = PasswordResetTokenHasher.hash("raw-token"),
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        val tokenRepository = FakePasswordResetTokenRepository(
            stored = stored,
            consumeSucceeds = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val result = handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))

        result.passwordChanged shouldBe true
        hasField(result, "accessToken") shouldBe false
        hasField(result, "refreshToken") shouldBe false
    }

    private fun hasField(instance: Any, fieldName: String): Boolean = try {
        instance::class.members.first { it.name == fieldName }
        true
    } catch (_: NoSuchElementException) {
        false
    }

    private fun newHandler(
        tokenRepository: PasswordResetTokenRepository,
        refreshSessionLifecycleService: RefreshSessionLifecycleService,
        enabled: Boolean = true,
        transactionRunner: AtomicTransactionRunner = NoopAtomicTransactionRunner,
        auditPort: PasswordResetAuditPort = PasswordResetAuditPort { },
    ): ResetPasswordHandler = ResetPasswordHandler(
        passwordResetTokenRepository = tokenRepository,
        passwordHasher = passwordHasher,
        refreshSessionLifecycleService = refreshSessionLifecycleService,
        transactionRunner = transactionRunner,
        clock = fixedClock,
        passwordRecoveryEnabled = { enabled },
        passwordResetAuditPort = auditPort,
    )

    private fun validStoredToken() = PasswordResetToken(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        principalId = "user-1",
        tokenHash = PasswordResetTokenHasher.hash(RAW_TOKEN),
        requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
        expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
    )

    private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class FakePasswordResetTokenRepository(
        private val stored: PasswordResetToken? = null,
        private val consumeSucceeds: Boolean = false,
        private val consumeFailsWithCredentialMissing: Boolean = false,
    ) : PasswordResetTokenRepository {
        var consumeCalls: Int = 0
        var lastConsumedToken: String? = null
        var lastConsumedNewHash: String? = null
        var lastLookedUpHash: String? = null

        override suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant) = Unit

        override suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant) =
            Unit

        override suspend fun findByTokenHash(tokenHash: String): PasswordResetToken? {
            lastLookedUpHash = tokenHash
            return stored
        }

        override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) {
            consumeCalls += 1
            lastConsumedToken = tokenHash
            lastConsumedNewHash = newPasswordHash
            when {
                consumeFailsWithCredentialMissing ->
                    throw PasswordResetCredentialMissingException()
                !consumeSucceeds -> throw PasswordResetCredentialMissingException()
            }
        }
    }

    private class RecordingRefreshSessionLifecycleService :
        RefreshSessionLifecycleService(
            refreshSessionGateway = RecordingRefreshSessionGateway(),
            refreshSessionTokenService = object : RefreshSessionTokenService() {
                override fun issue() = RefreshSessionToken("lookup", "secret")
            },
            properties = RefreshSessionProperties(
                cookieName = "pt_refresh",
                cookiePath = "/api/auth",
                sameSite = "Lax",
                secure = false,
                ttlSeconds = 604_800,
            ),
            clock = Clock.fixed(Instant.parse("2026-07-27T12:15:00Z"), ZoneOffset.UTC),
        ) {
        var revokeAllCalls: Int = 0
        var lastRevokedPrincipalId: String? = null

        override suspend fun revokeAllForPrincipal(principalId: String) {
            revokeAllCalls += 1
            lastRevokedPrincipalId = principalId
        }
    }

    private class RecordingRefreshSessionGateway : RefreshSessionGateway {
        override suspend fun create(
            principalId: String,
            refreshToken: RefreshSessionToken,
            expiresAt: Instant,
        ): CreatedRefreshSession = error("not used")

        override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession =
            error("not used")

        override suspend fun rotate(
            currentSessionId: String,
            replacementToken: RefreshSessionToken,
            expiresAt: Instant,
            now: Instant,
        ): CreatedRefreshSession = error("not used")

        override suspend fun revoke(currentSessionId: String, now: Instant) = Unit

        override suspend fun revokeAllForPrincipal(principalId: String, now: Instant) = Unit
    }

    private fun PrincipalIdentityFacts(principalId: String) = PrincipalIdentityFacts(
        principalId = principalId,
        principalType = PrincipalType.USER,
        subject = "local:user@example.com",
        provider = null,
        displayIdentity = "user",
        email = "user@example.com",
        username = "user",
        emailStatus = EmailStatus.VERIFIED,
    )

    private companion object {
        const val RAW_TOKEN = "raw-token-sensitive"
        const val NEW_PASSWORD = "NewPassword123!"
    }
}
