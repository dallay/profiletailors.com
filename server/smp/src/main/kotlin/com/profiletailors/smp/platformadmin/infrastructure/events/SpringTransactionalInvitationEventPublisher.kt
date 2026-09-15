package com.profiletailors.smp.platformadmin.infrastructure.events

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.context.PayloadApplicationEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalEventPublisher

@Component
internal class SpringTransactionalInvitationEventPublisher(
    private val transactionalEventPublisher: TransactionalEventPublisher,
) : InvitationEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        transactionalEventPublisher.publishEvent { context ->
            PayloadApplicationEvent(context, event)
        }.awaitSingleOrNull()
    }
}
