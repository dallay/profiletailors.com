package com.profiletailors.smp.platformadmin.application

import java.util.UUID

object PlatformPrincipalIds {
    private const val USER_PREFIX = "user-"

    /**
 * Converts a platform principal ID into a UUID.
 *
 * @param principalId The principal ID with its platform prefix.
 * @return The UUID represented by the principal ID.
 */
fun toUuid(principalId: String): UUID = UUID.fromString(principalId.removePrefix(USER_PREFIX))

    /**
 * Converts a UUID to a platform principal ID.
 *
 * @param principalId The UUID to convert.
 * @return The principal ID with the platform user prefix.
 */
fun fromUuid(principalId: UUID): String = "$USER_PREFIX$principalId"
}
