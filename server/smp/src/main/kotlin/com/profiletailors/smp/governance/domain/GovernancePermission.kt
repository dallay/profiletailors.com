package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.ValueObject
import com.profiletailors.common.domain.vo.permission.PermissionKey

/**
 * Centralized governance permission keys.
 *
 * Each entry maps to a [PermissionKey] used for authorization checks throughout
 * the governance context. Permissions are seeded in the database via Liquibase
 * changelogs and assigned to workspace roles.
 */
@ValueObject
enum class GovernancePermission(val permissionKey: PermissionKey) {
    MEDIA_READ(PermissionKey.of("workspace", "governance", "media-read")),
    MEDIA_TAKEDOWN(PermissionKey.of("workspace", "governance", "media-takedown")),
}
