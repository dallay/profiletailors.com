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
    val expectedVersion: Long,
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

data class DeactivateUserCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val principalId: String,
    val expectedVersion: Long,
)

data class ReactivateUserCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val principalId: String,
    val expectedVersion: Long,
)

const val BULK_INVITE_MAX_ENTRIES = 50

data class BulkInviteWaitlistEntriesCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val entryIds: List<String>,
)

enum class BulkInviteOutcome {
    INVITED,
    SKIPPED,
    FAILED,
}

data class BulkEntryResult(
    val entryId: String,
    val outcome: BulkInviteOutcome,
    val invitationId: UUID? = null,
    val code: String? = null,
)

data class BulkInviteSummary(val requested: Int, val invited: Int, val skipped: Int, val failed: Int)

data class BulkInviteWaitlistEntriesResult(val results: List<BulkEntryResult>, val summary: BulkInviteSummary)
