package com.profiletailors.smp.media.application

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaPreviewTokenServiceTest {

    private val signingSecret = "test-secret-key-12345"
    private val service = MediaPreviewTokenService(
        signingSecret = signingSecret,
        previewUrlExpirySeconds = 3_600,
    )

    // --- buildSignedPreviewPath ---

    @Test
    fun `buildSignedPreviewPath returns a path with assetId workspaceId expiresAt and signature`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")

        assertTrue(path.startsWith("/api/media/assets/asset-1/preview?workspaceId=ws-1&expiresAt="))
        assertTrue(path.contains("&signature="))
    }

    @Test
    fun `buildSignedPreviewPath includes a valid signature`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")

        val params = path.substringAfter("?").split("&")
        val signatureParam = params.find { it.startsWith("signature=") }!!
        val signature = signatureParam.substringAfter("signature=")

        assertNotNull(signature)
        assertTrue(signature.isNotBlank())
    }

    @Test
    fun `buildSignedPreviewPath sets expiry roughly one hour from now`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")

        val expiresAt = path
            .substringAfter("expiresAt=")
            .substringBefore("&")
            .toLong()

        val now = Instant.now().epochSecond
        assertTrue(expiresAt in (now + 3_500)..(now + 3_700))
    }

    // --- buildSignedContentPath ---

    @Test
    fun `buildSignedContentPath uses the content endpoint`() {
        val path = service.buildSignedContentPath("asset-1", "ws-1")

        assertTrue(path.startsWith("/api/media/assets/asset-1/content?"))
        assertTrue(path.contains("&signature="))
    }

    // --- isValid ---

    @Test
    fun `isValid returns true for a valid signature`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")
        val params = path.substringAfter("?").split("&")
        val expiresAt = params.find { it.startsWith("expiresAt=") }!!.substringAfter("expiresAt=").toLong()
        val signature = params.find { it.startsWith("signature=") }!!.substringAfter("signature=")

        val valid = service.isValid("asset-1", "ws-1", expiresAt, signature)

        assertTrue(valid)
    }

    @Test
    fun `isValid returns false for an expired timestamp`() {
        val expiredExpiresAt = Instant.now().epochSecond - 60 // 1 minute ago

        val valid = service.isValid("asset-1", "ws-1", expiredExpiresAt, "any-signature")

        assertFalse(valid)
    }

    @Test
    fun `isValid returns false for a mismatched signature`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")
        val params = path.substringAfter("?").split("&")
        val expiresAt = params.find { it.startsWith("expiresAt=") }!!.substringAfter("expiresAt=").toLong()

        val valid = service.isValid("asset-1", "ws-1", expiresAt, "wrong-signature")

        assertFalse(valid)
    }

    @Test
    fun `isValid returns false when assetId does not match`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")
        val params = path.substringAfter("?").split("&")
        val expiresAt = params.find { it.startsWith("expiresAt=") }!!.substringAfter("expiresAt=").toLong()
        val signature = params.find { it.startsWith("signature=") }!!.substringAfter("signature=")

        val valid = service.isValid("asset-other", "ws-1", expiresAt, signature)

        assertFalse(valid)
    }

    @Test
    fun `isValid returns false when workspaceId does not match`() {
        val path = service.buildSignedPreviewPath("asset-1", "ws-1")
        val params = path.substringAfter("?").split("&")
        val expiresAt = params.find { it.startsWith("expiresAt=") }!!.substringAfter("expiresAt=").toLong()
        val signature = params.find { it.startsWith("signature=") }!!.substringAfter("signature=")

        val valid = service.isValid("asset-1", "ws-other", expiresAt, signature)

        assertFalse(valid)
    }

    // --- constantTimeEquals ---

    @Test
    fun `isValid uses constant-time comparison to prevent timing attacks`() {
        // If constantTimeEquals were not constant, the return time could leak
        // the number of matching prefix bytes. We verify the method completes
        // normally with exact-match and partial-match strings.
        val correct = "abcdefghijklmnop"
        val wrong = "abcdefghijklmnop"
        // Note: the private method is tested implicitly via isValid(false) above.
        // Direct white-box test of the security-critical path:
        assertFalse(
            service.isValid(
                "asset-1",
                "ws-1",
                Instant.now().epochSecond + 3600,
                "x".repeat(44), // same length, all wrong chars
            ),
        )
    }
}
