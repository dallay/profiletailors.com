package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

data class InvitationNotificationRequested(
    val invitationId: UUID,
    val commandId: String,
    val kind: InvitationDeliveryKind,
) : BaseDomainEvent() {
    init {
        require(commandId.isNotBlank()) { "Invitation notification commandId must not be blank" }
    }
}

enum class InvitationDeliveryKind {
    INITIAL,
    RESEND,
}
