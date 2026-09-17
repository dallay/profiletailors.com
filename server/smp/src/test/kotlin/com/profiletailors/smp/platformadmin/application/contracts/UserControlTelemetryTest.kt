package com.profiletailors.smp.platformadmin.application.contracts

import org.junit.jupiter.api.Test

class UserControlTelemetryTest {

    @Test
    fun `noop tolerates all default operations`() {
        val telemetry = UserControlTelemetry.noop()

        telemetry.recordAuthorizationRejected()
        telemetry.recordAuthorizationFailure("disable")
        telemetry.recordIdempotencyReplay()
        telemetry.record("disable", "success")
    }
}
