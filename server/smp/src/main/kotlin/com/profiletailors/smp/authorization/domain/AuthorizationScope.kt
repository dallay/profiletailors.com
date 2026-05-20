package com.profiletailors.smp.authorization.domain

data class AuthorizationScope(
    val permission: PermissionKey,
    val resourceContextType: com.profiletailors.smp.platform.domain.ResourceContextType,
    val targetResourceType: String,
    val allowedTargetResourceIds: Set<String>,
)

