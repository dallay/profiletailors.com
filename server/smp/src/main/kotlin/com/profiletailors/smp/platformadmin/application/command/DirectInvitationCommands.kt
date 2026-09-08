package com.profiletailors.smp.platformadmin.application.command

import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import java.util.UUID

data class CreateInvitationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val email: String,
    val target: InvitationTarget,
    val workspaceId: String?,
)

data class RevokeInvitationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val invitationId: UUID,
    val expectedVersion: Long,
)

data class ResendInvitationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val invitationId: UUID,
)
