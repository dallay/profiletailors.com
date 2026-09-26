package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class WithdrawWaitlistEntryHandlerTest {

    private val repository = mockk<WaitlistEntryRepository>()
    private val handler = WithdrawWaitlistEntryHandler(repository)
    private val entry = WaitlistEntry(
        id = WaitlistEntryId("entry-1"),
        waitlistId = WaitlistId("waitlist-1"),
        email = EmailAddress("user@example.com"),
        normalizedEmail = NormalizedEmail.fromPersisted("user@example.com"),
        source = CaptureSource("marketing-site"),
        formId = "waitlist-hero",
        locale = CaptureLocale("en"),
        metadata = LeadMetadata(),
        consent = WaitlistConsent(earlyAccess = true, marketing = true, version = "2026-09"),
        joinedAt = Instant.parse("2026-09-24T10:00:00Z"),
        version = 0,
    )

    @Test
    fun `withdraws entry after consuming a valid token`() = runTest {
        coEvery { repository.withdrawByToken(any(), any(), any()) } returns entry

        val result = handler.handle("token")

        assertEquals(entry.id, requireNotNull(result).entryId)
        coVerify(exactly = 1) { repository.withdrawByToken(any(), any(), any()) }
    }
}
