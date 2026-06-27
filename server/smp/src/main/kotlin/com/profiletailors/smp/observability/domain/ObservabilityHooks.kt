package com.profiletailors.smp.observability.domain

import com.profiletailors.common.domain.observability.RequestOutcome

fun interface MetricsHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)
}

fun interface RateLimitHook {
    suspend fun onRequestReceived(requestName: String)
}
