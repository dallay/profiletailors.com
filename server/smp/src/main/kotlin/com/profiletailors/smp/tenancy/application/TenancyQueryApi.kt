package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.bus.query.Query

/**
 * Query to list all workspaces the authenticated principal belongs to.
 *
 * Returns workspaces where the principal has an active membership (OWNER or MEMBER).
 * This endpoint does NOT require an X-Workspace-Id header — it uses the
 * authenticated principal from the security context.
 */
object GetWorkspacesForPrincipalQuery : Query<List<WorkspaceSummary>>

/**
 * Lightweight workspace summary returned to the client for the workspace picker.
 *
 * @property workspaceId Unique workspace identifier (e.g. "ws-{uuid}")
 * @property name Display name of the workspace
 * @property role The principal's role in this workspace ("OWNER" or "MEMBER")
 */
data class WorkspaceSummary(
    val workspaceId: String,
    val name: String,
    val role: String,
)
