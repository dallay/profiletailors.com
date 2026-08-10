package com.profiletailors.smp.platformadmin.domain

import java.util.UUID

@JvmInline
value class InvitationId(val value: UUID) {
    companion object {
        fun generate(): InvitationId = InvitationId(UUID.randomUUID())
        fun of(value: String): InvitationId = InvitationId(UUID.fromString(value))
    }
}
