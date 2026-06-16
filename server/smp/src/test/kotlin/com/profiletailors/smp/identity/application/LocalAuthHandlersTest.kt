package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.testfixture.CredentialGenerator
import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserRegistered
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

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

    @Test
    fun `registers user and returns result without tokens`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val principalLookup = FakePrincipalIdentityLookup()
        val passwordGateway = FakeLocalPasswordCredentialGateway()
        val passwordHasher = FakePasswordHasher()
        val eventPublisher = RecordingEventPublisher()
        val handler = RegisterUserHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            principalIdentityLookup = principalLookup,
            localPasswordCredentialGateway = passwordGateway,
            passwordHasher = passwordHasher,
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            eventPublisher = eventPublisher,
            clock = fixedClock,
        )

        val result = handler.handle(
            RegisterUserCommand(
                email = " Yuniel@Example.com ",
                password = validPassword,
                username = " yuniel ",
            ),
        )

        assertEquals("yuniel@example.com", result.email)
        assertEquals("yuniel", result.username)
        assertEquals(EmailStatus.PENDING, result.emailStatus)
        assertNotNull(result.principalId)

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
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.VERIFIED,
                ),
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
    fun `rejects login with unverified email`() = runTest {
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
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.PENDING,
                ),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        try {
            handler.handle(LoginUserCommand("yuniel@example.com", validPassword))
            throw AssertionError("Expected UnverifiedEmailException")
        } catch (e: UnverifiedEmailException) {
            assertEquals("yuniel@example.com", e.email)
        }
    }

    @Test
    fun `refreshes user session with verified email`() = runTest {
        val handler = RefreshUserSessionHandler(
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.VERIFIED,
                ),
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
    fun `rejects refresh with unverified email`() = runTest {
        val handler = RefreshUserSessionHandler(
            principalIdentityLookup = FakePrincipalIdentityLookup(
                principalFacts = PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.PENDING,
                ),
            ),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        try {
            handler.handle(RefreshUserSessionCommand("refresh-lookup.refresh-secret"))
            throw AssertionError("Expected UnverifiedEmailException")
        } catch (e: UnverifiedEmailException) {
            assertEquals("yuniel@example.com", e.email)
        }
    }

    @Test
    fun `rejects invalid login`() = runTest {
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(),
            passwordHasher = FakePasswordHasher(),
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

    @Test
    fun `resend verification invalidates old tokens and publishes event`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val principalLookup = FakePrincipalIdentityLookup(
            principalFacts = PrincipalIdentityFacts(
                principalId = "user-1",
                principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                subject = "local:yuniel@example.com",
                provider = null,
                displayIdentity = "yuniel",
                email = "yuniel@example.com",
                username = "yuniel",
                emailStatus = EmailStatus.PENDING,
            ),
        )
        val eventPublisher = RecordingEventPublisher()
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            eventPublisher = eventPublisher,
            principalIdentityLookup = principalLookup,
        )

        val result = handler.handle(ResendVerificationCommand("yuniel@example.com"))

        assertTrue(result.accepted)
        assertTrue(identityRegistrationGateway.invalidatedTokens)
        assertNotNull(identityRegistrationGateway.createdToken)
        assertEquals(1, eventPublisher.published.size)
    }

    @Test
    fun `resend verification returns accepted for unknown email`() = runTest {
        val handler = ResendVerificationHandler(
            identityRegistrationGateway = FakeIdentityRegistrationGateway(),
            eventPublisher = RecordingEventPublisher(),
            principalIdentityLookup = FakePrincipalIdentityLookup(),
        )

        val result = handler.handle(ResendVerificationCommand("unknown@example.com"))

        assertTrue(result.accepted)
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

    private fun fakeRefreshLifecycleService(): RefreshSessionLifecycleService = RefreshSessionLifecycleService(
        refreshSessionGateway = FakeRefreshSessionGateway(),
        refreshSessionTokenService = object : RefreshSessionTokenService() {
            override fun issue(): RefreshSessionToken = RefreshSessionToken("refresh-lookup", "refresh-secret")
        },
        properties = refreshProperties,
        clock = fixedClock,
    )

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeIdentityRegistrationGateway : IdentityRegistrationGateway {
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
            created = CreatedIdentity(principalId, subject, email, username, provider, displayIdentity, emailStatus)
        }

        override suspend fun createEmailVerificationToken(
            email: String,
            tokenHash: String,
            expiresAt: Instant,
        ) {
            createdToken = com.profiletailors.smp.identity.application.EmailVerificationTokenData(
                email = email,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
                usedAt = null,
            )
        }

        override suspend fun verifyEmailToken(tokenHash: String): com.profiletailors.smp.identity.application.EmailVerificationTokenData? {
            verifiedTokenHash = tokenHash
            return createdToken?.takeIf { it.tokenHash == tokenHash }
        }

        override suspend fun markTokenUsed(tokenHash: String, now: Instant) {
            createdToken = createdToken?.copy(usedAt = now)
        }

        override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus) {
            updatedEmailStatus = emailStatus
        }

        override suspend fun invalidateEmailTokens(email: String) {
            invalidatedTokens = true
        }

        override suspend fun findActiveTokenByEmail(email: String): com.profiletailors.smp.identity.application.EmailVerificationTokenData? =
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

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? =
            if (email == existingEmail) {
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
    ) : LocalPasswordCredentialGateway {
        var createdPrincipalId: String? = null
        var createdHash: String? = null

        override suspend fun create(principalId: String, passwordHash: String) {
            createdPrincipalId = principalId
            createdHash = passwordHash
        }

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? =
            record?.takeIf { it.email == email }
    }

    private class FakePasswordHasher : PasswordHasher {
        override fun hash(rawPassword: String): String = "hashed-$rawPassword"

        override fun matches(rawPassword: String, passwordHash: String): Boolean =
            passwordHash == "hashed-$rawPassword"
    }

    private open class FakeLocalJwtIssuer : LocalJwtIssuer {
        override fun issue(
            principalId: String,
            subject: String,
            email: String,
            username: String?,
            issuedAt: Instant,
        ): IssuedAccessToken = IssuedAccessToken(
            value = "token-for-$email",
            expiresInSeconds = 900,
        )
    }

    private class FakeRefreshSessionGateway : RefreshSessionGateway {
        override suspend fun create(principalId: String, refreshToken: RefreshSessionToken, expiresAt: Instant): CreatedRefreshSession =
            CreatedRefreshSession(
                id = "refresh-session-1",
                principalId = principalId,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
            )

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

    private class FakeWorkspaceProvisioningService : WorkspaceProvisioningService {
        override suspend fun provisionDefaultWorkspace(
            principalId: String,
            displayName: String,
        ): WorkspaceProvisioningService.ProvisionedWorkspace = WorkspaceProvisioningService.ProvisionedWorkspace(
            workspaceId = "ws-fake-${principalId.hashCode().toUInt()}",
            name = "${displayName}'s Workspace",
        )
    }

    private class RecordingEventPublisher : EventPublisher<DomainEvent> {
        val published = mutableListOf<DomainEvent>()

        override suspend fun publish(event: DomainEvent) {
            published.add(event)
        }
    }
}
