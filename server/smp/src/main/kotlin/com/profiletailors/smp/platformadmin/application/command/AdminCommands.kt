package com.profiletailors.smp.platformadmin.application.command

import com.profiletailors.smp.platformadmin.domain.PlatformRole
import java.util.UUID

data class InviteWaitlistEntryCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val waitlistEntryId: String,
)

data class ResendWaitlistInvitationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val invitationId: UUID,
)

data class RevokeWaitlistInvitationCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val invitationId: UUID,
)

data class CancelWaitlistEntryCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val waitlistEntryId: String,
    val reason: String,
)

data class AssignPlatformRoleCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val targetPrincipalId: UUID,
    val role: PlatformRole,
)

data class RevokePlatformRoleCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val targetPrincipalId: UUID,
    val role: PlatformRole,
)
