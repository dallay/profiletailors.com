package com.profiletailors.common.domain.workspace

import com.profiletailors.common.domain.context.PrincipalType

enum class WorkspaceMembershipStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED,
}

interface WorkspaceMembershipSnapshot {
    val id: String
    val workspaceId: String
    val principalId: String
    val principalType: PrincipalType
    val status: WorkspaceMembershipStatus
    val roleKeys: Set<String>

    fun isActive(): Boolean = status == WorkspaceMembershipStatus.ACTIVE
}
