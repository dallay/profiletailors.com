package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class InvitationDeliveryStatus {
    PENDING,
    QUEUED,
    SENT,
    FAILED,
}
