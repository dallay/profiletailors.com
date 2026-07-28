package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent

data class PasswordResetRequested(
    val principalId: String,
    val email: String,
    val rawResetToken: String,
    val locale: String = "en",
) : BaseDomainEvent()
