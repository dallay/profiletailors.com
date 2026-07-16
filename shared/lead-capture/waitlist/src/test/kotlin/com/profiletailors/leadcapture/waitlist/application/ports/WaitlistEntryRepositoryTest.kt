package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Contract tests for [WaitlistEntryRepository] exercised against an
 * in-memory implementation, verifying the per-waitlist dedupe semantics
 * required by the port independently of any infrastructure adapter.
 */
internal class WaitlistEntryRepositoryTest {

    private class InMemoryWaitlistEntryRepository : WaitlistEntryRepository {
        private val store = mutableMapOf<Pair<WaitlistId, NormalizedEmail>, WaitlistEntry>()

        override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? =
            store[waitlistId to email]

        override fun save(entry: WaitlistEntry): WaitlistEntry {
            store[entry.waitlistId to entry.normalizedEmail] = entry
            return entry
        }

        override fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntryRepository.SaveResult {
            val key = entry.waitlistId to entry.normalizedEmail
            val existing = store[key]
            return if (existing != null) {
                WaitlistEntryRepository.SaveResult.AlreadyExists(existing)
            } else {
                store[key] = entry
                WaitlistEntryRepository.SaveResult.Saved(entry)
            }
        }
    }

    private fun entry(waitlistId: WaitlistId, email: String) = WaitlistEntry(
        id = WaitlistEntryId("e-$email"),
        waitlistId = waitlistId,
        email = EmailAddress(email),
        normalizedEmail = NormalizedEmail.from(EmailAddress(email)),
        source = CaptureSource("marketing-homepage"),
        formId = null,
        locale = null,
        metadata = LeadMetadata(),
        consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
        joinedAt = Instant.parse("2026-07-16T12:00:00Z"),
    )

    @Test
    fun `findByNormalizedEmail returns null when no entry exists`() {
        val repository: WaitlistEntryRepository = InMemoryWaitlistEntryRepository()

        val result = repository.findByNormalizedEmail(WaitlistId("w-1"), NormalizedEmail("user@example.com"))

        assertNull(result)
    }

    @Test
    fun `save persists the entry and returns it unchanged`() {
        val repository: WaitlistEntryRepository = InMemoryWaitlistEntryRepository()
        val newEntry = entry(WaitlistId("w-1"), "user@example.com")

        val saved = repository.save(newEntry)

        assertSame(newEntry, saved)
    }

    @Test
    fun `findByNormalizedEmail returns a previously saved entry`() {
        val repository: WaitlistEntryRepository = InMemoryWaitlistEntryRepository()
        val waitlistId = WaitlistId("w-1")
        val newEntry = entry(waitlistId, "user@example.com")
        repository.save(newEntry)

        val found = repository.findByNormalizedEmail(waitlistId, NormalizedEmail("user@example.com"))

        assertEquals(newEntry.id, found?.id)
    }

    @Test
    fun `dedupe key is scoped per waitlist`() {
        val repository: WaitlistEntryRepository = InMemoryWaitlistEntryRepository()
        val waitlistA = WaitlistId("w-a")
        val waitlistB = WaitlistId("w-b")
        repository.save(entry(waitlistA, "shared@example.com"))

        val foundInA = repository.findByNormalizedEmail(waitlistA, NormalizedEmail("shared@example.com"))
        val foundInB = repository.findByNormalizedEmail(waitlistB, NormalizedEmail("shared@example.com"))

        assertEquals("shared@example.com", foundInA?.normalizedEmail?.value)
        assertNull(foundInB)
    }
}
