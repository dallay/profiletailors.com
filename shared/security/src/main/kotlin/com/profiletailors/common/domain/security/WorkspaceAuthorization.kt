package com.profiletailors.common.domain.security

import java.util.UUID

interface WorkspaceAuthorization {
    suspend fun ensureAccess(workspaceId: UUID, userId: UUID)
    suspend fun ensureAccess(workspaceId: String, userId: String)
}
