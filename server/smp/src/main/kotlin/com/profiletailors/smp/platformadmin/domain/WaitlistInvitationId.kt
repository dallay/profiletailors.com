package com.profiletailors.smp.platformadmin.domain

import java.util.UUID

@JvmInline
value class WaitlistInvitationId(val value: UUID) {
    companion object {
        fun generate(): WaitlistInvitationId = WaitlistInvitationId(UUID.randomUUID())
        fun of(value: String): WaitlistInvitationId = WaitlistInvitationId(UUID.fromString(value))
    }
}
