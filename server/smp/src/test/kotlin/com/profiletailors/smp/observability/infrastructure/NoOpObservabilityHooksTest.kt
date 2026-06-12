package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.common.domain.observability.RequestOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NoOpObservabilityHooksTest {

    private val metricsHook = NoOpMetricsHook()
    private val rateLimitHook = NoOpRateLimitHook()

    @Test
    fun `NoOpMetricsHook onRequestHandled does not throw`() = runTest {
        metricsHook.onRequestHandled(
            requestName = "test-request",
            outcome = RequestOutcome.SUCCESS,
        )
    }

    @Test
    fun `NoOpRateLimitHook onRequestReceived does not throw`() = runTest {
        rateLimitHook.onRequestReceived(requestName = "test-request")
    }
}
