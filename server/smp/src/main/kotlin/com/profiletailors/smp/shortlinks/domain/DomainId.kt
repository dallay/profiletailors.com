package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@JvmInline
@ValueObject
value class DomainId(val value: UUID) {
    companion object {
        fun fromHost(host: String): DomainId = DomainId(UUID.nameUUIDFromBytes(host.trim().lowercase().toByteArray()))
    }
}
