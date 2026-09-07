package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

data class DirectInvitationResent(
    val invitationId: UUID,
    val operatorPrincipalId: UUID,
    val recipient: String,
    val workspaceName: String,
    val acceptUrl: String,
    val locale: String?,
    val rawToken: String,
    val previousInvitationId: UUID,
) : BaseDomainEvent()
