package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.spring.boot.security.SpringJwtClaimsMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class SpringJwtClaimsMapperTest {

    private val mapper = SpringJwtClaimsMapper()

    @Test
    fun `should map service account claims when principal_type is SERVICE_ACCOUNT`() {
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

        claims.principalTypeHint shouldBe PrincipalType.SERVICE_ACCOUNT
        claims.credentialReference shouldBe "svc-cred-1"
        claims.issuer shouldBe "https://issuer.example"
        val expectedStringClaims = mapOf(
            "credential_reference" to "svc-cred-1",
            "email" to "bot@example.com",
            "iss" to "https://issuer.example",
            "jti" to "jwt-service-1",
            "principal_type" to "SERVICE_ACCOUNT",
            "sub" to "service-account-subject",
        )

        claims.stringClaims shouldBe expectedStringClaims
        claims.issuedAt shouldBe issuedAt
        claims.expiresAt shouldBe expiresAt
    }

    @Test
    fun `should reject JWT when issuer claim is missing`() {
        val jwt = Jwt.withTokenValue("token-value")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            .build()

        val exception = shouldThrow<IllegalArgumentException> {
            mapper.map(jwt)
        }

        exception.message shouldBe "JWT missing 'iss' claim"
    }
}
