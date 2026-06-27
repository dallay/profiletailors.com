package com.profiletailors.spring.boot.config

import com.profiletailors.common.infrastructure.security.HmacHasher
import com.profiletailors.common.infrastructure.security.Sha256Hasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class HasherRegistryTest {

    @Test
    fun `should return default sha256 hasher when name is null`() {
        val registry = HasherRegistry(SecurityProperties(default = "sha256"))

        val hasher = registry.get(null)
        val expected = Sha256Hasher().hash("data")

        assertEquals(expected, hasher.hash("data"))
    }

    @Test
    fun `should return requested hasher by name`() {
        val registry = HasherRegistry(
            SecurityProperties(default = "sha256", ipHmacSecret = "some-secret"),
        )

        val hasher = registry.get("hmac")

        assertNotNull(hasher)
        val expectedSha256 = Sha256Hasher().hash("data")
        val actualHmac = hasher.hash("data")
        kotlin.test.assertNotEquals(expectedSha256, actualHmac)
    }

    @Test
    fun `should fall back to default hasher for unknown names`() {
        val registry = HasherRegistry(SecurityProperties(default = "sha256"))

        val hasher = registry.get("nonexistent")

        assertEquals(Sha256Hasher().hash("data"), hasher.hash("data"))
    }

    @Test
    fun `should fail fast when hmac default but secret is blank`() {
        assertFailsWith<IllegalStateException> {
            HasherRegistry(SecurityProperties(default = "hmac", ipHmacSecret = ""))
        }
    }

    @Test
    fun `should allow hmac default when secret is configured`() {
        val secret = "my-secret"
        val registry = HasherRegistry(
            SecurityProperties(default = "hmac", ipHmacSecret = secret),
        )

        val hasher = registry.get(null)

        assertEquals(HmacHasher(secret).hash("test"), hasher.hash("test"))
    }

    @Test
    fun `should throw for blank hmac secret`() {
        assertFailsWith<IllegalStateException> {
            HasherRegistry(SecurityProperties(default = "hmac", ipHmacSecret = "  "))
        }
    }
}
