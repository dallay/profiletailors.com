package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@ValueObject
@JvmInline
value class PlatformRoleAssignmentId(val value: UUID) {
    companion object {
        fun generate(): PlatformRoleAssignmentId = PlatformRoleAssignmentId(UUID.randomUUID())
        fun of(value: String): PlatformRoleAssignmentId = PlatformRoleAssignmentId(UUID.fromString(value))
    }
}
