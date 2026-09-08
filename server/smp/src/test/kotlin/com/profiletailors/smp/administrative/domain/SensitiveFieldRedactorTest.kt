package com.profiletailors.smp.administrative.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SensitiveFieldRedactorTest {

    @Test
    fun `password key is redacted`() {
        val input = mapOf("password" to "secret123", "action" to "LOGIN")
        val result = redact(input)
        assertThat(result).containsEntry("action", "LOGIN")
        assertThat(result).doesNotContainKey("password")
    }

    @Test
    fun `token substring keys are redacted`() {
        val input = mapOf("accessToken" to "abc", "userToken" to "xyz", "name" to "Alice")
        val result = redact(input)
        assertThat(result).containsEntry("name", "Alice")
        assertThat(result).doesNotContainKey("accessToken")
        assertThat(result).doesNotContainKey("userToken")
    }

    @Test
    fun `secret key is redacted`() {
        val input = mapOf("apiSecret" to "top-secret", "endpoint" to "/api/users")
        val result = redact(input)
        assertThat(result).containsEntry("endpoint", "/api/users")
        assertThat(result).doesNotContainKey("apiSecret")
    }

    @Test
    fun `credential key is redacted`() {
        val input = mapOf("credential" to "value123", "user" to "alice")
        val result = redact(input)
        assertThat(result).containsEntry("user", "alice")
        assertThat(result).doesNotContainKey("credential")
    }

    @Test
    fun `key substring key is redacted`() {
        val input = mapOf("apiKey" to "abc123", "resourceId" to "res-1")
        val result = redact(input)
        assertThat(result).containsEntry("resourceId", "res-1")
        assertThat(result).doesNotContainKey("apiKey")
    }

    @Test
    fun `case insensitive matching`() {
        val input = mapOf("PASSWORD" to "secret", "MyToken" to "value", "SeCrEtKeY" to "xyz")
        val result = redact(input)
        assertThat(result).doesNotContainKey("PASSWORD")
        assertThat(result).doesNotContainKey("MyToken")
        assertThat(result).doesNotContainKey("SeCrEtKeY")
    }

    @Test
    fun `null input returns empty map`() {
        val result = redact(null)
        assertThat(result).isEmpty()
    }

    @Test
    fun `empty map returns empty map`() {
        val result = redact(emptyMap())
        assertThat(result).isEmpty()
    }

    @Test
    fun `no sensitive keys returns identical entries`() {
        val input = mapOf("action" to "UPDATE", "targetId" to "123")
        val result = redact(input)
        assertThat(result).containsEntry("action", "UPDATE")
        assertThat(result).containsEntry("targetId", "123")
    }

    @Test
    fun `mixed sensitive and non-sensitive keys`() {
        val input = mapOf(
            "password" to "secret",
            "username" to "alice",
            "accessToken" to "abc",
            "email" to "alice@example.com",
            "resetToken" to "xyz",
            "profileId" to "p-1",
        )
        val result = redact(input)
        assertThat(result).containsEntry("username", "alice")
        assertThat(result).containsEntry("email", "alice@example.com")
        assertThat(result).containsEntry("profileId", "p-1")
        assertThat(result).doesNotContainKey("password")
        assertThat(result).doesNotContainKey("accessToken")
        assertThat(result).doesNotContainKey("resetToken")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "invitationToken",
            "resetToken",
            "refreshToken",
            "accessToken",
            "userToken",
            "apiToken",
            "sessionToken",
        ],
    )
    fun `compound camelCase variants are redacted`(key: String) {
        val input = mapOf(key to "redacted-value", "action" to "LOGIN")
        val result = redact(input)
        assertThat(result).containsEntry("action", "LOGIN")
        assertThat(result).doesNotContainKey(key)
    }

    @Test
    fun `invitationToken exact key is redacted`() {
        val input = mapOf("invitationToken" to "tok-abc", "event" to "INVITE_SENT")
        val result = redact(input)
        assertThat(result).containsEntry("event", "INVITE_SENT")
        assertThat(result).doesNotContainKey("invitationToken")
    }

    @Test
    fun `resetToken exact key is redacted`() {
        val input = mapOf("resetToken" to "tok-reset", "userId" to "u-1")
        val result = redact(input)
        assertThat(result).containsEntry("userId", "u-1")
        assertThat(result).doesNotContainKey("resetToken")
    }

    @Test
    fun `refreshToken exact key is redacted`() {
        val input = mapOf("refreshToken" to "tok-refresh", "clientId" to "c-1")
        val result = redact(input)
        assertThat(result).containsEntry("clientId", "c-1")
        assertThat(result).doesNotContainKey("refreshToken")
    }

    @Test
    fun `accessToken exact key is redacted`() {
        val input = mapOf("accessToken" to "tok-access", "expiresIn" to "3600")
        val result = redact(input)
        assertThat(result).containsEntry("expiresIn", "3600")
        assertThat(result).doesNotContainKey("accessToken")
    }
}
