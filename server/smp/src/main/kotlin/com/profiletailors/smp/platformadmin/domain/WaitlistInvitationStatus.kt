package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class WaitlistInvitationStatus {
    ACTIVE,
    ACCEPTED,
    EXPIRED,
    REVOKED,
    SUPERSEDED,
}
