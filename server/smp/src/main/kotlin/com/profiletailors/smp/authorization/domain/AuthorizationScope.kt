package com.profiletailors.smp.authorization.domain

data class AuthorizationScope(
    val permission: PermissionKey,
    val resourceContextType: com.profiletailors.common.domain.context.ResourceContextType,
    val targetResourceType: String,
    val allowedTargetResourceIds: Set<String>,
)

