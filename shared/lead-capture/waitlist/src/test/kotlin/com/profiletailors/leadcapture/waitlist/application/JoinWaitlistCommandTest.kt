package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class JoinWaitlistCommandTest {

    private fun command(
        email: EmailAddress = EmailAddress("user@example.com"),
        formId: String? = "homepage-hero",
        locale: CaptureLocale? = CaptureLocale("en"),
    ) = JoinWaitlistCommand(
        waitlistKey = WaitlistKey("profile-tailors-launch"),
        email = email,
        source = CaptureSource("marketing-homepage"),
        formId = formId,
        locale = locale,
        metadata = LeadMetadata(utmSource = "linkedin"),
        consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25"),
    )

    @Test
    fun `normalizedEmail trims and lowercases the raw email`() {
        val cmd = command(email = EmailAddress("  User@Example.com  ".trim()))
        assertEquals("user@example.com", cmd.normalizedEmail().value)
    }

    @Test
    fun `normalizedEmail does not mutate the original email`() {
        val cmd = command(email = EmailAddress("User@Example.com"))
        cmd.normalizedEmail()
        assertEquals("User@Example.com", cmd.email.value)
    }

    @Test
    fun `formId and locale are optional`() {
        val cmd = command(formId = null, locale = null)
        assertNull(cmd.formId)
        assertNull(cmd.locale)
    }

    @Test
    fun `equality is value-based for identical fields`() {
        val cmd1 = command()
        val cmd2 = command()
        assertEquals(cmd1, cmd2)
        assertEquals(cmd1.hashCode(), cmd2.hashCode())
    }

    @Test
    fun `copy overrides only the targeted field`() {
        val original = command(formId = "homepage-hero")
        val copy = original.copy(formId = "footer-cta")

        assertEquals("footer-cta", copy.formId)
        assertEquals(original.email, copy.email)
        assertEquals(original.waitlistKey, copy.waitlistKey)
    }

    @Test
    fun `different emails normalize to different keys`() {
        val cmd1 = command(email = EmailAddress("first@example.com"))
        val cmd2 = command(email = EmailAddress("second@example.com"))
        assertEquals("first@example.com", cmd1.normalizedEmail().value)
        assertEquals("second@example.com", cmd2.normalizedEmail().value)
    }
}