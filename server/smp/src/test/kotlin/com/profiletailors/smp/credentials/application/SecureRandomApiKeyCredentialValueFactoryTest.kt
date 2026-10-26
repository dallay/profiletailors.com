package com.profiletailors.smp.credentials.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SecureRandomApiKeyCredentialValueFactoryTest {
    private val factory = SecureRandomApiKeyCredentialValueFactory()

    @Test
    fun `credential row references are distinct canonical UUIDs`() {
        val first = factory.nextCredentialReference()
        val second = factory.nextCredentialReference()

        assertEquals(first, UUID.fromString(first).toString())
        assertEquals(second, UUID.fromString(second).toString())
        assertNotEquals(first, second)
    }

    @Test
    fun `plaintext keys preserve their opaque lookup and secret format`() {
        val key = factory.nextPlaintextApiKey()

        assertTrue(key.lookupKey.matches(Regex("ptk_[0-9a-f]{24}")))
        assertTrue(key.secret.matches(Regex("[0-9a-f]{48}")))
        assertEquals(key.lookupKey, key.keyPrefix)
        assertEquals("${key.lookupKey}.${key.secret}", key.value)
    }
}
