package com.profiletailors.smp.privacy.domain

import com.profiletailors.common.domain.ValueObject

/**
 * Strongly-typed identifier for a [DataSubjectRequest].
 *
 * Wraps a string with a "dsr-" prefix followed by a UUID.
 *
 * @since 1.0.0
 */
@ValueObject
@JvmInline
value class DataSubjectRequestId(val value: String) {
    init {
        require(value.startsWith("dsr-")) {
            "DataSubjectRequestId must start with 'dsr-' prefix but was: $value"
        }
    }

    companion object {
        /**
         * Creates a new random [DataSubjectRequestId] with a "dsr-" prefix.
         */
        fun random(): DataSubjectRequestId {
            val uuid = java.util.UUID.randomUUID()
            return DataSubjectRequestId("dsr-$uuid")
        }
    }
}
