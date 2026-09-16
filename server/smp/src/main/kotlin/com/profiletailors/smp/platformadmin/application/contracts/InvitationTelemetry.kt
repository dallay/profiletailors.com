package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.InvitationTarget

interface InvitationTelemetry {
    fun recordInvitationCreated()

    fun recordInvitationRevoked()

    fun recordInvitationAccepted(target: InvitationTarget)

    fun recordInvitationExpired()

    fun recordInvitationReplayRejected()

    fun recordBulkInvite(requested: Int, invited: Int, skipped: Int, failed: Int)

    companion object {
        fun noop(): InvitationTelemetry = object : InvitationTelemetry {
            override fun recordInvitationCreated() = Unit

            override fun recordInvitationRevoked() = Unit

            override fun recordInvitationAccepted(target: InvitationTarget) = Unit

            override fun recordInvitationExpired() = Unit

            override fun recordInvitationReplayRejected() = Unit

            override fun recordBulkInvite(requested: Int, invited: Int, skipped: Int, failed: Int) = Unit
        }
    }
}
