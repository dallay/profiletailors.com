package com.profiletailors.smp.platformadmin.application

import java.util.UUID

object PlatformPrincipalIds {
    private const val USER_PREFIX = "user-"

    fun toUuid(principalId: String): UUID = UUID.fromString(principalId.removePrefix(USER_PREFIX))

    fun fromUuid(principalId: UUID): String = "$USER_PREFIX$principalId"
}
