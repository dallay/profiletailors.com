package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

data class InvitationIssued(
    val invitationId: UUID,
    val recipientEmail: String,
    val workspaceName: String,
    val locale: String?,
    val rawToken: String,
) : BaseDomainEvent()
