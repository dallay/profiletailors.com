package com.profiletailors.smp.platformadmin.application

import java.util.UUID

object PlatformPrincipalIds {
    private const val LEGACY_USER_PREFIX = "user-"

    fun toUuid(principalId: String): UUID {
        val canonical = principalId.removePrefix(LEGACY_USER_PREFIX).lowercase()
        val uuid = UUID.fromString(canonical)
        require(uuid.toString() == canonical) { "Principal ID must be a canonical UUID." }
        return uuid
    }

    fun fromUuid(principalId: UUID): String = principalId.toString()

    fun fromUuid(principalId: String): String = toUuid(principalId).toString()
}
