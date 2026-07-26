package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.JoinResult
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistConsentRecorder
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryJoinedNotifier
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WaitlistApplicationConfigurationTest {

    private val configuration = WaitlistApplicationConfiguration()

    @Test
    fun `waitlistEntryIdGenerator produces deterministic UUIDs from waitlist and email`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val waitlistId = WaitlistId("waitlist-1")
        val email = NormalizedEmail.fromPersisted("user@example.com")
        val firstId = generator.generate(waitlistId, email).value
        val secondId = generator.generate(waitlistId, email).value

        assertEquals(firstId, secondId)
        assertTrue(firstId.length == 36)
    }

    @Test
    fun `waitlistEntryIdGenerator distinguishes different emails for the same waitlist`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val waitlistId = WaitlistId("waitlist-1")
        val userA = generator.generate(waitlistId, NormalizedEmail.fromPersisted("a@example.com")).value
        val userB = generator.generate(waitlistId, NormalizedEmail.fromPersisted("b@example.com")).value

        assertNotEquals(userA, userB)
    }

    @Test
    fun `waitlistEntryIdGenerator distinguishes different waitlists for the same email`() {
        val generator = configuration.waitlistEntryIdGenerator()

        val email = NormalizedEmail.fromPersisted("user@example.com")
        val waitlistA = generator.generate(WaitlistId("waitlist-A"), email).value
        val waitlistB = generator.generate(WaitlistId("waitlist-B"), email).value

        assertNotEquals(waitlistA, waitlistB)
    }

    @Test
    fun `joinWaitlistHandler wires repositories, generator, and clock into a working handler`() {
        val handler = configuration.joinWaitlistHandler(
            waitlistRepository = StubWaitlistRepository,
            entryRepository = RecordingWaitlistEntryRepository(),
            idGenerator = { _, _ -> WaitlistEntryId("test-id") },
            consentRecorder = WaitlistConsentRecorder.noop,
            notifier = WaitlistEntryJoinedNotifier.noop,
        )

        val fixedClock = Instant.parse("2026-07-17T10:00:00Z")
        val result = handler.handle(
            com.profiletailors.leadcapture.waitlist.application.JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("user@example.com"),
                source = com.profiletailors.leadcapture.common.CaptureSource("marketing-site"),
                formId = null,
                locale = null,
                metadata = com.profiletailors.leadcapture.common.LeadMetadata(),
                consent = com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent(
                    earlyAccess = true,
                    version = "2026-07-17",
                ),
            ),
        )

        assertEquals(JoinResult.JOINED_NEW, result)
    }

    private object StubWaitlistRepository : WaitlistRepository {
        override fun findByKey(key: WaitlistKey): Waitlist? = Waitlist(
            id = WaitlistId("waitlist-1"),
            key = key,
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
            createdAt = Instant.parse("2026-07-17T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-17T09:00:00Z"),
        )
    }

    private class RecordingWaitlistEntryRepository : WaitlistEntryRepository {
        override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? = null

        override fun save(entry: WaitlistEntry): WaitlistEntry = entry

        override fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntryRepository.SaveResult {
            val stored = entry
            return WaitlistEntryRepository.SaveResult.Saved(stored)
        }
    }
}
