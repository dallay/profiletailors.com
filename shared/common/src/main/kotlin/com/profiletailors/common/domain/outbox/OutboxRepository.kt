package com.profiletailors.common.domain.outbox

interface OutboxRepository {
    suspend fun save(entry: OutboxEntry)
}
