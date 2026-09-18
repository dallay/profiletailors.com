package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.application.contracts.UserControlTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class UserControlObservability(private val meterRegistry: MeterRegistry) : UserControlTelemetry {
    override fun record(operation: String, outcome: String) {
        Counter.builder(METRIC_NAME)
            .description("Platform admin user control requests")
            .tag("operation", operation)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
    }

    override fun recordAuthorizationRejected(operation: String) {
        record(operation, "rejected")
        recordAuthorizationFailure(operation)
    }

    override fun recordAuthorizationFailure(operation: String) {
        Counter.builder(AUTHORIZATION_FAILURE_METRIC_NAME)
            .description("Platform admin user control authorization failures")
            .tag("operation", operation)
            .register(meterRegistry)
            .increment()
    }

    override fun recordIdempotencyReplay() {
        record("idempotency", "idempotent")
    }

    private companion object {
        const val METRIC_NAME = "profiletailors.admin.user_control.requests"
        const val AUTHORIZATION_FAILURE_METRIC_NAME =
            "profiletailors.admin.user_control.authorization_failures"
    }
}
