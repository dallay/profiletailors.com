package com.profiletailors.common.domain

import java.util.UUID

/**
 * The default system user identifier used for system-originated operations.
 *
 * When an action is performed by the system rather than an authenticated principal
 * (e.g., scheduled jobs, internal migrations), [SYSTEM_USER] and [SYSTEM_USER_UUID]
 * serve as the fallback audit identity.
 *
 * @since 1.0.0
 */
const val SYSTEM_USER: String = "system"
val SYSTEM_USER_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
