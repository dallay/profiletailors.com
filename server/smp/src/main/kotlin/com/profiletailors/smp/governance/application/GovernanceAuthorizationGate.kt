package com.profiletailors.smp.governance.application

interface GovernanceAuthorizationGate {
    suspend fun requireAllowed(permission: GovernanceAuthorizationPermission)
}

enum class GovernanceAuthorizationPermission {
    MEDIA_READ,
    MEDIA_TAKEDOWN,
    CONSENT_READ,
    CONSENT_WRITE,
    AUDIT_READ,
}