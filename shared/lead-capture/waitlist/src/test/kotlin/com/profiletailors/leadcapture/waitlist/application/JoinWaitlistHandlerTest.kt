package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistClosedException
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistNotFoundException
import WaitlistStatus
import io.mockk.capture
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class JoinWaitlistHandlerTest {

    private val waitlistRepo: WaitlistRepository = mockk()
    private val entryRepo: WaitlistEntryRepository = mockk()
    private val idGenerator: WaitlistEntryIdGenerator = mockk()
    private val clock: () -> Instant = { Instant.parse("2026-07-16T12:00:00Z") }

    private val handler = JoinWaitlistHandler(
        waitlistRepository = waitlistRepo,
        entryRepository = entryRepo,
        idGenerator = idGenerator,
        clock = clock,
    )

    @Test
    fun `new email join returns Accepted with internal new distinction`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        every { waitlistRepo.findByKey(WaitlistKey("profile-tailors-launch")) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(any()) } answers {
            val entry = firstArg<WaitlistEntry>()
            WaitlistEntryRepository.SaveResult.Saved(entry)
        }

        val result = handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("user@example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = "homepage-hero",
                locale = CaptureLocale("en"),
                metadata = LeadMetadata(utmSource = "linkedin"),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        assertEquals(JoinResult.JOINED_NEW, result)
        assertEquals("Accepted", result.toString())
        verify(exactly = 1) { entryRepo.saveIfNotExists(any()) }
    }

    @Test
    fun `duplicate email join returns Accepted with internal already-joined distinction`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        val existingEntry = WaitlistEntry(
            id = WaitlistEntryId("e-existing"),
            waitlistId = waitlistId,
            email = EmailAddress("user@example.com"),
            normalizedEmail = NormalizedEmail("user@example.com"),
            source = CaptureSource("marketing-homepage"),
            formId = "homepage-hero",
            locale = CaptureLocale("en"),
            metadata = LeadMetadata(utmSource = "linkedin"),
            consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            joinedAt = Instant.parse("2026-07-15T12:00:00Z"),
        )
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(any()) } returns WaitlistEntryRepository.SaveResult.AlreadyExists(existingEntry)

        val result = handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("user@example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = "homepage-hero",
                locale = CaptureLocale("en"),
                metadata = LeadMetadata(utmSource = "linkedin"),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        assertEquals(JoinResult.ALREADY_JOINED, result)
        verify(exactly = 1) { entryRepo.saveIfNotExists(any()) }
    }

    @Test
    fun `unknown waitlist key throws NotFound`() {
        every { waitlistRepo.findByKey(WaitlistKey("nonexistent")) } returns null

        assertThrows<WaitlistNotFoundException> {
            handler.handle(
                JoinWaitlistCommand(
                    waitlistKey = WaitlistKey("nonexistent"),
                    email = EmailAddress("user@example.com"),
                    source = CaptureSource("marketing-homepage"),
                    formId = null,
                    locale = null,
                    metadata = LeadMetadata(),
                    consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
                ),
            )
        }
    }

    @Test
    fun `paused waitlist throws Closed`() {
        val pausedWaitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.PAUSED,
        )
        every { waitlistRepo.findByKey(any()) } returns pausedWaitlist

        assertThrows<WaitlistClosedException> {
            handler.handle(
                JoinWaitlistCommand(
                    waitlistKey = WaitlistKey("profile-tailors-launch"),
                    email = EmailAddress("user@example.com"),
                    source = CaptureSource("marketing-homepage"),
                    formId = null,
                    locale = null,
                    metadata = LeadMetadata(),
                    consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
                ),
            )
        }
    }

    @Test
    fun `result toString is uniform regardless of distinction`() {
        assertEquals(JoinResult.JOINED_NEW.toString(), JoinResult.ALREADY_JOINED.toString())
        assertTrue(JoinResult.JOINED_NEW.toString().contains("Accepted"))
    }

    @Test
    fun `new entry is saved with fields mapped from the command and joinedAt from the clock`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        val savedSlot = slot<WaitlistEntry>()
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(capture(savedSlot)) } answers {
            WaitlistEntryRepository.SaveResult.Saved(savedSlot.captured)
        }

        handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("User@Example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = "homepage-hero",
                locale = CaptureLocale("en"),
                metadata = LeadMetadata(utmSource = "linkedin"),
                consent = WaitlistConsent(earlyAccess = true, marketing = true, version = "2026-06-25"),
            ),
        )

        val saved = savedSlot.captured
        assertEquals(WaitlistEntryId("e-new"), saved.id)
        assertEquals(waitlistId, saved.waitlistId)
        assertEquals(EmailAddress("User@Example.com"), saved.email)
        assertEquals(NormalizedEmail("user@example.com"), saved.normalizedEmail)
        assertEquals(CaptureSource("marketing-homepage"), saved.source)
        assertEquals("homepage-hero", saved.formId)
        assertEquals(CaptureLocale("en"), saved.locale)
        assertEquals(LeadMetadata(utmSource = "linkedin"), saved.metadata)
        assertTrue(saved.consent.marketing)
        assertEquals(Instant.parse("2026-07-16T12:00:00Z"), saved.joinedAt)
    }

    @Test
    fun `idGenerator is invoked with the waitlist id and the normalized email`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(any()) } answers {
            val entry = firstArg<WaitlistEntry>()
            WaitlistEntryRepository.SaveResult.Saved(entry)
        }

        handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("User@Example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = null,
                locale = null,
                metadata = LeadMetadata(),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        verify(exactly = 1) {
            idGenerator.generate(waitlistId, NormalizedEmail("user@example.com"))
        }
    }

    @Test
    fun `saveIfNotExists is invoked with entry containing waitlist id and normalized email`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        val savedSlot = slot<WaitlistEntry>()
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(capture(savedSlot)) } answers {
            WaitlistEntryRepository.SaveResult.Saved(savedSlot.captured)
        }

        handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("  USER@EXAMPLE.COM  ".trim()),
                source = CaptureSource("marketing-homepage"),
                formId = null,
                locale = null,
                metadata = LeadMetadata(),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        val saved = savedSlot.captured
        assertEquals(waitlistId, saved.waitlistId)
        assertEquals(NormalizedEmail("user@example.com"), saved.normalizedEmail)
    }

    @Test
    fun `closed waitlist throws Closed and never queries the entry repository`() {
        val closedWaitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.CLOSED,
        )
        every { waitlistRepo.findByKey(any()) } returns closedWaitlist

        assertThrows<WaitlistClosedException> {
            handler.handle(
                JoinWaitlistCommand(
                    waitlistKey = WaitlistKey("profile-tailors-launch"),
                    email = EmailAddress("user@example.com"),
                    source = CaptureSource("marketing-homepage"),
                    formId = null,
                    locale = null,
                    metadata = LeadMetadata(),
                    consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
                ),
            )
        }
        verify(exactly = 0) { entryRepo.saveIfNotExists(any()) }
    }

    @Test
    fun `default clock is used when none is supplied`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        val savedSlot = slot<WaitlistEntry>()
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(capture(savedSlot)) } answers {
            WaitlistEntryRepository.SaveResult.Saved(savedSlot.captured)
        }

        val handlerWithDefaultClock = JoinWaitlistHandler(
            waitlistRepository = waitlistRepo,
            entryRepository = entryRepo,
            idGenerator = idGenerator,
        )
        val before = Instant.now()

        handlerWithDefaultClock.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("user@example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = null,
                locale = null,
                metadata = LeadMetadata(),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        val after = Instant.now()
        assertTrue(!savedSlot.captured.joinedAt.isBefore(before) && !savedSlot.captured.joinedAt.isAfter(after))
    }

    @Test
    fun `command with null formId and locale is joined successfully`() {
        val waitlistId = WaitlistId("w-1")
        val activeWaitlist = Waitlist(
            id = waitlistId,
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.ACTIVE,
        )
        val savedSlot = slot<WaitlistEntry>()
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        every { entryRepo.saveIfNotExists(capture(savedSlot)) } answers {
            WaitlistEntryRepository.SaveResult.Saved(savedSlot.captured)
        }

        val result = handler.handle(
            JoinWaitlistCommand(
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                email = EmailAddress("user@example.com"),
                source = CaptureSource("marketing-homepage"),
                formId = null,
                locale = null,
                metadata = LeadMetadata(),
                consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            ),
        )

        assertEquals(JoinResult.JOINED_NEW, result)
        assertNull(savedSlot.captured.formId)
        assertNull(savedSlot.captured.locale)
    }
}
