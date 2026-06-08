package com.profiletailors.smp.publishing.infrastructure.credentials

import java.time.OffsetDateTime
import java.util.*
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("secure_credentials")
data class EncryptedCredential(
    @Id
    val id: UUID = UUID.randomUUID(),
    val ownerType: String,
    val ownerId: UUID,
    val encryptedPayload: ByteArray,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)
