package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.infrastructure.security.SpringJwtValidatedTokenMapper
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
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
        principalMaterializer = JwtAuthenticatedPrincipalMaterializer(),
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
}
