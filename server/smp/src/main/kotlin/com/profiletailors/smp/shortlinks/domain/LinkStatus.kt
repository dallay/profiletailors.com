package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class LinkStatus {
    ACTIVE,
    DISABLED,
    EXPIRED,
    QUARANTINED,
    DELETED,
}
