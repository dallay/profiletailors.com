package com.profiletailors.spring.boot.infrastructure.persistence.outbox

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.outbox.OutboxEntry
import com.profiletailors.common.domain.outbox.OutboxRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

@Service
@ConditionalOnMissingBean(OutboxRepository::class)
class R2dbcOutboxRepository(
    private val repository: OutboxR2dbcRepository,
) : OutboxRepository {

    override suspend fun save(entry: OutboxEntry) {
        val entity = OutboxEntity(
            id = entry.id,
            aggregateType = entry.aggregateType,
            aggregateId = entry.aggregateId,
            eventType = entry.eventType,
            payload = entry.payload,
            occurredAt = entry.occurredAt,
            status = "PENDING",
            attempts = 0,
        )
        repository.save(entity)
    }
}
