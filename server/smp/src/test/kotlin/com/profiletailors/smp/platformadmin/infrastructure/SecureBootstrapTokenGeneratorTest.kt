package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.domain.BootstrapTokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SecureBootstrapTokenGeneratorTest {
    @Test
    fun `generates unguessable raw token and stores only its sha256 hash`() {
        val tokenGenerator: BootstrapTokenGenerator = SecureBootstrapTokenGenerator()

        val first = tokenGenerator.generate()
        val second = tokenGenerator.generate()
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(first.rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertTrue(first.rawToken.isNotBlank())
        assertEquals(64, first.tokenHash.length)
        assertEquals(expectedHash, first.tokenHash)
        assertNotEquals(first.rawToken, first.tokenHash)
        assertNotEquals(first.rawToken, second.rawToken)
    }
}
