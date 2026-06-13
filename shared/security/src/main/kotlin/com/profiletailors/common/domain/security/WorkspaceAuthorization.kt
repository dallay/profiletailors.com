package com.profiletailors.common.domain.security

import java.util.UUID

/**
 * Authorization check for workspace-level access.
 *
 * Throws an authorization exception if the user does not have access to the workspace.
 */
interface WorkspaceAuthorization {
    /** Ensure a user has access to a workspace (UUID overload). */
    suspend fun ensureAccess(workspaceId: UUID, userId: UUID)
    /** Ensure a user has access to a workspace (string overload). */
    suspend fun ensureAccess(workspaceId: String, userId: String)
}
