package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class InvitationTokenGeneratorTest {

    @Test
    fun `generated token is URL-safe base64`() {
        val token = InvitationTokenGenerator.generate()
        // URL-safe base64 uses A-Z a-z 0-9 - _ (no padding =)
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `generated token has at least 43 characters (256 bits in base64url)`() {
        val token = InvitationTokenGenerator.generate()
        // 32 bytes → 43 URL-safe base64 chars without padding
        assertTrue(token.length >= 43, "Token too short: ${token.length} chars")
    }

    @RepeatedTest(10)
    fun `each generated token is unique`() {
        val t1 = InvitationTokenGenerator.generate()
        val t2 = InvitationTokenGenerator.generate()
        assertNotEquals(t1, t2)
    }
}

private fun assertTrue(condition: Boolean, message: String = "") {
    if (!condition) throw AssertionError(message)
}
