package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import org.springframework.modulith.NamedInterface
import java.util.UUID

@NamedInterface("domain")
data class InvitationRevoked(val invitationId: UUID, val revokedBy: UUID) : BaseDomainEvent()
