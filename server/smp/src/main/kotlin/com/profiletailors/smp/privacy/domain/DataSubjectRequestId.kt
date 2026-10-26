package com.profiletailors.smp.privacy.domain

import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@ValueObject
@JvmInline
value class DataSubjectRequestId(val value: String) {
    init {
        require(UUID.fromString(value).toString() == value) {
            "DataSubjectRequestId must be a canonical UUID: $value"
        }
    }

    companion object {
        fun random(): DataSubjectRequestId = DataSubjectRequestId(UUID.randomUUID().toString())
    }
}
