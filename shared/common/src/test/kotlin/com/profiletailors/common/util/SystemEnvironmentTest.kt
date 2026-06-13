package com.profiletailors.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SystemEnvironmentTest {
    @Test
    fun `should return environment variable value`() {
        val pathValue = SystemEnvironment.getEnvOrDefault("PATH", "default")
        assertEquals(System.getenv("PATH"), pathValue)
    }

    @Test
    fun `should return default when variable does not exist`() {
        val result = SystemEnvironment.getEnvOrDefault(
            "THIS_ENV_VAR_DOES_NOT_EXIST_XYZ_123",
            "fallback",
        )
        assertEquals("fallback", result)
    }

    @Test
    fun `should return default for null`() {
        val result = SystemEnvironment.getEnvOrDefault(
            "THIS_ENV_VAR_DOES_NOT_EXIST_XYZ_123",
            "default",
        )
        assertEquals("default", result)
    }
}
