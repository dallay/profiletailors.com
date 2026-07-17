package com.profiletailors.leadcapture.waitlist.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WaitlistConsentTest {

    @Test
    fun `consent with earlyAccess true is valid`() {
        val consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-06-25")
        assertTrue(consent.earlyAccess)
        assertFalse(consent.marketing)
    }

    @Test
    fun `consent with earlyAccess false is rejected`() {
        assertThrows<IllegalArgumentException> {
            WaitlistConsent(earlyAccess = false, marketing = false, version = "2026-06-25")
        }
    }

    @Test
    fun `consent with blank version is rejected`() {
        assertThrows<IllegalArgumentException> {
            WaitlistConsent(earlyAccess = true, marketing = false, version = "")
        }
    }

    @Test
    fun `marketing defaults to false when not specified`() {
        val consent = WaitlistConsent(earlyAccess = true, version = "2026-06-25")
        assertFalse(consent.marketing)
    }

    @Test
    fun `marketing explicit true is allowed`() {
        val consent = WaitlistConsent(earlyAccess = true, marketing = true, version = "2026-06-25")
        assertTrue(consent.marketing)
    }

    @Test
    fun `consent equality is value-based`() {
        val c1 = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-06-25")
        val c2 = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-06-25")
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
    }
}
