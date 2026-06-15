package com.profiletailors.common.domain.workspace

import com.profiletailors.common.domain.context.PrincipalType

/**
 * Status of a workspace membership.
 *
 * - [ACTIVE]: the member has full access to the workspace.
 * - [SUSPENDED]: the member's access is temporarily revoked.
 * - [REMOVED]: the member has been permanently removed from the workspace.
 *
 * @since 1.0.0
 */
enum class WorkspaceMembershipStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED,
}

/**
 * Snapshot of a principal's membership in a workspace.
 *
 * Captures the current state of the membership at a point in time, including
 * the principal's identity, role assignments, and membership status.
 *
 * [isActive] provides a quick check for authorization purposes.
 *
 * @since 1.0.0
 */
interface WorkspaceMembershipSnapshot {
    val id: String
    val workspaceId: String
    val principalId: String
    val principalType: PrincipalType
    val status: WorkspaceMembershipStatus
    val roleKeys: Set<String>

    fun isActive(): Boolean = status == WorkspaceMembershipStatus.ACTIVE
}
