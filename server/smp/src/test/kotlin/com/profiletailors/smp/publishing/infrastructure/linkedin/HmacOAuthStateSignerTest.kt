package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HmacOAuthStateSignerTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC)
    private val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val signer = HmacOAuthStateSigner("test-secret-with-enough-entropy", objectMapper, clock)

    @Test
    fun `signs and verifies oauth state payload`() {
        val payload = validPayload()

        val state = signer.sign(payload)
        val verified = signer.verify(state)

        assertEquals(payload, verified)
    }

    @Test
    fun `rejects tampered oauth state`() {
        val state = signer.sign(validPayload())
        val tampered = state.replaceFirst('a', 'b')

        assertThrows(InvalidOAuthStateException::class.java) {
            signer.verify(tampered)
        }
    }

    @Test
    fun `rejects malformed oauth state`() {
        assertThrows(InvalidOAuthStateException::class.java) {
            signer.verify("not-a-valid-state")
        }
    }

    @Test
    fun `rejects expired oauth state`() {
        val state = signer.sign(validPayload(expiresAt = Instant.parse("2026-05-26T11:59:59Z")))

        assertThrows(ExpiredOAuthStateException::class.java) {
            signer.verify(state)
        }
    }

    @Test
    fun `rejects blank signing secret`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HmacOAuthStateSigner("", objectMapper, clock)
        }
        assertEquals("OAuth state signing secret is required.", error.message)
    }

    @Test
    fun `rejects state with blank payload part`() {
        val state = signer.sign(validPayload())

        assertThrows(InvalidOAuthStateException::class.java) {
            signer.verify(".$state")
        }
        assertThrows(InvalidOAuthStateException::class.java) {
            signer.verify("$state.")
        }
    }

    @Test
    fun `rejects state with unparseable json payload`() {
        val state = signer.sign(validPayload())
        val parts = state.split('.')
        val mangledPayload = parts[0].dropLast(2) // corrupted base64
        val mangledState = "$mangledPayload.${parts[1]}"

        assertThrows(InvalidOAuthStateException::class.java) {
            signer.verify(mangledState)
        }
    }

    private fun validPayload(expiresAt: Instant = Instant.parse("2026-05-26T12:10:00Z")): LinkedInOAuthStatePayload =
        LinkedInOAuthStatePayload(
            provider = SocialProvider.LINKEDIN,
            workspaceId = "workspace-1",
            principalId = "principal-1",
            redirectUri = "https://app.example.com/callback",
            nonce = "nonce-1",
            issuedAt = Instant.parse("2026-05-26T12:00:00Z"),
            expiresAt = expiresAt,
        )
}
