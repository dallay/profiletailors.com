package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@JvmInline
@ValueObject
value class LinkId(val value: UUID) {
    companion object {
        fun generate(): LinkId = LinkId(UUID.randomUUID())
    }
}
