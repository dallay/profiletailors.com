package com.profiletailors.common.domain.model

import java.util.UUID

/**
 * A strongly-typed workspace identifier backed by a [UUID].
 *
 * Use this instead of raw [UUID] values to leverage type safety at the domain level.
 *
 * @since 1.0.0
 */
@JvmInline
value class WorkspaceId(val value: UUID) {
    companion object {
        /** Creates a new random workspace ID. */
        fun random(): WorkspaceId = WorkspaceId(UUID.randomUUID())
    }
}
