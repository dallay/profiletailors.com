package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@ValueObject
@JvmInline
value class InvitationId(val value: UUID) {
    companion object {
        fun generate(): InvitationId = InvitationId(UUID.randomUUID())
        fun fromString(value: String): InvitationId = InvitationId(UUID.fromString(value))
    }
}
