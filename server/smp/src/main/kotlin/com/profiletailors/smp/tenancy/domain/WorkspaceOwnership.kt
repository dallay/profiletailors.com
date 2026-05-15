package com.profiletailors.smp.tenancy.domain

import com.profiletailors.smp.identity.domain.PrincipalType

data class WorkspaceOwnership(
    val workspaceId: String,
    val ownerPrincipalId: String,
    val ownerPrincipalType: PrincipalType,
) {
    fun belongsTo(principalId: String): Boolean = ownerPrincipalId == principalId
}
