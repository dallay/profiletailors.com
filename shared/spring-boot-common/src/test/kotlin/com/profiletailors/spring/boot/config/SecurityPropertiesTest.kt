package com.profiletailors.spring.boot.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityPropertiesTest {

    @Test
    fun `should use sha256 as default hasher`() {
        val props = SecurityProperties()
        assertEquals("sha256", props.default)
    }

    @Test
    fun `should have empty ip hmac secret by default`() {
        val props = SecurityProperties()
        assertEquals("", props.ipHmacSecret)
    }

    @Test
    fun `should allow insecure hasher by default`() {
        val props = SecurityProperties()
        assertEquals(false, props.allowInsecureHasher)
    }

    @Test
    fun `should accept custom values`() {
        val props = SecurityProperties(
            default = "hmac",
            ipHmacSecret = "secret-key",
            allowInsecureHasher = true,
        )
        assertEquals("hmac", props.default)
        assertEquals("secret-key", props.ipHmacSecret)
        assertTrue(props.allowInsecureHasher)
    }

    @Test
    fun `should be a data class with copy and equals`() {
        val a = SecurityProperties()
        val b = SecurityProperties()
        assertEquals(a, b)
        assertNotNull(a.copy())
    }
}
