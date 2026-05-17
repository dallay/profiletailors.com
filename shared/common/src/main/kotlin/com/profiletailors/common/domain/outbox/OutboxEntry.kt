package com.profiletailors.common.domain.outbox

import java.time.Instant
import java.util.UUID

data class OutboxEntry(
    val id: UUID = UUID.randomUUID(),
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant = Instant.now(),
    val processedAt: Instant? = null,
)
