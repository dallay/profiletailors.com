package com.profiletailors.smp.platformadmin.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactSensitiveMetadataTest {

    @Test
    fun `redact leaves non-sensitive entries unchanged`() {
        val input = mapOf("action" to "login", "region" to "us-east-1")
        assertEquals(input, redact(input))
    }

    @Test
    fun `redact masks value when key contains password substring`() {
        val result = redact(mapOf("user_password" to "secret123"))
        assertEquals(mapOf("user_password" to "[REDACTED]"), result) // kotlin constant
    }

    @Test
    fun `redact is case-insensitive for key matching`() {
        val result = redact(
            mapOf(
                "AUTHORIZATION_TOKEN" to "bearer abc",
                "X_Secret_Key" to "sk-12345",
            ),
        )
        assertEquals(
            mapOf(
                "AUTHORIZATION_TOKEN" to "[REDACTED]",
                "X_Secret_Key" to "[REDACTED]",
            ),
            result,
        )
    }

    @Test
    fun `redact masks value when key contains token`() {
        val result = redact(mapOf("session_token" to "tok_abc123"))
        assertEquals(mapOf("session_token" to "[REDACTED]"), result)
    }

    @Test
    fun `redact masks api key credential`() {
        val result = redact(mapOf("api_credential" to "ak-xxxx-yyyy"))
        assertEquals(mapOf("api_credential" to "[REDACTED]"), result)
    }

    @Test
    fun `redact returns empty map for empty input`() {
        assertEquals(emptyMap<String, String>(), redact(emptyMap()))
    }

    @Test
    fun `redact handles mixed sensitive and non-sensitive entries`() {
        val result = redact(
            mapOf(
                "action" to "create_user",
                "password" to "supersecret",
                "email" to "user@example.com",
                "api_key" to "key-12345",
            ),
        )
        assertEquals(
            mapOf(
                "action" to "create_user",
                "password" to "[REDACTED]",
                "email" to "user@example.com",
                "api_key" to "[REDACTED]",
            ),
            result,
        )
    }

    @Test
    fun `redact leaves previousMode and newMode configuration metadata unredacted`() {
        val input = mapOf("previousMode" to "OPEN", "newMode" to "CLOSED")
        assertEquals(input, redact(input))
    }

    @Test
    fun `redact takes only a metadata map and has no parameter for targetId or targetType`() {
        val redactParameters = ::redact.parameters
        assertEquals(1, redactParameters.size)
        assertEquals(Map::class, redactParameters.single().type.classifier)
    }

    @Test
    fun `redact keeps takedown report workspace and asset ids`() {
        val input = mapOf(
            "reportId" to "report-1",
            "workspaceId" to "ws-a",
            "assetId" to "asset-1",
        )
        assertEquals(input, redact(input))
    }
}
