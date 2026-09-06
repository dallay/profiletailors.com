package com.profiletailors.smp.authorization.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EntitlementTest {

    @Test
    fun `Entitlement accepts non-blank key`() {
        assertDoesNotThrow {
            Entitlement(key = "publishing:write", enabled = true)
        }
    }

    @Test
    fun `Entitlement accepts blank key`() {
        assertThrows<IllegalArgumentException> {
            Entitlement(key = "   ", enabled = true)
        }
    }

    @Test
    fun `Entitlement accepts disabled state`() {
        assertDoesNotThrow {
            Entitlement(key = "analytics:read", enabled = false)
        }
    }
}
