package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.common.domain.Service
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class CredentialEncryptionService(private val properties: PublishingCredentialsProperties) {
    private val secureRandom = SecureRandom()
    private val key: SecretKey

    init {
        val keyBase64 = properties.key?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "Credential encryption key (publishing.credentials.encryption.key) is missing or blank."
            )
        val bytes = Base64.getDecoder().decode(keyBase64)
        if (bytes.size !in VALID_KEY_SIZES) {
            val message = "Encryption key must be 128/192/256 bits (base64). " +
                "Found ${bytes.size * BITS_PER_BYTE} bits"
            throw IllegalArgumentException(message)
        }
        key = SecretKeySpec(bytes, "AES")
    }

    fun encrypt(jsonPayload: String): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))
        // store iv + ciphertext
        return iv + ciphertext
    }

    fun decrypt(payload: ByteArray): String {
        val minSize = IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES
        require(payload.size >= minSize) { "Encrypted payload too short" }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plain = cipher.doFinal(ciphertext)
        return String(plain, Charsets.UTF_8)
    }

    private companion object {
        val VALID_KEY_SIZES = listOf(16, 24, 32)
        const val BITS_PER_BYTE = 8
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = 16
    }
}
