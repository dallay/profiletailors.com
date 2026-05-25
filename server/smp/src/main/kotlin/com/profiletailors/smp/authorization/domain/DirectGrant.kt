package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.ResourceContext
import java.time.Instant

data class DirectGrant(
    val permission: PermissionKey,
    val effect: GrantEffect,
    val resourceContext: ResourceContext,
    val expiresAt: Instant? = null,
    val conditions: Map<String, String> = emptyMap(),
) {
    fun isActive(at: Instant): Boolean = expiresAt == null || expiresAt.isAfter(at)
}
