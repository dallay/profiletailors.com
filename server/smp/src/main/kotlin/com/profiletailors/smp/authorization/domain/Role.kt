package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class RoleCategory {
    SYSTEM,
    WORKSPACE,
    CUSTOM,
}

@AggregateRoot
data class Role(val key: String, val category: RoleCategory, val permissions: Set<PermissionKey>)
