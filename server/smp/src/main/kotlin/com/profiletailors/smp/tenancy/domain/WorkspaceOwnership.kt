package com.profiletailors.smp.tenancy.domain

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import java.time.Instant

data class WorkspaceOwnership(
    val workspaceId: String,
    val ownerPrincipalId: String,
    val ownerPrincipalType: PrincipalType,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
) {
    fun belongsTo(principalId: String): Boolean = ownerPrincipalId == principalId

    fun matches(membership: WorkspaceMembership): Boolean =
        workspaceId == membership.workspaceId &&
            ownerPrincipalId == membership.principalId &&
            ownerPrincipalType == membership.principalType
}
