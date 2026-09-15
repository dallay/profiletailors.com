package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.common.domain.bus.event.DomainEvent

fun interface InvitationEventPublisher {
    suspend fun publish(event: DomainEvent)
}
