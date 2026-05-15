package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.platform.domain.ResourceContext
import java.time.Instant

enum class GrantEffect {
    ALLOW,
    DENY,
}

data class DirectGrant(
    val permission: PermissionKey,
    val effect: GrantEffect,
    val resourceContext: ResourceContext,
    val expiresAt: Instant? = null,
    val conditions: Map<String, String> = emptyMap(),
) {
    fun isActive(at: Instant): Boolean = expiresAt == null || expiresAt.isAfter(at)
}

data class AuthorizationScope(
    val key: String,
)

data class Entitlement(
    val key: String,
    val enabled: Boolean,
)

enum class AuthorizationDecision {
    ALLOW,
    DENY,
}
