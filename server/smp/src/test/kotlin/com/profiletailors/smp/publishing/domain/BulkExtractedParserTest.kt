package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BulkHeaderParserTest {
    @Test
    fun `parses canonical header case insensitive`() = runTest {
        val parsed = BulkHeaderParser.parse("BODYText,SCHEDULEDFOR,TIMEZONE,MEDIA_URLS,HASHTAGS")
        val index = parsed as BulkHeaderParseResult.Valid
        assertEquals(0, index.columns.bodyIdx)
        assertEquals(1, index.columns.scheduledIdx)
        assertEquals(3, index.columns.mediaIdx)
    }

    @Test
    fun `rejects mismatched header`() = runTest {
        val result = BulkHeaderParser.parse("bad,header")
        assertTrue(result is BulkHeaderParseResult.Invalid)
    }

    @Test
    fun `reordered header stays invalid preserving contract`() = runTest {
        val header = "scheduledFor,bodyText,timezone,hashtags,media_urls"
        val result = BulkHeaderParser.parse(header)
        assertTrue(result is BulkHeaderParseResult.Invalid)
    }
}

class BulkScheduleParserTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `missing scheduledFor yields required error`() = runTest {
        val result = BulkScheduleParser.parse(null, fixedClock)
        assertTrue(result.error?.code == "INVALID_DATE")
    }

    @Test
    fun `past date yields future error`() = runTest {
        val result = BulkScheduleParser.parse("2026-08-29T09:00:00Z", fixedClock)
        assertTrue(result.error?.message?.contains("future") == true)
    }

    @Test
    fun `future date parses`() = runTest {
        val result = BulkScheduleParser.parse("2026-09-01T10:00:00Z", fixedClock)
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), result.scheduledFor)
    }
}
