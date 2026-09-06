package com.profiletailors.smp.authorization.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EntitlementTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `rejects a blank key`(key: String) {
        shouldThrow<IllegalArgumentException> {
            Entitlement(key = key, enabled = true)
        }
    }

    @Test
    fun `retains a valid key and state`() {
        Entitlement(key = "workspace.read", enabled = false) shouldBe
            Entitlement(key = "workspace.read", enabled = false)
    }
}
