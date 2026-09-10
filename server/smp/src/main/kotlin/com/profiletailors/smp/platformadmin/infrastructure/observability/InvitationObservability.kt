package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
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

    override fun recordInvitationAccepted(target: InvitationTarget) {
        Counter.builder(ACCEPTED_METRIC_NAME)
            .description("Platform invitations accepted")
            .tag("target", target.name)
            .register(meterRegistry)
            .increment()
    }

    override fun recordInvitationExpired() {
        Counter.builder(EXPIRED_METRIC_NAME)
            .description("Platform invitations rejected after expiry")
            .register(meterRegistry)
            .increment()
    }

    override fun recordInvitationReplayRejected() {
        Counter.builder(REPLAY_REJECTED_METRIC_NAME)
            .description("Platform invitations rejected after prior acceptance")
            .register(meterRegistry)
            .increment()
    }

    private companion object {
        const val ACCEPTED_METRIC_NAME = "platform.invitations.accepted"
        const val CREATED_METRIC_NAME = "platform.invitations.created"
        const val EXPIRED_METRIC_NAME = "platform.invitations.expired"
        const val REPLAY_REJECTED_METRIC_NAME = "platform.invitations.replay_rejected"
        const val REVOKED_METRIC_NAME = "platform.invitations.revoked"
    }
}
