package com.profiletailors.smp.platformadmin.application.ports

interface WaitlistQueryTelemetryPort {
    fun recordListQuery(statusFilter: String?, emailSearch: Boolean)
}
