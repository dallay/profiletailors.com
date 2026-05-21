package com.profiletailors.smp.observability.application

import com.profiletailors.common.domain.observability.RequestOutcome

interface MetricsHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)
}

interface RateLimitHook {
    suspend fun onRequestReceived(requestName: String)
}
