package com.profiletailors.common.domain.model

import java.util.UUID

@JvmInline
value class WorkspaceId(val value: UUID) {
    companion object {
        fun random(): WorkspaceId = WorkspaceId(UUID.randomUUID())
    }
}
