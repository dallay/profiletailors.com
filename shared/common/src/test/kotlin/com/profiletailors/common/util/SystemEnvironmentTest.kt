package com.profiletailors.common.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SystemEnvironmentTest {
    @AfterEach
    fun tearDown() {
        SystemEnvironment.resetLookup()
    }

    @Test
    fun `getEnvOrDefault should return default for unknown key`() {
        SystemEnvironment.getEnvOrDefault("UNKNOWN_KEY_ABC_123", "default") shouldBe "default"
    }

    @Test
    fun `getEnvOrDefault should return default for blank values`() {
        SystemEnvironment.setLookup { if (it == "BLANK") "  " else null }
        SystemEnvironment.getEnvOrDefault("BLANK", "default") shouldBe "default"
    }

    @Test
    fun `getEnvOrDefault should return value when present`() {
        SystemEnvironment.setLookup { if (it == "KEY") "value" else null }
        SystemEnvironment.getEnvOrDefault("KEY", "default") shouldBe "value"
    }
}
