package com.profiletailors.smp.privacy.domain

import com.profiletailors.common.domain.ValueObject

/**
 * The type of data subject request supported by the system.
 *
 * @since 1.0.0
 */
@ValueObject
enum class RequestType {
    ACCESS,
    EXPORT,
    CORRECTION,
    DELETION,
}
