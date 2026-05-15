package com.profiletailors.smp.authorization.domain

enum class RoleCategory {
    SYSTEM,
    WORKSPACE,
    CUSTOM,
}

data class Role(
    val key: String,
    val category: RoleCategory,
    val permissions: Set<PermissionKey>,
)
