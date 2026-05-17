package com.profiletailors.spring.boot.infrastructure.persistence.outbox

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table

@Table("outbox")
data class OutboxEntity(
    @Id
    private val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
    val processedAt: Instant? = null,
    val status: String = "PENDING",
    val attempts: Int = 0,
    val lastAttemptAt: Instant? = null,
    val errorMessage: String? = null,
    @CreatedBy
    val createdBy: String = "system",
    @CreatedDate
    val createdAt: Instant = Instant.now(),
    @LastModifiedBy
    val updatedBy: String? = null,
    @LastModifiedDate
    val updatedAt: Instant? = null,
) : Persistable<UUID> {
    override fun getId(): UUID = id
    override fun isNew(): Boolean = true // Always new for outbox entries as we generate UUIDs
}
