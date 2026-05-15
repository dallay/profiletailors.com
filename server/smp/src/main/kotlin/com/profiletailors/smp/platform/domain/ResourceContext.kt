package com.profiletailors.smp.platform.domain

enum class ResourceContextType {
    GLOBAL,
    USER,
    WORKSPACE,
    SYSTEM,
}

data class ResourceContext(
    val type: ResourceContextType,
    val workspaceId: String? = null,
    val resourceOwnerId: String? = null,
    val targetResourceType: String? = null,
    val targetResourceId: String? = null,
    val scopeHints: Set<String> = emptySet(),
)
