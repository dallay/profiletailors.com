package com.profiletailors.notifications.domain

import kotlin.test.Test
import kotlin.test.assertEquals

internal class PayloadRedactorTest {

    @Test
    fun `redactPayload removes token from top level`() {
        val payload = mapOf(
            "recipient" to "test@example.com",
            "token" to "secret-token-value",
            "channel" to "EMAIL",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["token"])
        assertEquals("test@example.com", result["recipient"])
    }

    @Test
    fun `redactPayload removes password from top level`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "password" to "supersecret",
            "status" to "PENDING",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["password"])
        assertEquals("user@test.com", result["recipient"])
    }

    @Test
    fun `redactPayload removes acceptUrl from top level`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "acceptUrl" to "https://example.com/accept",
            "templateId" to "platform.invitation",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["acceptUrl"])
        assertEquals("platform.invitation", result["templateId"])
    }

    @Test
    fun `redactPayload removes rawToken from top level`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "rawToken" to "abc123xyz",
            "status" to "SENT",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["rawToken"])
    }

    @Test
    fun `redactPayload removes verificationToken from top level`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "verificationToken" to "verify-123",
            "channel" to "SMS",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["verificationToken"])
    }

    @Test
    fun `redactPayload removes nested sensitive keys`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "data" to mapOf(
                "token" to "nested-token",
                "safeField" to "safe-value",
            ),
        )
        val result = redactPayload(payload)

        val nestedResult = result["data"] as? Map<*, *>
        assertEquals("[REDACTED]", nestedResult?.get("token"))
        assertEquals("safe-value", nestedResult?.get("safeField"))
    }

    @Test
    fun `redactPayload preserves allowed keys`() {
        val payload = mapOf(
            "id" to "ntf-123",
            "channel" to "EMAIL",
            "templateId" to "platform.welcome",
            "recipient" to "user@test.com",
            "status" to "SENT",
            "createdAt" to "2026-07-20T10:00:00Z",
            "errorMessage" to "some error",
        )
        val result = redactPayload(payload)
        assertEquals("ntf-123", result["id"])
        assertEquals("EMAIL", result["channel"])
        assertEquals("platform.welcome", result["templateId"])
        assertEquals("user@test.com", result["recipient"])
        assertEquals("SENT", result["status"])
        assertEquals("2026-07-20T10:00:00Z", result["createdAt"])
        assertEquals("some error", result["errorMessage"])
    }

    @Test
    fun `redactPayload handles empty map`() {
        val result = redactPayload(emptyMap())
        assertEquals(emptyMap<String, Any?>(), result)
    }

    @Test
    fun `redactPayload handles null values`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "optionalField" to null,
        )
        val result = redactPayload(payload)
        assertEquals("user@test.com", result["recipient"])
        assertEquals(null, result["optionalField"])
    }

    @Test
    fun `redactPayload handles case insensitive key matching`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "TOKEN" to "uppercase-token",
            "Password" to "mixed-case-password",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["TOKEN"])
        assertEquals("[REDACTED]", result["Password"])
    }

    @Test
    fun `redactPayload removes keys containing sensitive substrings`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "access_token" to "access-token-value",
            "reset_password_token" to "reset-token",
            "myToken" to "my-token-value",
            "authToken" to "auth-token-value",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["access_token"])
        assertEquals("[REDACTED]", result["reset_password_token"])
        assertEquals("[REDACTED]", result["myToken"])
        assertEquals("[REDACTED]", result["authToken"])
    }

    @Test
    fun `redactPayload handles deeply nested sensitive keys`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "level1" to mapOf(
                "level2" to mapOf(
                    "token" to "deeply-nested-token",
                    "safeData" to "safe",
                ),
            ),
        )
        val result = redactPayload(payload)

        val level1 = result["level1"] as? Map<*, *>

        val level2 = level1?.get("level2") as? Map<*, *>
        assertEquals("[REDACTED]", level2?.get("token"))
        assertEquals("safe", level2?.get("safeData"))
    }

    @Test
    fun `redactPayload redacts sensitive keys inside collections`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "attempts" to listOf(
                mapOf("token" to "first-token", "code" to "500"),
                mapOf("token" to "second-token", "code" to "503"),
            ),
            "tags" to listOf("email", "retryable"),
        )
        val result = redactPayload(payload)

        val attempts = result["attempts"] as? List<*>
        val first = attempts?.get(0) as? Map<*, *>
        val second = attempts?.get(1) as? Map<*, *>
        assertEquals("[REDACTED]", first?.get("token"))
        assertEquals("500", first?.get("code"))
        assertEquals("[REDACTED]", second?.get("token"))
        assertEquals("503", second?.get("code"))
        assertEquals(listOf("email", "retryable"), result["tags"])
    }

    @Test
    fun `redactPayload removes invitation and reset link keys`() {
        val payload = mapOf(
            "recipient" to "user@test.com",
            "inviteLink" to "https://example.com/invite?token=xyz789",
            "resetLink" to "https://example.com/reset?token=abc123",
            "resetUrl" to "https://example.com/reset?token=def456",
        )
        val result = redactPayload(payload)
        assertEquals("[REDACTED]", result["inviteLink"])
        assertEquals("[REDACTED]", result["resetLink"])
        assertEquals("[REDACTED]", result["resetUrl"])
        assertEquals("user@test.com", result["recipient"])
    }

    @Test
    fun `redactSensitiveValue redacts values containing sensitive substrings`() {
        assertEquals("[REDACTED]", redactSensitiveValue("Failed to send email: authentication token expired"))
        assertEquals("[REDACTED]", redactSensitiveValue("invalid password supplied"))
        assertEquals("SMTP timeout", redactSensitiveValue("SMTP timeout"))
        assertEquals("", redactSensitiveValue(""))
    }
}
