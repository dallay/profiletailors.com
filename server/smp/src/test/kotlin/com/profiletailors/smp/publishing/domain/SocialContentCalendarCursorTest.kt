package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class SocialContentCalendarCursorTest {
    @Test
    fun `accepts the current cursor version`() {
        assertEquals(CalendarCursorVersion.V1, CalendarCursorVersion(CalendarCursorVersion.V1).value)
    }

    @Test
    fun `rejects unsupported cursor versions`() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarCursorVersion("2")
        }
    }

    @Test
    fun `rejects blank cursor identity values`() {
        val arguments = listOf(
            { validCursor().copy(workspaceId = " ") },
            { validCursor().copy(socialAccountId = "") },
            { validCursor().copy(externalPostId = "\t") },
        )

        arguments.forEach { constructor ->
            assertThrows(IllegalArgumentException::class.java) { constructor() }
        }
    }

    @Test
    fun `rejects the internal delimiter in cursor fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            validCursor().copy(externalPostId = "post\u001Fid")
        }
    }

    private fun validCursor() = SocialContentCalendarCursor(
        version = CalendarCursorVersion(CalendarCursorVersion.V1),
        workspaceId = "workspace-1",
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "soacc-1",
        externalPostId = "post-1",
    )
}
