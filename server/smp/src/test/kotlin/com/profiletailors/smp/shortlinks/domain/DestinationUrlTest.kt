package com.profiletailors.smp.shortlinks.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class DestinationUrlTest {
    @Test
    fun `accepts http and https while preserving signed URL representation`() {
        val value = "https://example.com/path?b=2&a=%2F#section"

        assertEquals(value, DestinationUrl(value).value)
        assertEquals("http://example.com/path", DestinationUrl("http://example.com/path").value)
    }

    @Test
    fun `rejects non HTTP schemes`() {
        listOf(
            "javascript:alert(1)",
            "data:text/plain,hello",
            "file:///etc/passwd",
            "ftp://example.com",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { DestinationUrl(url) }
        }
    }

    @Test
    fun `rejects URLs without a host`() {
        assertThrows(IllegalArgumentException::class.java) { DestinationUrl("https:/relative") }
    }

    @Test
    fun `rejects empty HTTP host`() {
        assertThrows(IllegalArgumentException::class.java) { DestinationUrl("http://") }
    }
}
