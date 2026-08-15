package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.context.ResourceContextType

@AggregateRoot
data class AuthorizationScope(
    val permission: PermissionKey,
    val resourceContextType: ResourceContextType,
    val targetResourceType: String,
    val allowedTargetResourceIds: Set<String>,
)
