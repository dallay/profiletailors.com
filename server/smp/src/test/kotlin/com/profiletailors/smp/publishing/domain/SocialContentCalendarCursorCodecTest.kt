package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64

class SocialContentCalendarCursorCodecTest {
    private val cursor = SocialContentCalendarCursor(
        version = CalendarCursorVersion(CalendarCursorVersion.V1),
        workspaceId = "workspace-1",
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "soacc-1",
        externalPostId = "post|with|pipes",
    )

    @Test
    fun `encodes the versioned six-field envelope`() {
        val payload = String(
            Base64.getUrlDecoder().decode(SocialContentCalendarCursorCodec.encode(cursor)),
            Charsets.UTF_8,
        )

        payload.split('\u001F') shouldBe listOf(
            "1",
            "workspace-1",
            "2026-08-01T10:00:00Z",
            "LINKEDIN",
            "soacc-1",
            "post|with|pipes",
        )
    }

    @Test
    fun `encodes six fields as unpadded url safe base64`() {
        val encoded = SocialContentCalendarCursorCodec.encode(cursor)

        encoded shouldNotBe ""
        encoded.endsWith('=') shouldBe false
        encoded.matches(Regex("[A-Za-z0-9_-]+")) shouldBe true
        SocialContentCalendarCursorCodec.decode(encoded) shouldBe cursor
    }

    @Test
    fun `roundtrips fields without treating pipe characters as separators`() {
        val decoded = SocialContentCalendarCursorCodec.decode(
            SocialContentCalendarCursorCodec.encode(cursor),
        )

        decoded.workspaceId shouldBe "workspace-1"
        decoded.externalPostId shouldBe "post|with|pipes"
        decoded.publishedAt shouldBe cursor.publishedAt
    }

    @Test
    fun `rejects padded base64 tokens`() {
        val padded = "${SocialContentCalendarCursorCodec.encode(cursor)}="

        padded.endsWith('=') shouldBe true
        shouldThrow<InvalidSocialContentCursorException> {
            SocialContentCalendarCursorCodec.decode(padded)
        }
    }

    @Test
    fun `rejects decoded payload with a delimiter in an identity field`() {
        val token = encodePayload(
            "1",
            "workspace-1",
            "2026-08-01T10:00:00Z",
            "LINKEDIN",
            "soacc-1",
            "post\u001Fid",
        )

        shouldThrow<InvalidSocialContentCursorException> {
            SocialContentCalendarCursorCodec.decode(token)
        }
    }

    @Test
    fun `rejects invalid utf8 payloads`() {
        val validPrefix = "1\u001Fworkspace-1\u001F2026-08-01T10:00:00Z\u001FLINKEDIN\u001Fsoacc-1\u001F"
            .toByteArray(Charsets.UTF_8)
        val token = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(validPrefix + byteArrayOf(0xC3.toByte(), 0x28))

        shouldThrow<InvalidSocialContentCursorException> {
            SocialContentCalendarCursorCodec.decode(token)
        }
    }

    @Test
    fun `rejects blank malformed and wrong-shape tokens`() {
        listOf(
            "",
            "   ",
            "!!!not-base64!!!",
            encodePayload("1", "workspace-1", "2026-08-01T10:00:00Z"),
        ).forEach { token ->
            shouldThrow<InvalidSocialContentCursorException> {
                SocialContentCalendarCursorCodec.decode(token)
            }
        }
    }

    @Test
    fun `rejects unsupported version timestamp provider and blank identity fields`() {
        listOf(
            encodePayload("2", "workspace-1", "2026-08-01T10:00:00Z", "LINKEDIN", "soacc-1", "post-1"),
            encodePayload("1", "workspace-1", "not-an-instant", "LINKEDIN", "soacc-1", "post-1"),
            encodePayload("1", "workspace-1", "2026-08-01T10:00:00Z", "UNKNOWN", "soacc-1", "post-1"),
            encodePayload("1", "workspace-1", "2026-08-01T10:00:00Z", "LINKEDIN", "", "post-1"),
            encodePayload("1", "workspace-1", "2026-08-01T10:00:00Z", "LINKEDIN", "soacc-1", ""),
        ).forEach { token ->
            shouldThrow<InvalidSocialContentCursorException> {
                SocialContentCalendarCursorCodec.decode(token)
            }
        }
    }

    private fun encodePayload(vararg fields: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(fields.joinToString("\u001F").toByteArray(Charsets.UTF_8))
}
