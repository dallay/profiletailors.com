package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@JvmInline
@ValueObject
value class OwnerId(val value: UUID) {
    companion object {
        fun from(raw: String): OwnerId = OwnerId(UUID.fromString(raw))
    }
}
