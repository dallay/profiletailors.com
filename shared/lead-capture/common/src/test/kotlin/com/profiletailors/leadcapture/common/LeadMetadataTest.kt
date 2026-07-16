package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class LeadMetadataTest {

    @Test
    fun `whitelisted keys are accepted`() {
        val metadata = LeadMetadata(
            utmSource = "linkedin",
            utmMedium = "social",
            utmCampaign = "launch",
            utmContent = "hero-cta",
            utmTerm = "scheduler",
            referrer = "https://google.com",
            pagePath = "/",
            userAgentFamily = "Chrome",
            consentVersion = "2026-06-25",
        )
        assertEquals("linkedin", metadata.utmSource)
        assertEquals("social", metadata.utmMedium)
        assertEquals("launch", metadata.utmCampaign)
    }

    @Test
    fun `all fields are optional`() {
        val metadata = LeadMetadata()
        assertNull(metadata.utmSource)
        assertNull(metadata.pagePath)
        assertNull(metadata.consentVersion)
    }

    @Test
    fun `equality is value-based`() {
        val m1 = LeadMetadata(utmSource = "linkedin", pagePath = "/")
        val m2 = LeadMetadata(utmSource = "linkedin", pagePath = "/")
        assertEquals(m1, m2)
        assertEquals(m1.hashCode(), m2.hashCode())
    }
}
