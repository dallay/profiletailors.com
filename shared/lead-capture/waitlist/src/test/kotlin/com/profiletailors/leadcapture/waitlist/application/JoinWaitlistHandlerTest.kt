package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistConsentRecorder
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistClosedException
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistNotFoundException
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class JoinWaitlistHandlerTest {

    private val waitlistRepo: WaitlistRepository = mockk()
    private val entryRepo: WaitlistEntryRepository = mockk()
    private val idGenerator: WaitlistEntryIdGenerator = mockk()
    private val withdrawalUrlProvider: WaitlistWithdrawalUrlProvider = mockk(relaxed = true)
    private val consentRecorder: WaitlistConsentRecorder = mockk(relaxed = true)
    private val clock: () -> Instant = { Instant.parse("2026-07-16T12:00:00Z") }

    private val handler = JoinWaitlistHandler(
        waitlistRepository = waitlistRepo,
        entryRepository = entryRepo,
        idGenerator = idGenerator,
        transactionRunner = AtomicTransactionRunner.noop,
        withdrawalUrlProvider = withdrawalUrlProvider,
        consentRecorder = consentRecorder,
        clock = clock,
    )

    @Test
    fun `new email join returns Accepted with internal new distinction`() = runTest {
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
        coEvery { entryRepo.saveIfNotExists(any(), any()) } answers {
            WaitlistEntryRepository.SaveResult.Saved(this.firstArg())
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
        val savedToken = slot<WaitlistEntryRepository.WithdrawalToken>()
        coVerify(exactly = 1) { entryRepo.saveIfNotExists(any(), capture(savedToken)) }
        val rememberedToken = slot<String>()
        coVerify(exactly = 1) {
            withdrawalUrlProvider.remember(
                entryId = WaitlistEntryId("e-new"),
                token = capture(rememberedToken),
                expiresAt = Instant.parse("2026-10-14T12:00:00Z"),
            )
        }
        val fingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(rememberedToken.captured)
        assertEquals(fingerprint.candidate, savedToken.captured.candidate)
        assertEquals(fingerprint.hash, savedToken.captured.hash)
        verify(exactly = 1) {
            consentRecorder.record(
                match {
                    it.waitlistKey == WaitlistKey("profile-tailors-launch") &&
                        it.entryId == WaitlistEntryId("e-new") &&
                        it.normalizedEmail == NormalizedEmail.from(EmailAddress("user@example.com")) &&
                        it.consent == WaitlistConsent(earlyAccess = true, version = "2026-06-25") &&
                        it.locale == CaptureLocale("en") &&
                        it.source == CaptureSource("marketing-homepage")
                },
            )
        }
    }

    @Test
    fun `duplicate email join returns Accepted with internal already-joined distinction`() = runTest {
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
            normalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
            source = CaptureSource("marketing-homepage"),
            formId = "homepage-hero",
            locale = CaptureLocale("en"),
            metadata = LeadMetadata(utmSource = "linkedin"),
            consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            joinedAt = Instant.parse("2026-07-15T12:00:00Z"),
        )
        every { waitlistRepo.findByKey(any()) } returns activeWaitlist
        every { idGenerator.generate(any(), any()) } returns WaitlistEntryId("e-new")
        coEvery { entryRepo.saveIfNotExists(any(), any()) } returns
            WaitlistEntryRepository.SaveResult.AlreadyExists(existingEntry)

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
        coVerify(exactly = 1) { entryRepo.saveIfNotExists(any(), any()) }
        coVerify(exactly = 0) { withdrawalUrlProvider.remember(any(), any(), any()) }
        verify(exactly = 0) { consentRecorder.record(any()) }
    }

    @Test
    fun `unknown waitlist key throws NotFound`() = runTest {
        every { waitlistRepo.findByKey(WaitlistKey("nonexistent")) } returns null

        assertFailsWith<WaitlistNotFoundException> {
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
    fun `paused waitlist throws Closed`() = runTest {
        val pausedWaitlist = Waitlist(
            id = WaitlistId("w-1"),
            key = WaitlistKey("profile-tailors-launch"),
            name = "Profile Tailors Launch",
            context = "profile-tailors",
            status = WaitlistStatus.PAUSED,
        )
        every { waitlistRepo.findByKey(any()) } returns pausedWaitlist

        assertFailsWith<WaitlistClosedException> {
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
}
