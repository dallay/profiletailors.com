package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditEventCursorCodecTest {

    @Test
    fun `encodes cursor to base64url string without padding`() {
        val cursor = AuditEventCursor(
            createdAt = Instant.parse("2026-05-20T12:00:00Z"),
            id = "audit-5",
        )

        val encoded = AuditEventCursorCodec.encode(cursor)

        assertNotNull(encoded)
        // Does not contain padding characters
        assertEquals(false, encoded.endsWith("="))
        // Is valid base64url
        assertDoesNotThrow("Should be decodable") {
            AuditEventCursorCodec.decode(encoded)
        }
    }

    @Test
    fun `decode roundtrips a valid encoded cursor`() {
        val original = AuditEventCursor(
            createdAt = Instant.parse("2026-05-20T12:00:00Z"),
            id = "audit-5",
        )

        val encoded = AuditEventCursorCodec.encode(original)
        val decoded = AuditEventCursorCodec.decode(encoded)

        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.id, decoded.id)
    }

    @Test
    fun `decode rejects blank input`() {
        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode("")
        }
    }

    @Test
    fun `decode rejects whitespace-only input`() {
        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode("   ")
        }
    }

    @Test
    fun `decode rejects invalid base64 input`() {
        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode("!!!not-base64!!!")
        }
    }

    @Test
    fun `decode rejects input without separator`() {
        val noSeparator = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("no-separator-here".toByteArray(Charsets.UTF_8))

        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode(noSeparator)
        }
    }

    @Test
    fun `decode rejects input starting with separator`() {
        val startsWithSeparator = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("|audit-5".toByteArray(Charsets.UTF_8))

        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode(startsWithSeparator)
        }
    }

    @Test
    fun `decode rejects input with separator at the end`() {
        val endsWithSeparator = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("2026-05-20T12:00:00Z|".toByteArray(Charsets.UTF_8))

        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode(endsWithSeparator)
        }
    }

    @Test
    fun `decode rejects empty id after separator`() {
        val emptyId = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("2026-05-20T12:00:00Z|".toByteArray(Charsets.UTF_8))

        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode(emptyId)
        }
    }

    @Test
    fun `decode rejects invalid date format`() {
        val badDate = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("not-a-date|audit-5".toByteArray(Charsets.UTF_8))

        assertThrows(InvalidAuditEventCursorException::class.java) {
            AuditEventCursorCodec.decode(badDate)
        }
    }

    @Test
    fun `decode trims whitespace from input`() {
        val original = AuditEventCursor(
            createdAt = Instant.parse("2026-05-20T12:00:00Z"),
            id = "audit-5",
        )
        val encoded = AuditEventCursorCodec.encode(original)

        val decoded = AuditEventCursorCodec.decode("  $encoded  ")

        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.id, decoded.id)
    }

    @Test
    fun `InvalidAuditEventCursorException preserves cause`() {
        val cause = IllegalArgumentException("underlying cause")
        val exception = InvalidAuditEventCursorException(cause)

        assertEquals(cause, exception.cause)
        assertEquals("Invalid audit cursor", exception.message)
    }

    private fun assertDoesNotThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw AssertionError("$message: expected no exception but caught ${e::class.simpleName}: ${e.message}", e)
        }
    }
}
