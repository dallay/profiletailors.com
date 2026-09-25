package com.profiletailors.smp.leadcapture.infrastructure.notification

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class EncryptedWaitlistWithdrawalUrlProviderTest {
    private val entryId = WaitlistEntryId("entry-1")
    private val expiresAt = Instant.parse("2026-10-01T00:00:00Z")
    private val properties = WaitlistWithdrawalProperties(
        encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
        publicUrlBase = "https://profiletailors.com/waitlist/withdraw",
    )

    @Test
    fun `stores encrypted versioned token and returns stable public url`() = runTest {
        val repository = RecordingRepository()
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)

        provider.remember(entryId, "raw-token", expiresAt)

        val stored = requireNotNull(repository.stored)
        assertEquals("v1", stored.ciphertextVersion)
        assertFalse(stored.ciphertext.contains("raw-token"))
        assertEquals(
            "https://profiletailors.com/waitlist/withdraw?token=raw-token",
            provider.urlFor(entryId, Instant.parse("2026-09-30T00:00:00Z")),
        )
    }

    @Test
    fun `rejects blank encryption key at startup`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            EncryptedWaitlistWithdrawalUrlProvider(
                RecordingRepository(),
                properties.copy(encryptionKey = "  "),
            )
        }

        assertContains(exception.message.orEmpty(), "encryption key must be configured")
    }

    @Test
    fun `rejects invalid encryption key at startup`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            EncryptedWaitlistWithdrawalUrlProvider(
                RecordingRepository(),
                properties.copy(encryptionKey = "not-base64"),
            )
        }

        assertContains(exception.message.orEmpty(), "encryption key must be valid Base64")
    }

    @Test
    fun `rejects invalid public URL at startup`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            EncryptedWaitlistWithdrawalUrlProvider(
                RecordingRepository(),
                properties.copy(publicUrlBase = "http://profiletailors.com/waitlist/withdraw"),
            )
        }

        assertContains(exception.message.orEmpty(), "public URL must be a valid HTTPS URL")
    }

    @Test
    fun `rejects unsupported ciphertext version`() = runTest {
        val repository = RecordingRepository(
            stored = WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl(
                ciphertextVersion = "v2",
                ciphertext = "ciphertext",
                expiresAt = expiresAt,
            ),
        )
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.urlFor(entryId, Instant.parse("2026-09-30T00:00:00Z"))
        }

        assertContains(exception.message.orEmpty(), "Unsupported withdrawal ciphertext version")
    }

    @Test
    fun `rejects tampered ciphertext`() = runTest {
        val repository = RecordingRepository()
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)
        provider.remember(entryId, "raw-token", expiresAt)
        val stored = requireNotNull(repository.stored)
        val bytes = Base64.getUrlDecoder().decode(stored.ciphertext)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        repository.stored = stored.copy(
            ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
        )

        assertFailsWith<AEADBadTagException> {
            provider.urlFor(entryId, Instant.parse("2026-09-30T00:00:00Z"))
        }
    }

    @Test
    fun `rejects ciphertext copied to another entry`() = runTest {
        val repository = RecordingRepository()
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)
        provider.remember(entryId, "raw-token", expiresAt)

        assertFailsWith<AEADBadTagException> {
            provider.urlFor(WaitlistEntryId("entry-2"), Instant.parse("2026-09-30T00:00:00Z"))
        }
    }

    @Test
    fun `stores can be read by a provider restarted with the same key`() = runTest {
        val repository = RecordingRepository()
        val firstProvider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)
        firstProvider.remember(entryId, "raw-token", expiresAt)

        val restartedProvider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)

        assertEquals(
            "https://profiletailors.com/waitlist/withdraw?token=raw-token",
            restartedProvider.urlFor(entryId, Instant.parse("2026-09-30T00:00:00Z")),
        )
    }

    @Test
    fun `deletes expired token before returning no url`() = runTest {
        val repository = RecordingRepository()
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)
        provider.remember(entryId, "raw-token", expiresAt)

        assertNull(provider.urlFor(entryId, expiresAt))
        assertTrue(repository.deleted)
    }

    @Test
    fun `forget deletes token`() = runTest {
        val repository = RecordingRepository()
        val provider = EncryptedWaitlistWithdrawalUrlProvider(repository, properties)
        provider.remember(entryId, "raw-token", expiresAt)

        provider.forget(entryId)

        assertTrue(repository.deleted)
    }

    private class RecordingRepository(var stored: WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl? = null) :
        WaitlistWithdrawalUrlRepository {
        var deleted = false

        override suspend fun save(
            entryId: WaitlistEntryId,
            ciphertextVersion: String,
            ciphertext: String,
            expiresAt: Instant,
        ) {
            stored = WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl(ciphertextVersion, ciphertext, expiresAt)
        }

        override suspend fun findByEntryId(entryId: WaitlistEntryId) = stored

        override suspend fun deleteByEntryId(entryId: WaitlistEntryId) {
            deleted = true
            stored = null
        }
    }
}
