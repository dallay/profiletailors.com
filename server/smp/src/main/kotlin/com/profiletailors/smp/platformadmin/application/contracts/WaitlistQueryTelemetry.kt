package com.profiletailors.smp.platformadmin.application.contracts

fun interface WaitlistQueryTelemetry {
    fun recordListQuery(statusFilterApplied: Boolean, emailSearch: Boolean)
}
