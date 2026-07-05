package com.profiletailors.smp.publishing.infrastructure.credentials

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class CredentialEncryptionServiceTests {

    @Test
    fun `decrypt throws on short payload`() {
        val properties = PublishingCredentialsProperties()
        properties.encryptionKey = Base64.getEncoder().encodeToString(ByteArray(16))
        val service = CredentialEncryptionService(properties)

        val ex = assertThrows<IllegalArgumentException> {
            service.decrypt(ByteArray(2))
        }
        assertTrue(ex.message!!.contains("too short", ignoreCase = true))
    }

    @Test
    fun `init throws IllegalStateException when encryption key is blank`() {
        val properties = PublishingCredentialsProperties()
        properties.encryptionKey = "   "

        val exception = assertThrows<IllegalStateException> {
            CredentialEncryptionService(properties)
        }
        val expectedMessage =
            "PUBLISHING_CREDENTIALS_KEY is required to encrypt publishing credentials. " +
                "Set a Base64-encoded 16, 24, or 32 byte AES key."
        assertEquals(expectedMessage, exception.message)
    }

    @Test
    fun `encrypt and decrypt roundtrip`() {
        // generate temporary key and set into properties
        val keyBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(keyBytes)
        val base64Key = Base64.getEncoder().encodeToString(keyBytes)
        // cannot set property easily here; instead instantiate service directly
        val properties = PublishingCredentialsProperties()
        properties.encryptionKey = base64Key
        val service = CredentialEncryptionService(properties)
        val payload = "{\"accessToken\":\"abc123\"}"
        val encrypted = service.encrypt(payload)
        assertNotNull(encrypted)
        val decrypted = service.decrypt(encrypted)
        assertEquals(payload, decrypted)
    }
}
