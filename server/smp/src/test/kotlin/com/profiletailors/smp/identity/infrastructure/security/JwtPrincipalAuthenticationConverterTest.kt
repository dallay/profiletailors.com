package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ActiveServiceAccountCredential
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import com.profiletailors.smp.credentials.infrastructure.security.SpringJwtValidatedTokenMapper
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class JwtPrincipalAuthenticationConverterTest {

    private val converter = JwtPrincipalAuthenticationConverter(
        jwtValidatedTokenMapper = SpringJwtValidatedTokenMapper(),
        principalMaterializer = JwtAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(),
            serviceAccountCredentialStateLookup = StubServiceAccountCredentialStateLookup(),
        ),
    )

    @Test
    fun `converts jwt into authentication carrying repo local principal`() = runTest {
        val jwt = Jwt.withTokenValue("token-value")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            .claim("iss", "https://issuer.example")
            .claim("preferred_username", "yuniel")
            .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
            .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
            .build()

        val authentication = converter.convert(jwt).awaitSingle()

        val principal = authentication.principal as AuthenticatedPrincipal
        assertEquals("user-123", principal.context.subject)
        assertEquals("yuniel", principal.context.displayIdentity)
        assertEquals("https://issuer.example", principal.context.provider)
        assertEquals("user-123", authentication.name)
    }

    @Test
    fun `converts service-account jwt into authentication carrying service-account principal`() = runTest {
        val jwt = Jwt.withTokenValue("service-token")
            .header("alg", "RS256")
            .claim("sub", "service-account-subject")
            .claim("iss", "https://issuer.example")
            .claim("principal_type", "SERVICE_ACCOUNT")
            .claim("credential_reference", "svc-cred-1")
            .claim("jti", "jwt-service-1")
            .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
            .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
            .build()

        val authentication = converter.convert(jwt).awaitSingle()

        val principal = authentication.principal as AuthenticatedPrincipal
        assertEquals(PrincipalType.SERVICE_ACCOUNT, principal.context.principalType)
        assertEquals("service-principal-1", principal.context.principalId)
        assertEquals("svc-cred-1", principal.context.issuedCredentialReference)
        assertEquals("service-account-subject", authentication.name)
    }

    private class StubPrincipalIdentityLookup : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = when (principalType) {
            PrincipalType.SERVICE_ACCOUNT -> PrincipalIdentityFacts(
                principalId = "service-principal-1",
                principalType = PrincipalType.SERVICE_ACCOUNT,
                subject = subject,
                provider = provider,
                displayIdentity = "scheduler-bot",
                email = null,
                username = null,
            )
            else -> null
        }
    }

    private class StubServiceAccountCredentialStateLookup : ServiceAccountCredentialStateLookup {
        override suspend fun requireActive(
            credentialReference: String,
            subject: String,
            provider: String,
        ): ActiveServiceAccountCredential = ActiveServiceAccountCredential(
            principalId = "service-principal-1",
            credentialReference = credentialReference,
        )
    }
}
