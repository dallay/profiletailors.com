package com.profiletailors.smp.publishing.infrastructure.credentials

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.util.Base64

class CredentialEncryptionServiceTests {

    @Test
    fun `configuration binds publishing credentials encryption key`() {
        val expectedKey = Base64.getEncoder().encodeToString(ByteArray(32))
        val source =
            MapConfigurationPropertySource(
                mapOf("publishing.credentials.encryption.key" to expectedKey),
            )

        val properties =
            Binder(source)
                .bind("publishing.credentials.encryption", PublishingCredentialsProperties::class.java)
                .get()

        assertEquals(expectedKey, properties.key)
    }

    @Test
    fun `decrypt throws on short payload`() {
        val properties = PublishingCredentialsProperties()
        properties.key = Base64.getEncoder().encodeToString(ByteArray(16))
        val service = CredentialEncryptionService(properties)

        val ex = assertThrows<IllegalArgumentException> {
            service.decrypt(ByteArray(2))
        }
        assertTrue(ex.message!!.contains("too short", ignoreCase = true))
    }

    @Test
    fun `init throws IllegalStateException when encryption key is missing`() {
        assertMissingKeyRejected(PublishingCredentialsProperties())
    }

    @Test
    fun `init throws IllegalStateException when encryption key is blank`() {
        val properties = PublishingCredentialsProperties()
        properties.key = "   "

        assertMissingKeyRejected(properties)
    }

    @Test
    fun `encrypt and decrypt roundtrip`() {
        // generate temporary key and set into properties
        val keyBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(keyBytes)
        val base64Key = Base64.getEncoder().encodeToString(keyBytes)
        // cannot set property easily here; instead instantiate service directly
        val properties = PublishingCredentialsProperties()
        properties.key = base64Key
        val service = CredentialEncryptionService(properties)
        val payload = "{\"accessToken\":\"abc123\"}"
        val encrypted = service.encrypt(payload)
        assertNotNull(encrypted)
        val decrypted = service.decrypt(encrypted)
        assertEquals(payload, decrypted)
    }

    private fun assertMissingKeyRejected(properties: PublishingCredentialsProperties) {
        val exception = assertThrows<IllegalStateException> {
            CredentialEncryptionService(properties)
        }
        val expectedMessage =
            "PUBLISHING_CREDENTIALS_KEY is required to encrypt publishing credentials. " +
                "Set a Base64-encoded 16, 24, or 32 byte AES key."
        assertEquals(expectedMessage, exception.message)
    }
}
