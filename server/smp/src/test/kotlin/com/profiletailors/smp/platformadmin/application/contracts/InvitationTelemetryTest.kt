package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class InvitationTelemetryTest {

    @Test
    fun `noop telemetry executes without throwing exception`() {
        val noop = InvitationTelemetry.noop()

        assertDoesNotThrow {
            noop.recordInvitationCreated()
            noop.recordInvitationRevoked()
            noop.recordInvitationAccepted(InvitationTarget.NEW_WORKSPACE)
            noop.recordInvitationExpired()
            noop.recordInvitationReplayRejected()
        }
    }
}
