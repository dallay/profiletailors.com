package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@ValueObject
@JvmInline
value class WaitlistInvitationId(val value: UUID) {
    companion object {
        fun generate(): WaitlistInvitationId = WaitlistInvitationId(UUID.randomUUID())
        fun of(value: String): WaitlistInvitationId = WaitlistInvitationId(UUID.fromString(value))
    }
}
