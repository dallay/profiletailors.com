package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.smp.identity.domain.PrincipalType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class JwtAuthenticatedPrincipalMaterializerTest {

    private val materializer = JwtAuthenticatedPrincipalMaterializer()

    @Test
    fun `materializes user principal from validated jwt token`() = runTest {
        val token = ValidatedToken(
            credentialType = CredentialType.JWT,
            tokenValue = "token-value",
            subject = "user-123",
            issuer = "https://issuer.example",
            audience = setOf("profiletailors-api"),
            issuedAt = Instant.parse("2026-05-15T10:15:30Z"),
            expiresAt = Instant.parse("2026-05-15T11:15:30Z"),
            tokenId = "jwt-1",
            claims = mapOf(
                "preferred_username" to "yuniel",
                "email" to "yuniel@example.com",
            ),
        )

        val authenticatedPrincipal = materializer.materialize(token)

        assertEquals(PrincipalType.USER, authenticatedPrincipal.context.principalType)
        assertEquals("user-123", authenticatedPrincipal.context.principalId)
        assertEquals("user-123", authenticatedPrincipal.context.subject)
        assertEquals("https://issuer.example", authenticatedPrincipal.context.provider)
        assertEquals("yuniel", authenticatedPrincipal.context.displayIdentity)
        assertEquals("JWT_BEARER", authenticatedPrincipal.context.authenticationMethod)
        assertEquals("jwt-1", authenticatedPrincipal.context.issuedCredentialReference)
        assertEquals("yuniel@example.com", authenticatedPrincipal.context.attributes["email"])
        assertEquals(CredentialType.JWT, authenticatedPrincipal.credentialType)
    }
}
