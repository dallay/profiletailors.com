package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.application.contracts.WaitlistQueryTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class WaitlistQueryObservability(private val meterRegistry: MeterRegistry) : WaitlistQueryTelemetry {

    override fun recordListQuery(statusFilterApplied: Boolean, emailSearch: Boolean) {
        Counter.builder(METRIC_NAME)
            .description("Platform admin waitlist list queries")
            .tag("status.filter", statusFilterApplied.toString())
            .tag("email.search", emailSearch.toString())
            .register(meterRegistry)
            .increment()
    }

    private companion object {
        const val METRIC_NAME = "platform.admin.waitlist.queries"
    }
}
