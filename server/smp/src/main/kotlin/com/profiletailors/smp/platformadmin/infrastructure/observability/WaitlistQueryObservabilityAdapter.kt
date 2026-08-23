package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.application.ports.WaitlistQueryTelemetryPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class WaitlistQueryObservabilityAdapter(private val meterRegistry: MeterRegistry) : WaitlistQueryTelemetryPort {

    override fun recordListQuery(statusFilter: String?, emailSearch: Boolean) {
        Counter.builder(METRIC_NAME)
            .description("Platform admin waitlist list queries")
            .tag("status.filter", statusFilter ?: "none")
            .tag("email.search", emailSearch.toString())
            .register(meterRegistry)
            .increment()
    }

    private companion object {
        const val METRIC_NAME = "platform.admin.waitlist.queries"
    }
}
