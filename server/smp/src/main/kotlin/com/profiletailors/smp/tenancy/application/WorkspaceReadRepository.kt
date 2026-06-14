package com.profiletailors.smp.tenancy.application

/**
 * Read-only repository port for workspace queries.
 *
 * Defines the contract for querying workspaces from the application layer
 * without depending on infrastructure implementations.
 * The infrastructure layer provides the actual implementation.
 */
interface WorkspaceReadRepository {
    /**
     * Find all workspaces where the given principal has an ACTIVE membership.
     *
     * @param principalId The authenticated user's principal ID
     * @return List of workspace summaries ordered by name
     */
    suspend fun findWorkspacesByPrincipal(principalId: String): List<WorkspaceSummary>
}
