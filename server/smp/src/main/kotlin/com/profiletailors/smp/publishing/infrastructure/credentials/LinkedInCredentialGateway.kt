package com.profiletailors.smp.publishing.infrastructure.credentials

import java.util.*
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component

data class LinkedInCredentials(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?,
    val scope: String?,
)

interface LinkedInCredentialGateway {
    suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials): UUID
    suspend fun resolveCredential(id: UUID): LinkedInCredentials
}

@Component
class R2dbcLinkedInCredentialGateway(
    private val db: DatabaseClient,
    private val encryptionService: CredentialEncryptionService
) : LinkedInCredentialGateway {
    private val mapper = jacksonObjectMapper()

    override suspend fun storeForOwner(
        ownerType: String,
        ownerId: UUID,
        credentials: LinkedInCredentials
    ): UUID {
        val asJson = mapper.writeValueAsString(credentials)
        val encrypted = encryptionService.encrypt(asJson)
        val existingId = findExistingCredentialId(ownerType, ownerId)

        if (existingId != null) {
            db.sql(
                """
                UPDATE secure_credentials
                SET encrypted_payload = :payload,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """.trimIndent()
            )
                .bind("id", existingId)
                .bind("payload", encrypted)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            return existingId
        }

        val id = UUID.randomUUID()
        return db.sql(
            """
            INSERT INTO secure_credentials(id, owner_type, owner_id, encrypted_payload) 
            VALUES (:id, :ownerType, :ownerId, :payload)
            """.trimIndent()
        )
            .bind("id", id)
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .bind("payload", encrypted)
            .then()
            .thenReturn(id)
            .awaitSingle()
    }

    override suspend fun resolveCredential(id: UUID): LinkedInCredentials {
        return db.sql(
            """
            SELECT encrypted_payload 
            FROM secure_credentials 
            WHERE id = :id
            """.trimIndent()
        )
            .bind("id", id)
            .map { row, _ ->
                val bytes = row.get("encrypted_payload", ByteArray::class.java)
                    ?: throw IllegalStateException("Credential bytes missing for id $id")
                val json = encryptionService.decrypt(bytes)
                mapper.readValue(json, LinkedInCredentials::class.java)
            }
            .one()
            .awaitSingle()
    }

    private suspend fun findExistingCredentialId(ownerType: String, ownerId: UUID): UUID? =
        db.sql(
            """
            SELECT id
            FROM secure_credentials
            WHERE owner_type = :ownerType
              AND owner_id = :ownerId
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """.trimIndent()
        )
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .map { row, _ ->
                row.get("id", UUID::class.java)
                    ?: throw IllegalStateException("Credential id missing for owner $ownerType/$ownerId")
            }
            .all()
            .collectList()
            .awaitSingle()
            .firstOrNull()
}
