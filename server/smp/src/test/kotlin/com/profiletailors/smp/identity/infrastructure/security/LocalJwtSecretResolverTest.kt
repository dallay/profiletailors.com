package com.profiletailors.smp.identity.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LocalJwtSecretResolverTest {

    @Test
    fun `returns configured secret when it is not blank`() {
        val configured = "configured-secret-with-32+bytes-ok-1234"

        val resolved = resolveLocalJwtSecret(configured, envSupplier = { error("env must not be read") })

        assertEquals(configured, resolved)
    }

    @Test
    fun `falls back to env var when configured secret is blank`() {
        val fromEnv: String? = "env-var-secret-with-enough-bytes-1234"

        val resolved = resolveLocalJwtSecret("", envSupplier = { fromEnv })

        assertEquals(fromEnv, resolved)
    }

    @Test
    fun `fails fast when configured secret and env var are both blank`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            resolveLocalJwtSecret("", envSupplier = { null })
        }

        assertEquals(
            "JWT secret is not configured. Set app.security.local-jwt.secret or SMP_LOCAL_JWT_DEV_FALLBACK.",
            ex.message,
        )
    }

    @Test
    fun `fails fast when configured secret is blank and env var is empty string`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            resolveLocalJwtSecret("", envSupplier = { "" })
        }

        assertEquals(
            "JWT secret is not configured. Set app.security.local-jwt.secret or SMP_LOCAL_JWT_DEV_FALLBACK.",
            ex.message,
        )
    }
}
