package com.profiletailors.smp.platform.infrastructure.http

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HealthcheckControllerTest {

    private val controller = HealthcheckController()

    @Test
    fun `healthcheck returns OK`() = runTest {
        val result = controller.healthcheck()
        result shouldBe "OK"
    }
}
