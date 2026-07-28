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
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PasswordResetToken
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertEquals(INVALID_RESET_TOKEN_DETAIL, e.message)
        }

        assertEquals(0, refreshSvc.revokeAllCalls)
        assertEquals(0, tokenRepository.consumeCalls)
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
            assertTrue(e is InvalidPasswordResetTokenException)
            assertEquals(INVALID_RESET_TOKEN_DETAIL, e.message)
        }

        assertEquals(0, refreshSvc.revokeAllCalls)
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
            assertTrue(e is InvalidPasswordResetTokenException)
            assertEquals(INVALID_RESET_TOKEN_DETAIL, e.message)
        }

        assertEquals(0, refreshSvc.revokeAllCalls)
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
            consumeResult = false,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        try {
            handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))
            throw AssertionError("Expected InvalidPasswordResetTokenException")
        } catch (e: InvalidPasswordResetTokenException) {
            assertEquals(INVALID_RESET_TOKEN_DETAIL, e.message)
        }

        assertEquals(1, tokenRepository.consumeCalls)
        assertEquals(0, refreshSvc.revokeAllCalls)
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
            consumeResult = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val result = handler.handle(
            ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"),
        )

        assertTrue(result.passwordChanged)
        assertEquals(1, tokenRepository.consumeCalls)
        assertEquals(PasswordResetTokenHasher.hash("raw-token"), tokenRepository.lastConsumedToken)
        assertEquals(1, refreshSvc.revokeAllCalls)
        assertEquals("user-1", refreshSvc.lastRevokedPrincipalId)
        assertEquals("hashed:NewPassword123!", tokenRepository.lastConsumedNewHash)
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

        assertEquals(PasswordResetTokenHasher.hash("raw-token"), tokenRepository.lastLookedUpHash)
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
            assertEquals("Password does not meet policy requirements.", e.message)
        }

        assertEquals(0, tokenRepository.consumeCalls)
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
            assertEquals("Password does not meet policy requirements.", e.message)
        }

        assertEquals(0, tokenRepository.consumeCalls)
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
            consumeResult = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val maxPassword = "x".repeat(128)
        val result = handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = maxPassword))

        assertTrue(result.passwordChanged)
        assertEquals(1, tokenRepository.consumeCalls)
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
            assertEquals("Password recovery is disabled.", e.message)
        }

        assertEquals(0, tokenRepository.consumeCalls)
        assertEquals(0, refreshSvc.revokeAllCalls)
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
            consumeResult = true,
        )
        val refreshSvc = RecordingRefreshSessionLifecycleService()
        val handler = newHandler(tokenRepository, refreshSvc)

        val result = handler.handle(ResetPasswordCommand(token = "raw-token", newPassword = "NewPassword123!"))

        assertTrue(result.passwordChanged)
        assertEquals(false, hasField(result, "accessToken"))
        assertEquals(false, hasField(result, "refreshToken"))
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
    ): ResetPasswordHandler = ResetPasswordHandler(
        passwordResetTokenRepository = tokenRepository,
        passwordHasher = passwordHasher,
        refreshSessionLifecycleService = refreshSessionLifecycleService,
        transactionRunner = NoopAtomicTransactionRunner,
        clock = fixedClock,
        passwordRecoveryEnabled = { enabled },
    )

    private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class FakePasswordResetTokenRepository(
        private val stored: PasswordResetToken? = null,
        private val consumeResult: Boolean = false,
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

        override suspend fun consumeAndUpdatePassword(
            tokenHash: String,
            now: Instant,
            newPasswordHash: String,
        ): Boolean {
            consumeCalls += 1
            lastConsumedToken = tokenHash
            lastConsumedNewHash = newPasswordHash
            return consumeResult
        }
    }

    /**
     * Wraps the real [RefreshSessionLifecycleService] so we can observe
     * invocations of `revokeAllForPrincipal` while still letting the existing
     * gateway contract flow through. The wrapping subclass is `open` is not
     * an option because the service is `final`, so we instead forward through
     * a recording gateway that captures the call.
     */
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
}
