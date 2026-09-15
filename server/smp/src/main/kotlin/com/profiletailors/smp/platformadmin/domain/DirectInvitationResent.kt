package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

data class DirectInvitationResent(
    val invitationId: UUID,
    val operatorPrincipalId: UUID,
    val recipient: String,
    val workspaceName: String,
    val target: InvitationTarget,
    val acceptUrl: String,
    val locale: String?,
    val rawToken: String,
    val deliveryId: UUID,
    val previousInvitationId: UUID,
) : BaseDomainEvent() {
    init {
        require(workspaceName.isNotBlank()) { "Resolved workspace name cannot be blank" }
    }
}
