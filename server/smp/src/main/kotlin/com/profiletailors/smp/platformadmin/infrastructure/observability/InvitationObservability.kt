package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class InvitationObservability(private val meterRegistry: MeterRegistry) : InvitationTelemetry {

    override fun recordInvitationCreated() {
        Counter.builder(CREATED_METRIC_NAME)
            .description("Platform admin direct invitations created")
            .register(meterRegistry)
            .increment()
    }

    override fun recordInvitationRevoked() {
        Counter.builder(REVOKED_METRIC_NAME)
            .description("Platform admin direct invitations revoked")
            .register(meterRegistry)
            .increment()
    }

    private companion object {
        const val CREATED_METRIC_NAME = "platform.invitations.created"
        const val REVOKED_METRIC_NAME = "platform.invitations.revoked"
    }
}
