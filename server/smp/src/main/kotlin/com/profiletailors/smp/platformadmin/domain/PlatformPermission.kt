package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class PlatformPermission(val key: String) {
    DASHBOARD_READ("platform.dashboard.read"),
    WAITLIST_READ("platform.waitlist.read"),
    WAITLIST_INVITE("platform.waitlist.invite"),
    WAITLIST_CANCEL("platform.waitlist.cancel"),
    INVITATIONS_READ("platform.invitations.read"),
    INVITATIONS_RESEND("platform.invitations.resend"),
    INVITATIONS_REVOKE("platform.invitations.revoke"),
    USERS_READ("platform.users.read"),
    USERS_WORKSPACES_READ("platform.users.workspaces.read"),
    AUDIT_READ("platform.audit.read"),
    OPERATORS_READ("platform.operators.read"),
    OPERATORS_MANAGE("platform.operators.manage"),
    PUBLISHING_STALE_READ("platform.publishing.stale.read"),
    ;

    companion object {
        private val BY_KEY = entries.associateBy { it.key }

        fun of(key: String): PlatformPermission =
            BY_KEY[key] ?: throw IllegalArgumentException("Unknown platform permission key: $key")
    }
}

val PLATFORM_ROLE_PERMISSIONS: Map<PlatformRole, Set<PlatformPermission>> = mapOf(
    PlatformRole.PLATFORM_OWNER to PlatformPermission.entries.toSet(),
    PlatformRole.PLATFORM_OPERATOR to setOf(
        PlatformPermission.DASHBOARD_READ,
        PlatformPermission.WAITLIST_READ,
        PlatformPermission.WAITLIST_INVITE,
        PlatformPermission.WAITLIST_CANCEL,
        PlatformPermission.INVITATIONS_READ,
        PlatformPermission.INVITATIONS_RESEND,
        PlatformPermission.INVITATIONS_REVOKE,
        PlatformPermission.USERS_READ,
        PlatformPermission.USERS_WORKSPACES_READ,
        PlatformPermission.AUDIT_READ,
        PlatformPermission.OPERATORS_READ,
        PlatformPermission.PUBLISHING_STALE_READ,
    ),
    PlatformRole.SUPPORT_AGENT to setOf(
        PlatformPermission.USERS_READ,
        PlatformPermission.WAITLIST_READ,
        PlatformPermission.USERS_WORKSPACES_READ,
    ),
    PlatformRole.AUDITOR to setOf(
        PlatformPermission.AUDIT_READ,
        PlatformPermission.DASHBOARD_READ,
        PlatformPermission.WAITLIST_READ,
        PlatformPermission.USERS_READ,
        PlatformPermission.OPERATORS_READ,
    ),
)

fun Set<PlatformRole>.effectivePermissions(): Set<PlatformPermission> =
    flatMap { PLATFORM_ROLE_PERMISSIONS[it] ?: emptySet() }.toSet()
