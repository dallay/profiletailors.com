package com.profiletailors.smp.platformadmin.application.ports

interface WaitlistQueryTelemetryPort {
    fun recordListQuery(statusFilterApplied: Boolean, emailSearch: Boolean)
}
