package com.profiletailors.smp.tenancy.application

interface TenancyAuthorizationGate {
    suspend fun requireAllowed(permission: TenancyAuthorizationPermission)
}

enum class TenancyAuthorizationPermission {
    WORKSPACE_SETTINGS_MANAGE,
}
