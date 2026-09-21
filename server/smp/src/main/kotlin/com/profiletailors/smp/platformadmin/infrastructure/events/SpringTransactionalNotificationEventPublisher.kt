package com.profiletailors.smp.platformadmin.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.smp.platformadmin.application.contracts.NotificationEventPublisher
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.context.PayloadApplicationEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalEventPublisher

@Component
internal class SpringTransactionalNotificationEventPublisher(
    private val transactionalEventPublisher: TransactionalEventPublisher,
) : NotificationEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        transactionalEventPublisher.publishEvent { context ->
            PayloadApplicationEvent(context, event)
        }.awaitSingleOrNull()
    }
}
