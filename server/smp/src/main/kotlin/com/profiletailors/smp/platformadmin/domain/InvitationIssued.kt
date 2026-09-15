package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import org.springframework.modulith.NamedInterface
import java.util.UUID

@NamedInterface("domain")
data class InvitationIssued(
    val invitationId: UUID,
    val recipientEmail: String,
    val workspaceName: String,
    val target: InvitationTarget,
    val locale: String?,
    val rawToken: String,
    val deliveryId: UUID? = null,
) : BaseDomainEvent() {
    init {
        require(workspaceName.isNotBlank()) { "Resolved workspace name cannot be blank" }
        require(deliveryId == null) { "Initial invitation delivery must not carry a delivery id" }
    }
}
