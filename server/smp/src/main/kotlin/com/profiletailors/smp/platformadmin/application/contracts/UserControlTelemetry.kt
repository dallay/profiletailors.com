package com.profiletailors.smp.platformadmin.application.contracts

interface UserControlTelemetry {
    fun record(operation: String, outcome: String)

    fun recordAuthorizationRejected(operation: String = "authorization") {
        record(operation, "rejected")
        recordAuthorizationFailure(operation)
    }

    fun recordAuthorizationFailure(operation: String) = Unit

    fun recordIdempotencyReplay() {
        record("idempotency", "idempotent")
    }

    companion object {
        fun noop(): UserControlTelemetry = object : UserControlTelemetry {
            override fun record(operation: String, outcome: String) = Unit
        }
    }
}
