package com.profiletailors.common.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SystemEnvironmentTest {
    @Test
    fun `getEnvOrDefault should return default for unknown key`() {
        assertEquals("default", SystemEnvironment.getEnvOrDefault("UNKNOWN_KEY_ABC_123", "default"))
    }
}
