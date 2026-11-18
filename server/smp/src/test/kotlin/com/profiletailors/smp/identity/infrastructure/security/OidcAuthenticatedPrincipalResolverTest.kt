package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.ExternalIdentityLinkGateway
import com.profiletailors.smp.identity.application.ExternalIdentityResolutionService
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.VerifiedEmailSyncResult
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.ExternalIdentityLink
import com.profiletailors.smp.identity.domain.ExternalIdentityLinkStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.infrastructure.OidcAuthenticatedPrincipalResolver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import java.time.Instant

class OidcAuthenticatedPrincipalResolverTest {
    private val issuer = "https://keycloak.ahome.quest/realms/pt"

    @Test
    fun `resolves a validated linked eligible app identity into a session principal`() = runTest {
        val identityLookup = IdentityLookup()
        val resolver = resolver(identityLookup)

        val principal = resolver.resolve("pt-app", oidcUser())

        assertEquals("principal-1", principal?.context?.principalId)
        assertEquals(PrincipalType.USER, principal?.context?.principalType)
        assertEquals("oidc:user-1", principal?.context?.subject)
        assertEquals(issuer, principal?.context?.provider)
        assertEquals("OIDC_BFF", principal?.context?.authenticationMethod)
        assertEquals(1, identityLookup.findByPrincipalIdCalls)
        assertEquals(0, identityLookup.findByEmailCalls)
    }

    @Test
    fun `does not resolve a linked identity when local principal is disabled`() = runTest {
        val resolver = resolver(IdentityLookup(accountState = "DISABLED"))

        assertNull(resolver.resolve("pt-app", oidcUser()))
    }

    @Test
    fun `rejects admin identity without expected audience client and acr evidence`() {
        val resolver = resolver(IdentityLookup())

        assertThrows(OAuth2AuthenticationException::class.java) {
            resolver.validateAdminIdentity(oidcIdToken(audience = listOf("pt-app"), azp = "pt-app", acr = "1"))
        }
    }

    @Test
    fun `accepts admin identity only with expected issuer subject audience azp and acr`() {
        val resolver = resolver(IdentityLookup())

        resolver.validateAdminIdentity(
            oidcIdToken(
                audience = listOf("pt-admin"),
                azp = "pt-admin",
                acr = "2",
            ),
        )
    }

    private fun resolver(identityLookup: IdentityLookup): OidcAuthenticatedPrincipalResolver =
        OidcAuthenticatedPrincipalResolver(
            ExternalIdentityResolutionService(LinkGateway(), identityLookup),
            issuer,
        )

    private fun oidcIdToken(
        audience: List<String> = listOf("pt-app"),
        azp: String? = "pt-app",
        acr: String? = null,
    ): OidcIdToken {
        val claims = mutableMapOf<String, Any>(
            "iss" to issuer,
            "sub" to "oidc:user-1",
            "aud" to audience,
            "email" to "human@example.test",
            "email_verified" to true,
        )
        if (azp != null) claims["azp"] = azp
        if (acr != null) claims["acr"] = acr
        val token = OidcIdToken(
            "validated-id-token",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
            claims,
        )
        return token
    }

    private fun oidcUser() = DefaultOidcUser(emptyList(), oidcIdToken())

    private class LinkGateway : ExternalIdentityLinkGateway {
        override suspend fun findByIssuerAndSubject(issuer: String, subject: String) = listOf(
            ExternalIdentityLink(issuer, subject, "principal-1", ExternalIdentityLinkStatus.APPROVED),
        )

        override suspend fun create(issuer: String, subject: String, principalId: String) = Unit

        override suspend fun synchronizeVerifiedEmail(principalId: String, email: String) =
            VerifiedEmailSyncResult.UNCHANGED
    }

    private class IdentityLookup(private val accountState: String = "ACTIVE") : PrincipalIdentityLookup {
        var findByPrincipalIdCalls = 0
        var findByEmailCalls = 0

        override suspend fun findBySubject(principalType: PrincipalType, subject: String, provider: String?) = null

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? {
            findByEmailCalls += 1
            return null
        }

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? {
            findByPrincipalIdCalls += 1
            return PrincipalIdentityFacts(
                principalId = principalId,
                principalType = PrincipalType.USER,
                subject = "local-subject",
                provider = "local-provider",
                displayIdentity = "Human",
                email = "human@example.test",
                username = "human",
                emailStatus = EmailStatus.VERIFIED,
                accountState = com.profiletailors.smp.identity.domain.UserAccountState.valueOf(accountState),
            )
        }
    }
}
