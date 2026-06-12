package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.observability.domain.MetricsHook
import com.profiletailors.smp.observability.domain.RateLimitHook

class NoOpMetricsHook : MetricsHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit
}

class NoOpRateLimitHook : RateLimitHook {
    override suspend fun onRequestReceived(requestName: String) = Unit
}
