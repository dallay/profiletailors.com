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
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.identity.infrastructure.OidcAuthenticatedPrincipalResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import reactor.core.publisher.Mono
import java.time.Instant

class LocalPrincipalOidcUserServiceTest {
    private val issuer = "https://keycloak.ahome.quest/realms/pt"

    @Test
    fun `returns OIDC user carrying local principal only after delegate and eligibility resolution`() {
        val service = service(
            PrincipalIdentityFacts(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "local-subject",
                provider = "local-provider",
                displayIdentity = "Human",
                email = "human@example.test",
                username = "human",
                emailStatus = EmailStatus.VERIFIED,
            ),
        )

        val resolved = service.loadUser(userRequest()).block()

        val authenticatedPrincipal = (resolved as ResolvedOidcUser).authenticatedPrincipal
        assertEquals("principal-1", authenticatedPrincipal.context.principalId)
        assertEquals("oidc:user-1", authenticatedPrincipal.context.subject)
    }

    @Test
    fun `fails OIDC user loading when no approved local identity link exists`() {
        val service = service(null, hasLink = false)

        assertThrows(OAuth2AuthenticationException::class.java) { service.loadUser(userRequest()).block() }
    }

    @Test
    fun `fails OIDC user loading when linked local principal is ineligible`() {
        val service = service(
            PrincipalIdentityFacts(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "local-subject",
                provider = "local-provider",
                displayIdentity = "Human",
                email = null,
                username = null,
                accountState = UserAccountState.DISABLED,
            ),
        )

        assertThrows(OAuth2AuthenticationException::class.java) { service.loadUser(userRequest()).block() }
    }

    @Test
    fun `preserves delegate authentication failure and cause`() {
        val cause = IllegalStateException("provider user information failed")
        val failure = OAuth2AuthenticationException(OAuth2Error("provider_failure"), cause)
        val service = LocalPrincipalOidcUserService(
            OidcAuthenticatedPrincipalResolver(
                ExternalIdentityResolutionService(links(true), IdentityLookup(null)),
                issuer,
            ),
            ReactiveOAuth2UserService { Mono.error(failure) },
        )

        assertSame(
            failure,
            assertThrows(OAuth2AuthenticationException::class.java) {
                service.loadUser(userRequest()).block()
            },
        )
        assertSame(cause, failure.cause)
    }

    private fun service(facts: PrincipalIdentityFacts?, hasLink: Boolean = true): LocalPrincipalOidcUserService {
        val identityLookup = IdentityLookup(facts)
        val resolver = OidcAuthenticatedPrincipalResolver(
            ExternalIdentityResolutionService(links(hasLink), identityLookup),
            issuer,
        )
        return LocalPrincipalOidcUserService(resolver) { Mono.just(validatedOidcUser()) }
    }

    private fun links(hasLink: Boolean) = object : ExternalIdentityLinkGateway {
        override suspend fun create(issuer: String, subject: String, principalId: String) = Unit

        override suspend fun findByIssuerAndSubject(issuer: String, subject: String) = if (hasLink) {
            listOf(ExternalIdentityLink(issuer, subject, "principal-1", ExternalIdentityLinkStatus.APPROVED))
        } else {
            emptyList()
        }

        override suspend fun synchronizeVerifiedEmail(principalId: String, email: String) =
            VerifiedEmailSyncResult.UNCHANGED
    }

    private class IdentityLookup(private val facts: PrincipalIdentityFacts?) : PrincipalIdentityLookup {
        override suspend fun findBySubject(principalType: PrincipalType, subject: String, provider: String?) = null
        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null
        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = facts
    }

    private fun userRequest(): OidcUserRequest {
        val registration = ClientRegistration.withRegistrationId("pt-app")
            .clientId("pt-app")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://api.profiletailors.com/login/oauth2/code/pt-app")
            .authorizationUri("https://idp.example.test/authorize")
            .tokenUri("https://idp.example.test/token")
            .userInfoUri("https://idp.example.test/userinfo")
            .userNameAttributeName("sub")
            .clientName("pt-app")
            .build()
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "opaque-access-token",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
        )
        return OidcUserRequest(registration, accessToken, untrustedRequestToken())
    }

    private fun untrustedRequestToken() = OidcIdToken(
        "request-token",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T01:00:00Z"),
        mapOf("iss" to "https://untrusted.example", "sub" to "untrusted"),
    )

    private fun validatedOidcUser(): OidcUser {
        val token = OidcIdToken(
            "validated-delegated-token",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
            mapOf(
                "iss" to issuer,
                "sub" to "oidc:user-1",
                "aud" to listOf("pt-app"),
                "email" to "human@example.test",
                "email_verified" to true,
            ),
        )
        return DefaultOidcUser(emptyList(), token)
    }
}
