package com.profiletailors.smp.platformadmin.application.contracts

interface InvitationTelemetry {
    fun recordInvitationCreated()

    fun recordInvitationRevoked()
}
