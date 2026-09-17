package com.profiletailors.smp.tenancy.application

/**
 * Read-only repository port for workspace queries.
 *
 * Defines the contract for querying workspaces from the application layer
 * without depending on infrastructure implementations.
 * The infrastructure layer provides the actual implementation.
 */
interface WorkspaceReadRepository {
    suspend fun findWorkspacesByPrincipal(principalId: String): List<WorkspaceSummary>
    suspend fun findMembershipsByPrincipal(principalId: String): List<WorkspaceMembershipSummary>
}
