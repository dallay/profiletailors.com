package com.profiletailors.smp.identity.application

import com.profiletailors.common.testfixture.CredentialGenerator
import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
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
    fun `registers user and returns token plus refresh session`() = runTest {
        val identityRegistrationGateway = FakeIdentityRegistrationGateway()
        val principalLookup = FakePrincipalIdentityLookup()
        val passwordGateway = FakeLocalPasswordCredentialGateway()
        val passwordHasher = FakePasswordHasher()
        val jwtIssuer = FakeLocalJwtIssuer()
        val refreshLifecycleService = fakeRefreshLifecycleService()
        val handler = RegisterUserHandler(
            identityRegistrationGateway = identityRegistrationGateway,
            principalIdentityLookup = principalLookup,
            localPasswordCredentialGateway = passwordGateway,
            passwordHasher = passwordHasher,
            localJwtIssuer = jwtIssuer,
            refreshSessionLifecycleService = refreshLifecycleService,
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            clock = fixedClock,
        )

        val result = handler.handle(
            RegisterUserCommand(
                email = " Yuniel@Example.com ",
                password = validPassword,
                username = " yuniel ",
            ),
        )

        assertEquals("token-for-yuniel@example.com", result.tokens.accessToken)
        assertEquals("yuniel@example.com", result.tokens.email)
        assertEquals("yuniel", result.tokens.username)
        assertEquals("refresh-secret", result.refreshToken.secret)
        requireNotNull(identityRegistrationGateway.created)
        assertEquals("local:yuniel@example.com", identityRegistrationGateway.created?.subject)
        assertEquals("hashed-$validPassword", passwordGateway.createdHash)
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
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            workspaceProvisioningService = FakeWorkspaceProvisioningService(),
            clock = fixedClock,
        )

        assertThrows(UserAlreadyExistsException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(RegisterUserCommand("yuniel@example.com", validPassword, "yuniel"))
            }
        }
    }

    @Test
    fun `logs user in with valid password and returns refresh session`() = runTest {
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
    fun `refreshes user session`() = runTest {
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
    fun `rejects invalid login`() = runTest {
        val handler = LoginUserHandler(
            localPasswordCredentialGateway = FakeLocalPasswordCredentialGateway(),
            passwordHasher = FakePasswordHasher(),
            localJwtIssuer = FakeLocalJwtIssuer(),
            refreshSessionLifecycleService = fakeRefreshLifecycleService(),
            clock = fixedClock,
        )

        assertThrows(InvalidEmailPasswordException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(LoginUserCommand("missing@example.com", validPassword))
            }
        }
    }

    private fun fakeRefreshLifecycleService(): RefreshSessionLifecycleService = RefreshSessionLifecycleService(
        refreshSessionGateway = FakeRefreshSessionGateway(),
        refreshSessionTokenService = object : RefreshSessionTokenService() {
            override fun issue(): RefreshSessionToken = RefreshSessionToken("refresh-lookup", "refresh-secret")
        },
        properties = refreshProperties,
        clock = fixedClock,
    )

    private class FakeIdentityRegistrationGateway : IdentityRegistrationGateway {
        var created: CreatedIdentity? = null

        override suspend fun createUserIdentity(
            principalId: String,
            subject: String,
            email: String,
            username: String,
            provider: String?,
            displayIdentity: String,
        ) {
            created = CreatedIdentity(principalId, subject, email, username, provider, displayIdentity)
        }
    }

    private data class CreatedIdentity(
        val principalId: String,
        val subject: String,
        val email: String,
        val username: String,
        val provider: String?,
        val displayIdentity: String,
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
                )
            } else {
                null
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

    private class FakeLocalJwtIssuer : LocalJwtIssuer {
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
}
