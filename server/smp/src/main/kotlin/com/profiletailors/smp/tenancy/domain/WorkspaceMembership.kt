package com.profiletailors.smp.tenancy.domain

import com.profiletailors.smp.identity.domain.PrincipalType

enum class WorkspaceMembershipStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED,
}

data class WorkspaceMembership(
    val id: String = "",
    val workspaceId: String,
    val principalId: String,
    val principalType: PrincipalType,
    val status: WorkspaceMembershipStatus,
    val roleKeys: Set<String> = emptySet(),
) {
    fun isActive(): Boolean = status == WorkspaceMembershipStatus.ACTIVE
}
