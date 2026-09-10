package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class InvitationAccepted(
    val invitationId: UUID,
    val principalId: String,
    val workspaceId: String,
    val target: InvitationTarget,
    val outcome: InvitationAcceptanceOutcome = InvitationAcceptanceOutcome.ACCEPTED,
    val occurredAt: Instant = Instant.now(),
) : BaseDomainEvent(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)) {
    init {
        require(principalId.isNotBlank()) { "Accepted principal id must not be blank" }
        require(workspaceId.isNotBlank()) { "Accepted workspace id must not be blank" }
    }
}

enum class InvitationAcceptanceOutcome {
    ACCEPTED,
}
