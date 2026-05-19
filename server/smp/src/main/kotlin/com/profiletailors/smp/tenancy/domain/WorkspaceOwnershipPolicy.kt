package com.profiletailors.smp.tenancy.domain

import com.profiletailors.common.domain.error.BusinessRuleValidationException

class WorkspaceOwnershipPolicy {
    fun ensureOwnersRemainActiveMembers(
        ownerships: Set<WorkspaceOwnership>,
        memberships: Set<WorkspaceMembership>,
    ) {
        val activeMemberships = memberships.filter { it.isActive() }.toSet()
        ownerships.forEach { ownership ->
            if (activeMemberships.none { membership -> ownership.matches(membership) }) {
                throw OwnerMustRemainActiveMemberException(ownership.ownerPrincipalId, ownership.workspaceId)
            }
        }
    }

    fun ensureMembershipStatusChangeAllowed(
        ownerships: Set<WorkspaceOwnership>,
        memberships: Set<WorkspaceMembership>,
        membershipToChange: WorkspaceMembership,
        targetStatus: WorkspaceMembershipStatus,
    ) {
        if (targetStatus == WorkspaceMembershipStatus.ACTIVE) {
            return
        }

        val resultingMemberships = memberships.map { membership ->
            if (
                membership.workspaceId == membershipToChange.workspaceId &&
                membership.principalId == membershipToChange.principalId
            ) {
                membership.copy(status = targetStatus)
            } else {
                membership
            }
        }.toSet()

        ensureOwnersRemainActiveMembers(ownerships, resultingMemberships)
    }

    fun ensureAtLeastOneOwner(ownerships: Set<WorkspaceOwnership>) {
        if (ownerships.isEmpty()) {
            throw WorkspaceMustHaveAtLeastOneOwnerException()
        }
    }

    fun ensureOwnerRemovalAllowed(
        ownerships: Set<WorkspaceOwnership>,
        ownershipToRemove: WorkspaceOwnership,
    ) {
        if (ownerships.none { it == ownershipToRemove }) {
            return
        }

        if (ownerships.size == 1) {
            throw LastOwnerRemovalRequiresReplacementException(ownershipToRemove.workspaceId)
        }
    }
}

class WorkspaceMustHaveAtLeastOneOwnerException : BusinessRuleValidationException(
    "A workspace must always have at least one owner.",
)

class LastOwnerRemovalRequiresReplacementException(
    workspaceId: String,
) : BusinessRuleValidationException(
    "The last owner of workspace '$workspaceId' cannot be removed without a replacement.",
)

class OwnerMustRemainActiveMemberException(
    principalId: String,
    workspaceId: String,
) : BusinessRuleValidationException(
    "Owner '$principalId' must remain an active member of workspace '$workspaceId'.",
)
