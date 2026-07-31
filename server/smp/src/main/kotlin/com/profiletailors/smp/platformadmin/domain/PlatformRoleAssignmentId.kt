package com.profiletailors.smp.platformadmin.domain

import java.util.UUID

@JvmInline
value class PlatformRoleAssignmentId(val value: UUID) {
    companion object {
        fun generate(): PlatformRoleAssignmentId = PlatformRoleAssignmentId(UUID.randomUUID())
        fun of(value: String): PlatformRoleAssignmentId = PlatformRoleAssignmentId(UUID.fromString(value))
    }
}
