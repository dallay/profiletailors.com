@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@Suppress("MaxLineLength", "StringShouldBeRawString")
class BulkValidationPipelineTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)

    private fun pipeline(
        capabilityValidator: ProviderCapabilityValidator = ProviderCapabilityValidator { },
    ): BulkValidationPipeline = BulkValidationPipeline(
        providerCapabilityValidator = capabilityValidator,
        clock = fixedClock,
    )

    @Test
    fun `skips blank lines and returns two rows`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello world,2026-09-01T10:00:00Z,UTC,,

            Second post,2026-09-02T10:00:00Z,UTC,,

        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(2, result.rows.size)
        assertTrue(result.rows.all { it.status == BulkRowStatus.VALID })
    }

    @Test
    fun `flags INVALID_DATE and MISSING_CONTENT`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            ,not-a-date,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        val row = result.rows.first()
        assertEquals(BulkRowStatus.INVALID, row.status)
        assertTrue(row.errors.any { it.code == "INVALID_DATE" })
        assertTrue(row.errors.any { it.code == "MISSING_CONTENT" })
    }

    @Test
    fun `second duplicate warns DUPLICATE`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Same body,2026-09-01T10:00:00Z,UTC,,
            Same body,2026-09-01T10:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(2, result.rows.size)
        assertEquals(BulkRowStatus.VALID, result.rows[0].status)
        assertTrue(result.rows[1].errors.any { it.code == "DUPLICATE" })
    }

    @Test
    fun `handles BOM and quoted commas`() = runTest {
        val csv = "\uFEFFbodyText,scheduledFor,timezone,media_urls,hashtags\n\"Hello, world\",2026-09-01T10:00:00Z,UTC,,"
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        assertEquals("Hello, world", result.rows.first().bodyText)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
    }

    @Test
    fun `invalid media url flagged as INVALID_MEDIA ssrf guard`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,http://192.168.1.1/image.png,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        val row = result.rows.first()
        assertEquals(BulkRowStatus.INVALID, row.status)
        assertTrue(row.errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `invalid media url private 10 dot flagged`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hi,2026-09-01T10:00:00Z,UTC,http://10.0.0.1/file.jpg,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_MEDIA" })
    }

    @Test
    fun `capability violation PDF flagged`() = runTest {
        val failingValidator = ProviderCapabilityValidator { input ->
            if (input.assets.any { it.mediaType == "APPLICATION/PDF" }) {
                throw IllegalArgumentException("CAPABILITY_VIOLATION")
            }
        }
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/doc.pdf,
        """.trimIndent()
        val result = pipeline(capabilityValidator = failingValidator).validate("ws-1", csv)
        val row = result.rows.first()
        assertEquals(BulkRowStatus.INVALID, row.status)
        assertTrue(row.errors.any { it.code == "CAPABILITY_VIOLATION" })
    }

    @Test
    fun `missing header returns INVALID error`() = runTest {
        val csv = "not-a-header\nvalue1,value2"
        val result = pipeline().validate("ws-1", csv)
        assertTrue(
            result.rows.isEmpty() ||
                result.rows.any { it.errors.any { e -> e.code == "INVALID_HEADER" } } ||
                result.rows.isEmpty(),
        )
    }

    @Test
    fun `valid row with body and future date passes`() = runTest {
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Valid content,2026-09-10T10:00:00Z,UTC,,
        """.trimIndent()
        val result = pipeline().validate("ws-1", csv)
        assertEquals(1, result.rows.size)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
        assertTrue(result.rows.first().errors.isEmpty())
    }

    @Test
    fun `threads media row uses registry validator`() = runTest {
        val threadsAccount = SocialAccount(
            id = "account-threads-1",
            socialConnectionId = "connection-threads-1",
            workspaceId = "ws-threads",
            provider = SocialProvider.THREADS,
            providerAccountId = "threads-user-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "WireMock Threads User",
            status = SocialConnectionStatus.ACTIVE,
        )
        val accountRepository = object : SocialAccountRepository {
            override suspend fun upsert(account: SocialAccount): SocialAccount = account
            override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? = null
            override suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount? = threadsAccount
            override suspend fun listActiveByWorkspace(workspaceId: String): List<SocialAccount> =
                listOf(threadsAccount)
            override suspend fun deleteByConnectionId(connectionId: String) = Unit
        }
        val linkedInOnlyValidator = ProviderCapabilityValidator { input ->
            require(input.provider == SocialProvider.LINKEDIN) {
                "LinkedIn capability validator only supports LINKEDIN."
            }
        }
        val threadsValidator = ProviderCapabilityValidator { }
        val registry = ProviderCapabilityRegistry.fromValidators(
            SocialProvider.THREADS to threadsValidator,
        )
        val threadsPipeline = BulkValidationPipeline(
            providerCapabilityValidator = linkedInOnlyValidator,
            clock = fixedClock,
            socialAccountRepository = accountRepository,
            providerCapabilityRegistry = registry,
        )
        val csv = """
            bodyText,scheduledFor,timezone,media_urls,hashtags
            Hello Threads,2026-09-01T10:00:00Z,UTC,https://cdn.example.com/image.jpg,
        """.trimIndent()
        val result = threadsPipeline.validate("ws-threads", csv)
        assertEquals(1, result.rows.size)
        assertEquals(BulkRowStatus.VALID, result.rows.first().status)
    }
}
