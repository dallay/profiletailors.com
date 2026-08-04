package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.Service
import reactor.core.publisher.Mono

/**
 * Checks workspace membership for authenticated users.
 *
 * Stub implementation for PR 2. Real implementation will come in PR 3
 * with actual workspace membership verification against the database.
 */
@Service
class McpWorkspaceMembershipChecker {

    /**
     * Checks if the principal is a member of the specified workspace.
     *
     * Stub implementation: always returns true for now.
     * PR 3 will implement real membership verification.
     *
     * @param workspaceId The workspace ID to check
     * @param principalId The user/principal ID
     * @return Mono emitting true if member, false otherwise
     */
    @Suppress("UnusedParameter")
    fun checkMembership(workspaceId: String, principalId: String): Mono<Boolean> = // Stub: always allow for now
        // Real implementation in PR 3 will query workspace membership table
        Mono.just(true)
}
