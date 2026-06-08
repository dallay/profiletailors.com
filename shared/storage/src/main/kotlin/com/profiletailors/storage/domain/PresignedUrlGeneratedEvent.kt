package com.profiletailors.storage.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Event published when a presigned URL is generated for an object.
 * Used for auditing and detecting potential abuse of presigned URL generation.
 */
data class PresignedUrlGeneratedEvent(
    val bucket: String,
    val key: String,
    val expirySeconds: Long,
    val requesterId: String,
    val timestamp: Instant = Instant.now(),
    val expiryTime: Instant
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(bucket.isNotBlank()) { "Bucket cannot be blank" }
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(expirySeconds > 0) { "Expiry seconds must be positive" }
        require(requesterId.isNotBlank()) { "Requester ID cannot be blank" }
    }
}
