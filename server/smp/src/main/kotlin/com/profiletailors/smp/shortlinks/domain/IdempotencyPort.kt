package com.profiletailors.smp.shortlinks.domain

data class IdempotencyRecord(val payloadHash: String, val resultJson: String)

interface IdempotencyPort {
    suspend fun findStoredResult(key: String, ownerId: OwnerId): IdempotencyRecord?

    suspend fun claim(key: String, ownerId: OwnerId, payloadHash: String): Boolean

    suspend fun store(key: String, ownerId: OwnerId, payloadHash: String, resultJson: String)
}
