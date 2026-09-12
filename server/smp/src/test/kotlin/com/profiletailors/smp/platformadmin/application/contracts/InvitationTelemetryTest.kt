package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.kotest.assertions.throwables.shouldNotThrowAny
import org.junit.jupiter.api.Test

class InvitationTelemetryTest {

    @Test
    fun `should do nothing when noop telemetry records invitation events`() {
        val noop = InvitationTelemetry.noop()

        shouldNotThrowAny {
            noop.recordInvitationCreated()
            noop.recordInvitationRevoked()
            noop.recordInvitationAccepted(InvitationTarget.NEW_WORKSPACE)
            noop.recordInvitationExpired()
            noop.recordInvitationReplayRejected()
        }
    }
}
