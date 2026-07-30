package com.profiletailors.smp.platform.infrastructure.http

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthcheckControllerTest {

    private val controller = HealthcheckController()

    @Test
    fun `healthcheck returns OK`() = runTest {
        val result = controller.healthcheck()
        assertEquals("OK", result)
    }
}
