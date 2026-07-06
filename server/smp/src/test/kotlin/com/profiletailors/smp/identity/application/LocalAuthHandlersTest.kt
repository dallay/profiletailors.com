package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.testfixture.CredentialGenerator
import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.identity.application.EmailVerificationTokenData
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserRegistered
import com.profiletailors.smp.identity.infrastructure.BCryptPasswordHasher
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@Suppress("LargeClass") // Pre-existing — extensive handler test coverage
class LocalAuthHandlersTest {

    private val validPassword = CredentialGenerator.generateValidPassword()

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC)
    private val refreshProperties = RefreshSessionProperties(
        cookieName = "pt_refresh",
        cookiePath = "/api/auth",
        sameSite = "Lax",
        secure = false,
        ttlSeconds = 604800,
    )

    /** BCrypt-based PasswordHasher for seeding legacy credentials in tests. */
    private fun bcryptHasher(): PasswordHasher = BCryptPasswordHasher()

    @Test
    fun `register wraps writes in transaction and defers side effects until after commit`() = runTest {
        val order = mutableListOf<String>()
        val identityRegistrationGateway = FakeIdentityRegistrationGateway(order)
        val passwordGateway = FakeLocalPasswordCredentialGateway(order = order)
        val workspaceProvisioningService = FakeWorkspaceProvisioningService(order)
        val eventPublisher = RecordingEventPublisher(order)
        val jwtIssuer = FakeLocalJwtIssuer(order)
        val refreshSvc = fakeRefreshLifecycleService(order)
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = RegisterUserHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            principalIdentityLookup = FakePrincipalIdentityLookup(),
            localPasswordCredentialGateway = passwordGateway,
            passwordHasher = FakePasswordHasher(),
            workspaceProvisioningService = workspaceProvisioningService,
            eventPublisher = eventPublisher,
            clock = fixedClock,
            localJwtIssuer = jwtIssuer,
            refreshSessionLifecycleService = refreshSvc,
            transactionRunner = transactionRunner,
        )

        handler.handle(
            RegisterUserCommand(
                email = " Yuniel@Example.com ",
                password = validPassword,
                username = " yuniel ",
            ),
        )

        assertEquals(
            listOf(
                "tx:start",
                "identity:create",
                "credential:create",
                "workspace:provision",
                "token:create",
                "tx:commit",
                "event:publish",
                "jwt:issue",
                "refresh:create",
            ),
            order,
        )
        assertEquals(1, transactionRunner.invocations)
    }

    @Test
    fun `registers user and returns session with tokens`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val principalLookup = FakePrincipalIdentityLookup()
        val passwordGateway = FakeLocalPasswordCredentialGateway()
        val passwordHasher = FakePasswordHasher()
        val eventPublisher = RecordingEventPublisher()
        val jwtIssuer = FakeLocalJwtIssuer()
        val refreshSvc = fakeRefreshLifecycleService()
        val handler = RegisterUserHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            principalIdentityLookup = principalLookup,
            localPasswordCredentialGateway = passwordGateway,
            passwordHasher = passwordHasher,
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            eventPublisher = eventPublisher,
            clock = fixedClock,
            localJwtIssuer = jwtIssuer,
            refreshSessionLifecycleService = refreshSvc,
            transactionRunner = NoopAtomicTransactionRunner,
        )

        val result = handler.handle(
            RegisterUserCommand(
                email = " Yuniel@Example.com ",
                password = validPassword,
                username = " yuniel ",
            ),
        )

        // Verify session tokens are returned
        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("yuniel@example.com", result.tokens.email)
        assertEquals("yuniel", result.tokens.username)
        assertEquals(EmailStatus.PENDING.name, result.tokens.emailStatus)
        assertNotNull(result.tokens.principalId)
        assertEquals("refresh-secret", result.refreshToken.secret)

        // Verify identity was created with PENDING status
        val createdIdentity = identityRegistrationGateway.created
        assertNotNull(createdIdentity)
        assertEquals(EmailStatus.PENDING, createdIdentity?.emailStatus)

        // Verify verification token was created
        assertNotNull(identityRegistrationGateway.createdToken)
        assertEquals("yuniel@example.com", identityRegistrationGateway.createdToken?.email)

        // Verify domain event was published
        assertEquals(1, eventPublisher.published.size)
        val event = eventPublisher.published.first() as UserRegistered
        assertEquals("yuniel@example.com", event.email)
        assertEquals("yuniel", event.username)
        assertNotNull(event.rawVerificationToken)
    }

    @Test
    fun `registers user with email local-part when username is blank`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val handler = RegisterUserHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            principalIdentityLookup = FakePrincipalIdentityLookup(),
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(),
            passwordHasher = FakePasswordHasher(),
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            eventPublisher = RecordingEventPublisher(),
            clock = fixedClock,
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            transactionRunner = NoopAtomicTransactionRunner,
        )

        val result = handler.handle(
            RegisterUserCommand(
                email = " Yuniel@Example.com ",
                password = validPassword,
                username = "   ",
            ),
        )

        assertEquals("yuniel@example.com", result.tokens.email)
        assertEquals("yuniel", result.tokens.username)
        assertEquals("yuniel", identityRegistrationGateway.created?.username)
        assertEquals("yuniel", identityRegistrationGateway.created?.displayIdentity)
    }

    @Test
    fun `rejects duplicate registration`() = runTest {
        val handler = RegisterUserHandler(
            identityRegistrationGateway = FakeIdentityRegistrationGateway(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                existingEmail = "yuniel@example.com",
            ),
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(),
            passwordHasher = FakePasswordHasher(),
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            eventPublisher = RecordingEventPublisher(),
            clock = fixedClock,
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            transactionRunner = NoopAtomicTransactionRunner,
        )

        try {
            handler.handle(RegisterUserCommand("yuniel@example.com", validPassword, "yuniel"))
            throw AssertionError("Expected UserAlreadyExistsException")
        } catch (e: UserAlreadyExistsException) {
            assertTrue(e.message?.contains("yuniel@example.com") == true)
        }
    }

    @Test
    fun `logs user in with valid password and verified email`() = runTest {
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(
                record = LocalPasswordCredentialRecord(
                    principalId = "user-1",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    passwordHash = "hashed-$validPassword",
                ),
            ),
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(LoginUserCommand("yuniel@example.com", validPassword))

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("user-1", result.tokens.principalId)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    @Test
    fun `allows login with unverified email`() = runTest {
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(
                record = LocalPasswordCredentialRecord(
                    principalId = "user-1",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    passwordHash = "hashed-$validPassword",
                ),
            ),
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(LoginUserCommand("yuniel@example.com", validPassword))

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("user-1", result.tokens.principalId)
        assertEquals(EmailStatus.PENDING.name, result.tokens.emailStatus)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    @Test
    fun `refreshes user session with verified email`() = runTest {
        val handler = RefreshUserSessionHandler(
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(RefreshUserSessionCommand("refresh-lookup.refresh-secret"))

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("user-1", result.tokens.principalId)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    @Test
    fun `allows refresh with unverified email`() = runTest {
        val handler = RefreshUserSessionHandler(
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(RefreshUserSessionCommand("refresh-lookup.refresh-secret"))

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("user-1", result.tokens.principalId)
        assertEquals(EmailStatus.PENDING.name, result.tokens.emailStatus)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    @Test
    fun `rejects invalid login`() = runTest {
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(),
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        try {
            handler.handle(LoginUserCommand("missing@example.com", validPassword))
            throw AssertionError("Expected InvalidEmailPasswordException")
        } catch (e: InvalidEmailPasswordException) {
            assertNotNull(e)
        }
    }

    // ── Algorithm-aware login + rehash + fail-closed tests (RED → GREEN) ─────

    @Test
    fun `login with bcrypt algorithm record passes and triggers argon2id rehash`() = runTest {
        val bcryptHasher = bcryptHasher()
        val rawPassword = CredentialGenerator.generateValidPassword()
        val bcryptHash = bcryptHasher.hash(rawPassword)
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-bcrypt",
                email = "bcrypt@example.com",
                username = "bcrypt-user",
                passwordHash = bcryptHash,
                passwordAlgorithm = "bcrypt",
            ),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(algorithm = "argon2id"),
            bcryptPasswordHasher = bcryptHasher,
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(LoginUserCommand("bcrypt@example.com", rawPassword))

        assertEquals("token-for-bcrypt@example.com", result.tokens.accessToken)
        // Verify rehash occurred
        assertEquals("user-bcrypt", gateway.updatedPrincipalId)
        assertNotNull(gateway.updatedHash)
        assertEquals("argon2id", gateway.updatedAlgorithm)
    }

    @Test
    fun `login with null passwordAlgorithm and bcrypt hash infers bcrypt and rehashes`() = runTest {
        val bcryptHasher = bcryptHasher()
        val rawPassword = CredentialGenerator.generateValidPassword()
        val bcryptHash = bcryptHasher.hash(rawPassword)
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-null-algo",
                email = "null-algo@example.com",
                username = "null-algo-user",
                passwordHash = bcryptHash,
                passwordAlgorithm = null,
            ),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(algorithm = "argon2id"),
            bcryptPasswordHasher = bcryptHasher,
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(LoginUserCommand("null-algo@example.com", rawPassword))

        assertEquals("token-for-null-algo@example.com", result.tokens.accessToken)
        assertEquals("user-null-algo", gateway.updatedPrincipalId)
        assertEquals("argon2id", gateway.updatedAlgorithm)
    }

    @Test
    fun `login with argon2id algorithm record does not trigger rehash`() = runTest {
        val rawPassword = CredentialGenerator.generateValidPassword()
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-argon2",
                email = "argon2@example.com",
                username = "argon2-user",
                passwordHash = "hashed-$rawPassword",
                passwordAlgorithm = "argon2id",
            ),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(
            LoginUserCommand("argon2@example.com", rawPassword),
        )

        assertEquals("token-for-argon2@example.com", result.tokens.accessToken)
        // No rehash should have occurred
        assertNull(gateway.updatedPrincipalId)
        assertNull(gateway.updatedHash)
        assertNull(gateway.updatedAlgorithm)
    }

    @Test
    fun `login with unknown password algorithm fails closed as InvalidEmailPasswordException`() = runTest {
        val rawPassword = CredentialGenerator.generateValidPassword()
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-unknown-algo",
                email = "unknown-algo@example.com",
                username = "unknown-algo-user",
                passwordHash = "hashed-$rawPassword",
                passwordAlgorithm = "scrypt",
            ),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        try {
            handler.handle(LoginUserCommand("unknown-algo@example.com", rawPassword))
            throw AssertionError("Expected InvalidEmailPasswordException")
        } catch (e: InvalidEmailPasswordException) {
            assertNotNull(e)
        }
    }

    @Test
    fun `login continues when opportunistic password upgrade fails`() = runTest {
        val bcryptHasher = bcryptHasher()
        val rawPassword = CredentialGenerator.generateValidPassword()
        val bcryptHash = bcryptHasher.hash(rawPassword)
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-upgrade-failure",
                email = "upgrade-failure@example.com",
                username = "upgrade-failure-user",
                passwordHash = bcryptHash,
                passwordAlgorithm = "bcrypt",
            ),
            updateFailure = IllegalStateException("simulated update failure"),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(algorithm = "argon2id"),
            bcryptPasswordHasher = bcryptHasher,
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(EmailStatus.VERIFIED),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        val result = handler.handle(LoginUserCommand("upgrade-failure@example.com", rawPassword))

        assertEquals("token-for-upgrade-failure@example.com", result.tokens.accessToken)
        assertEquals("user-upgrade-failure", gateway.updatedPrincipalId)
        assertEquals("argon2id", gateway.updatedAlgorithm)
    }

    @Test
    fun `login with malformed hash fails closed as InvalidEmailPasswordException`() = runTest {
        val gateway = FakeLocalPasswordCredentialGateway(
            record = LocalPasswordCredentialRecord(
                principalId = "user-malformed",
                email = "malformed@example.com",
                username = "malformed-user",
                passwordHash = "broken-hash-format",
                passwordAlgorithm = "bcrypt",
            ),
        )
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = gateway,
            passwordHasher = FakePasswordHasher(),
            bcryptPasswordHasher = bcryptHasher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = identityFacts(),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        try {
            handler.handle(LoginUserCommand("malformed@example.com", "anyPassword"))
            throw AssertionError("Expected InvalidEmailPasswordException")
        } catch (e: InvalidEmailPasswordException) {
            assertNotNull(e)
        }
    }

    @Test
    fun `resend verification invalidates old tokens and publishes event`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = identityFacts(),
        )
        val eventPublisher = RecordingEventPublisher()
        val transactionRunner = NoopAtomicTransactionRunner
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            eventPublisher = eventPublisher,
            principalIdentityLookup = principalLookup,
            transactionRunner = transactionRunner,
        )

        val result = handler.handle(ResendVerificationCommand("yuniel@example.com"))

        assertTrue(result.accepted)
        assertTrue(identityRegistrationGateway.invalidatedTokens)
        assertNotNull(identityRegistrationGateway.createdToken)
        assertEquals(1, eventPublisher.published.size)
        val event = eventPublisher.published.single() as UserRegistered
        val newestToken = requireNotNull(identityRegistrationGateway.createdToken)
        assertEquals(newestToken.tokenHash, EmailVerificationTokenHasher.hash(event.rawVerificationToken))
    }

    @Test
    fun `resend verification returns accepted for unknown email`() = runTest {
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = FakeIdentityRegistrationGateway(),
            eventPublisher = RecordingEventPublisher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(),
            transactionRunner = NoopAtomicTransactionRunner,
        )

        val result = handler.handle(ResendVerificationCommand("unknown@example.com"))

        assertTrue(result.accepted)
    }

    @Test
    fun `register stores credential with passwordAlgorithm from hasher`() = runTest {
        val passwordGateway = FakeLocalPasswordCredentialGateway(order = mutableListOf())
        val handler = RegisterUserHandler(
            identityRegistrationGateway = FakeIdentityRegistrationGateway(),
            principalIdentityLookup = FakePrincipalIdentityLookup(),
            localPasswordCredentialGateway = passwordGateway,
            passwordHasher = FakePasswordHasher(),
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            eventPublisher = RecordingEventPublisher(),
            clock = fixedClock,
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            transactionRunner = NoopAtomicTransactionRunner,
        )

        handler.handle(
            RegisterUserCommand(
                email = " algo@example.com ",
                password = validPassword,
                username = " algo ",
            ),
        )

        assertNotNull(passwordGateway.createdPrincipalId)
        assertEquals("fake", passwordGateway.createdAlgorithm)
    }

    @Test
    fun `default email verification policy requires verification for all features`() {
        val policy = emailVerificationPolicyOf()

        AuthFeature.entries.forEach { feature ->
            assertTrue(policy(feature))
        }
    }

    @Test
    fun `bcrypt password hasher exposes bcrypt algorithm`() {
        assertEquals("bcrypt", bcryptHasher().algorithm)
    }

    // ── VerifyEmailHandler transaction tests ───────────────────────────────────

    @Test
    fun `should wrap email verification writes in a transaction when verification succeeds`() = runTest {
        val order = mutableListOf<String>()
        val identityGateway = object : FakeIdentityRegistrationGateway(order) {
            override suspend fun verifyEmailToken(tokenHash: String): EmailVerificationTokenData? {
                order.add("token:verify")
                return EmailVerificationTokenData(
                    email = "yuniel@example.com",
                    tokenHash = tokenHash,
                    expiresAt = fixedClock.instant().plusSeconds(3600),
                    usedAt = null,
                )
            }
        }
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = identityFacts(),
        )
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = VerifyEmailHandler(
            identityRegistrationGateway = identityGateway,
            principalIdentityLookup = principalLookup,
            localJwtIssuer = FakeLocalJwtIssuer(order),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(order),
            clock = fixedClock,
            transactionRunner = transactionRunner,
        )

        handler.handle(VerifyEmailCommand("raw-token"))

        assertEquals(
            listOf(
                "token:verify",
                "tx:start",
                "markTokenUsed",
                "updateEmailStatus",
                "tx:commit",
                "jwt:issue",
                "refresh:create",
            ),
            order,
        )
    }

    @Test
    fun `should roll back email verification writes when updating email status fails`() = runTest {
        val order = mutableListOf<String>()
        val identityGateway = object : FakeIdentityRegistrationGateway(order) {
            override suspend fun verifyEmailToken(tokenHash: String): EmailVerificationTokenData? =
                EmailVerificationTokenData(
                    email = "yuniel@example.com",
                    tokenHash = tokenHash,
                    expiresAt = fixedClock.instant().plusSeconds(3600),
                    usedAt = null,
                )

            override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus) {
                order.add("updateEmailStatus")
                throw IllegalStateException("DB error")
            }
        }
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = identityFacts(),
        )
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = VerifyEmailHandler(
            identityRegistrationGateway = identityGateway,
            principalIdentityLookup = principalLookup,
            localJwtIssuer = FakeLocalJwtIssuer(order),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(order),
            clock = fixedClock,
            transactionRunner = transactionRunner,
        )

        try {
            handler.handle(VerifyEmailCommand("raw-token"))
            throw AssertionError("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("DB error", e.message)
        }

        assertEquals(
            listOf(
                "tx:start",
                "markTokenUsed",
                "updateEmailStatus",
                "tx:rollback",
            ),
            order,
        )
    }

    // ── ResendVerificationHandler transaction tests ─────────────────────────────

    @Test
    fun `should wrap resend verification writes in a transaction when resending succeeds`() = runTest {
        val order = mutableListOf<String>()
        val identityGateway = FakeIdentityRegistrationGateway(order)
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = identityFacts(),
        )
        val eventPublisher = RecordingEventPublisher(order)
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = identityGateway,
            eventPublisher = eventPublisher,
            principalIdentityLookup = principalLookup,
            transactionRunner = transactionRunner,
        )

        handler.handle(ResendVerificationCommand("yuniel@example.com"))

        assertEquals(
            listOf(
                "tx:start",
                "invalidateEmailTokens",
                "token:create",
                "tx:commit",
                "event:publish",
            ),
            order,
        )
    }

    @Test
    fun `should roll back resend verification writes when creating the new token fails`() = runTest {
        val order = mutableListOf<String>()
        val identityGateway = object : FakeIdentityRegistrationGateway(order) {
            override suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant) {
                order.add("token:create")
                throw IllegalStateException("DB error")
            }
        }
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = identityFacts(),
        )
        val eventPublisher = RecordingEventPublisher(order)
        val transactionRunner = RecordingAtomicTransactionRunner(order)
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = identityGateway,
            eventPublisher = eventPublisher,
            principalIdentityLookup = principalLookup,
            transactionRunner = transactionRunner,
        )

        try {
            handler.handle(ResendVerificationCommand("yuniel@example.com"))
            throw AssertionError("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("DB error", e.message)
        }

        assertEquals(
            listOf(
                "tx:start",
                "invalidateEmailTokens",
                "token:create",
                "tx:rollback",
            ),
            order,
        )
    }

    // ── issueAuthSession tests (covers AuthSessionContext data class) ─────────

    @Test
    fun `issueAuthSession with workspaceId set`() = runTest {
        val jwtIssuer = FakeLocalJwtIssuer()
        val refreshSvc = fakeRefreshLifecycleService()

        val context = AuthSessionContext(
            principalId = "user-ws-1",
            subject = "local:yuniel@example.com",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = EmailStatus.VERIFIED,
            workspaceId = "ws-123",
            clock = fixedClock,
            localJwtIssuer = jwtIssuer,
            refreshSessionLifecycleService = refreshSvc,
        )

        val result = issueAuthSession(context)

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals(900, result.tokens.expiresIn)
        assertEquals("user-ws-1", result.tokens.principalId)
        assertEquals("yuniel@example.com", result.tokens.email)
        assertEquals("yuniel", result.tokens.username)
        assertEquals(EmailStatus.VERIFIED.name, result.tokens.emailStatus)
        assertEquals("ws-123", result.tokens.workspaceId)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    @Test
    fun `issueAuthSession with workspaceId null`() = runTest {
        val jwtIssuer = object : FakeLocalJwtIssuer() {
            override fun issue(
                principalId: String,
                subject: String,
                email: String,
                username: String?,
                emailStatus: com.profiletailors.smp.identity.domain.EmailStatus,
                issuedAt: Instant,
            ): IssuedAccessToken = IssuedAccessToken(
                value = "anon-token",
                expiresInSeconds = 3600,
            )
        }
        val refreshSvc = fakeRefreshLifecycleService()

        val context = AuthSessionContext(
            principalId = "user-anon",
            subject = "local:anon@example.com",
            email = "anon@example.com",
            username = null,
            emailStatus = EmailStatus.PENDING,
            workspaceId = null,
            clock = fixedClock,
            localJwtIssuer = jwtIssuer,
            refreshSessionLifecycleService = refreshSvc,
        )

        val result = issueAuthSession(context)

        assertEquals("anon-token", result.tokens.accessToken)
        assertEquals(3600, result.tokens.expiresIn)
        assertEquals("user-anon", result.tokens.principalId)
        assertEquals("anon@example.com", result.tokens.email)
        assertEquals(null, result.tokens.username)
        assertEquals(EmailStatus.PENDING.name, result.tokens.emailStatus)
        assertEquals(null, result.tokens.workspaceId)
        assertEquals("refresh-secret", result.refreshToken.secret)
    }

    private fun fakeRefreshLifecycleService(order: MutableList<String>? = null): RefreshSessionLifecycleService =
        RefreshSessionLifecycleService(
            refreshSessionGateway = FakeRefreshSessionGateway(order),
            refreshSessionTokenService = object : RefreshSessionTokenService() {
                override fun issue(): RefreshSessionToken = RefreshSessionToken("refresh-lookup", "refresh-secret")
            },
            properties = refreshProperties,
            clock = fixedClock,
        )

    private fun identityFacts(emailStatus: EmailStatus = EmailStatus.PENDING): PrincipalIdentityFacts =
        PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
            subject = "local:yuniel@example.com",
            provider = null,
            displayIdentity = "yuniel",
            email = "yuniel@example.com",
            username = "yuniel",
            emailStatus = emailStatus,
        )

    private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class RecordingAtomicTransactionRunner(private val order: MutableList<String>) : AtomicTransactionRunner {
        var invocations: Int = 0

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocations += 1
            order += "tx:start"
            return try {
                block().also { order += "tx:commit" }
            } catch (error: Throwable) {
                order += "tx:rollback"
                throw error
            }
        }
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private open class FakeIdentityRegistrationGateway(private val order: MutableList<String>? = null) :
        IdentityRegistrationGateway {
        var created: CreatedIdentity? = null
        var createdToken: com.profiletailors.smp.identity.application.EmailVerificationTokenData? = null
        var verifiedTokenHash: String? = null
        var updatedEmailStatus: EmailStatus? = null
        var invalidatedTokens: Boolean = false

        override suspend fun createUserIdentity(
            principalId: String,
            subject: String,
            email: String,
            username: String,
            provider: String?,
            displayIdentity: String,
            emailStatus: EmailStatus,
        ) {
            order?.add("identity:create")
            created = CreatedIdentity(principalId, subject, email, username, provider, displayIdentity, emailStatus)
        }

        override suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant) {
            order?.add("token:create")
            createdToken = com.profiletailors.smp.identity.application.EmailVerificationTokenData(
                email = email,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
                usedAt = null,
            )
        }

        override suspend fun verifyEmailToken(
            tokenHash: String,
        ): com.profiletailors.smp.identity.application.EmailVerificationTokenData? {
            verifiedTokenHash = tokenHash
            return createdToken?.takeIf { it.tokenHash == tokenHash }
        }

        override suspend fun markTokenUsed(tokenHash: String, now: Instant) {
            order?.add("markTokenUsed")
            createdToken = createdToken?.copy(usedAt = now)
        }

        override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus) {
            order?.add("updateEmailStatus")
            updatedEmailStatus = emailStatus
        }

        override suspend fun invalidateEmailTokens(email: String) {
            order?.add("invalidateEmailTokens")
            invalidatedTokens = true
        }

        override suspend fun findActiveTokenByEmail(
            email: String,
        ): com.profiletailors.smp.identity.application.EmailVerificationTokenData? =
            createdToken?.takeIf { it.email == email && it.usedAt == null }
    }

    private data class CreatedIdentity(
        val principalId: String,
        val subject: String,
        val email: String,
        val username: String,
        val provider: String?,
        val displayIdentity: String,
        val emailStatus: EmailStatus,
    )

    private class FakePrincipalIdentityLookup(
        private val existingEmail: String? = null,
        private val principalFacts: PrincipalIdentityFacts? = null,
    ) : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: com.profiletailors.common.domain.context.PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = principalFacts

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = if (email == existingEmail) {
            PrincipalIdentityFacts(
                principalId = "existing-user",
                principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                subject = "local:$email",
                provider = null,
                displayIdentity = "existing",
                email = email,
                username = "existing",
                emailStatus = EmailStatus.PENDING,
            )
        } else {
            principalFacts
        }

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = principalFacts
    }

    private class FakeLocalPasswordCredentialGateway(
        private val record: LocalPasswordCredentialRecord? = null,
        private val order: MutableList<String>? = null,
        private val updateFailure: RuntimeException? = null,
    ) : LocalPasswordCredentialGateway {
        var createdPrincipalId: String? = null
        var createdHash: String? = null
        var createdAlgorithm: String? = null
        var updatedPrincipalId: String? = null
        var updatedHash: String? = null
        var updatedAlgorithm: String? = null

        override suspend fun create(principalId: String, passwordHash: String, passwordAlgorithm: String) {
            order?.add("credential:create")
            createdPrincipalId = principalId
            createdHash = passwordHash
            createdAlgorithm = passwordAlgorithm
        }

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? =
            record?.takeIf { it.email == email }

        override suspend fun updatePassword(principalId: String, passwordHash: String, passwordAlgorithm: String) {
            order?.add("credential:update")
            updatedPrincipalId = principalId
            updatedHash = passwordHash
            updatedAlgorithm = passwordAlgorithm
            updateFailure?.let { throw it }
        }
    }

    private class FakePasswordHasher(override val algorithm: String = "fake") : PasswordHasher {
        override fun hash(rawPassword: String): String = "hashed-$rawPassword"

        override fun matches(rawPassword: String, passwordHash: String): Boolean = passwordHash == "hashed-$rawPassword"
    }

    private open class FakeLocalJwtIssuer(private val order: MutableList<String>? = null) : LocalJwtIssuer {
        override fun issue(
            principalId: String,
            subject: String,
            email: String,
            username: String?,
            emailStatus: com.profiletailors.smp.identity.domain.EmailStatus,
            issuedAt: Instant,
        ): IssuedAccessToken {
            order?.add("jwt:issue")
            return IssuedAccessToken(
                value = "token-for-$email",
                expiresInSeconds = 900,
            )
        }
    }

    private class FakeRefreshSessionGateway(private val order: MutableList<String>? = null) : RefreshSessionGateway {
        override suspend fun create(
            principalId: String,
            refreshToken: RefreshSessionToken,
            expiresAt: Instant,
        ): CreatedRefreshSession {
            order?.add("refresh:create")
            return CreatedRefreshSession(
                id = "refresh-session-1",
                principalId = principalId,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
            )
        }

        override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession =
            ActiveRefreshSession(
                id = "refresh-session-1",
                principalId = "user-1",
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
            id = "refresh-session-2",
            principalId = "user-1",
            refreshToken = replacementToken,
            expiresAt = expiresAt,
        )

        override suspend fun revoke(currentSessionId: String, now: Instant) = Unit
    }

    private class FakeWorkspaceProvisioningService(private val order: MutableList<String>? = null) :
        WorkspaceProvisioningService {
        override suspend fun provisionDefaultWorkspace(
            principalId: String,
            displayName: String,
        ): WorkspaceProvisioningService.ProvisionedWorkspace {
            order?.add("workspace:provision")
            return WorkspaceProvisioningService.ProvisionedWorkspace(
                workspaceId = "ws-fake-${principalId.hashCode().toUInt()}",
                name = "$displayName's Workspace",
            )
        }
    }

    private class RecordingEventPublisher(private val order: MutableList<String>? = null) :
        EventPublisher<DomainEvent> {
        val published = mutableListOf<DomainEvent>()

        override suspend fun publish(event: DomainEvent) {
            order?.add("event:publish")
            published.add(event)
        }
    }
}
