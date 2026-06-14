package com.profiletailors.smp.tenancy.application

/**
 * Provisions a default workspace for a newly registered user.
 *
 * Creates the workspace, adds the user as owner, and creates an active membership.
 * This is a write-once service called during user registration — not a general-purpose
 * workspace management service.
 */
fun interface WorkspaceProvisioningService {
    data class ProvisionedWorkspace(
        val workspaceId: String,
        val name: String,
    )

    /**
     * Creates a default workspace for the given principal.
     *
     * @param principalId The user's principal ID (e.g., "user-abc123")
     * @param displayName The user's display name, used to derive the workspace name
     * @return The provisioned workspace with ID and name
     */
    suspend fun provisionDefaultWorkspace(
        principalId: String,
        displayName: String,
    ): ProvisionedWorkspace
}
