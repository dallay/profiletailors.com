package com.profiletailors.leadcapture.waitlist.domain

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class WaitlistEntryTest {

    @Test
    fun `new entry starts as pending with joined_at set`() {
        val now = Instant.now()
        val entry = WaitlistEntry(
            id = WaitlistEntryId("e-1"),
            waitlistId = WaitlistId("w-1"),
            email = EmailAddress("user@example.com"),
            normalizedEmail = NormalizedEmail("user@example.com"),
            source = CaptureSource("marketing-homepage"),
            formId = "homepage-hero",
            locale = CaptureLocale("en"),
            metadata = LeadMetadata(utmSource = "linkedin"),
            consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
            joinedAt = now,
        )
        assertEquals(WaitlistEntryStatus.PENDING, entry.status)
        assertEquals(now, entry.joinedAt)
        assertNull(entry.invitedAt)
        assertNull(entry.convertedAt)
        assertNull(entry.cancelledAt)
    }

    @Test
    fun `invite transitions status and sets invited_at`() {
        val now = Instant.now()
        val entry = createEntry(joinedAt = now)
        val inviteTime = now.plusSeconds(60)
        entry.invite(at = inviteTime)
        assertEquals(WaitlistEntryStatus.INVITED, entry.status)
        assertEquals(inviteTime, entry.invitedAt)
    }

    @Test
    fun `convert transitions status and sets converted_at`() {
        val now = Instant.now()
        val entry = createEntry(joinedAt = now)
        entry.invite(at = now.plusSeconds(60))
        val convertTime = now.plusSeconds(120)
        entry.convert(at = convertTime)
        assertEquals(WaitlistEntryStatus.CONVERTED, entry.status)
        assertEquals(convertTime, entry.convertedAt)
    }

    @Test
    fun `cancel transitions status and sets cancelled_at`() {
        val now = Instant.now()
        val entry = createEntry(joinedAt = now)
        val cancelTime = now.plusSeconds(30)
        entry.cancel(at = cancelTime)
        assertEquals(WaitlistEntryStatus.CANCELLED, entry.status)
        assertEquals(cancelTime, entry.cancelledAt)
    }

    @Test
    fun `cannot invite a cancelled entry`() {
        val now = Instant.now()
        val entry = createEntry(joinedAt = now)
        entry.cancel(at = now.plusSeconds(10))
        assertThrows<IllegalStateException> { entry.invite(at = now.plusSeconds(20)) }
    }

    @Test
    fun `cannot convert a pending entry`() {
        val now = Instant.now()
        val entry = createEntry(joinedAt = now)
        assertThrows<IllegalStateException> { entry.convert(at = now.plusSeconds(20)) }
    }

    private fun createEntry(joinedAt: Instant): WaitlistEntry = WaitlistEntry(
        id = WaitlistEntryId("e-1"),
        waitlistId = WaitlistId("w-1"),
        email = EmailAddress("user@example.com"),
        normalizedEmail = NormalizedEmail("user@example.com"),
        source = CaptureSource("marketing-homepage"),
        formId = "homepage-hero",
        locale = CaptureLocale("en"),
        metadata = LeadMetadata(utmSource = "linkedin"),
        consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
        joinedAt = joinedAt,
    )
}
