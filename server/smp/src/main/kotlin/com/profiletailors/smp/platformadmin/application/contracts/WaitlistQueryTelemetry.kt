package com.profiletailors.smp.platformadmin.application.contracts

interface WaitlistQueryTelemetry {
    fun recordListQuery(statusFilterApplied: Boolean, emailSearch: Boolean)
}
