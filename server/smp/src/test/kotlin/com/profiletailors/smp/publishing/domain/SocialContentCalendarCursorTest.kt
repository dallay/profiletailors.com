package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class SocialContentCalendarCursorTest {
    @Test
    fun `accepts the current cursor version`() {
        CalendarCursorVersion(CalendarCursorVersion.V1).value shouldBe CalendarCursorVersion.V1
    }

    @Test
    fun `rejects unsupported cursor versions`() {
        shouldThrow<IllegalArgumentException> {
            CalendarCursorVersion("2")
        }
    }

    @Test
    fun `rejects blank cursor identity values`() {
        val constructors: List<() -> SocialContentCalendarCursor> = listOf(
            { validCursor().copy(workspaceId = " ") },
            { validCursor().copy(socialAccountId = "") },
            { validCursor().copy(externalPostId = "\t") },
        )

        constructors.forEach { constructor ->
            shouldThrow<IllegalArgumentException> { constructor() }
        }
    }

    @Test
    fun `rejects the internal delimiter in cursor fields`() {
        shouldThrow<IllegalArgumentException> {
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
