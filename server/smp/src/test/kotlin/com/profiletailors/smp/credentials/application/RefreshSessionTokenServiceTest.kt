package com.profiletailors.smp.credentials.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RefreshSessionTokenServiceTest {

    private val service = RefreshSessionTokenService()

    @Test
    fun `issues parseable refresh token`() {
        val token = service.issue()

        val parsed = service.parse(token.asCookieValue())

        assertEquals(token.lookupKey, parsed.lookupKey)
        assertEquals(token.secret, parsed.secret)
    }

    @Test
    fun `rejects malformed refresh token`() {
        assertThrows(RefreshSessionNotActiveException::class.java) {
            service.parse("invalid-token")
        }
    }
}
