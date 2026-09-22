package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.common.domain.bus.event.DomainEvent

fun interface NotificationEventPublisher {
    suspend fun publish(event: DomainEvent)
}
