package com.profiletailors.smp.leadcapture.infrastructure.notification

import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalUrlProvider
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
internal class EncryptedWaitlistWithdrawalUrlProvider(
    private val repository: WaitlistWithdrawalUrlRepository,
    properties: WaitlistWithdrawalProperties,
) : WaitlistWithdrawalUrlProvider {
    private val key = SecretKeySpec(decodeKey(properties.encryptionKey), "AES")
    private val publicUrlBase = validatePublicUrl(properties.publicUrlBase)
    private val secureRandom = SecureRandom()

    /** Encrypts [token] and replaces any stored withdrawal material for [entryId]. */
    override suspend fun remember(entryId: WaitlistEntryId, token: String, expiresAt: Instant) {
        val iv = ByteArray(IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        repository.save(
            entryId = entryId,
            ciphertextVersion = CIPHERTEXT_VERSION,
            ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(iv + encrypted),
            expiresAt = expiresAt,
        )
    }

    /**
     * Resolves a stored token to a withdrawal URL, deleting it when [now] reaches its expiry.
     *
     * Returns `null` when no token exists or it has expired. Persistence failures propagate.
     * @throws IllegalArgumentException If the stored ciphertext version or format is invalid.
     * @throws javax.crypto.AEADBadTagException If the stored ciphertext fails authentication.
     */
    override suspend fun urlFor(entryId: WaitlistEntryId, now: Instant): String? {
        val stored = repository.findByEntryId(entryId) ?: return null
        if (!now.isBefore(stored.expiresAt)) {
            repository.deleteByEntryId(entryId)
            return null
        }
        val token = decrypt(stored)
        return "$publicUrlBase?token=${encode(token)}"
    }

    override suspend fun forget(entryId: WaitlistEntryId) {
        repository.deleteByEntryId(entryId)
    }

    private fun decrypt(stored: WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl): String {
        require(stored.ciphertextVersion == CIPHERTEXT_VERSION) { "Unsupported withdrawal ciphertext version" }
        val payload = Base64.getUrlDecoder().decode(stored.ciphertext)
        require(payload.size > IV_LENGTH_BYTES + TAG_LENGTH_BYTES) { "Invalid withdrawal ciphertext" }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOf(IV_LENGTH_BYTES)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH_BYTES, payload.size)).toString(Charsets.UTF_8)
    }

    private fun encode(token: String): String = java.net.URLEncoder.encode(token, Charsets.UTF_8)

    private fun validatePublicUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "Waitlist withdrawal public URL must not be blank" }
        val uri = try {
            URI(normalized)
        } catch (exception: URISyntaxException) {
            throw IllegalArgumentException("Waitlist withdrawal public URL must be a valid HTTPS URL", exception)
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.isOpaque && !uri.host.isNullOrBlank()) {
            "Waitlist withdrawal public URL must be a valid HTTPS URL"
        }
        return normalized
    }

    private fun decodeKey(value: String): ByteArray {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Waitlist withdrawal encryption key must be configured" }
        val decoded = try {
            Base64.getDecoder().decode(normalized)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Waitlist withdrawal encryption key must be valid Base64", exception)
        }
        require(decoded.size in VALID_KEY_SIZES) { "Waitlist withdrawal encryption key must be 128, 192, or 256 bits" }
        return decoded
    }

    private companion object {
        const val CIPHERTEXT_VERSION = "v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE_BITS
        val VALID_KEY_SIZES = setOf(16, 24, 32)
    }
}
