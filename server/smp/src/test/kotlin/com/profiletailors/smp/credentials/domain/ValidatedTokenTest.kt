package com.profiletailors.smp.credentials.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ValidatedTokenTest {

    private val validTokenValue = "TEST_TOKEN_VAL_FOR_UNIT_TEST_ONLY"

    @Test
    fun `ValidatedToken accepts valid token with all fields`() {
        assertDoesNotThrow {
            ValidatedToken(
                credentialType = CredentialType.JWT,
                tokenValue = validTokenValue,
                subject = "user-1",
                issuer = "https://auth.example.com",
                audience = setOf("profiletailors.com"),
                issuedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(3600),
            )
        }
    }

    @Test
    fun `ValidatedToken accepts minimal valid token`() {
        assertDoesNotThrow {
            ValidatedToken(
                credentialType = CredentialType.JWT,
                tokenValue = validTokenValue,
                subject = "user-1",
                issuer = "https://auth.example.com",
                audience = emptySet(),
                issuedAt = null,
                expiresAt = null,
            )
        }
    }

    @Test
    fun `ValidatedToken rejects blank tokenValue`() {
        assertThrows<IllegalArgumentException> {
            ValidatedToken(
                credentialType = CredentialType.JWT,
                tokenValue = "   ",
                subject = "user-1",
                issuer = "https://auth.example.com",
                audience = emptySet(),
                issuedAt = null,
                expiresAt = null,
            )
        }
    }

    @Test
    fun `ValidatedToken rejects blank subject`() {
        assertThrows<IllegalArgumentException> {
            ValidatedToken(
                credentialType = CredentialType.JWT,
                tokenValue = validTokenValue,
                subject = "",
                issuer = "https://auth.example.com",
                audience = emptySet(),
                issuedAt = null,
                expiresAt = null,
            )
        }
    }
}
