package com.profiletailors.common.domain.context

/** Categorization of a resource context scope. */
enum class ResourceContextType {
    /** Global — no specific scope. */
    GLOBAL,
    /** User-scoped — owned by a single user. */
    USER,
    /** Workspace-scoped — multi-tenant context. */
    WORKSPACE,
    /** System-level — internal operations. */
    SYSTEM,
}

/**
 * Describes the resource context for an authorization decision.
 *
 * @param type the scope type of the resource
 * @param workspaceId the workspace ID (for WORKSPACE-scoped resources)
 * @param resourceOwnerId the owner's principal ID
 * @param targetResourceType the type of resource being accessed
 * @param targetResourceId the specific resource identifier
 * @param scopeHints additional context hints for authorization
 */
data class ResourceContext(
    val type: ResourceContextType,
    val workspaceId: String? = null,
    val resourceOwnerId: String? = null,
    val targetResourceType: String? = null,
    val targetResourceId: String? = null,
    val scopeHints: Set<String> = emptySet(),
)
