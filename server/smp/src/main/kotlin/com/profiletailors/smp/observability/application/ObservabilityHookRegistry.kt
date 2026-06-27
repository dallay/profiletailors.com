package com.profiletailors.smp.observability.application

import com.profiletailors.smp.observability.domain.MetricsHook
import com.profiletailors.smp.observability.domain.RateLimitHook

class ObservabilityHookRegistry(val metricsHook: MetricsHook, val rateLimitHook: RateLimitHook)
