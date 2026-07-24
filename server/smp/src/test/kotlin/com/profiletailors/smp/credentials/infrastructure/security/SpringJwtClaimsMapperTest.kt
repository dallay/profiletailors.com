package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.spring.boot.security.SpringJwtClaimsMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class SpringJwtClaimsMapperTest {

    private val mapper = SpringJwtClaimsMapper()

    @Test
    fun `maps service account JWT claims into a shared token representation`() = runTest {
        val issuedAt = Instant.parse("2026-05-15T10:15:30Z")
        val expiresAt = Instant.parse("2026-05-15T11:15:30Z")
        val jwt = Jwt.withTokenValue("service-token")
            .header("alg", "RS256")
            .claim("sub", "service-account-subject")
            .claim("iss", "https://issuer.example")
            .claim("principal_type", "SERVICE_ACCOUNT")
            .claim("credential_reference", "svc-cred-1")
            .claim("jti", "jwt-service-1")
            .claim("email", "bot@example.com")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build()

        val claims = mapper.map(jwt)

        assertEquals(PrincipalType.SERVICE_ACCOUNT, claims.principalTypeHint)
        assertEquals("svc-cred-1", claims.credentialReference)
        assertEquals("https://issuer.example", claims.issuer)
        val expectedStringClaims = mapOf(
            "credential_reference" to "svc-cred-1",
            "email" to "bot@example.com",
            "iss" to "https://issuer.example",
            "jti" to "jwt-service-1",
            "principal_type" to "SERVICE_ACCOUNT",
            "sub" to "service-account-subject",
        )

        assertEquals(expectedStringClaims, claims.stringClaims)
        assertEquals(issuedAt, claims.issuedAt)
        assertEquals(expiresAt, claims.expiresAt)
    }

    @Test
    fun `rejects a JWT without an issuer claim`() = runTest {
        val jwt = Jwt.withTokenValue("token-value")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            .build()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            mapper.map(jwt)
        }

        assertEquals("JWT missing 'iss' claim", exception.message)
    }
}
