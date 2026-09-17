package com.profiletailors.smp.platformadmin.application.contracts

import org.junit.jupiter.api.Test

class UserControlTelemetryTest {

    @Test
    fun `should tolerate all default operations on the noop telemetry`() {
        val telemetry = UserControlTelemetry.noop()

        telemetry.recordAuthorizationRejected()
        telemetry.recordAuthorizationFailure("disable")
        telemetry.recordIdempotencyReplay()
        telemetry.record("disable", "success")
    }
}
