package com.profiletailors.smp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlatformAdminBootstrapCommandDispatchTest {
    @Test
    fun `exact bootstrap command dispatches without starting the application`() {
        val calls = mutableListOf<String>()

        dispatchSmpCommand(
            args = arrayOf("platform-admin-bootstrap"),
            bootstrap = { calls += "bootstrap" },
            server = { calls += "server" },
        )

        assertEquals(listOf("bootstrap"), calls)
    }

    @Test
    fun `normal arguments start only the application`() {
        val calls = mutableListOf<String>()

        dispatchSmpCommand(
            args = arrayOf("--spring.profiles.active=prod"),
            bootstrap = { calls += "bootstrap" },
            server = { calls += "server" },
        )

        assertEquals(listOf("server"), calls)
    }
}
