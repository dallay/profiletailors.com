@file:Suppress("MaxLineLength", "StringShouldBeRawString", "TooManyFunctions", "LongMethod")

package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BulkValidationPipelineCoverageTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)

    private fun pipeline(
        capabilityValidator: ProviderCapabilityValidator = ProviderCapabilityValidator { },
        repo: SocialAccountRepository? = null,
    ): BulkValidationPipeline = BulkValidationPipeline(
        providerCapabilityValidator = capabilityValidator,
        clock = fixedClock,
        socialAccountRepository = repo,
    )

    @Test
    fun `blank csv returns empty`() = runTest {
        val result = pipeline().validate("ws-1", "")
        assertTrue(result.rows.isEmpty())
        val result2 = pipeline().validate("ws-1", "   ")
        assertTrue(result2.rows.isEmpty())
    }

    @Test
    fun `header blank returns empty`() = runTest {
        val csv = "\nHello,2026-09-01T10:00:00Z,UTC,,"
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `invalid header returns INVALID_HEADER row 0`() = runTest {
        val csv = "bad,header\nvalue1,value2"
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        assertEquals(0, result.rows.first().rowIndex)
        assertEquals(BulkRowStatus.INVALID, result.rows.first().status)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_HEADER" })
    }

    @Test
    fun `scheduledFor missing returns INVALID_DATE required`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(BulkRowStatus.INVALID, result.rows.first().status)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_DATE" && it.message.contains("required") })
    }

    @Test
    fun `scheduledFor invalid format returns INVALID_DATE ISO`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,not-a-date,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_DATE" && it.message.contains("ISO-8601") })
    }

    @Test
    fun `scheduledFor past returns INVALID_DATE future`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-08-29T09:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_DATE" && it.message.contains("future") })
    }

    @Test
    fun `MISSING_CONTENT when body and media empty but scheduledFor present`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            ,2026-09-01T10:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "MISSING_CONTENT" })
        assertEquals(BulkRowStatus.INVALID, result.rows.first().status)
    }

    @Test
    fun `blank rows skipped`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,,

            ,,,,

            World,2026-09-02T10:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `padded columns less than canonical still parsed`() = runTest {
        val csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2026-09-01T10:00:00Z"
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        assertEquals("Hello", result.rows.first().bodyText)
    }

    @Test
    fun `media blocked allowlist`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://evil.com/image.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" && it.message.contains("allowlist") })
    }

    @Test
    fun `media blocked size oversized`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/oversized-image.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" && it.message.contains("size") })
    }

    @Test
    fun `media blocked extension exe`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/file.exe,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media blocked private 127`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://127.0.0.1/evil.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media blocked private 192 168`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://192.168.5.10/image.png,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media blocked private 10`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://10.10.10.10/image.png,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media blocked private 169 254`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://169.254.1.5/image.png,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media blocked private 172 range`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://172.20.10.5/image.png,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
        val csv2 = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://172.32.0.1/image.png,
        """.trimIndent()
        val result2 = pipeline().validate("ws-1", csv2)
        assertTrue(
            result2.rows.first().errors.any { it.code == "INVALID_MEDIA" && it.message.contains("allowlist") } ||
                result2.rows.first().errors.none { it.code == "INVALID_MEDIA" && it.message.contains("private") },
        )
    }

    @Test
    fun `media blocked private fc fd fe80 and localhost and 0 0 0 0`() = runTest {
        val hosts =
            listOf(
                "http://[fc00::1]/a.jpg",
                "http://[fd00::1]/a.jpg",
                "http://[fe80::1]/a.jpg",
                "http://localhost/a.jpg",
                "http://0.0.0.0/a.jpg",
            )
        for (host in hosts) {
            val url = host.replace("[", "").replace("]", "")
            val csv = """
                bodyText,scheduledFor,timezone,media_urls,hashtags
                Hello,2026-09-01T10:00:00Z,UTC,$url,
            """.trimIndent()
            val result = pipeline().validate("ws-1", csv)
            assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" }, "expected INVALID_MEDIA for $url")
        }
    }

    @Test
    fun `media blocked invalid scheme`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,ftp://cdn.example.com/file.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `multiple media urls with comma split and one blocked`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,"https://cdn.example.com/a.jpg, https://evil.com/b.jpg",
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `duplicate detection across rows`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Same,2026-09-01T10:00:00Z,UTC,,
            Same,2026-09-01T10:00:00Z,UTC,,
            Different,2026-09-01T10:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(BulkRowStatus.VALID, result.rows[0].status)
        assertTrue(result.rows[1].errors.any { it.code == "DUPLICATE" })
        assertFalse(result.rows[2].errors.any { it.code == "DUPLICATE" })
    }

    @Test
    fun `conflict detection within 15 minutes flags hasConflict`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            First,2026-09-01T10:00:00Z,UTC,,
            Second,2026-09-01T10:10:00Z,UTC,,
            Third,2026-09-01T12:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows[0].hasConflict == true)
        assertTrue(result.rows[1].hasConflict == true)
        assertTrue(result.rows[2].hasConflict != true)
    }

    @Test
    fun `no conflict when rows far apart`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            First,2026-09-01T10:00:00Z,UTC,,
            Second,2026-09-01T11:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.all { it.hasConflict != true })
    }

    @Test
    fun `quoted commas and escaped quotes`() = runTest {
        val csv =
            "bodyText,scheduledFor,timezone,media_urls,hashtags\n" +
                "\"Hello, \"\"world\"\"\",2026-09-01T10:00:00Z,UTC,,"
        val result = pipeline().validate("ws-1", csv)
        assertEquals("Hello, \"world\"", result.rows.first().bodyText)
    }

    @Test
    fun `media urls valid from allowlist pass`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/image.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
        assertTrue(result.rows.first().errors.none { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `media urls with subdomain allowlist pass`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://sub.cdn.example.com/image.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
    }

    @Test
    fun `capability violation adds error`() = runTest {
        val failing = ProviderCapabilityValidator { input ->
            if (input.assets.isNotEmpty()) throw IllegalArgumentException("capability boom")
        }
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/image.jpg,
        """.trimIndent()
        val result = pipeline(capabilityValidator = failing).validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "CAPABILITY_VIOLATION" })
    }

    @Test
    fun `BOM and header case insensitive`() = runTest {
        val csv =
            "\uFEFFBODYText,SCHEDULEDFOR,TIMEZONE,MEDIA_URLS,HASHTAGS\n" +
                "Hello,2026-09-01T10:00:00Z,UTC,,"
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
    }
}
