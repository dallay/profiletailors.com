package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class WaitlistEntryIdGeneratorTest {

    @Test
    fun `SAM lambda implementation returns the generated id`() {
        val generator = WaitlistEntryIdGenerator { _, _ -> WaitlistEntryId("fixed-id") }

        val result = generator.generate(WaitlistId("w-1"), NormalizedEmail("user@example.com"))

        assertEquals(WaitlistEntryId("fixed-id"), result)
    }

    @Test
    fun `generator receives the waitlistId and normalizedEmail it was invoked with`() {
        var capturedWaitlistId: WaitlistId? = null
        var capturedEmail: NormalizedEmail? = null
        val generator = WaitlistEntryIdGenerator { waitlistId, normalizedEmail ->
            capturedWaitlistId = waitlistId
            capturedEmail = normalizedEmail
            WaitlistEntryId("e-1")
        }

        generator.generate(WaitlistId("w-42"), NormalizedEmail("someone@example.com"))

        assertEquals(WaitlistId("w-42"), capturedWaitlistId)
        assertEquals(NormalizedEmail("someone@example.com"), capturedEmail)
    }

    @Test
    fun `deterministic generator based on inputs produces stable but distinguishable ids`() {
        val generator = WaitlistEntryIdGenerator { waitlistId, normalizedEmail ->
            WaitlistEntryId("${waitlistId.value}:${normalizedEmail.value}")
        }

        val first = generator.generate(WaitlistId("w-1"), NormalizedEmail("a@example.com"))
        val second = generator.generate(WaitlistId("w-1"), NormalizedEmail("b@example.com"))

        assertEquals(WaitlistEntryId("w-1:a@example.com"), first)
        assertNotEquals(first, second)
    }
}
